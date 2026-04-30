from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import uvicorn
import io
import os

from PIL import Image
from detection import detect_ingredients
from database import init_db

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield

app = FastAPI(
    title="Freeze AI Server",
    description="스마트 냉장고 식재료 인식 API",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health_check():
    return {"status": "ok", "message": "Freeze AI Server is running"}


@app.post("/detect")
async def detect(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드 가능합니다")

    contents = await file.read()
    image = Image.open(io.BytesIO(contents)).convert("RGB")

    detections = detect_ingredients(image)

    return {
        "filename": file.filename,
        "detections": detections,
        "count": len(detections),
    }


@app.get("/ingredients/common")
async def get_common_ingredients():
    return {
        "ingredients": [
            {"name": "당근", "category": "채소", "unit": "개"},
            {"name": "양파", "category": "채소", "unit": "개"},
            {"name": "마늘", "category": "채소", "unit": "개"},
            {"name": "감자", "category": "채소", "unit": "개"},
            {"name": "고추", "category": "채소", "unit": "개"},
            {"name": "계란", "category": "유제품", "unit": "개"},
            {"name": "우유", "category": "유제품", "unit": "ml"},
            {"name": "돼지고기", "category": "육류", "unit": "g"},
            {"name": "닭고기", "category": "육류", "unit": "g"},
            {"name": "소고기", "category": "육류", "unit": "g"},
            {"name": "두부", "category": "기타", "unit": "모"},
            {"name": "사과", "category": "과일", "unit": "개"},
            {"name": "배", "category": "과일", "unit": "개"},
            {"name": "토마토", "category": "채소", "unit": "개"},
            {"name": "오이", "category": "채소", "unit": "개"},
        ]
    }


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
