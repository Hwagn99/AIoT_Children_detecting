"""Trạng thái dùng chung giữa các thành phần."""
import json
import os
import threading
import time
from dataclasses import dataclass, field

from app.schemas import Emotion, EmotionResult, RuntimeSettings


@dataclass
class Telemetry:
    temp_c: float | None = None
    door_state: str = "unknown"
    last_seen: float = 0.0
    status_online: bool = False

    @property
    def hardware_online(self) -> bool:
        return self.status_online and (time.time() - self.last_seen) < 20


@dataclass
class EmotionState:
    result: EmotionResult = field(default_factory=EmotionResult)
    ts: float = 0.0
    max_age_s: float = 30.0

    def set(self, r: EmotionResult) -> None:
        self.result, self.ts = r, time.monotonic()

    def current(self) -> EmotionResult:
        if time.monotonic() - self.ts > self.max_age_s:
            return EmotionResult()
        return self.result


class RuntimeSettingsStore:
    """Lưu ngưỡng vào file JSON để giữ sau khi khởi động lại."""
    def __init__(self, path: str):
        self.path = path
        self._lock = threading.Lock()
        self._value = RuntimeSettings()
        if os.path.exists(path):
            try:
                with open(path, encoding="utf-8") as f:
                    self._value = RuntimeSettings(**json.load(f))
            except Exception:  # noqa: BLE001
                pass

    def get(self) -> RuntimeSettings:
        with self._lock:
            return self._value

    def update(self, new: RuntimeSettings) -> RuntimeSettings:
        with self._lock:
            self._value = new
            os.makedirs(os.path.dirname(self.path) or ".", exist_ok=True)
            with open(self.path, "w", encoding="utf-8") as f:
                f.write(new.model_dump_json(indent=2))
            return new
