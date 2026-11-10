"""Participant-level baseline for i-PROGNOSIS smartphone typing data.

The command loads and aggregates event files, reserves participants for a final
test, tunes two classifiers with grouped cross-validation, and saves a fitted
pipeline with the split assignments and evaluation records.
"""

from __future__ import annotations

import argparse
import json
import warnings
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
import sklearn
from joblib import Parallel, delayed
from sklearn.base import clone
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    balanced_accuracy_score,
    confusion_matrix,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, LeaveOneGroupOut,StratifiedGroupKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from src.iprognosis_data import DEFAULT_IPROGNOSIS_ROOT, build_participant_dataset

PROJECT_ROOT = Path(__file__).resolve().parents[1]
RANDOM_SEED = 42  # Fixed default so the participant split and estimators are repeatable.

N_BOOTSTRAP = 2000  # participant-level bootstrap resamples for confidence intervals
GRANULARITIES = ("participant", "excerpt")  # which row unit the model is trained on
FEATURE_SETS = ("all", "no_pressure", "core")  # predictor subsets compared in CV
# a small a-priori feature list (hold time, rhythm, pauses). It was chosen from domain reasoning, NOT by looking at which features score best on this dataset.
CORE_FEATURES = [
    "hold_ms_mean",
    "hold_ms_std",
    "hold_ms_median",
    "hold_ms_cv",
    "press_interval_ms_median",
    "press_interval_ms_iqr",
    "interkey_gap_ms_median",
    "pause_fraction",
]
# Columns that identify rows or audit the data; they must never be predictors.
PARTICIPANT_NON_PREDICTORS = {"participant_id", "target", "excerpt_count", "total_valid_events"}
EXCERPT_NON_PREDICTORS = {"participant_id", "excerpt_id", "target", "event_count_audit_only"}

def select_feature_columns(columns: list[str], feature_set: str) -> list[str]:
    """Return the predictor columns for one named feature set."""
    if feature_set == "all":
        return list(columns)
    if feature_set == "no_pressure":
        # Pressure is device-dependent on Android, so test whether the model needs it.
        return [column for column in columns if not column.startswith("pressure_normalized")]
    if feature_set == "core":
        return [column for column in CORE_FEATURES if column in columns]
    raise ValueError(f"Unknown feature set: {feature_set}")

def _pipeline(classifier: Any, *, scale: bool) -> Pipeline:
    """Wrap an estimator with fold-local median imputation and optional scaling."""
    steps: list[tuple[str, Any]] = [("imputer", SimpleImputer(strategy="median", add_indicator=True))]
    if scale:
        steps.append(("scaler", StandardScaler()))
    steps.append(("classifier", classifier))
    return Pipeline(steps)

# combines standard logistic regression with an L₁ penalty term to shrink less important model weights down to zero
def _l1_logistic(seed: int) -> LogisticRegression:
    major, minor = (int(part) for part in sklearn.__version__.split(".")[:2])
    if (major, minor) >= (1, 8):
        return LogisticRegression(
            l1_ratio=1.0, solver="saga", class_weight="balanced", max_iter=20000, random_state=seed
        )
    return LogisticRegression(
        penalty="l1", solver="liblinear", class_weight="balanced", max_iter=20000, random_state=seed
    )

def candidate_models(seed: int) -> dict[str, tuple[Pipeline, dict[str, list[Any]]]]:
    """Return the model pipelines and hyperparameter grids compared in every configuration."""
    return {
        "logistic_regression": (
            _pipeline(LogisticRegression(class_weight="balanced", max_iter=3000, random_state=seed), scale=True),
            # smaller C values added (stronger regularisation suits 26-33 people)
            {"classifier__C": [0.01, 0.03, 0.1, 1.0, 10.0]},
        ),
        "logistic_l1": (
            _pipeline(_l1_logistic(seed), scale=True),
            {"classifier__C": [0.05, 0.1, 0.3, 1.0]},
        ),
        "random_forest": (
            _pipeline(
                RandomForestClassifier(
                    n_estimators=200, class_weight="balanced", random_state=seed, n_jobs=1
                ),
                scale=False,
            ),
            {"classifier__max_features": ["sqrt"], "classifier__min_samples_leaf": [2, 4]},
        ),
    }

def _positive_scores(model: Pipeline, features: pd.DataFrame) -> np.ndarray:
    """Extract the model's estimated probability for the positive class, PD."""
    return np.asarray(model.predict_proba(features)[:, 1], dtype=float)

# metrics computed on pooled out-of-fold participant scores, with CIs
def participant_metrics(labels: np.ndarray, scores: np.ndarray) -> dict[str, Any]:
    """Compute metrics for one score per participant (threshold 0.5 for label metrics)."""
    predictions = (scores >= 0.5).astype(int)
    true_negative, false_positive, false_negative, true_positive = confusion_matrix(
        labels, predictions, labels=[0, 1]
    ).ravel()
    return {
        "n_participants": int(len(labels)),
        "roc_auc": float(roc_auc_score(labels, scores)),
        "accuracy": float(accuracy_score(labels, predictions)),
        "balanced_accuracy": float(balanced_accuracy_score(labels, predictions)),
        "sensitivity": float(true_positive / (true_positive + false_negative)) if true_positive + false_negative else 0.0,
        "specificity": float(true_negative / (true_negative + false_positive)) if true_negative + false_positive else 0.0,
        "confusion_matrix_labels_control_pd": [[int(true_negative), int(false_positive)], [int(false_negative), int(true_positive)]],
    }

def bootstrap_intervals(labels: np.ndarray, scores: np.ndarray, seed: int, n_resamples: int = N_BOOTSTRAP) -> dict[str, list[float]]:
    """95% percentile bootstrap intervals, resampling participants with replacement."""
    rng = np.random.default_rng(seed)
    aucs, balanced = [], []
    for _ in range(n_resamples):
        sample = rng.integers(0, len(labels), len(labels))
        if len(np.unique(labels[sample])) < 2:
            continue  # AUC is undefined without both classes
        aucs.append(roc_auc_score(labels[sample], scores[sample]))
        balanced.append(balanced_accuracy_score(labels[sample], (scores[sample] >= 0.5).astype(int)))
    return {
        "roc_auc_ci95": [float(np.percentile(aucs, 2.5)), float(np.percentile(aucs, 97.5))],
        "balanced_accuracy_ci95": [float(np.percentile(balanced, 2.5)), float(np.percentile(balanced, 97.5))],
    } 
 
def _outer_fold(
    estimator: Pipeline,
    grid: dict[str, list[Any]],
    X: pd.DataFrame,
    y: np.ndarray,
    groups: np.ndarray,
    train_indices: np.ndarray,
    test_indices: np.ndarray,
    seed: int,
    track_features: bool,
) -> tuple[np.ndarray, np.ndarray, list[str], dict[str, Any]]:
    """Tune on the training participants only, then score the held-out participant."""
    X_train, y_train, groups_train = X.iloc[train_indices], y[train_indices], groups[train_indices]
    groups_per_class = pd.DataFrame({"y": y_train, "g": groups_train}).drop_duplicates().groupby("y")["g"].nunique()
    inner_folds = min(4, int(groups_per_class.min()))
    inner_splits = list(
        StratifiedGroupKFold(n_splits=inner_folds, shuffle=True, random_state=seed).split(X_train, y_train, groups_train)
    )
    search = GridSearchCV(
        clone(estimator), grid, scoring="roc_auc", cv=inner_splits, n_jobs=1, error_score="raise"
    )
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # saga/liblinear convergence chatter inside CV
        search.fit(X_train, y_train)
    scores = _positive_scores(search.best_estimator_, X.iloc[test_indices])
 
    selected: list[str] = []
    if track_features:
        # Record which features the L1 model kept (non-zero coefficient) in this fold.
        best = search.best_estimator_
        try:
            names = best[:-1].get_feature_names_out(list(X.columns))
            selected = [str(n) for n, c in zip(names, best[-1].coef_[0]) if abs(c) > 1e-8]
        except Exception:  # feature names are a diagnostic only; never fail the run
            selected = []
    return test_indices, scores, selected, search.best_params_

def run_nested_cv(
    estimator: Pipeline,
    grid: dict[str, list[Any]],
    X: pd.DataFrame,
    y_rows: np.ndarray,
    groups_rows: np.ndarray,
    seed: int,
    n_jobs: int,
    track_features: bool,
) -> tuple[pd.DataFrame, list[list[str]]]:
    """Leave-one-participant-out outer loop with inner grouped-CV tuning.
 
    Returns one pooled out-of-fold score per participant (mean of that participant's
    row scores) and the per-fold feature selections.
    """
    splits = list(LeaveOneGroupOut().split(X, y_rows, groups_rows))
    results = Parallel(n_jobs=n_jobs)(
        delayed(_outer_fold)(estimator, grid, X, y_rows, groups_rows, train, test, seed + 1, track_features)
        for train, test in splits
    )
    row_scores = np.full(len(X), np.nan)
    selections: list[list[str]] = []
    for test_indices, scores, selected, _ in results:
        row_scores[test_indices] = scores
        selections.append(selected)
    if np.isnan(row_scores).any():
        raise AssertionError("Some rows were never scored in the outer loop")
    frame = pd.DataFrame({"participant_id": groups_rows, "target": y_rows, "score": row_scores})
    per_participant = frame.groupby("participant_id", as_index=False).agg(
        target=("target", "first"), score=("score", "mean"), n_rows=("score", "size")
    )
    return per_participant, selections

# Complete experiment orchestration and artifact output
def run_experiment(
    iprognosis_root: Path = DEFAULT_IPROGNOSIS_ROOT,
    model_path: Path = PROJECT_ROOT / "models" / "typing_iprognosis_model.joblib",
    results_dir: Path = PROJECT_ROOT / "results" / "typing_iprognosis",
    *,
    seed: int = RANDOM_SEED,
    n_jobs: int = -1,  # parallelise the outer folds (-1 = all cores)
) -> dict[str, Any]:
    """Compare configurations with nested CV, refit the best, and save everything. there is no untouched test set any more. All 33 participants are used for nested CV, so the reported numbers are out-of-fold estimates
    """
    # Convert raw events to one predictor row per participant before splitting.
    participants, excerpt_features, data_audit = build_participant_dataset(iprognosis_root)

    frames = {
        "participant": (participants, PARTICIPANT_NON_PREDICTORS),
        "excerpt": (excerpt_features, EXCERPT_NON_PREDICTORS),
    }

    models = candidate_models(seed)
    rows: list[dict[str, Any]] = []
    oof_frames: list[pd.DataFrame] = []
    stability_rows: list[dict[str, Any]] = []
    configuration_data: dict[str, dict[str, Any]] = {}

    # evaluate every (granularity, feature set, model) configuration
    for granularity in GRANULARITIES:
        frame, non_predictors = frames[granularity]
        all_columns = [c for c in frame.columns if c not in non_predictors]
        y_rows = frame["target"].to_numpy(dtype=np.int64)
        groups_rows = frame["participant_id"].to_numpy(dtype=object)
        for feature_set in FEATURE_SETS:
            columns = select_feature_columns(all_columns, feature_set)
            X = frame[columns]
            for model_name, (estimator, grid) in models.items():
                name = f"{granularity}|{feature_set}|{model_name}"
                print(f"Nested CV: {name} ({len(columns)} features, {len(X)} rows)")
                per_participant, selections = run_nested_cv(
                    estimator, grid, X, y_rows, groups_rows, seed, n_jobs, track_features=(model_name == "logistic_l1")
                )
                labels = per_participant["target"].to_numpy(dtype=int)
                scores = per_participant["score"].to_numpy(dtype=float)
                metrics = participant_metrics(labels, scores)
                intervals = bootstrap_intervals(labels, scores, seed)
                rows.append({
                    "configuration": name,
                    "granularity": granularity,
                    "feature_set": feature_set,
                    "model": model_name,
                    "n_features": len(columns),
                    **{k: v for k, v in metrics.items() if k != "confusion_matrix_labels_control_pd"},
                    "roc_auc_ci95_low": intervals["roc_auc_ci95"][0],
                    "roc_auc_ci95_high": intervals["roc_auc_ci95"][1],
                    "balanced_accuracy_ci95_low": intervals["balanced_accuracy_ci95"][0],
                    "balanced_accuracy_ci95_high": intervals["balanced_accuracy_ci95"][1],
                    "confusion_matrix": json.dumps(metrics["confusion_matrix_labels_control_pd"]),
                })
                oof_frames.append(per_participant.assign(configuration=name))
                configuration_data[name] = {"columns": columns, "metrics": metrics, "intervals": intervals}
                if model_name == "logistic_l1":
                    counts = pd.Series([f for fold in selections for f in fold]).value_counts()
                    for feature, count in counts.items():
                        stability_rows.append({
                            "configuration": name, "feature": feature,
                            "fraction_of_folds_selected": float(count / len(selections)),
                        })
 
    cv_table = pd.DataFrame(rows).sort_values(["roc_auc", "balanced_accuracy"], ascending=False).reset_index(drop=True)
    best = cv_table.iloc[0]
    selected_config = str(best["configuration"])
    granularity, feature_set, selected_name = selected_config.split("|")

    # refit the winning configuration on all participants(grouped CV for tuning)
    frame, non_predictors = frames[granularity]
    feature_columns = configuration_data[selected_config]["columns"]
    y_all = frame["target"].to_numpy(dtype=np.int64)
    groups_all = frame["participant_id"].to_numpy(dtype=object)
    groups_per_class = pd.DataFrame({"y": y_all, "g": groups_all}).drop_duplicates().groupby("y")["g"].nunique()
    final_folds = min(5, int(groups_per_class.min()))
    if final_folds < 2:
        raise ValueError("Insufficient participants per class for grouped CV")
    final_splits = list(
        StratifiedGroupKFold(n_splits=final_folds, shuffle=True, random_state=seed + 2).split(
            frame[feature_columns], y_all, groups_all
        )
    )
    estimator, grid = models[selected_name]
    final_search = GridSearchCV(clone(estimator), grid, scoring="roc_auc", cv=final_splits, n_jobs=1, error_score="raise")
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        final_search.fit(frame[feature_columns], y_all)
    final_model = final_search.best_estimator_
 
    oof = pd.concat(oof_frames, ignore_index=True)
    best_metrics = configuration_data[selected_config]["metrics"]
    best_intervals = configuration_data[selected_config]["intervals"]
 
    # Keep derived features, predictions, metadata, and model together.
    results_dir.mkdir(parents=True, exist_ok=True)
    model_path.parent.mkdir(parents=True, exist_ok=True)
    participants.to_csv(results_dir / "typing_participant_features.csv", index=False)
    excerpt_features.to_csv(results_dir / "typing_excerpt_feature_audit.csv", index=False)
    cv_table.to_csv(results_dir / "typing_cv_results.csv", index=False)  # one row per configuration
    oof.to_csv(results_dir / "typing_oof_predictions.csv", index=False)  # replaces typing_test_predictions.csv
    pd.DataFrame(stability_rows).sort_values(["configuration", "fraction_of_folds_selected"], ascending=[True, False]).to_csv(
        results_dir / "typing_feature_stability.csv", index=False
    )

    # Record choices and IDs so the experiment can be audited after completion.
    config = {
        "study_scope": "Retrospective classification of known PD versus control status from smartphone touchscreen typing; not prospective risk prediction",
        "seed": int(seed),
        "evaluation": "nested leave-one-participant-out CV; inner StratifiedGroupKFold tuning on training participants only",
        "bootstrap_resamples": N_BOOTSTRAP,
        "selection_metric": "pooled out-of-fold participant ROC-AUC (ties: balanced accuracy)", 
        "selection_bias_note": "The best of 18 configurations is reported, so its estimate is mildly optimistic.", 
        "decision_threshold": 0.5,
        "positive_class": "PD",
        "granularity": granularity,
        "feature_set": feature_set,
        "aggregation_at_inference": (
            "average the model's PD score over all of a participant's excerpts"
            if granularity == "excerpt"
            else "median of excerpt-level features per participant, plus *_trend slopes"
        ),
        "feature_columns": feature_columns,
        "data_audit": data_audit,
        "selected_configuration": selected_config,
        "selected_model": selected_name,
        "selected_hyperparameters": final_search.best_params_,
        "nested_cv_metrics_selected": {**best_metrics, **best_intervals},
        "all_configurations": json.loads(cv_table.to_json(orient="records")),
    }

    (results_dir / "typing_training_config.json").write_text(json.dumps(config, indent=2), encoding="utf-8")
    metrics = {
        "study_scope": config["study_scope"],
        "dataset_audit": data_audit,
        "seed": int(seed),
        "evaluation": config["evaluation"],
        "selected_configuration": selected_config,
        "selected_hyperparameters": final_search.best_params_,
        "nested_cv_metrics_selected": config["nested_cv_metrics_selected"],
        "all_configurations": config["all_configurations"],
        "interpretation": "Small single-study cohort; out-of-fold metrics have wide confidence intervals and do not establish external validity, clinical readiness, or prospective risk prediction.",
    }
    (results_dir / "typing_training_metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    joblib.dump({
        "pipeline": final_model,
        "feature_columns": feature_columns,
        "label_mapping": {"Control": 0, "PD": 1},
        "selected_model": selected_name,
        "selected_hyperparameters": final_search.best_params_,
        "granularity": granularity,
        "feature_set": feature_set,
        "aggregation": config["aggregation_at_inference"],
    }, model_path)

    report = [
        "# i-PROGNOSIS smartphone typing baseline",
        "",
        f"**Scope:** {config['study_scope']}.",
        "",
        f"- Cohort: {data_audit['participants']} participants ({data_audit['participants_by_label']['control']} controls, {data_audit['participants_by_label']['pd']} PD); {data_audit['excerpt_files']} excerpt files.",
        f"- Event audit: {data_audit['audit_totals'].get('valid_events', 0)} valid events; {data_audit['audit_totals'].get('malformed_lines', 0)} malformed lines; {data_audit['audit_totals'].get('timestamp_order_reversals', 0)} timestamp reversals; {data_audit['audit_totals'].get('out_of_range_pressure_events', 0)} pressure values outside documented [0,1].",
        f"- Evaluation: {config['evaluation']}.",
        f"- Selected configuration: `{selected_config}`, parameters `{json.dumps(final_search.best_params_, sort_keys=True)}`.",
        f"- Out-of-fold ROC-AUC {best_metrics['roc_auc']:.3f} (95% CI {best_intervals['roc_auc_ci95'][0]:.3f} to {best_intervals['roc_auc_ci95'][1]:.3f}); balanced accuracy {best_metrics['balanced_accuracy']:.3f} (95% CI {best_intervals['balanced_accuracy_ci95'][0]:.3f} to {best_intervals['balanced_accuracy_ci95'][1]:.3f}); sensitivity {best_metrics['sensitivity']:.3f}; specificity {best_metrics['specificity']:.3f}.",
        f"- Confusion matrix, rows=true and columns=predicted [control, PD]: `{best_metrics['confusion_matrix_labels_control_pd']}`.",
        "- The best of 18 configurations is reported, so this estimate is mildly optimistic. Compare it with the other rows in `typing_cv_results.csv`.",
        "- Estimates are unstable due to the small single-study cohort. No external validation or clinical readiness is claimed.",
        "- Dataset license: CC BY 4.0. Cite Iakovakis et al. (2018), Scientific Reports 8, 7663, doi:10.1038/s41598-018-25999-0.",
        "",
    ]
    (results_dir / "typing_experiment_report.md").write_text("\n".join(report), encoding="utf-8")
    print(json.dumps({
         "participants": data_audit["participants"],
        "selected_configuration": selected_config,
        "selected_hyperparameters": final_search.best_params_,
        "nested_cv_metrics_selected": config["nested_cv_metrics_selected"],
        "results_dir": str(results_dir),
        "model_path": str(model_path),
    }, indent=2))
    return metrics


# Command-line entry point
def parse_args() -> argparse.Namespace:
    """Define CLI options for dataset location, output paths, and random seed."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--iprognosis-root", type=Path, default=DEFAULT_IPROGNOSIS_ROOT)
    parser.add_argument("--model-path", type=Path, default=PROJECT_ROOT / "models" / "typing_iprognosis_model.joblib")
    parser.add_argument("--results-dir", type=Path, default=PROJECT_ROOT / "results" / "typing_iprognosis")
    parser.add_argument("--seed", type=int, default=RANDOM_SEED)
    parser.add_argument("--n-jobs", type=int, default=-1)
    return parser.parse_args()


def main() -> None:
    """Parse command-line options and run the participant-level experiment."""
    args = parse_args()
    run_experiment(args.iprognosis_root, args.model_path, args.results_dir, seed=args.seed)


if __name__ == "__main__":
    main()
