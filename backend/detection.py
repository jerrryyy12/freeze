import base64
import io
import json
import os
from typing import Any, Dict, List

from PIL import Image

ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_API_KEY", "")

DETECTION_PROMPT = """이 이미지에서 식재료를 모두 찾아주세요.

각 식재료에 대해 다음 JSON 배열 형식으로만 답하세요 (다른 텍스트 없이):
[
  {"name": "식재료 이름(한국어)", "category": "카테고리", "confidence": 0.0~1.0}
]

카테고리는 다음 중 하나만 사용하세요: 채소, 과일, 육류, 해산물, 유제품, 곡류, 조미료, 기타

식재료가 없으면 빈 배열 []을 반환하세요.
이미지에 보이는 식재료만 포함하고, 확신할 수 없으면 제외하세요."""


def _image_to_base64(image: Image.Image) -> str:
    buf = io.BytesIO()
    image.convert("RGB").save(buf, format="JPEG", quality=85)
    return base64.standard_b64encode(buf.getvalue()).decode()


def detect_ingredients(image: Image.Image) -> List[Dict[str, Any]]:
    if ANTHROPIC_API_KEY:
        try:
            return _detect_with_claude(image)
        except Exception as e:
            print(f"Claude API error: {e}")

    return _mock_detect()


def _detect_with_claude(image: Image.Image) -> List[Dict[str, Any]]:
    import anthropic

    client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)
    image_data = _image_to_base64(image)

    message = client.messages.create(
        model="claude-haiku-4-5",
        max_tokens=512,
        messages=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": "image/jpeg",
                            "data": image_data,
                        },
                    },
                    {"type": "text", "text": DETECTION_PROMPT},
                ],
            }
        ],
    )

    raw = message.content[0].text.strip()
    # Extract JSON array from response
    start = raw.find("[")
    end = raw.rfind("]") + 1
    if start == -1 or end == 0:
        return []

    items = json.loads(raw[start:end])
    results = []
    for item in items:
        name = item.get("name", "").strip()
        category = item.get("category", "기타").strip()
        confidence = float(item.get("confidence", 0.8))
        if name:
            results.append({"name": name, "category": category, "confidence": round(confidence, 3), "bbox": []})
    return results


def _mock_detect() -> List[Dict[str, Any]]:
    return [
        {"name": "당근", "category": "채소", "confidence": 0.92, "bbox": []},
        {"name": "양파", "category": "채소", "confidence": 0.88, "bbox": []},
        {"name": "계란", "category": "유제품", "confidence": 0.85, "bbox": []},
    ]
