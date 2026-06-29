# WiFi Sensing for VR Full-Body Tracking — Feasibility Notes

These notes record what this prototype demonstrates and what it implies for a
VR full-body-tracking ("풀트") product built on commodity WiFi (router/PC, CSI).

## What WiFi CSI reliably gives you

Demonstrated by `demo.py` on synthetic-but-physical CSI:

| Capability | Status | Evidence |
|---|---|---|
| **Presence / motion detection** | Solid | Motion windows flagged exactly during the scripted events; static scene stays quiet. |
| **Coarse speed (still / slow / fast)** | Workable with caveats | Dominant-Doppler feature separates activity levels. |
| **Per-joint 3D position** | Not achievable from one link | Out of scope of the physics — see below. |

## Why precise full-body tracking is hard on commodity WiFi

1. **One link is one aggregate measurement.** A single Tx→Rx pair sums *all*
   reflections into one channel response. You cannot algebraically separate
   "left wrist" from "right knee" — there are far more unknowns (limb positions)
   than independent measurements. State-of-the-art skeleton work (MIT RF-Pose,
   CMU DensePose-from-WiFi) uses **multiple antennas + deep priors** and still
   produces low-rate, approximate poses, not game-ready input.

2. **Sample-rate aliasing.** This prototype runs at 100 packets/s. A 4 m/s limb
   produces a Doppler of `2·v·fc/c ≈ 138 Hz` at 5 GHz — above Nyquist, so it
   aliases and is misread as slow. That is *visible in the demo* (the fast wave
   is labeled "slow"). VR needs 60–90 Hz **pose** updates; pushing CSI packet
   rates that high on commodity gear is itself difficult.

3. **Environment dependence.** The static multipath is part of the signal, so
   moving furniture or changing rooms requires recalibration. A consumer product
   cannot assume a fixed scene.

4. **Latency.** Robust CSI features need a temporal window (tens of packets).
   That alone is 100–300 ms — past the ~20 ms budget for VR comfort.

## Implication for the business

- A pure-WiFi "풀트 replacement" competing with Vive/Tundra trackers or SlimeVR
  IMUs is **not viable with today's commodity-WiFi physics.** The gap is orders
  of magnitude in latency and precision, not a tuning problem.
- The defensible directions this code is structured to support:
  - **Hybrid**: IMU (SlimeVR-class) as the primary tracker, WiFi as a slow
    absolute-position reference to fight IMU drift. The pipeline here can feed
    a coarse position/zone prior.
  - **Adjacent markets where WiFi sensing actually wins**: presence, fall
    detection, breathing/sleep monitoring — privacy-friendly, no wearable.

## How to extend this prototype toward a real evaluation

1. Capture real CSI (Intel 5300 CSI Tool, Nexmon, or ESP32) and load it via
   `io_formats.load_csv` / `load_npz`.
2. Add multiple Rx antennas/links and fuse them (more measurements → more DoF).
3. Replace the threshold classifier in `detect.py` with a learned model trained
   on labeled activities; measure real precision/latency.
4. Quantify against ground truth (a camera or IMU rig) to get honest error bars
   before any product or investor claim.
