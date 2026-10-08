"""Entry point phần cứng:  python main.py   (HW_SIMULATE=1 để chạy thử trên PC)"""
import logging
import os
import signal

os.environ.setdefault("GPIOZERO_PIN_FACTORY", "lgpio")   # bắt buộc trên Raspberry Pi 5

from config import CFG                    # noqa: E402
from controller import HardwareController  # noqa: E402
from mqtt_bridge import MqttBridge         # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")


def main() -> None:
    ctrl = HardwareController(CFG)
    bridge = MqttBridge(CFG, ctrl)

    def _bye(*_):
        bridge.stop()
        ctrl.shutdown()
        raise SystemExit(0)

    signal.signal(signal.SIGINT, _bye)
    signal.signal(signal.SIGTERM, _bye)
    bridge.run()


if __name__ == "__main__":
    main()
