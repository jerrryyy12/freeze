#!/usr/bin/env python3
"""Log WiFi RSSI on macOS into a CSV for rssi_demo.py (no ESP32, no phone).

    python3 log_rssi_mac.py                 # 24s guided capture -> rssi_log.csv
    python3 log_rssi_mac.py --seconds 30 --out my.csv
    python3 log_rssi_mac.py --fake          # offline self-test (no WiFi needed)

Then analyse what was captured:

    python3 rssi_demo.py rssi_log.csv

Best signal: put the laptop and the router a few metres apart and walk across
the line between them during the MOVE phase the script prompts for.

RSSI source, in order of preference:
  1. CoreWLAN  -- fast/accurate. Needs:  pip3 install pyobjc-framework-CoreWLAN
  2. system_profiler  -- no install, but slow (~1 Hz).
On recent macOS both need Location Services enabled for your terminal app
(System Settings > Privacy & Security > Location Services).
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time

import numpy as np


def _corewlan_reader():
    import CoreWLAN  # pip3 install pyobjc-framework-CoreWLAN
    iface = CoreWLAN.CWWiFiClient.sharedWiFiClient().interface()
    if iface is None:
        raise RuntimeError("no WiFi interface")

    def read():
        v = iface.rssiValue()
        return float(v) if v else None

    return read


def _system_profiler_reader():
    def read():
        out = subprocess.run(
            ["system_profiler", "SPAirPortDataType"],
            capture_output=True, text=True, timeout=15,
        ).stdout
        m = re.search(r"Signal\s*/\s*Noise:\s*(-?\d+)\s*dBm", out)
        return float(m.group(1)) if m else None

    if read() is None:
        raise RuntimeError("system_profiler returned no RSSI (Location off? on Ethernet?)")
    return read


def make_reader():
    errors = []
    for name, factory in (("CoreWLAN", _corewlan_reader),
                          ("system_profiler", _system_profiler_reader)):
        try:
            return factory(), name
        except Exception as exc:  # noqa: BLE001 - report and try next backend
            errors.append(f"{name}: {exc}")
    raise RuntimeError("no RSSI source available:\n  " + "\n  ".join(errors))


def phase_for(frac: float) -> str:
    if frac < 1 / 3:
        return "STILL  (stand out of the path)"
    if frac < 2 / 3:
        return "MOVE   (walk across: laptop <-> router)"
    return "STILL  (stop moving)"


def capture(seconds: float, hz: float, out: str) -> None:
    reader, backend = make_reader()
    print(f"RSSI source: {backend}\n")
    interval = 1.0 / hz
    rows, last_phase = [], None
    t0 = time.time()
    while True:
        now = time.time()
        elapsed = now - t0
        if elapsed >= seconds:
            break
        phase = phase_for(elapsed / seconds)
        if phase != last_phase:
            print(f"[{elapsed:4.1f}s] {phase}")
            last_phase = phase
        r = reader()
        if r is not None:
            rows.append((now, r))
        time.sleep(interval)

    if not rows:
        sys.exit(
            "\nNo RSSI samples captured. Most likely causes on macOS:\n"
            "  - Location Services off for your terminal -> System Settings >\n"
            "    Privacy & Security > Location Services > enable Terminal/iTerm.\n"
            "  - Not on WiFi (Ethernet), or WiFi is off.\n"
            "  - For a faster, more reliable source: pip3 install pyobjc-framework-CoreWLAN"
        )

    arr = np.asarray(rows)
    np.savetxt(out, arr, delimiter=",", fmt="%.3f")
    print(f"\nWrote {len(rows)} samples (~{len(rows)/seconds:.1f} Hz) to {out}")
    print(f"Now run:  python3 rssi_demo.py {out}")


def write_fake(out: str) -> None:
    """Offline self-test: synthesise a still->move->still RSSI log (no WiFi)."""
    from csi_pipeline import simulator
    cfg = simulator.demo_scene(seed=0)
    csi, t = simulator.generate(cfg)
    rssi = simulator.csi_to_rssi(csi)
    step = 20  # 100 Hz -> ~5 Hz, like a real phone/laptop log
    arr = np.column_stack([t[::step], rssi[::step]])
    np.savetxt(out, arr, delimiter=",", fmt="%.3f")
    print(f"[fake] wrote {arr.shape[0]} samples to {out}")
    print(f"Now run:  python3 rssi_demo.py {out}")


def main() -> None:
    ap = argparse.ArgumentParser(description="Log macOS WiFi RSSI for rssi_demo.py")
    ap.add_argument("--seconds", type=float, default=24.0, help="capture duration")
    ap.add_argument("--hz", type=float, default=10.0, help="target sample rate")
    ap.add_argument("--out", default="rssi_log.csv", help="output CSV path")
    ap.add_argument("--fake", action="store_true", help="offline self-test, no WiFi")
    args = ap.parse_args()
    if args.fake:
        write_fake(args.out)
    else:
        capture(args.seconds, args.hz, args.out)


if __name__ == "__main__":
    main()
