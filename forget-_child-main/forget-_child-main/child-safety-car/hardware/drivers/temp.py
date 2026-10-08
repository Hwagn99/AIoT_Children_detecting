"""Đọc nhiệt độ cabin từ DS18B20 qua 1-Wire (bật dtoverlay=w1-gpio trong /boot/firmware/config.txt)."""
import glob


class TempSensor:
    def __init__(self, enabled: bool = True, simulate: bool = False):
        self.enabled = enabled
        self.simulate = simulate
        self._paths = glob.glob("/sys/bus/w1/devices/28-*/w1_slave") if enabled and not simulate else []

    def read_c(self) -> float | None:
        if self.simulate:
            return 30.0
        if not self._paths:
            return None
        try:
            with open(self._paths[0]) as f:
                lines = f.read().strip().splitlines()
            if len(lines) < 2 or not lines[0].endswith("YES"):
                return None
            return int(lines[1].split("t=")[-1]) / 1000.0
        except (OSError, ValueError):
            return None
