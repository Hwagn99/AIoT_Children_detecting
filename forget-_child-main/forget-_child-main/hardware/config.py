"""Cấu hình phần cứng. Đổi chân GPIO theo mạch thực tế của bạn (đánh số BCM)."""
import os
from dataclasses import dataclass


def _b(name: str, default: str = "0") -> bool:
    return os.getenv(name, default) == "1"


@dataclass(frozen=True)
class HwConfig:
    device_id: str = os.getenv("DEVICE_ID", "car-001")

    # --- MQTT (broker Mosquitto chạy trên Raspberry Pi) ---
    mqtt_host: str = os.getenv("MQTT_HOST", "localhost")
    mqtt_port: int = int(os.getenv("MQTT_PORT", "1883"))
    mqtt_user: str | None = os.getenv("MQTT_USER")
    mqtt_pass: str | None = os.getenv("MQTT_PASS")
    mqtt_tls: bool = _b("MQTT_TLS")

    # --- Bảo mật lệnh: HMAC chung với backend ---
    cmd_secret: str = os.getenv("CMD_SECRET", "change-me")
    cmd_max_age_s: int = 15          # chống replay: lệnh cũ hơn 15s bị bỏ

    # --- Chân GPIO (BCM) ---
    pin_buzzer: int = 17
    pin_led_red: int = 27
    pin_led_green: int = 22
    pin_servo: int = 18

    # --- Servo khoá cửa ---
    servo_locked_angle: float = 0.0
    servo_unlocked_angle: float = 90.0

    # --- Cảm biến nhiệt DS18B20 (1-Wire) ---
    use_temp_sensor: bool = _b("USE_TEMP_SENSOR", "1")

    telemetry_period_s: float = 5.0
    simulate: bool = _b("HW_SIMULATE")   # =1 để chạy thử trên PC, không cần GPIO


CFG = HwConfig()
