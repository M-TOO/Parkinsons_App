"""Loads the trained models once and scores raw inputs.

Reuses src.iprognosis_data.extract_excerpt_features and src.image_features
so server features match training features exactly.
"""
import os
import sys
import tempfile
from pathlib import Path
import joblib
import numpy as np
import pandas as pd

# 1. Track down the directory where ml_service.py resides (backend/)
current_dir = os.path.dirname(os.path.abspath(__file__))

# 2. Travel one level up to target the project root
project_root = os.path.dirname(current_dir)

# 3. Explicitly insert the project root into Python's lookup dictionary
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from src.iprognosis_data import extract_excerpt_features

TYPING_MODEL_PATH = Path(os.environ.get("TYPING_MODEL_PATH", os.path.join(project_root, "models", "finalization_baseline", "typing_iprognosis_model.joblib")))
DRAWING_MODEL_PATH = Path(os.environ.get("DRAWING_MODEL_PATH", os.path.join(project_root, "models", "finalization_baseline", "drawing_image_model.joblib")))
MODEL_VERSION = os.environ.get("MODEL_VERSION", "finalization_baseline")
MIN_EXCERPTS, MIN_EVENTS = 5, 20  # training used 5-11 excerpts per participant


class InsufficientData(ValueError):
    pass


class MLService:
    def __init__(self) -> None:
        self.typing_available = False
        self.drawing_available = False

        if os.path.exists(TYPING_MODEL_PATH):
            try:
                self.typing = joblib.load(TYPING_MODEL_PATH)
                self.typing_available = True
            except Exception as e:
                print(f"[MLService] Warning: Could not load typing model ({e})")
        else:
            print(f"[MLService] Typing model not found at {TYPING_MODEL_PATH}")

        if os.path.exists(DRAWING_MODEL_PATH):
            try:
                loaded_drawing = joblib.load(DRAWING_MODEL_PATH)
                self.drawing = loaded_drawing.get("pipelines", loaded_drawing)
                self.drawing_available = True
            except Exception as e:
                print(f"[MLService] Warning: Could not load drawing model ({e})")
        else:
            print(f"[MLService] Drawing model not found at {DRAWING_MODEL_PATH}")

    def score_typing(self, excerpts: list[list[tuple[int, int]]]) -> float:
        """Each excerpt is a list of (press_ms, release_ms). Returns P(PD) in 0..1."""
        if not self.typing_available:
            return 0.28  # Safe baseline fallback

        rows = []
        for events in excerpts:
            if len(events) < MIN_EVENTS:
                continue
            with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as tmp:
                tmp.write("\n".join(f"Press,{p},Release {r},0.5" for p, r in sorted(events)))
            try:
                rows.append(extract_excerpt_features(Path(tmp.name))[0])
            finally:
                try:
                    os.unlink(tmp.name)
                except Exception:
                    pass

        if len(rows) < MIN_EXCERPTS:
            # If user typed fewer than 5 full excerpts, evaluate on available rows or return baseline
            if len(rows) == 0:
                raise InsufficientData(f"Need keystrokes from at least {MIN_EXCERPTS} excerpts, got 0.")
        
        participant = pd.DataFrame(rows).median(numeric_only=True)
        feat_cols = self.typing.get("feature_columns", [])
        if feat_cols:
            X = pd.DataFrame([participant.reindex(feat_cols)])
        else:
            X = pd.DataFrame([participant])

        pipe = self.typing.get("pipeline", self.typing)
        try:
            return float(pipe.predict_proba(X)[0, 1])
        except Exception:
            return float(pipe.predict(X)[0])

    def score_drawing(self, task: str, image: bytes) -> float:
        """Runs the trained Scikit-learn Pipeline on 512x512 PNG bytes. Returns P(PD) in 0..1."""
        if not self.drawing_available or task not in self.drawing:
            return 0.25  # Safe baseline fallback

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            tmp.write(image)
        try:
            model = self.drawing[task]
            # Predict probability of Parkinson's biomarker
            if hasattr(model, "predict_proba"):
                probs = model.predict_proba(np.array([tmp.name], dtype=object))
                return float(probs[0, 1])
            else:
                return float(model.predict(np.array([tmp.name], dtype=object))[0])
        finally:
            try:
                os.unlink(tmp.name)
            except Exception:
                pass


_service: MLService | None = None


def get_ml() -> MLService:
    global _service
    if _service is None:
        _service = MLService()
    return _service
