"""
AI Hub 음식 데이터셋 전처리 스크립트.

AI Hub 데이터 구조:
  원천/  → 이미지 파일들
  라벨/  → JSON 어노테이션 파일들

실행:
  python scripts/prepare_dataset.py \
    --label_dir ~/Downloads/labels \
    --image_dir ~/Downloads/images \
    --output_dir ~/freeze_dataset \
    --categories 사과,당근,계란,복숭아,양파,감자,토마토,바나나,오렌지,브로콜리
"""

import argparse
import json
import os
import random
import shutil
from pathlib import Path

SPLIT_RATIO = 0.8  # train 80%, val 20%
MAX_PER_CLASS = 300  # 클래스당 최대 이미지 수


def load_labels(label_dir: str):
    """AI Hub JSON 라벨 파일을 파싱해서 {이미지파일명: 카테고리명} 딕셔너리 반환."""
    mapping = {}
    for json_path in Path(label_dir).rglob("*.json"):
        try:
            with open(json_path, encoding="utf-8") as f:
                data = json.load(f)

            # AI Hub 포맷 1: COCO 스타일
            if "images" in data and "annotations" in data:
                id_to_file = {img["id"]: img["file_name"] for img in data["images"]}
                id_to_cat = {cat["id"]: cat["name"] for cat in data.get("categories", [])}
                for ann in data["annotations"]:
                    fname = id_to_file.get(ann["image_id"], "")
                    cat = id_to_cat.get(ann["category_id"], "")
                    if fname and cat:
                        mapping[os.path.basename(fname)] = cat

            # AI Hub 포맷 2: 단일 이미지 JSON
            elif "category" in data and "image" in data:
                fname = os.path.basename(data["image"].get("file_name", ""))
                cat = data.get("category", {}).get("name", "")
                if fname and cat:
                    mapping[fname] = cat

        except Exception:
            continue

    return mapping


def build_dataset(label_dir, image_dir, output_dir, categories):
    """라벨 매핑을 기반으로 train/val 폴더 구조 생성."""
    print("라벨 파싱 중...")
    mapping = load_labels(label_dir)
    print(f"  총 {len(mapping)}개 이미지 라벨 로드")

    # 필터링
    cat_set = set(categories) if categories else None

    buckets: dict[str, list[Path]] = {}
    for img_path in Path(image_dir).rglob("*.jpg"):
        cat = mapping.get(img_path.name)
        if cat is None:
            continue
        if cat_set and cat not in cat_set:
            continue
        buckets.setdefault(cat, []).append(img_path)

    print(f"  선택된 카테고리: {list(buckets.keys())}")

    out = Path(output_dir)
    for split in ("train", "val"):
        (out / split).mkdir(parents=True, exist_ok=True)

    for cat, paths in buckets.items():
        random.shuffle(paths)
        paths = paths[:MAX_PER_CLASS]
        split_idx = int(len(paths) * SPLIT_RATIO)
        splits = {"train": paths[:split_idx], "val": paths[split_idx:]}

        for split, files in splits.items():
            dest_dir = out / split / cat
            dest_dir.mkdir(parents=True, exist_ok=True)
            for src in files:
                shutil.copy2(src, dest_dir / src.name)

        print(f"  {cat}: train {split_idx}장 / val {len(paths)-split_idx}장")

    print(f"\n완료: {output_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--label_dir", required=True)
    parser.add_argument("--image_dir", required=True)
    parser.add_argument("--output_dir", required=True)
    parser.add_argument("--categories", default="", help="쉼표로 구분 (비우면 전체)")
    args = parser.parse_args()

    cats = [c.strip() for c in args.categories.split(",") if c.strip()] or None
    build_dataset(args.label_dir, args.image_dir, args.output_dir, cats)
