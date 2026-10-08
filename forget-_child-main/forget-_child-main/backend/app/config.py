from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Cấu hình cố định (env). Ngưỡng chỉnh được từ app nằm ở RuntimeSettings."""
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    device_id: str = "car-001"

    camera_source: str = "0"
    yolo_weights: str = "models/adult_child.pt"
    yolo_conf: float = 0.4
    yolo_imgsz: int = 640
    detect_fps: int = 8
    presence_hold_s: float = 3.0       # giữ trạng thái "có mặt" khi YOLO rớt khung ngắn

    mqtt_host: str = "localhost"
    mqtt_port: int = 1883
    mqtt_user: str | None = None
    mqtt_pass: str | None = None
    mqtt_tls: bool = False
    cmd_secret: str = "change-me"

    app_username: str = "parent"
    app_password: str = "change-me"
    jwt_secret: str = "change-me-too"
    jwt_expire_minutes: int = 60 * 24 * 7

    vlm_base_url: str = "http://localhost:11434"
    vlm_model: str = "qwen2.5vl:3b"
    crew_llm_model: str = "ollama/qwen2.5:3b"
    crew_llm_base_url: str = "http://localhost:11434"

    fcm_credentials_path: str | None = None
    twilio_sid: str | None = None
    twilio_token: str | None = None
    twilio_from: str | None = None

    data_dir: str = "data"
    db_path: str = "data/events.db"
    runtime_settings_path: str = "data/runtime_settings.json"


settings = Settings()
