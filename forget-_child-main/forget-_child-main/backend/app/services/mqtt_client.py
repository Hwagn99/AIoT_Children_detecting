"""MQTT phía backend: gửi lệnh (có ký HMAC) xuống hardware, nhận telemetry."""
import hashlib
import hmac
import json
import logging
import time

import paho.mqtt.client as mqtt

from app.config import Settings
from app.core.state import Telemetry

log = logging.getLogger("mqtt")


def _canonical(cmd: dict) -> str:
    return json.dumps(cmd, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


class MqttClient:
    def __init__(self, cfg: Settings, telemetry: Telemetry):
        self.cfg, self.telemetry = cfg, telemetry
        base = f"car/{cfg.device_id}"
        self.t_cmd, self.t_tele = f"{base}/cmd", f"{base}/telemetry"
        self.t_status, self.t_ack = f"{base}/status", f"{base}/ack"

        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"backend-{cfg.device_id}")
        if cfg.mqtt_user:
            self.client.username_pw_set(cfg.mqtt_user, cfg.mqtt_pass)
        if cfg.mqtt_tls:
            self.client.tls_set()
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

    def start(self) -> None:
        self.client.connect_async(self.cfg.mqtt_host, self.cfg.mqtt_port, keepalive=30)
        self.client.loop_start()

    def stop(self) -> None:
        self.client.loop_stop()
        self.client.disconnect()

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        log.info("MQTT connected: %s", reason_code)
        client.subscribe([(self.t_tele, 0), (self.t_status, 1), (self.t_ack, 1)])

    def _on_message(self, client, userdata, msg):
        if msg.topic == self.t_status:
            self.telemetry.status_online = msg.payload.decode() == "online"
        elif msg.topic == self.t_tele:
            try:
                d = json.loads(msg.payload)
            except json.JSONDecodeError:
                return
            self.telemetry.temp_c = d.get("temp_c")
            self.telemetry.door_state = d.get("door", "unknown")
            self.telemetry.last_seen = time.time()
            self.telemetry.status_online = True
        elif msg.topic == self.t_ack:
            log.info("ACK từ hardware: %s", msg.payload.decode())

    def send(self, cmd: dict) -> None:
        ts = int(time.time())
        sig = hmac.new(self.cfg.cmd_secret.encode(), f"{ts}.{_canonical(cmd)}".encode(),
                       hashlib.sha256).hexdigest()
        self.client.publish(self.t_cmd, json.dumps({"cmd": cmd, "ts": ts, "sig": sig}), qos=1)

    # tiện ích
    def set_level(self, level: int) -> None: self.send({"type": "level", "level": int(level)})
    def door(self, action: str) -> None: self.send({"type": "door", "action": action})
    def siren(self, on: bool) -> None: self.send({"type": "siren", "on": on})
    def stop_alarm(self) -> None: self.send({"type": "stop_alarm"})
