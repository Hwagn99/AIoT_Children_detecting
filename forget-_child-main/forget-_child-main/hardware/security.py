"""Ký / xác thực lệnh MQTT bằng HMAC-SHA256 (khớp với backend/app/services/mqtt_client.py)."""
import hashlib
import hmac
import json
import time


def canonical(cmd: dict) -> str:
    return json.dumps(cmd, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def sign(secret: str, ts: int, cmd: dict) -> str:
    msg = f"{ts}.{canonical(cmd)}".encode()
    return hmac.new(secret.encode(), msg, hashlib.sha256).hexdigest()


def verify(secret: str, envelope: dict, max_age_s: int) -> dict | None:
    """Trả về cmd nếu hợp lệ, ngược lại None."""
    try:
        cmd, ts, sig = envelope["cmd"], int(envelope["ts"]), envelope["sig"]
    except (KeyError, TypeError, ValueError):
        return None
    if abs(time.time() - ts) > max_age_s:
        return None
    if not hmac.compare_digest(sig, sign(secret, ts, cmd)):
        return None
    return cmd
