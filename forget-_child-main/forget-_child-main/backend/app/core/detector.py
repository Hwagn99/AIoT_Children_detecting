"""YOLO26n phân loại adult/child + ByteTrack (tích hợp sẵn trong Ultralytics)."""
from dataclasses import dataclass, field

import numpy as np
from ultralytics import YOLO

# Tên lớp trong file weights của bạn -> nhãn nội bộ
CLASS_MAP = {"adult": "adult", "child": "child"}


@dataclass
class Detection:
    label: str                       # "adult" | "child"
    conf: float
    box: tuple[int, int, int, int]   # x1, y1, x2, y2
    track_id: int | None = None


@dataclass
class Observation:
    detections: list[Detection] = field(default_factory=list)

    @property
    def adults(self) -> int:
        return sum(d.label == "adult" for d in self.detections)

    @property
    def children(self) -> int:
        return sum(d.label == "child" for d in self.detections)

    def best_child(self) -> Detection | None:
        kids = [d for d in self.detections if d.label == "child"]
        return max(kids, key=lambda d: d.conf) if kids else None


class ChildAdultDetector:
    def __init__(self, weights: str, conf: float = 0.4, imgsz: int = 640):
        self.model = YOLO(weights)
        self.conf, self.imgsz = conf, imgsz

    def detect(self, frame: np.ndarray) -> Observation:
        res = self.model.track(
            frame, persist=True, tracker="bytetrack.yaml",
            conf=self.conf, imgsz=self.imgsz, verbose=False,
        )[0]
        out = Observation()
        if res.boxes is None or len(res.boxes) == 0:
            return out
        ids = res.boxes.id.int().tolist() if res.boxes.id is not None else [None] * len(res.boxes)
        for box, tid in zip(res.boxes, ids):
            name = CLASS_MAP.get(res.names[int(box.cls[0])])
            if name is None:
                continue
            x1, y1, x2, y2 = (int(v) for v in box.xyxy[0].tolist())
            out.detections.append(Detection(name, float(box.conf[0]), (x1, y1, x2, y2), tid))
        return out

    @staticmethod
    def crop(frame: np.ndarray, det: Detection, pad: float = 0.15) -> np.ndarray:
        h, w = frame.shape[:2]
        x1, y1, x2, y2 = det.box
        px, py = int((x2 - x1) * pad), int((y2 - y1) * pad)
        return frame[max(0, y1 - py):min(h, y2 + py), max(0, x1 - px):min(w, x2 + px)]
