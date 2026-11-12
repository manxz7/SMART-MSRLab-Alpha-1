"""
build_embeddings.py — Generate EfficientNet-B0 fingerprints for all product images

HOW IT WORKS:
  1. Load EfficientNet-B0 pretrained on ImageNet (no training needed)
  2. For each product image in lotus_images/, pass it through the model
  3. Extract the 1280-number vector (fingerprint) from the second-to-last layer
  4. Save all fingerprints + product names to embeddings.npz

This only needs to run ONCE. The result is used by the API for every scan.
Runtime: ~3-5 minutes for 4562 images on CPU.

Usage:
    python build_embeddings.py
"""

import os
import csv
import numpy as np
from PIL import Image

print("Loading TensorFlow (first time may be slow)...")
import tensorflow as tf
from tensorflow.keras.applications import EfficientNetB0
from tensorflow.keras.applications.efficientnet import preprocess_input

# ── Config ────────────────────────────────────────────────────────────────────
IMAGES_DIR   = "lotus_images"
CSV_FILE     = "lotus_products.csv"
OUTPUT_FILE  = "embeddings.npz"   # Where we save the fingerprints
IMG_SIZE     = (224, 224)         # EfficientNet-B0 input size
BATCH_SIZE   = 32                 # Process this many images at once

# ── Load EfficientNet-B0 ─────────────────────────────────────────────────────
# include_top=False removes the final classification layer
# We want the raw 1280-feature vector, not a category prediction
print("Loading EfficientNet-B0 pretrained weights...")
base_model = EfficientNetB0(
    weights="imagenet",        # Use weights trained on ImageNet (1.2M images)
    include_top=False,         # Remove final classification layer
    pooling="avg",             # Global average pooling → 1280 numbers per image
    input_shape=(224, 224, 3)
)
base_model.trainable = False   # Freeze weights — we're not training, just extracting
print(f"  Model loaded: {base_model.output_shape} output shape")


def load_and_preprocess(filepath):
    """Load an image file and prepare it for EfficientNet-B0"""
    try:
        img = Image.open(filepath).convert("RGB")
        img = img.resize(IMG_SIZE, Image.LANCZOS)
        arr = np.array(img, dtype=np.float32)
        arr = preprocess_input(arr)   # Normalize pixels the way EfficientNet expects
        return arr
    except Exception:
        return None


# ── Read product list from CSV ────────────────────────────────────────────────
print(f"\nReading product list from {CSV_FILE}...")
products = []
with open(CSV_FILE, "r", encoding="utf-8") as f:
    for row in csv.DictReader(f):
        filepath = os.path.join(IMAGES_DIR, row["filename"])
        if os.path.exists(filepath):
            products.append({
                "name":     row["name"],
                "category": row["category"],
                "filename": row["filename"],
                "filepath": filepath,
            })

print(f"  {len(products)} product images found")

# ── Generate embeddings in batches ───────────────────────────────────────────
print(f"\nGenerating fingerprints (batch size={BATCH_SIZE})...")
print("  This takes ~3-5 minutes on CPU — please wait...\n")

all_embeddings = []
all_names      = []
all_categories = []
all_filenames  = []

failed = 0
for batch_start in range(0, len(products), BATCH_SIZE):
    batch = products[batch_start : batch_start + BATCH_SIZE]

    # Load images in this batch
    batch_arrays = []
    valid_products = []
    for p in batch:
        arr = load_and_preprocess(p["filepath"])
        if arr is not None:
            batch_arrays.append(arr)
            valid_products.append(p)
        else:
            failed += 1

    if not batch_arrays:
        continue

    # Stack into a single numpy array: shape (batch_size, 224, 224, 3)
    batch_tensor = np.stack(batch_arrays)

    # Run through EfficientNet → shape (batch_size, 1280)
    embeddings = base_model.predict(batch_tensor, verbose=0)

    # Normalize to unit length (makes cosine similarity = dot product, faster search)
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    norms = np.where(norms == 0, 1, norms)   # avoid divide-by-zero
    embeddings = embeddings / norms

    for i, p in enumerate(valid_products):
        all_embeddings.append(embeddings[i])
        all_names.append(p["name"])
        all_categories.append(p["category"])
        all_filenames.append(p["filename"])

    processed = batch_start + len(batch)
    pct = processed / len(products) * 100
    print(f"  [{processed:4d}/{len(products)}] {pct:.0f}% done", end="\r")

print(f"\n  Done — {len(all_embeddings)} embeddings generated, {failed} images failed")

# ── Save to file ──────────────────────────────────────────────────────────────
print(f"\nSaving to {OUTPUT_FILE}...")
np.savez_compressed(
    OUTPUT_FILE,
    embeddings = np.array(all_embeddings, dtype=np.float32),
    names      = np.array(all_names),
    categories = np.array(all_categories),
    filenames  = np.array(all_filenames),
)

size_mb = os.path.getsize(OUTPUT_FILE) / (1024 * 1024)
print(f"  Saved: {OUTPUT_FILE} ({size_mb:.1f} MB)")
print(f"\n✓ DONE — {len(all_embeddings)} product fingerprints ready for similarity search")
print(f"  Run the API (uvicorn api:app --reload) and use /scan-image to search")
