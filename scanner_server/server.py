import base64
import io
import os
import tempfile

import numpy as np
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from paddleocr import PaddleOCR
from PIL import Image
from pydantic import BaseModel

# ─────────────────────────────────────────────────────────────────────────────
# Scanner Server
# Endpoints:
#   POST /ocr      — extract text from document/image using PaddleOCR
#   POST /analyze  — describe image using Qwen3-VL API (add API key later)
#   GET  /health   — check server is running
# ─────────────────────────────────────────────────────────────────────────────

app = FastAPI(title="Scanner Server")

# Allow Flutter app (any origin) to call this server
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load PaddleOCR once at startup (English + Malay friendly)
# use_angle_cls=True  → auto-rotate tilted text
# lang='en'           → English model (also reads Malay/numbers well)
print("Loading PaddleOCR model...")
ocr = PaddleOCR(use_textline_orientation=True, lang="en")
print("PaddleOCR ready.")

# ── Qwen VL API config (via OpenRouter) ──────────────────────────────────────
# Set your OpenRouter API key: $env:OPENROUTER_API_KEY = "sk-or-v1-..."
QWEN_API_KEY  = os.environ.get("OPENROUTER_API_KEY", "")
QWEN_MODEL    = "qwen/qwen2.5-vl-72b-instruct:free"  # Free Qwen VL on OpenRouter
QWEN_BASE_URL = "https://openrouter.ai/api/v1"


# ── Request model ─────────────────────────────────────────────────────────────
class ScanRequest(BaseModel):
    image: str          # base64-encoded image
    latitude: float = 0.0
    longitude: float = 0.0
    mode: str = "ocr"   # "ocr" | "analyze" | "both"


# ── Helper: decode base64 image → PIL Image ───────────────────────────────────
def decode_image(b64: str) -> Image.Image:
    data = base64.b64decode(b64)
    return Image.open(io.BytesIO(data)).convert("RGB")


# ── Helper: save PIL image to temp file → return path ─────────────────────────
def save_temp(img: Image.Image) -> str:
    tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
    img.save(tmp.name, "JPEG")
    return tmp.name


# ── /health ───────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {
        "status": "ok",
        "paddleocr": "ready",
        "qwen_openrouter": "configured" if QWEN_API_KEY else "no api key yet",
    }


# ── /ocr ──────────────────────────────────────────────────────────────────────
@app.post("/ocr")
def run_ocr(req: ScanRequest):
    try:
        img = decode_image(req.image)
        img_np = np.array(img)

        result = ocr.ocr(img_np, cls=True)

        # Flatten all detected text lines into a list
        lines = []
        if result and result[0]:
            for line in result[0]:
                text  = line[1][0]        # detected text
                conf  = round(line[1][1], 3)  # confidence score
                box   = line[0]           # bounding box coordinates
                lines.append({
                    "text": text,
                    "confidence": conf,
                    "box": box,
                })

        full_text = " ".join([l["text"] for l in lines])

        return {
            "success": True,
            "mode": "ocr",
            "full_text": full_text,
            "lines": lines,
            "total_lines": len(lines),
            "location": {
                "latitude": req.latitude,
                "longitude": req.longitude,
            },
        }

    except Exception as e:
        return {"success": False, "error": str(e)}


# ── /analyze ──────────────────────────────────────────────────────────────────
@app.post("/analyze")
def run_analyze(req: ScanRequest):
    if not QWEN_API_KEY:
        return {
            "success": False,
            "error": "OpenRouter API key not set. Set OPENROUTER_API_KEY environment variable.",
        }

    try:
        from openai import OpenAI

        img = decode_image(req.image)
        tmp_path = save_temp(img)

        # Re-encode as base64 for API
        with open(tmp_path, "rb") as f:
            b64 = base64.b64encode(f.read()).decode()
        os.unlink(tmp_path)

        client = OpenAI(
            api_key=QWEN_API_KEY,
            base_url=QWEN_BASE_URL,
        )

        response = client.chat.completions.create(
            model=QWEN_MODEL,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/jpeg;base64,{b64}"
                            },
                        },
                        {
                            "type": "text",
                            "text": (
                                "Analyze this image and provide:\n"
                                "1. What is shown (building, product, document, scene)\n"
                                "2. Key details visible\n"
                                "3. Any text visible\n"
                                "4. Estimated location type (indoor/outdoor, commercial/residential)\n"
                                "Be concise and structured."
                            ),
                        },
                    ],
                }
            ],
            max_tokens=512,
        )

        description = response.choices[0].message.content

        return {
            "success": True,
            "mode": "analyze",
            "description": description,
            "model": QWEN_MODEL,
            "location": {
                "latitude": req.latitude,
                "longitude": req.longitude,
            },
        }

    except Exception as e:
        return {"success": False, "error": str(e)}


# ── /scan ─────────────────────────────────────────────────────────────────────
# Runs both OCR + Analyze in one call
@app.post("/scan")
def run_scan(req: ScanRequest):
    ocr_result     = run_ocr(req)
    analyze_result = run_analyze(req) if QWEN_API_KEY else {"success": False, "error": "No API key"}

    return {
        "success": True,
        "mode": "both",
        "ocr": ocr_result,
        "analysis": analyze_result,
        "location": {
            "latitude": req.latitude,
            "longitude": req.longitude,
        },
    }


# ─────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("\n" + "="*50)
    print("Scanner Server starting...")
    print("OCR endpoint:     POST http://0.0.0.0:8080/ocr")
    print("Analyze endpoint: POST http://0.0.0.0:8080/analyze")
    print("Health check:     GET  http://0.0.0.0:8080/health")
    print("="*50 + "\n")
    uvicorn.run(app, host="0.0.0.0", port=8080)
