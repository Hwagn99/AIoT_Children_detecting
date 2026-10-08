"""Luật an toàn XÁC ĐỊNH (fast path). Đây là 'sàn an toàn': không phụ thuộc LLM."""
from app.schemas import AlertLevel, Emotion, RuntimeSettings


def evaluate(alone_s: float, temp_c: float | None, emotion: Emotion,
             rt: RuntimeSettings) -> tuple[AlertLevel, list[str]]:
    if alone_s <= 0 or alone_s < rt.grace_seconds:
        return AlertLevel.SAFE, []

    level, why = AlertLevel.WATCH, [f"trẻ ở một mình {int(alone_s)}s"]

    if alone_s >= rt.warn_seconds:
        level = max(level, AlertLevel.WARNING)
    if alone_s >= rt.emergency_seconds:
        level = AlertLevel.EMERGENCY

    if temp_c is not None:
        if temp_c >= rt.temp_emergency_c:
            level = AlertLevel.EMERGENCY
            why.append(f"nhiệt độ cabin {temp_c:.0f}°C rất cao")
        elif temp_c >= rt.temp_warn_c:
            level = max(level, AlertLevel.WARNING)
            why.append(f"nhiệt độ cabin {temp_c:.0f}°C cao")

    if emotion == Emotion.CRYING:
        level = max(level, AlertLevel.WARNING)
        why.append("trẻ đang khóc")
        if temp_c is not None and temp_c >= rt.temp_warn_c:
            level = AlertLevel.EMERGENCY

    return level, why
