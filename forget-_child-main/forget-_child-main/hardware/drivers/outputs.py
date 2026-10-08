"""Còi (buzzer) và đèn LED, có hỗ trợ pattern nhấp nháy chạy nền."""
import threading

from config import HwConfig

# pattern = danh sách (giây_bật, giây_tắt), lặp vô hạn. "steady" = bật liên tục.
BUZZER_PATTERNS: dict[str, object] = {
    "off": [],
    "watch": [],                      # mức 1: chưa kêu, chỉ báo app
    "warning": [(0.3, 1.2)],          # mức 2: bíp ngắt quãng
    "emergency": [(0.6, 0.2)],        # mức 3: còi gần như liên tục
    "steady": "steady",
}
LED_PATTERNS: dict[str, object] = {
    "off": [],
    "steady": "steady",
    "slow": [(0.5, 1.5)],
    "fast": [(0.2, 0.3)],
    "rapid": [(0.08, 0.08)],
}


class _FakeOut:
    """Thiết bị giả để test trên PC (HW_SIMULATE=1)."""
    def __init__(self, name): self.name = name
    def on(self): print(f"[SIM] {self.name} ON")
    def off(self): print(f"[SIM] {self.name} OFF")


class PatternOutput:
    def __init__(self, device, patterns: dict):
        self._dev = device
        self._patterns = patterns
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.current = "off"

    def play(self, name: str) -> None:
        if name not in self._patterns:
            raise ValueError(f"pattern không tồn tại: {name}")
        self.stop()
        self.current = name
        pattern = self._patterns[name]
        if pattern == "steady":
            self._dev.on()
        elif pattern:
            self._stop.clear()
            self._thread = threading.Thread(target=self._run, args=(pattern,), daemon=True)
            self._thread.start()

    def _run(self, pattern) -> None:
        while not self._stop.is_set():
            for on_s, off_s in pattern:
                self._dev.on()
                if self._stop.wait(on_s):
                    break
                self._dev.off()
                if self._stop.wait(off_s):
                    break
        self._dev.off()

    def stop(self) -> None:
        self._stop.set()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.5)
        self._thread = None
        self._dev.off()
        self.current = "off"


def make_buzzer(cfg: HwConfig) -> PatternOutput:
    if cfg.simulate:
        return PatternOutput(_FakeOut("buzzer"), BUZZER_PATTERNS)
    from gpiozero import Buzzer  # còi active (có mạch dao động sẵn)
    return PatternOutput(Buzzer(cfg.pin_buzzer), BUZZER_PATTERNS)


def make_led(cfg: HwConfig, pin: int, name: str) -> PatternOutput:
    if cfg.simulate:
        return PatternOutput(_FakeOut(name), LED_PATTERNS)
    from gpiozero import LED
    return PatternOutput(LED(pin), LED_PATTERNS)
