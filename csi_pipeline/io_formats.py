"""Loaders for real CSI captures from commodity tools.

Each loader returns complex CSI of shape ``[n_time, n_subcarriers]`` so the rest
of the pipeline does not care where the data came from. Only the CSV/NPZ paths
are implemented here (they need no extra dependencies); the Intel/Atheros/Nexmon
binary parsers are documented stubs you can fill in for your specific hardware.

Capture tooling for a commodity-router/PC setup:
* Intel 5300  -> Linux 802.11n CSI Tool (Halperin et al.)
* Atheros     -> Atheros-CSI-Tool
* Broadcom    -> Nexmon CSI (Raspberry Pi, some routers)
* ESP32       -> esp-csi (cheapest entry point)
"""

from __future__ import annotations

import numpy as np


def load_npz(path: str, key: str = "csi") -> np.ndarray:
    """Load complex CSI saved with ``numpy.savez`` under ``key``."""
    with np.load(path) as data:
        csi = data[key]
    return np.asarray(csi, dtype=np.complex128)


def save_npz(path: str, csi: np.ndarray, **extra) -> None:
    """Persist a CSI array (and any extra arrays) to an ``.npz`` file."""
    np.savez_compressed(path, csi=csi, **extra)


def load_csv(path: str, n_subcarriers: int) -> np.ndarray:
    """Load CSI from CSV laid out as interleaved real/imag per subcarrier.

    Each row is one packet: ``re0, im0, re1, im1, ..., re{K-1}, im{K-1}``.
    This matches the most common ESP32 ``esp-csi`` export format.
    """
    raw = np.loadtxt(path, delimiter=",")
    if raw.ndim == 1:
        raw = raw[None, :]
    expected = 2 * n_subcarriers
    if raw.shape[1] < expected:
        raise ValueError(f"row has {raw.shape[1]} cols, need {expected} for {n_subcarriers} subcarriers")
    raw = raw[:, :expected]
    re = raw[:, 0::2]
    im = raw[:, 1::2]
    return re + 1j * im


def load_intel5300(path: str) -> np.ndarray:  # pragma: no cover - hardware specific
    """Parse Intel 5300 ``.dat`` logs (Linux 802.11n CSI Tool).

    Stub: the .dat format is a sequence of length-prefixed records with a fixed
    header (timestamp, rate, antenna config) followed by packed CSI. Port the
    reference ``read_bf_file.m`` logic here for your antenna layout, then return
    ``[n_time, n_subcarriers]`` (collapse Tx/Rx streams as needed).
    """
    raise NotImplementedError(
        "Intel 5300 .dat parsing is hardware specific - port read_bf_file.m. "
        "For a first run use the simulator or load_csv/load_npz."
    )
