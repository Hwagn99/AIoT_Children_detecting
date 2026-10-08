"""Ánh xạ lệnh cấp cao từ backend -> hành động phần cứng."""
import threading
import time

from config import HwConfig
from drivers.door import DoorServo
from drivers.outputs import make_buzzer, make_led
from drivers.temp import TempSensor

# Mức cảnh báo -> (pattern đèn đỏ, pattern còi)
LEVEL_MAP = {
    0: ("off", "off"),
    1: ("slow", "watch"),
    2: ("fast", "warning"),
    3: ("rapid", "emergency"),
}


class HardwareController:
    def __init__(self, cfg: HwConfig):
        self.cfg = cfg
        self.buzzer = make_buzzer(cfg)
        self.led_red = make_led(cfg, cfg.pin_led_red, "led_red")
        self.led_green = make_led(cfg, cfg.pin_led_green, "led_green")
        self.door = DoorServo(cfg)
        self.temp = TempSensor(cfg.use_temp_sensor, cfg.simulate)
        self.level = 0
        self._lock = threading.Lock()
        self.apply_level(0)

    # ---- hành động ----
    def apply_level(self, level: int) -> None:
        level = max(0, min(3, level))
        red, buz = LEVEL_MAP[level]
        with self._lock:
            self.level = level
            self.led_red.play(red)
            self.buzzer.play(buz)
            self.led_green.play("steady" if level == 0 else "off")

    def stop_alarm(self) -> None:
        """Người dùng bấm 'tắt còi' trên app: tắt còi, đèn đỏ sáng đứng."""
        with self._lock:
            self.buzzer.stop()
            self.led_red.play("steady")

    def siren(self, on: bool) -> None:
        with self._lock:
            self.buzzer.play("emergency" if on else "off")

    # ---- xử lý lệnh ----
    def handle(self, cmd: dict) -> dict:
        t = cmd.get("type")
        try:
            if t == "level":
                self.apply_level(int(cmd["level"]))
            elif t == "door":
                {"lock": self.door.lock, "unlock": self.door.unlock}[cmd["action"]]()
            elif t == "siren":
                self.siren(bool(cmd["on"]))
            elif t == "stop_alarm":
                self.stop_alarm()
            elif t == "ping":
                pass
            else:
                return {"ok": False, "error": f"lệnh không hỗ trợ: {t}"}
            return {"ok": True, "type": t}
        except Exception as e:  # noqa: BLE001
            return {"ok": False, "type": t, "error": str(e)}

    def telemetry(self) -> dict:
        return {
            "temp_c": self.temp.read_c(),
            "door": self.door.state,
            "level": self.level,
            "buzzer": self.buzzer.current,
            "ts": time.time(),
        }

    def shutdown(self) -> None:
        self.buzzer.stop()
        self.led_red.stop()
        self.led_green.stop()
