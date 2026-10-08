"""Vòng lặp chính: camera -> YOLO -> luật an toàn (fast path) + CrewAI/VLM (slow path) -> hành động."""
import asyncio
import logging
import os
import time

import cv2

from app.agents.crew import build_crew
from app.agents.vlm_client import VLMClient
from app.config import Settings
from app.core.camera import Camera
from app.core.detector import ChildAdultDetector, Observation
from app.core.presence import PresenceTracker
from app.core.rules import evaluate
from app.core.state import EmotionState, RuntimeSettingsStore, Telemetry
from app.schemas import (AlertLevel, EmotionResult, SituationDecision, StatusResponse)
from app.services.db import Database
from app.services.mqtt_client import MqttClient
from app.services.notifier import Notifier

log = logging.getLogger("pipeline")


class Pipeline:
    def __init__(self, cfg: Settings, runtime: RuntimeSettingsStore, camera: Camera,
                 detector: ChildAdultDetector, mqtt: MqttClient, telemetry: Telemetry,
                 notifier: Notifier, db: Database):
        self.cfg, self.runtime, self.camera, self.detector = cfg, runtime, camera, detector
        self.mqtt, self.telemetry, self.notifier, self.db = mqtt, telemetry, notifier, db

        self.presence = PresenceTracker(cfg.presence_hold_s)
        self.emotion = EmotionState()
        self.level = AlertLevel.SAFE
        self.silenced_level: AlertLevel | None = None
        self.message = "An toàn"
        self.obs = Observation()

        self._last_frame = None
        self._child_crop = None
        self._crew_decision: tuple[SituationDecision, float] | None = None
        self._crew_busy = False
        self._last_crew = -1e9
        self._stop = asyncio.Event()

        vlm = VLMClient(cfg.vlm_base_url, cfg.vlm_model)
        self.crew = build_crew(cfg, vlm, self._get_child_jpeg, self._on_emotion)
        os.makedirs(os.path.join(cfg.data_dir, "snapshots"), exist_ok=True)

    # ---------- callback cho crew ----------
    def _get_child_jpeg(self) -> bytes | None:
        img = self._child_crop if self._child_crop is not None else self._last_frame
        if img is None or img.size == 0:
            return None
        ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 85])
        return buf.tobytes() if ok else None

    def _on_emotion(self, r: EmotionResult) -> None:
        self.emotion.set(r)

    # ---------- vòng lặp ----------
    async def run(self) -> None:
        period = 1.0 / self.cfg.detect_fps
        while not self._stop.is_set():
            t0 = time.monotonic()
            try:
                await self._tick()
            except Exception:  # noqa: BLE001
                log.exception("Lỗi trong pipeline tick")
            await asyncio.sleep(max(0.0, period - (time.monotonic() - t0)))

    def stop(self) -> None:
        self._stop.set()

    async def _tick(self) -> None:
        frame = self.camera.read()
        if frame is None:
            return
        self._last_frame = frame
        self.obs = await asyncio.to_thread(self.detector.detect, frame)
        now = time.monotonic()
        self.presence.update(self.obs.adults, self.obs.children, now)

        child = self.obs.best_child()
        if child is not None:
            self._child_crop = self.detector.crop(frame, child)

        rt = self.runtime.get()
        alone_s = self.presence.alone_seconds(now)
        temp = self.telemetry.temp_c

        # --- fast path: luật xác định ---
        rule_level, reasons = evaluate(alone_s, temp, self.emotion.current().emotion, rt)

        # --- slow path: CrewAI + VLM (không chặn vòng lặp) ---
        if (alone_s >= rt.grace_seconds and not self._crew_busy
                and now - self._last_crew >= rt.analysis_interval_s):
            self._crew_busy, self._last_crew = True, now
            asyncio.create_task(self._run_crew(alone_s, temp))

        crew_level = AlertLevel.SAFE
        if self._crew_decision and now - self._crew_decision[1] < rt.analysis_interval_s * 3 + 30:
            # An toàn: LLM không được tự kích hoạt EMERGENCY (mở cửa) -> chặn ở WARNING
            crew_level = AlertLevel(min(self._crew_decision[0].level, AlertLevel.WARNING))

        target = max(rule_level, crew_level) if alone_s > 0 else AlertLevel.SAFE
        # Chốt mức (latch): chỉ leo thang; chỉ về SAFE khi tình huống chấm dứt
        new_level = AlertLevel.SAFE if target == AlertLevel.SAFE else max(self.level, target)

        if self._crew_decision and new_level > AlertLevel.SAFE:
            msg = self._crew_decision[0].message_vi
        elif reasons:
            msg = "; ".join(reasons).capitalize()
        else:
            msg = "An toàn"
        self.message = msg
        await self._apply_level(new_level, msg, alone_s, temp)

    async def _run_crew(self, alone_s: float, temp: float | None) -> None:
        try:
            result = await asyncio.to_thread(self.crew.kickoff, inputs={
                "alone_seconds": int(alone_s),
                "temp_c": "không rõ" if temp is None else f"{temp:.1f}",
                "adults": self.obs.adults,
                "children": self.obs.children,
            })
            if result.pydantic is not None:
                self._crew_decision = (result.pydantic, time.monotonic())
        except Exception:  # noqa: BLE001
            log.exception("CrewAI lỗi")
        finally:
            self._crew_busy = False

    # ---------- hành động khi đổi mức ----------
    async def _apply_level(self, new: AlertLevel, msg: str, alone_s: float, temp: float | None) -> None:
        prev = self.level
        if new == prev:
            return
        self.level = new
        rt = self.runtime.get()

        if new == AlertLevel.SAFE:
            self.silenced_level = None
            self._crew_decision = None
            self.mqtt.set_level(0)
            log.info("Tình huống kết thúc -> SAFE")
            return

        if new > prev:
            snap = self._save_snapshot()
            emo = self.emotion.current()
            self.db.add_event(int(new), msg, emo.emotion.value, alone_s, temp, snap)
            if self.silenced_level is None or new > self.silenced_level:
                self.mqtt.set_level(int(new))
            if new == AlertLevel.EMERGENCY and rt.auto_unlock:
                self.mqtt.door("unlock")
            await self.notifier.notify(new, msg, rt)
            log.warning("Mức cảnh báo: %s -> %s | %s", prev.name, new.name, msg)

    def _save_snapshot(self) -> str | None:
        if self._last_frame is None:
            return None
        path = os.path.join(self.cfg.data_dir, "snapshots", f"{int(time.time())}.jpg")
        cv2.imwrite(path, self._last_frame)
        # chỉ giữ 'latest' để app xem nhanh
        cv2.imwrite(os.path.join(self.cfg.data_dir, "snapshots", "latest.jpg"), self._last_frame)
        return path

    def latest_snapshot_path(self) -> str | None:
        """Chụp mới ngay khi app yêu cầu (nếu có khung hình)."""
        if self._last_frame is not None:
            cv2.imwrite(os.path.join(self.cfg.data_dir, "snapshots", "latest.jpg"), self._last_frame)
        p = os.path.join(self.cfg.data_dir, "snapshots", "latest.jpg")
        return p if os.path.exists(p) else None

    # ---------- điều khiển từ app ----------
    def silence(self) -> None:
        self.silenced_level = self.level
        self.mqtt.stop_alarm()

    def force_level(self, level: int) -> None:
        """Dùng cho nút 'test cảnh báo' — chỉ tác động phần cứng, không ghi sự kiện."""
        self.mqtt.set_level(level)

    # ---------- trạng thái xuất cho API ----------
    def status(self) -> StatusResponse:
        now = time.monotonic()
        emo = self.emotion.current()
        return StatusResponse(
            level=int(self.level), level_name=self.level.name,
            adults=self.obs.adults, children=self.obs.children,
            alone_seconds=round(self.presence.alone_seconds(now), 1),
            emotion=emo.emotion.value, emotion_confidence=round(emo.confidence, 2),
            temp_c=self.telemetry.temp_c, door_state=self.telemetry.door_state,
            hardware_online=self.telemetry.hardware_online,
            camera_online=self.camera.online,
            silenced=self.silenced_level is not None and self.level <= self.silenced_level,
            message=self.message, ts=time.time(),
        )
