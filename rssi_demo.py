#!/usr/bin/env python3
"""Phone-RSSI motion-sensing demo (no ESP32 needed).

Two ways to run it:

  # 1) No data yet -- simulate what a PHONE would see (CSI collapsed to RSSI),
  #    so you can preview the degraded-but-working result:
  python rssi_demo.py

  # 2) Real data -- log RSSI on your phone (see docs/phone_rssi.md), copy the
  #    CSV here, and point the demo at it:
  python rssi_demo.py rssi_log.csv          # 2 cols: timestamp,rssi
  python rssi_demo.py rssi_log.csv 5        # 1 col: rssi  (5 = sample rate Hz)

RSSI is a single value per packet (vs CSI's 30+ subcarriers), so expect to catch
GROSS motion (someone walking through) but not fine motion. That contrast is the
whole point of running it.
"""

from __future__ import annotations

import sys

import numpy as np

from csi_pipeline import detect, io_formats, simulator


def detector_for(fs: float) -> detect.DetectorConfig:
    """Scale the analysis windows to the (usually low) RSSI sample rate."""
    frame = max(6, int(round(fs * 1.0)))      # ~1 s energy window
    return detect.DetectorConfig(
        fs=fs,
        energy_frame=frame,
        energy_hop=max(1, frame // 2),
        doppler_frame=max(8, int(round(fs * 2.0))),
        doppler_hop=max(1, int(round(fs * 0.5))),
        baseline_factor=3.0,                   # RSSI is noisier -> looser threshold
    )


def load(argv: list[str]) -> tuple[np.ndarray, float, str]:
    if len(argv) >= 2:
        path = argv[1]
        fs = float(argv[2]) if len(argv) >= 3 else None
        csi, fs = io_formats.load_rssi_csv(path, fs=fs)
        return csi, fs, f"real RSSI log: {path}"
    # no file: simulate a phone's view by collapsing a CSI scene to RSSI
    cfg = simulator.demo_scene(seed=0)
    csi_full, _ = simulator.generate(cfg)
    rssi = simulator.csi_to_rssi(csi_full)
    return io_formats.rssi_to_csi(rssi), cfg.sample_rate_hz, "SIMULATED phone RSSI"


def main() -> None:
    csi, fs, source = load(sys.argv)
    print(f"Source: {source}")
    print(f"RSSI samples: {csi.shape[0]} @ ~{fs:.1f} Hz "
          f"({csi.shape[0]/fs:.1f}s of data)\n")

    det = detect.run(csi, detector_for(fs))

    print("Detected activity timeline (RSSI):")
    print(detect.summarize(det))
    print(f"\nWindows flagged as motion: {det.moving.mean()*100:.0f}%")
    print("Reminder: RSSI = CSI collapsed to one number. Coarse motion only.")


if __name__ == "__main__":
    main()
