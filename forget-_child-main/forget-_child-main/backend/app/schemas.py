from enum import Enum, IntEnum

from pydantic import BaseModel, Field


class AlertLevel(IntEnum):
    SAFE = 0
    WATCH = 1
    WARNING = 2
    EMERGENCY = 3


class Emotion(str, Enum):
    CALM = "CALM"
    SLEEPING = "SLEEPING"
    FUSSY = "FUSSY"
    CRYING = "CRYING"
    UNKNOWN = "UNKNOWN"


class EmotionResult(BaseModel):
    emotion: Emotion = Emotion.UNKNOWN
    confidence: float = 0.0
    description: str = ""


class SituationDecision(BaseModel):
    """Đầu ra của Situation Analyzer Agent (CrewAI)."""
    level: int = Field(ge=0, le=3, description="0=an toàn, 1=theo dõi, 2=cảnh báo, 3=khẩn cấp")
    reason: str
    message_vi: str = Field(description="Câu thông báo ngắn tiếng Việt gửi phụ huynh")


class RuntimeSettings(BaseModel):
    """Ngưỡng cấu hình — chỉnh được từ app Android."""
    grace_seconds: int = Field(10, ge=0, le=600)         # trẻ một mình quá X giây -> WATCH
    warn_seconds: int = Field(60, ge=1, le=1800)         # -> WARNING
    emergency_seconds: int = Field(180, ge=1, le=3600)   # -> EMERGENCY
    temp_warn_c: float = 35.0
    temp_emergency_c: float = 42.0
    auto_unlock: bool = True                             # EMERGENCY tự mở cửa
    enable_push: bool = True
    enable_sms: bool = False
    emergency_contacts: list[str] = []                   # số điện thoại dạng +84...
    analysis_interval_s: int = Field(5, ge=2, le=60)     # chu kỳ chạy CrewAI/VLM


class StatusResponse(BaseModel):
    level: int = 0
    level_name: str = "SAFE"
    adults: int = 0
    children: int = 0
    alone_seconds: float = 0.0
    emotion: str = "UNKNOWN"
    emotion_confidence: float = 0.0
    temp_c: float | None = None
    door_state: str = "unknown"
    hardware_online: bool = False
    camera_online: bool = False
    silenced: bool = False
    message: str = ""
    ts: float = 0.0


# ---- request/response API ----
class LoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class FcmTokenRequest(BaseModel):
    token: str


class DoorRequest(BaseModel):
    action: str = Field(pattern="^(lock|unlock)$")


class SirenRequest(BaseModel):
    on: bool


class TestAlertRequest(BaseModel):
    level: int = Field(ge=0, le=3)


class OkResponse(BaseModel):
    ok: bool = True
    detail: str = ""


class EventRecord(BaseModel):
    id: int
    ts: float
    level: int
    reason: str
    emotion: str
    alone_seconds: float
    temp_c: float | None = None
    has_snapshot: bool = False
