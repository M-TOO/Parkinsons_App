"""Loads the trained models once and scores raw inputs.

Reuses src.iprognosis_data.extract_excerpt_features so server features equal training
features. Run the API from the repo root so `src.*` imports resolve.
"""
import os
import tempfile
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

import sys

# 1. Track down the directory where ml_service.py resides (backend/)
current_dir = os.path.dirname(os.path.abspath(__file__))

# 2. Travel one level up to target the project root (pd-ml-lab/)
project_root = os.path.dirname(current_dir)

# 3. Explicitly insert the project root into Python's lookup dictionary
if project_root not in sys.path:
  sys.path.insert(0, project_root)

from src.iprognosis_data import extract_excerpt_features

TYPING_MODEL_PATH = Path(os.environ.get("TYPING_MODEL_PATH", "models/finalization_baseline/typing_iprognosis_model.joblib"))
DRAWING_MODEL_PATH = Path(os.environ.get("DRAWING_MODEL_PATH", "models/finalization_baseline/drawing_image_model.joblib"))
MODEL_VERSION = os.environ.get("MODEL_VERSION", "finalization_baseline")
MIN_EXCERPTS, MIN_EVENTS = 5, 20  # training used 5-11 excerpts per participant


class InsufficientData(ValueError):
    pass


class MLService:
    def __init__(self) -> None:
        self.typing = joblib.load(TYPING_MODEL_PATH)
        if self.typing["granularity"] != "participant":
            raise RuntimeError("expected a participant-level typing model")
        self.drawing = joblib.load(DRAWING_MODEL_PATH)["pipelines"]  # {"spiral": ..., "wave": ...}

    def score_typing(self, excerpts: list[list[tuple[int, int]]]) -> float:
        """Each excerpt is a list of (press_ms, release_ms). Returns P(PD) in 0..1."""
        rows = []
        for events in excerpts:
            if len(events) < MIN_EVENTS:
                continue
            # Write the dataset's "Press,<ms>,Release <ms>,<pressure>" format so the training
            # parser is reused as-is (pressure is unused by the selected core feature set).
            with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as tmp:
                tmp.write("\n".join(f"Press,{p},Release {r},0.5" for p, r in sorted(events)))
            try:
                rows.append(extract_excerpt_features(Path(tmp.name))[0])
            finally:
                os.unlink(tmp.name)
        if len(rows) < MIN_EXCERPTS:
            raise InsufficientData(f"need at least {MIN_EXCERPTS} usable excerpts (20+ keystrokes each), got {len(rows)}")
        participant = pd.DataFrame(rows).median(numeric_only=True)  # median over excerpts, as in training
        X = pd.DataFrame([participant.reindex(self.typing["feature_columns"])])
        return float(self.typing["pipeline"].predict_proba(X)[0, 1])

    def score_drawing(self, task: str, image: bytes) -> float:
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            tmp.write(image)
        try:
            return float(self.drawing[task].predict_proba(np.array([tmp.name], dtype=object))[0, 1])
        finally:
            os.unlink(tmp.name)


_service: MLService | None = None


def get_ml() -> MLService:
    global _service
    if _service is None:
        _service = MLService()
    return _service
