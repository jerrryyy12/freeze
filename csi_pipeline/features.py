"""Feature extraction from cleaned CSI amplitude.

The features here are the workhorses of WiFi sensing:

* **motion energy** - how much the channel is changing right now. Cheap and
  reliable; this is why presence/motion detection "just works".
* **dominant Doppler** - the strongest temporal frequency, which tracks the
  speed of movement. Useful for coarse activity classification (still vs slow
  vs fast), but it does NOT localize *which* limb moved -- the core reason WiFi
  struggles with precise body tracking.
"""

from __future__ import annotations

import numpy as np


def motion_energy(amp: np.ndarray, frame: int = 20, hop: int = 10) -> tuple[np.ndarray, np.ndarray]:
    """Sliding-window motion energy.

    For each window, average the per-subcarrier temporal variance. Returns
    ``(energy, center_idx)`` where ``center_idx`` are the sample indices at each
    window center (so callers can map back to time).
    """
    n = amp.shape[0]
    energies, centers = [], []
    for start in range(0, max(1, n - frame + 1), hop):
        win = amp[start:start + frame]
        energies.append(float(win.var(axis=0).mean()))
        centers.append(start + frame // 2)
    return np.asarray(energies), np.asarray(centers)


def dominant_doppler(amp: np.ndarray, fs: float, frame: int = 64, hop: int = 16) -> tuple[np.ndarray, np.ndarray]:
    """Dominant temporal frequency (Hz) per window, averaged across subcarriers.

    Returns ``(freq_hz, center_idx)``. A still scene -> ~0 Hz; faster motion ->
    higher dominant frequency.
    """
    n = amp.shape[0]
    # use the most active subcarriers so a few stable ones don't wash out motion
    activity = amp.var(axis=0)
    keep = np.argsort(activity)[-max(1, amp.shape[1] // 3):]
    sig = amp[:, keep]
    sig = sig - sig.mean(axis=0, keepdims=True)

    freqs = np.fft.rfftfreq(frame, d=1.0 / fs)
    out_freq, centers = [], []
    win = np.hanning(frame)[:, None]
    for start in range(0, max(1, n - frame + 1), hop):
        seg = sig[start:start + frame]
        if seg.shape[0] < frame:
            break
        spec = np.abs(np.fft.rfft(seg * win, axis=0)).mean(axis=1)
        spec[0] = 0.0  # ignore DC
        out_freq.append(float(freqs[np.argmax(spec)]))
        centers.append(start + frame // 2)
    return np.asarray(out_freq), np.asarray(centers)
