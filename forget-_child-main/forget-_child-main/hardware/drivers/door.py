"""Servo đóng/mở khoá cửa xe."""
import threading
import time

from config import HwConfig


class DoorServo:
    def __init__(self, cfg: HwConfig):
        self._cfg = cfg
        self._lock = threading.Lock()
        self.state = "unknown"          # locked | unlocked | unknown
        self._servo = None
        if not cfg.simulate:
            from gpiozero import AngularServo
            self._servo = AngularServo(
                cfg.pin_servo, min_angle=-90, max_angle=90,
                min_pulse_width=0.0005, max_pulse_width=0.0025,
                initial_angle=None,
            )

    def _move(self, angle: float, new_state: str) -> None:
        with self._lock:
            if self._servo is None:
                print(f"[SIM] servo -> {angle}° ({new_state})")
            else:
                self._servo.angle = angle
                time.sleep(0.7)          # chờ servo tới vị trí
                self._servo.detach()     # nhả xung PWM để khỏi rung/nóng
            self.state = new_state

    def lock(self) -> None:
        self._move(self._cfg.servo_locked_angle, "locked")

    def unlock(self) -> None:
        self._move(self._cfg.servo_unlocked_angle, "unlocked")
