"""Cầu nối MQTT: nhận lệnh từ backend, đẩy telemetry lên."""
import json
import logging
import threading

import paho.mqtt.client as mqtt

import security
from config import HwConfig
from controller import HardwareController

log = logging.getLogger("hw.mqtt")


class MqttBridge:
    def __init__(self, cfg: HwConfig, ctrl: HardwareController):
        self.cfg, self.ctrl = cfg, ctrl
        base = f"car/{cfg.device_id}"
        self.t_cmd, self.t_tele = f"{base}/cmd", f"{base}/telemetry"
        self.t_ack, self.t_status = f"{base}/ack", f"{base}/status"
        self._stop = threading.Event()

        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"hw-{cfg.device_id}")
        if cfg.mqtt_user:
            self.client.username_pw_set(cfg.mqtt_user, cfg.mqtt_pass)
        if cfg.mqtt_tls:
            self.client.tls_set()
        # Last Will: nếu Pi mất kết nối đột ngột, broker tự báo offline
        self.client.will_set(self.t_status, "offline", qos=1, retain=True)
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        log.info("MQTT connected: %s", reason_code)
        client.subscribe(self.t_cmd, qos=1)
        client.publish(self.t_status, "online", qos=1, retain=True)

    def _on_message(self, client, userdata, msg):
        try:
            envelope = json.loads(msg.payload)
        except json.JSONDecodeError:
            return
        cmd = security.verify(self.cfg.cmd_secret, envelope, self.cfg.cmd_max_age_s)
        if cmd is None:
            log.warning("Lệnh bị từ chối (sai chữ ký hoặc quá hạn)")
            client.publish(self.t_ack, json.dumps({"ok": False, "error": "unauthorized"}))
            return
        ack = self.ctrl.handle(cmd)
        client.publish(self.t_ack, json.dumps(ack), qos=1)

    def run(self) -> None:
        self.client.connect_async(self.cfg.mqtt_host, self.cfg.mqtt_port, keepalive=30)
        self.client.loop_start()                       # tự reconnect
        while not self._stop.wait(self.cfg.telemetry_period_s):
            self.client.publish(self.t_tele, json.dumps(self.ctrl.telemetry()), qos=0)

    def stop(self) -> None:
        self._stop.set()
        self.client.publish(self.t_status, "offline", qos=1, retain=True)
        self.client.loop_stop()
        self.client.disconnect()
