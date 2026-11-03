"""Deterministic image transformations used by the drawing classifier """
from __future__ import annotations

from typing import Any

import numpy as np
from PIL import Image, ImageOps
from sklearn.base import BaseEstimator, TransformerMixin

# scipy (already installed with scikit-learn) for stroke/tremor measurements
from scipy import ndimage
from scipy.optimize import minimize
from scipy.signal import find_peaks, savgol_filter
from sklearn.pipeline import FeatureUnion

NORMALISED_SIDE = 256  # every drawing is rescaled to this square before measuring

def load_gray(path: Any) -> np.ndarray:
    """Load an image as an upright uint8 grayscale array."""
    with Image.open(path) as image:
        return np.asarray(ImageOps.exif_transpose(image).convert("L"))


def _border_background(gray: np.ndarray) -> float:
    """Estimate the paper colour as the median of the image border."""
    border = np.concatenate([gray[0, :], gray[-1, :], gray[:, 0], gray[:, -1]])
    return float(np.median(border))


def _otsu_threshold(values: np.ndarray) -> float:
    """Otsu threshold for a 0-255 array (maximises between-class variance)."""
    hist, edges = np.histogram(values, bins=256, range=(0, 255))
    centers = (edges[:-1] + edges[1:]) / 2
    total = hist.sum()
    weight0 = np.cumsum(hist)
    weight1 = total - weight0
    cumulative = np.cumsum(hist * centers)
    with np.errstate(divide="ignore", invalid="ignore"):
        between = (cumulative[-1] * weight0 - cumulative * total) ** 2 / (weight0 * weight1)
    return float(centers[np.argmax(np.nan_to_num(between))])


def ink_mask(gray: np.ndarray) -> np.ndarray:
    """Boolean mask of drawn strokes, independent of ink/background polarity.

    Strokes are whatever differs from the border (paper) colour. Tiny specks
    (scanner noise, JPEG artefacts) are removed.
    """
    pixels = gray.astype(np.float32)
    difference = np.abs(pixels - _border_background(gray))
    if difference.max() < 25:  # essentially blank
        return np.zeros(gray.shape, dtype=bool)
    mask = difference > max(_otsu_threshold(difference), 20.0)
    labelled, count = ndimage.label(mask)
    if count > 1:
        sizes = ndimage.sum(mask, labelled, index=np.arange(1, count + 1))
        keep_labels = np.nonzero(sizes >= max(20, 0.01 * sizes.max()))[0] + 1
        mask = np.isin(labelled, keep_labels)
    return mask


def square_crop(array: np.ndarray, mask: np.ndarray, *, margin: float = 0.05, fill: float = 0) -> np.ndarray:
    """Crop to the ink bounding box, then centre it on a square canvas (no distortion)."""
    rows, cols = np.nonzero(mask)
    if rows.size == 0:
        return array
    sub = array[rows.min(): rows.max() + 1, cols.min(): cols.max() + 1]
    height, width = sub.shape
    side = max(height, width)
    pad = int(round(margin * side))
    canvas = np.full((side + 2 * pad, side + 2 * pad), fill, dtype=array.dtype)
    top, left = pad + (side - height) // 2, pad + (side - width) // 2
    canvas[top: top + height, left: left + width] = sub
    return canvas

def _pad_to_square(array: np.ndarray, fill: float) -> np.ndarray:
    """Pad (never stretch) an image to a square canvas."""
    height, width = array.shape
    side = max(height, width)
    canvas = np.full((side, side), fill, dtype=array.dtype)
    top, left = (side - height) // 2, (side - width) // 2
    canvas[top: top + height, left: left + width] = array
    return canvas

class GrayscaleVectorizer(BaseEstimator, TransformerMixin):
    """Convert image paths into fixed-size, normalized grayscale vectors.

    The transformer performs the same independent resize on each image, so it
    learns no dataset statistics and can safely be placed inside an sklearn
    pipeline. By default images are stretched to a square, which is the
    preserved baseline behavior and may distort non-square drawings.
    """

    def __init__(self, size: int = 32, crop_to_content: bool = False, pad_to_square: bool = False) -> None:
        """Store output size and optional geometry normalisation flags."""
        self.size = size
        self.crop_to_content = crop_to_content  # crop to the ink box, centred on a square canvas
        self.pad_to_square = pad_to_square  # pad (not stretch) non-square images to a square

    def fit(self, X: Any, y: Any = None) -> GrayscaleVectorizer:
        """Validate configuration; no data-dependent fitting is required."""
        if self.size < 8:
            raise ValueError("Image size must be at least 8 pixels")
        return self

    def transform(self, X: Any) -> np.ndarray:
        """Load paths and return one flattened [0, 1] grayscale row per image."""
        paths = np.asarray(X, dtype=object)
        # ColumnTransformer may pass a one-column table; reduce it to paths.
        if paths.ndim == 2:
            paths = paths[:, 0]
        if paths.ndim != 1:
            raise ValueError("Expected one image path per row")

        features = []
        for path in paths:
            # geometry normalisation happens before the resize
            gray = load_gray(path)
            if self.crop_to_content:
                gray = square_crop(gray, ink_mask(gray), fill=_border_background(gray)).astype(np.uint8)
            elif self.pad_to_square:
                gray = _pad_to_square(gray, _border_background(gray)).astype(np.uint8)
            image = Image.fromarray(gray)
            # Discard color (already grayscale) and resize using high-quality
            # Lanczos filtering before scaling byte values.
            image = image.resize((self.size, self.size), Image.Resampling.LANCZOS)
            features.append(np.asarray(image, dtype=np.float32).ravel() / 255.0)
        return np.vstack(features)


COMMON_FEATURES = [
    "ink_fraction_bbox",        # ink pixels / bounding-box area
    "bbox_aspect",              # width / height of the ink bounding box
    "n_components",             # separate pieces of ink (broken strokes)
    "path_length_norm",         # approx. total stroke length / image side
    "stroke_width_mean_norm",   # mean stroke width / image side
    "stroke_width_cv",          # stroke-width variability (pressure/tremor proxy)
]
SPIRAL_FEATURES = [
    "spiral_fit_concentration",    # 0-1: how well the ink follows one Archimedean spiral
    "spiral_turns",                # fitted number of turns
    "spiral_clockwise",            # +1 / -1 fitted winding direction
    "spiral_residual_std_norm",    # deviation from the fitted spiral / radius
    "spiral_residual_pitch_frac",  # same deviation as a fraction of the arm spacing
    "spiral_hf_power_ratio",       # share of deviation power at high angular frequency (tremor)
]
WAVE_FEATURES = [
    "wave_amplitude_norm",       # wave height / wave width
    "wave_cycles",               # dominant number of cycles across the drawing
    "wave_residual_std_norm",    # deviation from a smoothed wave / amplitude
    "wave_hf_power_ratio",       # share of power well above the dominant frequency (tremor)
    "wave_n_peaks",              # number of prominent peaks
    "wave_peak_spacing_cv",      # irregular rhythm
    "wave_peak_height_cv",       # amplitude irregularity / shrinking amplitude
]
TASK_FEATURES = {"spiral": SPIRAL_FEATURES, "wave": WAVE_FEATURES}


def _normalised_mask(gray: np.ndarray) -> tuple[np.ndarray, float, float]:
    """Return (mask on a 256x256 canvas, bbox aspect, ink fraction of the bbox)."""
    mask = ink_mask(gray)
    rows, cols = np.nonzero(mask)
    if rows.size == 0:
        return np.zeros((NORMALISED_SIDE, NORMALISED_SIDE), dtype=bool), np.nan, np.nan
    height, width = rows.max() - rows.min() + 1, cols.max() - cols.min() + 1
    squared = square_crop(mask.astype(np.uint8) * 255, mask, margin=0.02, fill=0)
    resized = Image.fromarray(squared).resize((NORMALISED_SIDE, NORMALISED_SIDE), Image.Resampling.BILINEAR)
    # A low threshold keeps thin strokes alive after downscaling.
    return np.asarray(resized) > 64, float(width / height), float(mask.sum() / (height * width))


def _common_features(mask: np.ndarray, aspect: float, bbox_ink_fraction: float) -> dict[str, float]:
    """Task-independent stroke statistics measured on the normalised mask."""
    if not mask.any():
        return {name: np.nan for name in COMMON_FEATURES}
    distance = ndimage.distance_transform_edt(mask)
    # Ridge of the distance map approximates the stroke centre-line.
    ridge = (distance == ndimage.maximum_filter(distance, size=3)) & (distance > 0.5)
    widths = 2.0 * distance[ridge]
    return {
        "ink_fraction_bbox": bbox_ink_fraction,
        "bbox_aspect": aspect,
        "n_components": float(ndimage.label(mask)[1]),
        "path_length_norm": float(ridge.sum() / NORMALISED_SIDE),
        "stroke_width_mean_norm": float(widths.mean() / NORMALISED_SIDE),
        "stroke_width_cv": float(widths.std() / widths.mean()) if widths.mean() > 0 else 0.0,
    }


def _phase(radius: np.ndarray, theta: np.ndarray, pitch: float, sign: int) -> np.ndarray:
    """Position of each ink pixel along the arm spacing; constant for an ideal spiral."""
    return ((radius - sign * pitch * theta / (2 * np.pi)) / pitch) % 1.0


def _concentration(radius: np.ndarray, theta: np.ndarray, pitch: float, sign: int) -> tuple[float, float]:
    """Circular mean resultant length R of the phase and the preferred phase (0-1)."""
    resultant = np.exp(2j * np.pi * _phase(radius, theta, pitch, sign)).mean()
    return float(abs(resultant)), float(np.angle(resultant) / (2 * np.pi))


def _best_spiral_fit(radius: np.ndarray, theta: np.ndarray, r_max: float) -> tuple[float, float, int, float]:
    """Search winding direction and number of turns; return (R, turns, sign, mean_phase)."""
    candidates = []
    for sign in (1, -1):
        for turns in np.linspace(1.5, 8.0, 66):
            concentration, mean_phase = _concentration(radius, theta, r_max / turns, sign)
            candidates.append((concentration, float(turns), sign, mean_phase))
    best_r = max(c[0] for c in candidates)
    # Smaller pitches alias onto the true one; take the fewest turns that fit nearly as well.
    return min((c for c in candidates if c[0] >= 0.97 * best_r), key=lambda c: c[1])


def _spiral_features(mask: np.ndarray) -> dict[str, float]:
    """Fit r = a + pitch * theta / 2pi to the ink and measure how far the ink wobbles from it.

    Phase trick: for the correct pitch every ink pixel of an ideal spiral has the
    same value of ((r - s*pitch*theta/2pi) / pitch) mod 1, so the circular mean
    resultant length R of that phase measures the fit. Tremor shows up as spread
    around the preferred phase. The spiral centre is refined by a small search,
    because the bounding-box centre is a poor estimate for hand-drawn spirals.
    """
    nan = {name: np.nan for name in SPIRAL_FEATURES}
    rows, cols = np.nonzero(mask)
    if rows.size < 50:
        return nan
    middle = (mask.shape[0] - 1) / 2.0
    dx0, dy0 = cols - middle, middle - rows
    r_max = float(np.percentile(np.hypot(dx0, dy0), 99))
    if r_max <= 0:
        return nan

    def polar(offset_x: float, offset_y: float) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        dx, dy = dx0 - offset_x, dy0 - offset_y
        radius = np.hypot(dx, dy)
        return radius, np.arctan2(dy, dx), radius > 0.05 * r_max  # drop clutter at the very centre

    radius, theta, keep = polar(0.0, 0.0)
    if keep.sum() < 50:
        return nan
    # Stage 1: every (winding direction, coarse turns) combination is a start, because a wrong
    # centre can make the wrong direction or turn count look best at the bounding-box centre.
    starts = [(0.0, float(turns), sign) for sign in (1, -1) for turns in np.linspace(1.5, 8.0, 14)]

    # Stage 2: refine the spiral centre for every start (coarse grid), keep the best overall,
    # then refine it again on a finer grid.
    def refine(turns: float, sign: int, centre_x: float, centre_y: float, span: float, steps: int):
        best = (-1.0, centre_x, centre_y)
        for offset_x in centre_x + np.linspace(-span, span, steps) * r_max:
            for offset_y in centre_y + np.linspace(-span, span, steps) * r_max:
                radius, theta, keep = polar(offset_x, offset_y)
                if keep.sum() < 50:
                    continue
                concentration, _ = _concentration(radius[keep], theta[keep], r_max / turns, sign)
                if concentration > best[0]:
                    best = (concentration, offset_x, offset_y)
        return best

    refined = [(refine(turns, sign, 0.0, 0.0, 0.2, 7), turns, sign) for _, turns, sign in starts]
    top = max(item[0][0] for item in refined)
    # Aliases (double the turns) fit as well as the true spiral; prefer the fewest turns.
    (_, centre_x, centre_y), turns, sign = min((item for item in refined if item[0][0] >= 0.97 * top), key=lambda item: item[1])
    _, centre_x, centre_y = refine(turns, sign, centre_x, centre_y, 0.06, 7)

    # Stage 3: continuous polish of (centre, turns); the grids above are only coarse starts.
    def negative_concentration(values: np.ndarray) -> float:
        offset_x, offset_y, candidate_turns = values
        if not 0.88 * turns <= candidate_turns <= 1.12 * turns:  # stay on the grid solution (no aliasing)
            return 1.0
        radius, theta, keep = polar(offset_x, offset_y)
        if keep.sum() < 50:
            return 1.0
        return -_concentration(radius[keep], theta[keep], r_max / candidate_turns, sign)[0]

    start = np.array([centre_x, centre_y, turns])
    polished = minimize(
        negative_concentration,
        start,
        method="Nelder-Mead",
        options={
            "initial_simplex": np.vstack([start, start + [3.0, 0, 0], start + [0, 3.0, 0], start + [0, 0, 0.1]]),
            "xatol": 0.05, "fatol": 1e-4, "maxiter": 200,
        },
    )
    centre_x, centre_y, turns = (float(v) for v in polished.x)
    radius, theta, keep = polar(centre_x, centre_y)
    if keep.sum() < 50 or turns < 1.2:
        return nan
    radius, theta = radius[keep], theta[keep]
    concentration, mean_phase = _concentration(radius, theta, r_max / turns, sign)

    pitch = r_max / turns
    deviation = ((_phase(radius, theta, pitch, sign) - mean_phase + 0.5) % 1.0) - 0.5  # in arm spacings
    residual = deviation * pitch

    # Angular profile of the deviation, then the share of power at high angular frequency.
    bins = 180
    index = np.clip(((theta + np.pi) / (2 * np.pi) * bins).astype(int), 0, bins - 1)
    counts = np.bincount(index, minlength=bins)
    sums = np.bincount(index, weights=residual, minlength=bins)
    profile = np.full(bins, np.nan)
    profile[counts > 0] = sums[counts > 0] / counts[counts > 0]
    valid = ~np.isnan(profile)
    if valid.sum() < 20:
        hf_ratio = np.nan
    else:
        profile = np.interp(np.arange(bins), np.nonzero(valid)[0], profile[valid])
        power = np.abs(np.fft.rfft(profile - profile.mean())) ** 2
        hf_ratio = float(power[8:].sum() / power[1:].sum()) if power[1:].sum() > 0 else 0.0
    return {
        "spiral_fit_concentration": concentration,
        "spiral_turns": turns,
        "spiral_clockwise": float(sign),
        "spiral_residual_std_norm": float(residual.std() / r_max),
        "spiral_residual_pitch_frac": float(deviation.std()),
        "spiral_hf_power_ratio": hf_ratio,
    }


def _wave_features(mask: np.ndarray) -> dict[str, float]:
    """Centre-line height profile of the wave, its smooth version, and the wobble around it."""
    nan = {name: np.nan for name in WAVE_FEATURES}
    rows, cols = np.nonzero(mask)
    if rows.size < 50:
        return nan
    width = mask.shape[1]
    sums = np.bincount(cols, weights=rows, minlength=width)
    counts = np.bincount(cols, minlength=width)
    left, right = cols.min(), cols.max() + 1
    profile = np.full(width, np.nan)
    profile[counts > 0] = sums[counts > 0] / counts[counts > 0]
    profile = profile[left:right]
    if profile.size < 40:
        return nan
    valid = ~np.isnan(profile)
    profile = -np.interp(np.arange(profile.size), np.nonzero(valid)[0], profile[valid])  # up is up

    window = min(31, profile.size - (1 - profile.size % 2))  # odd window no longer than the data
    smooth = savgol_filter(profile, window_length=window, polyorder=3)
    residual = profile - smooth
    amplitude = float(np.ptp(smooth))
    if amplitude <= 0:
        return nan

    spectrum = np.abs(np.fft.rfft((profile - profile.mean()) * np.hanning(profile.size))) ** 2
    dominant = int(np.argmax(spectrum[1:]) + 1)
    cutoff = max(4 * dominant, 12)
    hf_ratio = float(spectrum[cutoff:].sum() / spectrum[1:].sum()) if spectrum[1:].sum() > 0 else 0.0

    peaks, _ = find_peaks(smooth, prominence=0.25 * amplitude)
    spacing_cv = height_cv = 0.0
    if len(peaks) >= 3:
        gaps = np.diff(peaks).astype(float)
        spacing_cv = float(gaps.std() / gaps.mean())
        heights = smooth[peaks] - smooth.mean()
        height_cv = float(heights.std() / abs(heights.mean())) if abs(heights.mean()) > 1e-9 else 0.0
    return {
        "wave_amplitude_norm": float(amplitude / profile.size),
        "wave_cycles": float(dominant),
        "wave_residual_std_norm": float(residual.std() / amplitude),
        "wave_hf_power_ratio": hf_ratio,
        "wave_n_peaks": float(len(peaks)),
        "wave_peak_spacing_cv": spacing_cv,
        "wave_peak_height_cv": height_cv,
    }


def extract_drawing_features(gray: np.ndarray, task: str) -> dict[str, float]:
    """Compute all hand-crafted features for one grayscale drawing of the given task."""
    if task not in TASK_FEATURES:
        raise ValueError(f"task must be one of {sorted(TASK_FEATURES)}, got {task!r}")
    mask, aspect, bbox_ink_fraction = _normalised_mask(gray)
    features = _common_features(mask, aspect, bbox_ink_fraction)
    features.update(_spiral_features(mask) if task == "spiral" else _wave_features(mask))
    return features


class DrawingFeatureExtractor(BaseEstimator, TransformerMixin):
    """Turn image paths into hand-crafted irregularity features for ONE task.

    Stateless like GrayscaleVectorizer. Undefined measurements are NaN, so put a
    SimpleImputer after this transformer inside the model pipeline.
    """

    def __init__(self, task: str = "spiral") -> None:
        """Store which drawing task (spiral or wave) this extractor measures."""
        self.task = task

    def fit(self, X: Any, y: Any = None) -> DrawingFeatureExtractor:
        """Validate configuration; no data-dependent fitting is required."""
        if self.task not in TASK_FEATURES:
            raise ValueError(f"task must be one of {sorted(TASK_FEATURES)}")
        return self

    def get_feature_names_out(self, input_features: Any = None) -> np.ndarray:
        """Names of the output columns, in order."""
        return np.asarray(COMMON_FEATURES + TASK_FEATURES[self.task], dtype=object)

    def transform(self, X: Any) -> np.ndarray:
        """Load paths and return one row of hand-crafted features per image."""
        paths = np.asarray(X, dtype=object)
        if paths.ndim == 2:
            paths = paths[:, 0]
        if paths.ndim != 1:
            raise ValueError("Expected one image path per row")
        names = self.get_feature_names_out()
        rows = []
        for path in paths:
            features = extract_drawing_features(load_gray(path), self.task)
            rows.append([features.get(name, np.nan) for name in names])
        return np.asarray(rows, dtype=np.float64)


def make_extractor(task: str, image_size: int = 32) -> FeatureUnion:
    """Path -> [hand-crafted features | crop-and-padded grayscale pixels] for one task."""
    return FeatureUnion(
        [
            ("handcrafted", DrawingFeatureExtractor(task=task)),
            ("pixels", GrayscaleVectorizer(size=image_size, crop_to_content=True, pad_to_square=True)),
        ]
    )