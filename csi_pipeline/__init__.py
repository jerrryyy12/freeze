"""WiFi CSI motion-sensing pipeline.

A dependency-light (numpy-only) toolkit for experimenting with WiFi Channel
State Information (CSI) as a motion-sensing source. Built to evaluate how far
commodity WiFi sensing can go toward use cases like VR body tracking.

Modules
-------
simulator   : Generate synthetic CSI streams with a controllable motion model.
io_formats  : Load CSI from common capture tools (Intel 5300 / Atheros / Nexmon / ESP32).
preprocess  : Amplitude/phase extraction and denoising.
features    : Temporal/spectral features (motion energy, dominant Doppler).
detect      : Motion detection and coarse activity classification.
"""

from . import simulator, io_formats, preprocess, features, detect

__all__ = ["simulator", "io_formats", "preprocess", "features", "detect"]
__version__ = "0.1.0"
