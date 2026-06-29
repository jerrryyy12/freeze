"""Tests for the CSI pipeline. Run: python3 -m pytest -q  (or python3 tests/test_pipeline.py)"""

from __future__ import annotations

import numpy as np

from csi_pipeline import detect, features, preprocess, simulator, io_formats


def test_simulator_shape_and_dtype():
    cfg = simulator.SceneConfig(duration_s=2.0, sample_rate_hz=50, n_subcarriers=30)
    csi, t = simulator.generate(cfg)
    assert csi.shape == (100, 30)
    assert csi.dtype == np.complex128
    assert t.shape == (100,)


def test_static_scene_has_low_motion_energy():
    cfg = simulator.SceneConfig(duration_s=4.0, events=[])  # nobody moving
    csi, _ = simulator.generate(cfg)
    det = detect.run(csi, detect.DetectorConfig(fs=cfg.sample_rate_hz))
    # a still scene should almost never flag motion
    assert det.moving.mean() < 0.1


def test_motion_is_detected_during_event_window():
    cfg = simulator.demo_scene(seed=1)
    csi, _ = simulator.generate(cfg)
    det = detect.run(csi, detect.DetectorConfig(fs=cfg.sample_rate_hz))
    # during the walking event (3-5s) at least one window must flag motion
    in_walk = (det.time_s >= 3.0) & (det.time_s <= 5.0)
    assert det.moving[in_walk].any()


def test_fast_motion_has_higher_doppler_than_slow():
    cfg = simulator.demo_scene(seed=2)
    csi, _ = simulator.generate(cfg)
    det = detect.run(csi, detect.DetectorConfig(fs=cfg.sample_rate_hz))
    walk = det.doppler_hz[(det.time_s >= 3.0) & (det.time_s <= 5.0) & det.moving]
    wave = det.doppler_hz[(det.time_s >= 8.0) & (det.time_s <= 9.5) & det.moving]
    if walk.size and wave.size:
        assert wave.mean() >= walk.mean()


def test_hampel_removes_spike():
    x = np.ones((50, 1))
    x[25, 0] = 100.0  # impulse outlier
    cleaned = preprocess.hampel(x, window=5, n_sigma=3.0)
    assert cleaned[25, 0] < 2.0


def test_npz_roundtrip(tmp_path=None):
    import tempfile, os
    csi, _ = simulator.generate(simulator.SceneConfig(duration_s=1.0))
    d = tempfile.mkdtemp()
    p = os.path.join(d, "csi.npz")
    io_formats.save_npz(p, csi)
    loaded = io_formats.load_npz(p)
    assert np.allclose(loaded, csi)


def test_rssi_path_detects_motion():
    # collapse a CSI scene to RSSI (what a phone sees) and run detection
    cfg = simulator.demo_scene(seed=0)
    csi, _ = simulator.generate(cfg)
    rssi = simulator.csi_to_rssi(csi)
    csi1 = io_formats.rssi_to_csi(rssi)
    assert csi1.shape == (csi.shape[0], 1)
    det = detect.run(csi1, detect.DetectorConfig(fs=cfg.sample_rate_hz, energy_frame=20))
    in_walk = (det.time_s >= 3.0) & (det.time_s <= 5.0)
    assert det.moving[in_walk].any()


def test_load_rssi_csv_infers_rate(tmp_path=None):
    import tempfile, os
    n, fs = 50, 5.0
    ts = np.arange(n) / fs
    rssi = -45 + np.random.RandomState(0).randn(n)
    d = tempfile.mkdtemp()
    p = os.path.join(d, "rssi.csv")
    np.savetxt(p, np.column_stack([ts, rssi]), delimiter=",")
    csi, got_fs = io_formats.load_rssi_csv(p)
    assert csi.shape == (n, 1)
    assert abs(got_fs - fs) < 0.1


def test_mac_logger_fake_roundtrip():
    import tempfile, os
    import log_rssi_mac
    d = tempfile.mkdtemp()
    p = os.path.join(d, "rssi_log.csv")
    log_rssi_mac.write_fake(p)
    csi, fs = io_formats.load_rssi_csv(p)
    assert csi.shape[1] == 1 and csi.shape[0] > 10
    det = detect.run(csi, detect.DetectorConfig(fs=fs, energy_frame=max(6, int(fs))))
    assert det.moving.any()  # the synthesized scene contains motion


def test_motion_energy_windows_align():
    amp = np.random.RandomState(0).rand(200, 30)
    energy, centers = features.motion_energy(amp, frame=20, hop=10)
    assert len(energy) == len(centers)
    assert centers.max() < amp.shape[0]


if __name__ == "__main__":
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for fn in fns:
        fn()
        print(f"ok  {fn.__name__}")
    print(f"\n{len(fns)} tests passed")
