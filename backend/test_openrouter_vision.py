"""
Uji Qwen Vision melalui OpenRouter dengan satu gambar — tanpa perlu jalankan server/DB.

Cara guna (di Terminal):
    export OPENROUTER_API_KEY="sk-or-v1-..."
    python3 backend/test_openrouter_vision.py gambar_produk.jpg

Pilihan: export VISION_MODEL="qwen/qwen3-vl-30b-a3b-instruct" untuk cuba model lain.
"""
import base64
import os
import sys
import json
import time
import urllib.error
import urllib.request

KEY = os.environ.get("OPENROUTER_API_KEY", "")
MODEL = os.environ.get("VISION_MODEL", "qwen/qwen3-vl-8b-instruct")

if not KEY:
    sys.exit("OPENROUTER_API_KEY belum ditetapkan. Jalankan: export OPENROUTER_API_KEY=\"sk-or-v1-...\"")
if len(sys.argv) < 2:
    sys.exit("Guna: python3 backend/test_openrouter_vision.py <fail_gambar.jpg>")

path = sys.argv[1]
ext = os.path.splitext(path)[1].lower().lstrip(".") or "jpeg"
mime = "image/" + ("jpeg" if ext == "jpg" else ext)
with open(path, "rb") as f:
    image_b64 = base64.b64encode(f.read()).decode()

prompt = (
    "You are analyzing a product packaging image. Identify the BRAND, the PRODUCT "
    "name and up to 3 KEYWORDS. Reply ONLY in this exact JSON format: "
    "{\"brand\": \"...\", \"product\": \"...\", \"keywords\": [\"...\"]}"
)

print(f"Menghantar {path} ke {MODEL} ...")
t0 = time.time()
payload = {
    "model": MODEL,
    "messages": [{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{image_b64}"}},
            {"type": "text", "text": prompt},
        ],
    }],
    "max_tokens": 200,
    "temperature": 0.1,
}
req = urllib.request.Request(
    "https://openrouter.ai/api/v1/chat/completions",
    data=json.dumps(payload).encode(),
    headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"},
)
try:
    with urllib.request.urlopen(req, timeout=60) as r:
        status, data = r.status, json.load(r)
except urllib.error.HTTPError as e:
    status, data = e.code, json.loads(e.read() or b"{}")
print(f"HTTP {status} dalam {time.time() - t0:.1f}s")

if "error" in data:
    print("RALAT:", data["error"])
    sys.exit(1)

print("Jawapan model:", data["choices"][0]["message"]["content"])
usage = data.get("usage", {})
print(f"Token: input {usage.get('prompt_tokens')} / output {usage.get('completion_tokens')}")
