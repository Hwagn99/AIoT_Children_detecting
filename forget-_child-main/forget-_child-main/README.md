# ChildGuard – Hệ thống xử lý trẻ bị bỏ quên / ở một mình trong ô tô

```
┌────────────────────────── XE (Raspberry Pi 5 + Camera) ──────────────────────────┐
│                                                                                  │
│  Camera ─► backend/  (FastAPI)                       hardware/ (gpiozero)        │
│            ├─ YOLO26n + ByteTrack  (adult/child)        ├─ Còi (buzzer)          │
│            ├─ Luật an toàn (fast path)                  ├─ Đèn LED đỏ/xanh       │
│            ├─ CrewAI: Image Agent ► VLM (cảm xúc)       ├─ Servo khoá cửa        │
│            │          Situation Agent ► mức nguy cơ     └─ DS18B20 (nhiệt độ)    │
│            └─ Notifier (FCM / SMS)          ◄── MQTT (Mosquitto, ký HMAC) ──►    │
└──────────┬───────────────────────────────────────────────────────────────────────┘
           │ REST + WebSocket (JWT)  /  FCM push
           ▼
     android/  (Kotlin + Jetpack Compose)
```

| Thư mục | Nhiệm vụ | Ngôn ngữ |
|---|---|---|
| `hardware/` | Điều khiển GPIO: còi, đèn, servo, đọc nhiệt độ; nhận lệnh MQTT đã ký | Python |
| `backend/`  | Camera → AI → quyết định → gửi lệnh phần cứng, thông báo, API cho app | Python |
| `android/`  | App phụ huynh: giám sát realtime, điều khiển, lịch sử, cấu hình | Kotlin |

## Luồng xử lý
1. `Camera` giữ khung hình mới nhất → `ChildAdultDetector` (YOLO26n + ByteTrack) đếm adult/child.
2. `PresenceTracker` tính **số giây trẻ ở một mình** (có độ trễ chống rớt khung).
3. **Fast path** (`core/rules.py`, xác định, không phụ thuộc LLM): thời gian + nhiệt độ + cảm xúc → mức 0-3.
4. **Slow path** (`agents/crew.py`): khi trẻ ở một mình quá ngưỡng, cứ mỗi N giây chạy CrewAI:
   Image Analyzer Agent gọi VLM đọc cảm xúc → Situation Analyzer Agent viết đánh giá + thông điệp.
   *Crew chỉ được nâng mức tối đa tới WARNING; EMERGENCY (mở cửa) chỉ do luật xác định kích hoạt.*
5. Đổi mức → gửi lệnh MQTT cho hardware (đèn/còi), lưu sự kiện + ảnh, push FCM/SMS; mức 3 → mở servo (nếu bật `auto_unlock`).

| Mức | Tên | Điều kiện mặc định | Phần cứng | Thông báo |
|---|---|---|---|---|
| 0 | SAFE | có người lớn / không có trẻ | LED xanh | – |
| 1 | WATCH | trẻ một mình > 10s | LED đỏ chớp chậm | push |
| 2 | WARNING | > 60s, hoặc khóc, hoặc ≥ 35°C | đèn chớp + còi ngắt quãng | push + SMS |
| 3 | EMERGENCY | > 180s, hoặc ≥ 42°C, hoặc khóc + nóng | đèn rapid + còi + mở cửa | push + SMS |

## Hợp đồng MQTT (backend ⇄ hardware)
| Topic | Chiều | Nội dung |
|---|---|---|
| `car/<id>/cmd` | backend → hw | `{"cmd": {...}, "ts": 1700000000, "sig": "<hmac-sha256>"}` |
| `car/<id>/telemetry` | hw → backend | `{"temp_c": 31.2, "door": "locked", "level": 0, ...}` mỗi 5s |
| `car/<id>/status` | hw → backend | `online` / `offline` (retained + Last Will) |
| `car/<id>/ack` | hw → backend | kết quả thực thi lệnh |

Lệnh: `{"type":"level","level":0-3}`, `{"type":"door","action":"lock|unlock"}`, `{"type":"siren","on":true}`, `{"type":"stop_alarm"}`, `{"type":"ping"}`.

## API backend (JWT, trừ /auth/login)
`POST /auth/login` · `POST /devices/fcm-token` · `GET /status` · `WS /ws?token=` · `GET /events` ·
`GET|PUT /settings` · `GET /snapshots/latest` · `POST /control/{door|siren|silence|test-alert}`

## Chạy thử
```bash
# 0) Broker MQTT (trên Pi)
sudo apt install mosquitto && sudo systemctl enable --now mosquitto
#   (thật: bật allow_anonymous false + password_file + TLS)

# 1) Hardware  (thử trên PC: HW_SIMULATE=1)
cd hardware && pip install -r requirements.txt
CMD_SECRET=change-me python main.py

# 2) Backend
cd backend && pip install -r requirements.txt && cp .env.example .env
#   đặt models/adult_child.pt (xem models/README.md), chạy Ollama nếu dùng VLM/LLM local
uvicorn app.main:app --host 0.0.0.0 --port 8000

# 3) Android: mở thư mục android/ bằng Android Studio (Gradle sẽ tự tạo wrapper)
#   - Đăng nhập với http://<IP_Pi>:8000 + APP_USERNAME/APP_PASSWORD trong .env
#   - Muốn push FCM: thêm google-services.json vào android/app/ và bỏ comment 2 dòng plugin google-services
#     ở build.gradle.kts (gốc + app); backend đặt FCM_CREDENTIALS_PATH tới service-account.json
```
Sơ đồ chân mặc định (BCM): buzzer 17 · LED đỏ 27 · LED xanh 22 · servo 18 · DS18B20 data = GPIO4 (kèm trở kéo 4.7kΩ).
Servo/còi công suất lớn nên cấp nguồn riêng, chung GND với Pi.

## Việc còn lại (TODO)
- Fine-tune YOLO26n cho 2 lớp `adult`/`child` (dữ liệu trong khoang xe, CVAT).
- Thêm cảm biến dự phòng (PIR/áp lực ghế) để giảm phụ thuộc vào camera bị che/tối (bật IR cho ban đêm).
- Đổi xác thực sang user DB + băm mật khẩu; mã hoá token trên Android bằng Keystore; HTTPS/VPN thay cho `usesCleartextTraffic`.
- Xử lý khi camera mất tín hiệu lúc đang có trẻ (hiện chỉ hiển thị `camera_online=false`).
- Đo độ trễ thực tế từng khâu, tối ưu (ONNX/NCNN cho YOLO trên Pi).
