"""Motion detection and coarse activity classification.

This is deliberately a *coarse* classifier: still / slow / fast. It demonstrates
the realistic ceiling of commodity WiFi sensing -- you can robustly tell that
something is moving and roughly how fast, but not reconstruct a precise skeleton
from one link. Treat the thresholds as defaults to calibrate per environment.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from . import features, preprocess


@dataclass
class DetectorConfig:
    fs: float                      # CSI sample rate (Hz)
    energy_frame: int = 20
    energy_hop: int = 10
    # motion is flagged when windowed energy exceeds baseline * factor
    baseline_factor: float = 4.0
    # Doppler band edges (Hz) separating still / slow / fast
    slow_fast_split_hz: float = 2.0
    # FFT window for the dominant-Doppler feature (shrink these for low-rate RSSI)
    doppler_frame: int = 64
    doppler_hop: int = 16


@dataclass
class Detection:
    time_s: np.ndarray            # window center times
    energy: np.ndarray            # motion energy per window
    moving: np.ndarray            # bool mask, motion present
    doppler_hz: np.ndarray        # dominant frequency per window
    activity: list[str]           # "still" | "slow" | "fast" per window


def _baseline_energy(energy: np.ndarray) -> float:
    """Estimate the resting (no-motion) energy as the low quantile."""
    return float(np.quantile(energy, 0.2))


def run(csi: np.ndarray, cfg: DetectorConfig) -> Detection:
    amp = preprocess.clean_amplitude(csi)

    energy, e_idx = features.motion_energy(amp, frame=cfg.energy_frame, hop=cfg.energy_hop)
    doppler, d_idx = features.dominant_doppler(
        amp, fs=cfg.fs, frame=min(cfg.doppler_frame, amp.shape[0]), hop=cfg.doppler_hop
    )

    base = _baseline_energy(energy)
    threshold = base * cfg.baseline_factor
    moving = energy > threshold

    # align doppler windows onto the energy grid by nearest center index
    dop_on_energy = np.interp(e_idx, d_idx, doppler) if len(d_idx) else np.zeros_like(energy)

    activity = []
    for mv, dz in zip(moving, dop_on_energy):
        if not mv:
            activity.append("still")
        elif dz < cfg.slow_fast_split_hz:
            activity.append("slow")
        else:
            activity.append("fast")

    return Detection(
        time_s=e_idx / cfg.fs,
        energy=energy,
        moving=moving,
        doppler_hz=dop_on_energy,
        activity=activity,
    )


def summarize(det: Detection) -> str:
    """Human-readable run summary collapsing consecutive same-activity windows."""
    lines, prev, start = [], None, None
    for tm, act in zip(det.time_s, det.activity):
        if act != prev:
            if prev is not None:
                lines.append(f"  {start:5.2f}s - {tm:5.2f}s : {prev}")
            prev, start = act, tm
    if prev is not None:
        lines.append(f"  {start:5.2f}s - {det.time_s[-1]:5.2f}s : {prev}")
    return "\n".join(lines)
