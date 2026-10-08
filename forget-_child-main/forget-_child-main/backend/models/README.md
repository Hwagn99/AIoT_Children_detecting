Đặt file weights YOLO26n đã fine-tune (2 lớp: `adult`, `child`) tại đây: `adult_child.pt`

Huấn luyện nhanh:
    yolo detect train model=yolo26n.pt data=dataset.yaml epochs=100 imgsz=640

dataset.yaml:
    path: ./dataset
    train: images/train
    val: images/val
    names: {0: adult, 1: child}

Gợi ý dữ liệu: ảnh trong khoang xe (búp bê + người thật), nhiều điều kiện sáng, góc camera, mức che khuất
(gán nhãn bằng CVAT như bài báo tham khảo).
