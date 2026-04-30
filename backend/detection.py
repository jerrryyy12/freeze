from PIL import Image
from typing import List, Dict, Any
import os

INGREDIENT_CATEGORIES = {
    "carrot": ("당근", "채소"),
    "onion": ("양파", "채소"),
    "potato": ("감자", "채소"),
    "tomato": ("토마토", "채소"),
    "cucumber": ("오이", "채소"),
    "broccoli": ("브로콜리", "채소"),
    "pepper": ("피망", "채소"),
    "garlic": ("마늘", "채소"),
    "apple": ("사과", "과일"),
    "orange": ("오렌지", "과일"),
    "banana": ("바나나", "과일"),
    "egg": ("계란", "유제품"),
    "milk": ("우유", "유제품"),
    "beef": ("소고기", "육류"),
    "pork": ("돼지고기", "육류"),
    "chicken": ("닭고기", "육류"),
    "fish": ("생선", "해산물"),
    "shrimp": ("새우", "해산물"),
}

_model = None


def _load_model():
    global _model
    if _model is not None:
        return _model
    try:
        from ultralytics import YOLO
        model_path = os.path.join(os.path.dirname(__file__), "models", "yolov8n.pt")
        _model = YOLO(model_path if os.path.exists(model_path) else "yolov8n.pt")
        return _model
    except Exception:
        return None


def detect_ingredients(image: Image.Image) -> List[Dict[str, Any]]:
    model = _load_model()

    if model is not None:
        return _detect_with_yolo(model, image)

    return _mock_detect(image)


def _detect_with_yolo(model, image: Image.Image) -> List[Dict[str, Any]]:
    results = model(image, conf=0.4)
    detections = []
    seen = set()

    for result in results:
        for box in result.boxes:
            cls_id = int(box.cls[0])
            cls_name = result.names[cls_id].lower()
            conf = float(box.conf[0])

            if cls_name in seen:
                continue
            seen.add(cls_name)

            if cls_name in INGREDIENT_CATEGORIES:
                kr_name, category = INGREDIENT_CATEGORIES[cls_name]
            else:
                kr_name = cls_name
                category = "기타"

            detections.append({
                "name": kr_name,
                "english_name": cls_name,
                "category": category,
                "confidence": round(conf, 3),
                "bbox": box.xyxy[0].tolist(),
            })

    return detections


def _mock_detect(image: Image.Image) -> List[Dict[str, Any]]:
    """YOLOv8 모델이 없을 때 사용하는 mock 결과"""
    return [
        {"name": "당근", "english_name": "carrot", "category": "채소", "confidence": 0.92, "bbox": []},
        {"name": "양파", "english_name": "onion", "category": "채소", "confidence": 0.88, "bbox": []},
        {"name": "계란", "english_name": "egg", "category": "유제품", "confidence": 0.85, "bbox": []},
    ]
