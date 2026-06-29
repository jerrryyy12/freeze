"""Synthetic CSI generator.

Produces complex CSI streams shaped like what a commodity 802.11n NIC reports:
``H[t, k]`` where ``t`` indexes packets in time and ``k`` indexes OFDM
subcarriers (the Intel 5300 CSI Tool reports 30 subcarriers per stream).

The model is a small multipath sum:

    H[t, k] = sum_p  a_p * exp(-j * 2*pi * f_k * tau_p(t))  +  noise

A static environment has fixed path delays ``tau_p`` -> low temporal variance.
A moving person adds paths whose delay changes over time,
``tau(t) = tau0 + (v / c) * t``, which both perturbs amplitude and injects a
Doppler shift proportional to the velocity ``v``. This is intentionally simple:
it is enough to exercise the detection pipeline and to show *why* coarse motion
is easy while precise per-joint tracking is hard.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

C = 3e8  # speed of light (m/s)


@dataclass
class MotionEvent:
    """A moving scatterer (a limb / a person) active over a time window."""

    start_s: float
    end_s: float
    velocity_mps: float          # radial velocity -> sets Doppler
    reflectivity: float = 0.5    # 0..1, how strongly it perturbs the channel
    base_delay_ns: float = 20.0  # nominal path delay


@dataclass
class SceneConfig:
    n_subcarriers: int = 30
    center_freq_hz: float = 5.18e9   # WiFi channel 36 (5 GHz)
    subcarrier_spacing_hz: float = 312.5e3  # 802.11 OFDM
    sample_rate_hz: float = 100.0    # CSI packets per second
    duration_s: float = 10.0
    n_static_paths: int = 4
    noise_std: float = 0.02
    seed: int | None = 0
    events: list[MotionEvent] = field(default_factory=list)


def _subcarrier_freqs(cfg: SceneConfig) -> np.ndarray:
    k = np.arange(cfg.n_subcarriers) - cfg.n_subcarriers // 2
    return cfg.center_freq_hz + k * cfg.subcarrier_spacing_hz


def generate(cfg: SceneConfig) -> tuple[np.ndarray, np.ndarray]:
    """Return ``(csi, t)`` where csi is complex ``[n_time, n_subcarriers]``.

    ``t`` is the time axis in seconds.
    """
    rng = np.random.default_rng(cfg.seed)
    n_time = int(cfg.duration_s * cfg.sample_rate_hz)
    t = np.arange(n_time) / cfg.sample_rate_hz
    fk = _subcarrier_freqs(cfg)  # [K]

    csi = np.zeros((n_time, cfg.n_subcarriers), dtype=np.complex128)

    # --- static multipath: fixed delays, fixed amplitudes ---
    for _ in range(cfg.n_static_paths):
        amp = rng.uniform(0.3, 1.0)
        delay = rng.uniform(0, 60e-9)
        phase0 = rng.uniform(0, 2 * np.pi)
        csi += amp * np.exp(-1j * (2 * np.pi * fk * delay + phase0))[None, :]

    # --- moving scatterers: time-varying delay -> Doppler + amplitude churn ---
    for ev in cfg.events:
        active = (t >= ev.start_s) & (t < ev.end_s)
        tau = ev.base_delay_ns * 1e-9 + (ev.velocity_mps / C) * t
        # phase ramp across time for each subcarrier
        phase = 2 * np.pi * fk[None, :] * tau[:, None]
        contrib = ev.reflectivity * np.exp(-1j * phase)
        csi += np.where(active[:, None], contrib, 0.0)

    # --- additive complex Gaussian noise (receiver + quantization) ---
    noise = cfg.noise_std * (rng.standard_normal(csi.shape) + 1j * rng.standard_normal(csi.shape))
    csi += noise
    return csi, t


def csi_to_rssi(csi: np.ndarray, baseline_dbm: float = -45.0) -> np.ndarray:
    """Collapse per-subcarrier CSI into a single RSSI time series (dBm).

    RSSI is the *aggregate* received power across all subcarriers -- exactly what
    a phone reports. This is why RSSI is a weaker sensing signal than CSI: it
    throws away the per-subcarrier detail. Returned values are recentred to look
    like a realistic indoor RSSI (median ~ ``baseline_dbm``).
    """
    power = (np.abs(csi) ** 2).sum(axis=1)
    rssi = 10.0 * np.log10(power + 1e-12)
    return rssi - np.median(rssi) + baseline_dbm


def demo_scene(seed: int | None = 0) -> SceneConfig:
    """A 12 s scene: still -> slow walk -> still -> fast wave -> still."""
    return SceneConfig(
        duration_s=12.0,
        seed=seed,
        events=[
            MotionEvent(start_s=3.0, end_s=5.0, velocity_mps=1.2, reflectivity=0.6),   # walking
            MotionEvent(start_s=8.0, end_s=9.5, velocity_mps=4.0, reflectivity=0.45),  # fast wave
        ],
    )
