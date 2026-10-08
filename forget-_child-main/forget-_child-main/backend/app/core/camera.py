"""Đọc camera trong thread riêng, luôn giữ khung hình mới nhất (không tích hàng đợi)."""
import logging
import threading
import time

import cv2
import numpy as np

log = logging.getLogger("camera")


class Camera:
    def __init__(self, source: str):
        self._source = int(source) if source.isdigit() else source
        self._frame: np.ndarray | None = None
        self._ts = 0.0
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._loop, daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._thread.join(timeout=2)

    def _loop(self) -> None:
        cap = None
        while not self._stop.is_set():
            if cap is None or not cap.isOpened():
                cap = cv2.VideoCapture(self._source)
                if not cap.isOpened():
                    log.warning("Không mở được camera %s, thử lại sau 2s", self._source)
                    time.sleep(2)
                    continue
            ok, frame = cap.read()
            if not ok:
                cap.release()
                cap = None
                continue
            with self._lock:
                self._frame, self._ts = frame, time.time()
        if cap:
            cap.release()

    def read(self) -> np.ndarray | None:
        with self._lock:
            return None if self._frame is None else self._frame.copy()

    @property
    def online(self) -> bool:
        return (time.time() - self._ts) < 3.0
