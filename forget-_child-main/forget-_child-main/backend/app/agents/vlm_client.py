"""Gọi VLM (mặc định Ollama) để phân tích cảm xúc từ ảnh crop khuôn mặt/người của trẻ."""
import base64
import json
import logging

import httpx

from app.schemas import Emotion, EmotionResult

log = logging.getLogger("vlm")

PROMPT = (
    "Bạn là hệ thống giám sát an toàn trẻ em trong ô tô. Nhìn ảnh và cho biết trạng thái cảm xúc "
    "của đứa trẻ. Chỉ trả về JSON: "
    '{"emotion": "CALM|SLEEPING|FUSSY|CRYING|UNKNOWN", "confidence": 0-1, "description": "một câu ngắn"}'
)


class VLMClient:
    def __init__(self, base_url: str, model: str, timeout: float = 30.0):
        self.base_url, self.model = base_url.rstrip("/"), model
        self._http = httpx.Client(timeout=timeout)

    def analyze_emotion(self, jpeg: bytes) -> EmotionResult:
        body = {
            "model": self.model,
            "stream": False,
            "format": "json",
            "messages": [{
                "role": "user",
                "content": PROMPT,
                "images": [base64.b64encode(jpeg).decode()],
            }],
        }
        try:
            r = self._http.post(f"{self.base_url}/api/chat", json=body)
            r.raise_for_status()
            data = json.loads(r.json()["message"]["content"])
            emo = str(data.get("emotion", "UNKNOWN")).upper()
            return EmotionResult(
                emotion=Emotion(emo) if emo in Emotion.__members__ else Emotion.UNKNOWN,
                confidence=float(data.get("confidence", 0.0)),
                description=str(data.get("description", "")),
            )
        except Exception as e:  # noqa: BLE001
            log.warning("VLM lỗi: %s", e)
            return EmotionResult(description=f"VLM error: {e}")
