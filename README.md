# freeze — WiFi CSI Motion Sensing (PoC)

A dependency-light prototype for evaluating **WiFi Channel State Information
(CSI)** as a motion-sensing source — built to answer a concrete question:

> Can commodity WiFi (router/PC) drive **VR full-body tracking (풀트)**?

Short answer from this PoC: WiFi reliably detects **presence and coarse motion
speed**, but **not** precise per-joint position. See
[`docs/feasibility.md`](docs/feasibility.md) for the reasoning and the
implications for a tracking product (incl. a viable IMU+WiFi hybrid direction).

## Quick start

No hardware needed — the demo runs on a synthetic, physically-modeled CSI stream.

```bash
pip install numpy
PYTHONPATH=. python3 demo.py        # end-to-end detection on a scripted scene
PYTHONPATH=. python3 tests/test_pipeline.py   # run tests (or: python3 -m pytest -q)
```

The demo prints the ground-truth motion script, the detected activity timeline,
and a terminal plot of motion energy.

## Pipeline

```
simulator / io_formats   ->  preprocess        ->  features            ->  detect
(complex CSI [T x K])        |H|, denoise,         motion energy,          motion mask +
                             normalize            dominant Doppler        still/slow/fast
```

| Module | Role |
|---|---|
| `csi_pipeline/simulator.py` | Synthetic CSI with a controllable multipath + motion model |
| `csi_pipeline/io_formats.py` | Load real captures (CSV/NPZ now; Intel 5300/Nexmon/ESP32 stubs) |
| `csi_pipeline/preprocess.py` | Amplitude/phase, Hampel + moving-average denoise, normalize |
| `csi_pipeline/features.py` | Sliding-window motion energy and dominant-Doppler frequency |
| `csi_pipeline/detect.py` | Threshold motion detection + coarse activity classification |

## Test on real signals with NO extra hardware

No ESP32? Log **RSSI** from a device you already own (Windows laptop or Android
phone) and run it through the same detector. RSSI is coarser than CSI — gross
motion only — but it's real radio data. Full guide: [`docs/phone_rssi.md`](docs/phone_rssi.md).

```cmd
python rssi_demo.py                              # preview (simulated phone view)
python rssi_demo.py examples\sample_rssi_log.csv # bundled ~5 Hz sample
python rssi_demo.py rssi_log.csv                 # your own capture
```

## Using real CSI data

Replace the `simulator.generate(...)` call in `demo.py` with a loader:

```python
from csi_pipeline import io_formats
csi = io_formats.load_npz("capture.npz")        # complex [n_packets, n_subcarriers]
# or load_csv("esp32_log.csv", n_subcarriers=64) for esp-csi exports
```

Capture tooling for a commodity setup: Intel 5300 (Linux 802.11n CSI Tool),
Broadcom/Nexmon CSI (Raspberry Pi), or ESP32 `esp-csi` (cheapest entry point).

## Status

Proof of concept. The detector is intentionally coarse to make the real-world
ceiling of single-link WiFi sensing explicit. Next steps are listed at the
bottom of [`docs/feasibility.md`](docs/feasibility.md).
