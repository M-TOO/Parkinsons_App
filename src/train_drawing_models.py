"""Leakage-aware case-control pipeline for spiral and wave drawing images.

Run from the repository root with:
    python -m src.train_drawing_models

The filename-derived V## token is treated as a provisional participant key and
namespaced by class because healthy and Parkinson's IDs may be numbered
independently. Confirm that convention against the source documentation before
using the scores as evidence of generalization.

The module builds one image-level row per drawing, keeps filename-derived
candidate groups together during splitting, tunes classifiers using grouped
cross-validation, and writes a fitted pipeline plus reproducibility summaries.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import warnings
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from joblib import Parallel, delayed
from sklearn.base import clone
from sklearn.compose import ColumnTransformer
from sklearn.decomposition import PCA
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import balanced_accuracy_score, roc_auc_score
from sklearn.model_selection import GridSearchCV, StratifiedGroupKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.svm import SVC

from src.image_features import DrawingFeatureExtractor, make_extractor

PROJECT_ROOT = Path(__file__).resolve().parents[1]
# Resolve input/output defaults relative to this repository, not the shell cwd.
DEFAULT_IMAGE_ROOT = (
    PROJECT_ROOT
    / "data"
    / "Parkinson's drawings dataset"
)
LABEL_TO_TARGET = {"healthy": 0, "parkinson": 1}  # Binary target encoding used in metrics and estimators.
SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".tif", ".tiff"}  # Files the loader can decode.
RANDOM_SEED = 42  # Fixed default for repeatable partitions and estimators.


# File manifest and duplicate checks
def sha256_file(path: Path) -> str:
    """Return a streaming SHA-256 digest used to detect exact image duplicates."""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_manifest(image_root: Path) -> pd.DataFrame:
    """Build and validate the labeled image table used by the experiment.

    Folder names provide task and class labels; the leading V-number in each
    filename provides a provisional, class-scoped group key. Exact duplicate
    bytes are removed only when their group and label agree.
    """
    records = []
    # The class is read from the directory rather than inferred from a filename.
    for task in ("spiral", "wave"):
        for split in ("training", "testing"):
            for label in LABEL_TO_TARGET:
                directory = image_root / task / split / label
                if not directory.is_dir():
                    continue
                for path in sorted(directory.iterdir()):
                    if not path.is_file() or path.suffix.lower() not in SUPPORTED_EXTENSIONS:
                        continue
                    match = re.match(r"^(V\d+)", path.stem, flags=re.IGNORECASE)
                    if match is None:
                        raise ValueError(f"Cannot derive a conservative group token from filename: {path.name}")
                    records.append(
                        {
                            "path": str(path.resolve()),
                            "task": task,
                            "source_split": split,
                            "label": label,
                            "target": LABEL_TO_TARGET[label],
                            "group": f"{label}:{match.group(1).upper()}",
                            "sha256": sha256_file(path),
                        }
                    )

    manifest = pd.DataFrame(records)
    if manifest.empty:
        raise FileNotFoundError(f"No supported labeled spiral/wave images found under {image_root}")

    conflicting_hashes = manifest.groupby("sha256")["target"].nunique().gt(1)
    if conflicting_hashes.any():
        raise ValueError("Identical image bytes have conflicting class labels; resolve this before training")
    duplicate_hashes = manifest[manifest.duplicated("sha256", keep=False)]
    if not duplicate_hashes.empty:
        groups_per_image = duplicate_hashes.groupby("sha256")["group"].nunique()
        if groups_per_image.gt(1).any():
            raise ValueError("An identical image maps to multiple participant groups; resolve this before splitting")
        manifest = manifest.drop_duplicates("sha256", keep="first").reset_index(drop=True)

    observed_labels = set(manifest["label"])
    if observed_labels != set(LABEL_TO_TARGET):
        raise ValueError(f"Expected both labels {set(LABEL_TO_TARGET)}, found {observed_labels}")
    return manifest

TASKS = ("spiral", "wave")
FEATURE_SETS = ("handcrafted", "pixels_pca", "handcrafted+pixels_pca")
N_REPEATS = 3  # repeated 5-fold grouped CV; more repeats = steadier estimates, longer runtime
N_BOOTSTRAP = 2000

def make_model_pipeline(feature_set: str, n_handcrafted: int, n_pixels: int, classifier: Any) -> Pipeline:
    """Model pipeline on the precomputed matrix [hand-crafted columns | pixel columns]."""
    handcrafted_columns = list(range(n_handcrafted))
    pixel_columns = list(range(n_handcrafted, n_handcrafted + n_pixels))
    blocks = []
    if "handcrafted" in feature_set:
        blocks.append((
            "handcrafted",
            Pipeline([("impute", SimpleImputer(strategy="median")), ("scale", StandardScaler())]),
            handcrafted_columns,
        ))
    if "pixels_pca" in feature_set:
        # PCA compresses 1,000+ correlated pixels into a few components; fitted per training fold.
        blocks.append((
            "pixels",
            Pipeline([("scale", StandardScaler()), ("pca", PCA(n_components=10, random_state=0))]),
            pixel_columns,
        ))
    return Pipeline([
        ("features", ColumnTransformer(blocks, remainder="drop", sparse_threshold=0)),
        ("classifier", classifier),
    ])
 
 
def candidate_grid(seed: int, feature_set: str) -> dict[str, tuple[Any, dict[str, list[Any]]]]:
    """Classifiers and hyperparameter grids for one feature set."""
    pca_grid = {"features__pixels__pca__n_components": [5, 10]} if "pixels_pca" in feature_set else {}
    return {
        "logistic_regression": (
            LogisticRegression(class_weight="balanced", max_iter=5000, random_state=seed),
            {"classifier__C": [0.001, 0.01, 0.1, 1.0], **pca_grid},
        ),
        "rbf_svm": (
            SVC(kernel="rbf", class_weight="balanced", random_state=seed),
            {"classifier__C": [0.1, 1.0, 10.0], "classifier__gamma": ["scale"], **pca_grid},
        ),
        "random_forest": (
            RandomForestClassifier(n_estimators=200, class_weight="balanced_subsample", random_state=seed, n_jobs=1),
            {"classifier__max_features": ["sqrt", 0.5], "classifier__min_samples_leaf": [2, 4], **pca_grid},
        ),
    }

def decision_scores(model: Pipeline, X: Any) -> np.ndarray:
    """Return continuous positive-class scores for ranking metrics such as AUC."""
    if hasattr(model, "predict_proba"):
        return model.predict_proba(X)[:, 1]
    return model.decision_function(X)

def build_feature_matrix(frame: pd.DataFrame, task: str, image_size: int) -> tuple[np.ndarray, int, int, Any]:
    """Extract [hand-crafted | pixel] features once for every image of one task.
 
    Safe before cross-validation because both extractors are stateless. Returns the
    matrix, the two block widths, and the fitted extractor (reused in the saved model).
    """
    extractor = make_extractor(task, image_size)
    paths = frame["path"].to_numpy(dtype=object)
    extractor.fit(paths)
    matrix = extractor.transform(paths)
    n_handcrafted = len(DrawingFeatureExtractor(task=task).get_feature_names_out())
    return matrix, n_handcrafted, matrix.shape[1] - n_handcrafted, extractor
 
 
def _group_count_per_class(y: np.ndarray, groups: np.ndarray) -> int:
    """Smallest number of distinct groups in either class."""
    return int(pd.DataFrame({"y": y, "g": groups}).drop_duplicates().groupby("y")["g"].nunique().min())
 
 
def _drawing_outer_fold(pipeline, grid, F, y, groups, train, test, seed):
    """Tune on training groups only (inner grouped CV), then score the held-out groups."""
    inner_folds = min(3, _group_count_per_class(y[train], groups[train]))
    inner = list(StratifiedGroupKFold(n_splits=inner_folds, shuffle=True, random_state=seed).split(F[train], y[train], groups[train]))
    search = GridSearchCV(clone(pipeline), grid, scoring="roc_auc", cv=inner, n_jobs=1, error_score="raise")
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        search.fit(F[train], y[train])
    model = search.best_estimator_
    return test, decision_scores(model, F[test]), model.predict(F[test])
 
 
def _group_bootstrap_auc(labels: np.ndarray, scores: np.ndarray, seed: int) -> list[float]:
    """95% percentile bootstrap interval for AUC, resampling participant groups."""
    rng = np.random.default_rng(seed)
    values = []
    for _ in range(N_BOOTSTRAP):
        sample = rng.integers(0, len(labels), len(labels))
        if len(np.unique(labels[sample])) == 2:
            values.append(roc_auc_score(labels[sample], scores[sample]))
    return [float(np.percentile(values, 2.5)), float(np.percentile(values, 97.5))]
 
 
def evaluate_configuration(pipeline, grid, F, y, groups, seed, n_repeats, n_jobs) -> dict[str, Any]:
    """Repeated nested grouped CV for one (feature set, model) configuration of one task."""
    outer_folds = min(5, _group_count_per_class(y, groups))
    if outer_folds < 3:
        raise ValueError("Need at least three participant groups per class for grouped cross-validation")
    jobs, repeat_of = [], []
    for repeat in range(n_repeats):
        splitter = StratifiedGroupKFold(n_splits=outer_folds, shuffle=True, random_state=seed + 100 * repeat)
        for train, test in splitter.split(F, y, groups):
            jobs.append((train, test))
            repeat_of.append(repeat)
    results = Parallel(n_jobs=n_jobs)(
        delayed(_drawing_outer_fold)(pipeline, grid, F, y, groups, train, test, seed + 1) for train, test in jobs
    )
    scores = np.full((n_repeats, len(y)), np.nan)
    predictions = np.full((n_repeats, len(y)), np.nan)
    for repeat, (test, fold_scores, fold_predictions) in zip(repeat_of, results):
        scores[repeat, test], predictions[repeat, test] = fold_scores, fold_predictions
 
    image_auc, image_bal, group_auc, group_bal, group_score_rows = [], [], [], [], []
    for repeat in range(n_repeats):
        frame = pd.DataFrame({"g": groups, "y": y, "s": scores[repeat], "p": predictions[repeat]})
        per_group = frame.groupby("g").agg(y=("y", "first"), s=("s", "mean"), p=("p", "mean"))
        image_auc.append(roc_auc_score(y, scores[repeat]))
        image_bal.append(balanced_accuracy_score(y, predictions[repeat]))
        group_auc.append(roc_auc_score(per_group["y"], per_group["s"]))
        group_bal.append(balanced_accuracy_score(per_group["y"], (per_group["p"] >= 0.5).astype(int)))
        group_score_rows.append(per_group["s"])
    mean_group = pd.concat(group_score_rows, axis=1).mean(axis=1)  # score per group, averaged over repeats
    group_labels = pd.DataFrame({"g": groups, "y": y}).groupby("g")["y"].first().loc[mean_group.index]
    return {
        "outer_folds": int(outer_folds),
        "image_roc_auc_mean": float(np.mean(image_auc)), "image_roc_auc_std_over_repeats": float(np.std(image_auc)),
        "image_balanced_accuracy_mean": float(np.mean(image_bal)),
        "group_roc_auc_mean": float(np.mean(group_auc)), "group_roc_auc_std_over_repeats": float(np.std(group_auc)),
        "group_balanced_accuracy_mean": float(np.mean(group_bal)),
        "group_roc_auc_ci95": _group_bootstrap_auc(group_labels.to_numpy(), mean_group.to_numpy(), seed),
        "group_scores": mean_group, "group_labels": group_labels,
    }
 
 
def _zscore(values: pd.Series) -> pd.Series:
    """Standardise one task's group scores so spiral and wave scores are comparable."""
    spread = values.std(ddof=0)
    return (values - values.mean()) / spread if spread > 0 else values * 0.0

# Complete experiment orchestration and artifact output
def run_experiment(
    image_root: Path,
    model_path: Path,
    results_dir: Path,
    seed: int = RANDOM_SEED,
    image_size: int = 32,
    n_repeats: int = N_REPEATS,
    n_jobs: int = -1,  # parallelise outer folds (-1 = all cores)
) -> dict[str, Any]:
    """Run repeated nested grouped CV per task, refit the best configurations, save artifacts.
    """
    manifest = build_manifest(image_root)
    candidate_rows: list[dict[str, Any]] = []
    task_results: dict[str, dict[str, Any]] = {}
    final_pipelines: dict[str, Pipeline] = {}
    selected_scores: dict[str, tuple[pd.Series, pd.Series]] = {}
 
    for task in TASKS:  # one independent model per task
        task_frame = manifest[manifest["task"] == task].reset_index(drop=True)
        if task_frame.empty:
            raise FileNotFoundError(f"No {task} images found under {image_root}")
        y = task_frame["target"].to_numpy(dtype=np.int64)
        groups = task_frame["group"].to_numpy(dtype=object)
        print(f"[{task}] extracting features from {len(task_frame)} images ...")
        F, n_hand, n_pix, extractor = build_feature_matrix(task_frame, task, image_size)
 
        evaluated: dict[str, dict[str, Any]] = {}
        for feature_set in FEATURE_SETS:
            for model_name, (classifier, grid) in candidate_grid(seed, feature_set).items():
                name = f"{feature_set}|{model_name}"
                print(f"[{task}] nested CV: {name}")
                pipeline = make_model_pipeline(feature_set, n_hand, n_pix, classifier)
                outcome = evaluate_configuration(pipeline, grid, F, y, groups, seed, n_repeats, n_jobs)
                evaluated[name] = {"outcome": outcome, "pipeline": pipeline, "grid": grid}
                candidate_rows.append({
                    "task": task, "configuration": name, "feature_set": feature_set, "model": model_name,
                    **{k: v for k, v in outcome.items() if k not in ("group_scores", "group_labels", "group_roc_auc_ci95")},
                    "group_roc_auc_ci95_low": outcome["group_roc_auc_ci95"][0],
                    "group_roc_auc_ci95_high": outcome["group_roc_auc_ci95"][1],
                })
 
        # Select on out-of-fold image-level AUC (ties: image balanced accuracy).
        best_name = max(evaluated, key=lambda n: (evaluated[n]["outcome"]["image_roc_auc_mean"], evaluated[n]["outcome"]["image_balanced_accuracy_mean"]))
        best = evaluated[best_name]
 
        # Refit the winner on ALL images of this task (inner grouped CV tunes it).
        final_folds = min(3, _group_count_per_class(y, groups))
        final_cv = list(StratifiedGroupKFold(n_splits=final_folds, shuffle=True, random_state=seed + 2).split(F, y, groups))
        final_search = GridSearchCV(clone(best["pipeline"]), best["grid"], scoring="roc_auc", cv=final_cv, n_jobs=1, error_score="raise")
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            final_search.fit(F, y)
        # The saved pipeline accepts image PATHS: stateless extractor -> fitted model.
        final_pipelines[task] = Pipeline([("extract", extractor), ("model", final_search.best_estimator_)])
        selected_scores[task] = (best["outcome"]["group_scores"], best["outcome"]["group_labels"])
        task_results[task] = {
            "n_images": int(len(task_frame)),
            "n_groups": int(pd.Series(groups).nunique()),
            "selected_configuration": best_name,
            "selected_hyperparameters": final_search.best_params_,
            "cv_metrics_selected": {k: v for k, v in best["outcome"].items() if k not in ("group_scores", "group_labels")},
        }
 
    # combine spiral and wave per participant group (z-scored mean of out-of-fold scores)
    spiral_scores, spiral_labels = selected_scores["spiral"]
    wave_scores, wave_labels = selected_scores["wave"]
    shared = spiral_scores.index.intersection(wave_scores.index)
    combined = {"n_groups_with_both_tasks": int(len(shared))}
    if len(shared) >= 6 and spiral_labels.loc[shared].nunique() == 2:
        merged = (_zscore(spiral_scores.loc[shared]) + _zscore(wave_scores.loc[shared])) / 2
        shared_labels = spiral_labels.loc[shared].to_numpy()
        combined.update({
            "group_roc_auc": float(roc_auc_score(shared_labels, merged.to_numpy())),
            "group_roc_auc_ci95": _group_bootstrap_auc(shared_labels, merged.to_numpy(), seed),
            "method": "mean of per-task z-scored out-of-fold group scores; not validated beyond this CV",
        })
 
    candidate_table = pd.DataFrame(candidate_rows).sort_values(["task", "image_roc_auc_mean"], ascending=[True, False])
    group_score_table = pd.DataFrame({
        "spiral_score": spiral_scores, "wave_score": wave_scores,
        "label": spiral_labels.combine_first(wave_labels),
    }).reset_index().rename(columns={"g": "group"})
 
    summary = {
        "study_scope": "Exploratory healthy-versus-known-Parkinson's image classification; not prospective early-risk prediction",
        "seed": int(seed),
        "image_size": int(image_size),
        "label_mapping": LABEL_TO_TARGET,
        "group_key": "class-scoped filename prefix V## (provisional participant token; verify with source documentation)",
        "input_root": str(image_root.resolve()),
        "n_images_after_exact_deduplication": int(len(manifest)),
        "n_unique_candidate_groups": int(manifest["group"].nunique()),
        "evaluation": f"repeated ({n_repeats}x) nested StratifiedGroupKFold; inner CV tunes on training groups only; chance ROC-AUC = 0.5",
        "selection_bias_note": "Best of 9 configurations per task is reported, so its estimate is mildly optimistic.",
        "per_task": task_results,
        "combined_spiral_wave": combined,
        "candidate_results": json.loads(candidate_table.to_json(orient="records")),
        "motion_fusion": "not performed: no verified participant crosswalk or labeled healthy-control motion cohort is available",
    }
 
    # Create output folders only after evaluation is complete, then save artifacts.
    model_path.parent.mkdir(parents=True, exist_ok=True)
    results_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "pipelines": final_pipelines,  # {"spiral": Pipeline, "wave": Pipeline}; each takes image paths
            "label_mapping": LABEL_TO_TARGET,
            "group_key_note": summary["group_key"],
            "image_size": image_size,
            "selected_configurations": {t: r["selected_configuration"] for t, r in task_results.items()},
        },
        model_path,
    )
    candidate_table.to_csv(results_dir / "drawing_candidate_results.csv", index=False)
    group_score_table.to_csv(results_dir / "drawing_group_scores.csv", index=False) 
    with (results_dir / "drawing_training_metrics.json").open("w", encoding="utf-8") as output:
        json.dump(summary, output, indent=2)
 
    show = ["task", "configuration", "image_roc_auc_mean", "group_roc_auc_mean", "group_roc_auc_ci95_low", "group_roc_auc_ci95_high", "image_balanced_accuracy_mean"]
    print("\nOut-of-fold results (chance ROC-AUC = 0.5):")
    print(candidate_table[show].to_string(index=False))
    for task, result in task_results.items():
        print(f"\n[{task}] selected: {result['selected_configuration']}  params: {result['selected_hyperparameters']}")
    print(f"\nCombined spiral+wave: {json.dumps(combined)}")
    print(f"\nSaved pipelines: {model_path}")
    print(f"Saved metrics: {results_dir / 'drawing_training_metrics.json'}")
    return summary


# Command-line entry point
def parse_args() -> argparse.Namespace:
    """Define CLI arguments for dataset, artifact paths, and reproducibility."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image-root", type=Path, default=DEFAULT_IMAGE_ROOT)
    parser.add_argument("--model-path", type=Path, default=PROJECT_ROOT / "models" / "drawing_image_model.joblib")
    parser.add_argument("--results-dir", type=Path, default=PROJECT_ROOT / "results")
    parser.add_argument("--seed", type=int, default=RANDOM_SEED)
    parser.add_argument("--image-size", type=int, default=32)
    parser.add_argument("--n-repeats", type=int, default=N_REPEATS)
    parser.add_argument("--n-jobs", type=int, default=-1) 
    return parser.parse_args()


def main() -> None:
    """Parse command-line options and run the drawing baseline experiment."""
    arguments = parse_args()
    run_experiment(
        image_root=arguments.image_root,
        model_path=arguments.model_path,
        results_dir=arguments.results_dir,
        seed=arguments.seed,
        image_size=arguments.image_size,
        n_repeats=arguments.n_repeats,
        n_jobs=arguments.n_jobs,
    )


if __name__ == "__main__":
    main()
