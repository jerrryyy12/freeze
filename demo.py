#!/usr/bin/env python3
"""End-to-end CSI motion-sensing demo (no hardware required).

Generates a synthetic CSI stream with a known motion script, runs the detection
pipeline, and prints what WiFi sensing recovers vs. what actually happened.

    python3 demo.py

Swap in real data by replacing the `generate(...)` call with
`io_formats.load_npz("capture.npz")` or `io_formats.load_csv(...)`.
"""

from __future__ import annotations

import numpy as np

from csi_pipeline import detect, simulator


def ascii_plot(values, mask, width: int = 50) -> str:
    """Tiny terminal bar plot of motion energy; '#' where motion is flagged."""
    v = np.asarray(values, dtype=float)
    vmax = v.max() if v.max() > 0 else 1.0
    lines = []
    for val, mv in zip(v, mask):
        n = int(round(width * val / vmax))
        bar = ("#" if mv else ".") * n
        lines.append(f"{bar:<{width}} {'MOTION' if mv else '      '}")
    return "\n".join(lines)


def main() -> None:
    cfg = simulator.demo_scene(seed=0)
    csi, t = simulator.generate(cfg)

    print(f"Generated CSI: {csi.shape[0]} packets x {csi.shape[1]} subcarriers "
          f"@ {cfg.sample_rate_hz:.0f} Hz ({cfg.duration_s:.0f}s)\n")

    print("Ground-truth motion script:")
    for ev in cfg.events:
        kind = "fast" if ev.velocity_mps >= 2.0 else "slow"
        print(f"  {ev.start_s:5.2f}s - {ev.end_s:5.2f}s : {kind} "
              f"(v={ev.velocity_mps} m/s)")
    print()

    det_cfg = detect.DetectorConfig(fs=cfg.sample_rate_hz)
    det = detect.run(csi, det_cfg)

    print("Detected activity timeline:")
    print(detect.summarize(det))
    print()

    print("Motion energy (each row = one analysis window):")
    print(ascii_plot(det.energy, det.moving))
    print()

    moved = det.moving.mean()
    print(f"Windows flagged as motion: {moved*100:.0f}%")
    print("Note: detection of *presence* and *coarse speed* is reliable; "
          "per-joint position is NOT recovered here -- that is the known limit.")


if __name__ == "__main__":
    main()
