import cv2
import time
from pathlib import Path
from ultralytics import YOLO

# 1. Khởi tạo mô hình
model = YOLO("model/YOLO26n.pt") # Lưu ý: Đảm bảo tên file model của bạn đúng

# 2. Mở file video trên máy

base_dir = Path(__file__).resolve().parent
video_candidates = [
    base_dir / "video_anh_test" / "Video Project.mp4",
    base_dir / "vìdeo_anh_test" / "Video Project.mp4",
    base_dir / "Video Project.mp4",
]

video_path = next((p for p in video_candidates if p.exists()), video_candidates[0])
cap = cv2.VideoCapture(str(video_path))

if not cap.isOpened():
    print(f"❌ LỖI: Không thể mở được video! Hãy kiểm tra lại đường dẫn: {video_path}")
    exit()

prev_time = 0

while cap.isOpened():
    success, frame = cap.read()
    
    if not success:
        print("Đã chạy hết video hoặc đường dẫn video bị sai.")
        break

    # 3. SỬ DỤNG TRACKING THAY VÌ PREDICT
    # persist=True: Bắt buộc phải có để model nhớ đối tượng qua các frame
    # tracker="bytetrack.yaml": Thuật toán theo dõi giúp bounding box ổn định, không bị nháy
    # imgsz=640: Giảm kích thước ảnh để tăng FPS (có thể thử 480 hoặc 320 nếu vẫn chậm)
    # conf=0.35: Giảm nhẹ ngưỡng tự tin để tránh rớt box
    results = model.track(
        source=frame, 
        conf=0.35, 
        imgsz=640, 
        persist=True, 
        tracker="bytetrack.yaml", 
        verbose=False
    )
    
    result = results[0]
    
    # YOLO vẽ bounding box, nhãn, ID tracking và độ tin cậy
    annotated_frame = result.plot()
    
    # 4. Trích xuất tọa độ để làm logic cảnh báo riêng
    # Khi dùng track, bạn có thể lấy thêm ID của đối tượng (box.id)
    if result.boxes.id is not None:
        for box, track_id in zip(result.boxes, result.boxes.id):
            if int(box.cls[0]) == 0: 
                conf = float(box.conf[0])
                obj_id = int(track_id)
                # print(f"[BÁO ĐỘNG] Trẻ em (ID: {obj_id}) - Conf: {conf:.2f}")

    # 5. Tính và in FPS
    current_time = time.time()
    fps = 1 / (current_time - prev_time)
    prev_time = current_time
    
    cv2.putText(annotated_frame, f"FPS: {int(fps)}", (10, 30), 
                cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2)

    # 6. Hiển thị
    cv2.imshow("Test YOLO - Nhan dien tre em trong xe", annotated_frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()