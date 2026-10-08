"""Gửi cảnh báo: push FCM tới app + SMS Twilio tới danh bạ khẩn cấp."""
import asyncio
import logging

from app.config import Settings
from app.schemas import AlertLevel, RuntimeSettings
from app.services.db import Database

log = logging.getLogger("notifier")

TITLES = {
    AlertLevel.WATCH: "Chú ý: trẻ đang ở một mình trong xe",
    AlertLevel.WARNING: "CẢNH BÁO: trẻ ở một mình trong xe",
    AlertLevel.EMERGENCY: "KHẨN CẤP: trẻ có nguy hiểm trong xe!",
}


class Notifier:
    def __init__(self, cfg: Settings, db: Database):
        self.cfg, self.db = cfg, db
        self._fcm_ready = False
        self._twilio = None

        if cfg.fcm_credentials_path:
            try:
                import firebase_admin
                from firebase_admin import credentials
                firebase_admin.initialize_app(credentials.Certificate(cfg.fcm_credentials_path))
                self._fcm_ready = True
            except Exception as e:  # noqa: BLE001
                log.warning("Không khởi tạo được FCM: %s", e)
        if cfg.twilio_sid and cfg.twilio_token and cfg.twilio_from:
            try:
                from twilio.rest import Client
                self._twilio = Client(cfg.twilio_sid, cfg.twilio_token)
            except Exception as e:  # noqa: BLE001
                log.warning("Không khởi tạo được Twilio: %s", e)

    async def notify(self, level: AlertLevel, message: str, rt: RuntimeSettings) -> None:
        if level == AlertLevel.SAFE:
            return
        tasks = []
        if rt.enable_push:
            tasks.append(asyncio.to_thread(self._push, level, message))
        if rt.enable_sms and level >= AlertLevel.WARNING:
            tasks.append(asyncio.to_thread(self._sms, level, message, rt.emergency_contacts))
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    def _push(self, level: AlertLevel, message: str) -> None:
        tokens = self.db.all_tokens()
        if not self._fcm_ready or not tokens:
            log.info("[push bỏ qua] fcm_ready=%s tokens=%d", self._fcm_ready, len(tokens))
            return
        from firebase_admin import messaging
        msg = messaging.MulticastMessage(
            tokens=tokens,
            data={"level": str(int(level)), "title": TITLES[level], "body": message},
            android=messaging.AndroidConfig(priority="high"),
        )
        resp = messaging.send_each_for_multicast(msg)
        log.info("FCM: %d ok, %d lỗi", resp.success_count, resp.failure_count)

    def _sms(self, level: AlertLevel, message: str, contacts: list[str]) -> None:
        if not self._twilio:
            log.info("[sms bỏ qua] Twilio chưa cấu hình")
            return
        for number in contacts:
            try:
                self._twilio.messages.create(
                    to=number, from_=self.cfg.twilio_from, body=f"{TITLES[level]}\n{message}")
            except Exception as e:  # noqa: BLE001
                log.warning("SMS tới %s lỗi: %s", number, e)
