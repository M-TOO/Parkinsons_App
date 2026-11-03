# i-PROGNOSIS smartphone typing baseline

**Scope:** Retrospective classification of known PD versus control status from smartphone touchscreen typing; not prospective risk prediction.

- Cohort: 33 participants (15 controls, 18 PD); 338 excerpt files.
- Event audit: 26343 valid events; 0 malformed lines; 1 timestamp reversals; 7 pressure values outside documented [0,1].
- Evaluation: nested leave-one-participant-out CV; inner StratifiedGroupKFold tuning on training participants only.
- Selected configuration: `participant|core|logistic_regression`, parameters `{"classifier__C": 0.01}`.
- Out-of-fold ROC-AUC 0.889 (95% CI 0.756 to 0.977); balanced accuracy 0.800 (95% CI 0.671 to 0.921); sensitivity 0.667; specificity 0.933.
- Confusion matrix, rows=true and columns=predicted [control, PD]: `[[14, 1], [6, 12]]`.
- The best of 18 configurations is reported, so this estimate is mildly optimistic. Compare it with the other rows in `typing_cv_results.csv`.
- Estimates are unstable due to the small single-study cohort. No external validation or clinical readiness is claimed.
- Dataset license: CC BY 4.0. Cite Iakovakis et al. (2018), Scientific Reports 8, 7663, doi:10.1038/s41598-018-25999-0.
