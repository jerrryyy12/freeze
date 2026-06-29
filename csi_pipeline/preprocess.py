"""CSI preprocessing: amplitude/phase extraction and denoising.

Real CSI is noisy and carries hardware artifacts (CFO/SFO phase drift, AGC
amplitude scaling). These helpers cover the steps that matter most before any
motion analysis. They operate on complex CSI of shape ``[n_time, n_subcarriers]``.
"""

from __future__ import annotations

import numpy as np


def amplitude(csi: np.ndarray) -> np.ndarray:
    """Per-sample subcarrier amplitude ``|H|``."""
    return np.abs(csi)


def phase(csi: np.ndarray) -> np.ndarray:
    """Per-sample subcarrier phase, unwrapped across subcarriers."""
    return np.unwrap(np.angle(csi), axis=1)


def hampel(x: np.ndarray, window: int = 5, n_sigma: float = 3.0) -> np.ndarray:
    """Hampel filter along time (axis 0): replace outliers with the local median.

    Robust to the spiky impulse noise common in real CSI captures.
    """
    x = np.asarray(x, dtype=float)
    out = x.copy()
    n = x.shape[0]
    half = max(1, window // 2)
    k = 1.4826  # MAD -> std for normal data
    # global per-column scale as a fallback when a local window is flat (MAD==0)
    global_mad = np.median(np.abs(x - np.median(x, axis=0)), axis=0)
    eps = np.where(global_mad > 0, global_mad, 1e-9)
    for i in range(n):
        lo, hi = max(0, i - half), min(n, i + half + 1)
        win = x[lo:hi]
        med = np.median(win, axis=0)
        mad = np.median(np.abs(win - med), axis=0)
        scale = np.where(mad > 0, mad, eps)
        thresh = n_sigma * k * scale
        dev = np.abs(x[i] - med)
        mask = dev > thresh
        out[i] = np.where(mask, med, x[i])
    return out


def moving_average(x: np.ndarray, window: int = 5) -> np.ndarray:
    """Causal moving average along time (axis 0)."""
    if window <= 1:
        return x
    kernel = np.ones(window)
    # edge-normalized: divide by the number of samples actually averaged at each
    # position so the first/last few samples are not biased toward zero.
    def smooth(c):
        num = np.convolve(c, kernel, mode="same")
        den = np.convolve(np.ones_like(c), kernel, mode="same")
        return num / den
    return np.apply_along_axis(smooth, 0, x)


def normalize_subcarriers(amp: np.ndarray) -> np.ndarray:
    """Zero-mean / unit-std per subcarrier so all subcarriers are comparable."""
    mean = amp.mean(axis=0, keepdims=True)
    std = amp.std(axis=0, keepdims=True)
    return (amp - mean) / np.where(std == 0, 1.0, std)


def clean_amplitude(csi: np.ndarray, hampel_window: int = 5, smooth_window: int = 5) -> np.ndarray:
    """Full amplitude cleaning chain: |H| -> Hampel -> moving average."""
    amp = amplitude(csi)
    amp = hampel(amp, window=hampel_window)
    amp = moving_average(amp, window=smooth_window)
    return amp
