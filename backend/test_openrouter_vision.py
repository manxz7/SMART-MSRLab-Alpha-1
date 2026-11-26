"""
Uji Qwen Vision melalui OpenRouter — tanpa perlu jalankan server/DB.
Prompt dan cara baca jawapan SAMA seperti dalam api.py.

Cara guna (di Terminal):
    export OPENROUTER_API_KEY="sk-or-v1-..."
    python3 backend/test_openrouter_vision.py produk gambar_produk.jpg
    python3 backend/test_openrouter_vision.py logo   gambar_logo_halal.jpg
    python3 backend/test_openrouter_vision.py premis gambar_papan_tanda.jpg

Pilihan: export VISION_MODEL="qwen/qwen3-vl-30b-a3b-instruct" untuk cuba model lain.
"""
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

KEY = os.environ.get("OPENROUTER_API_KEY", "")
MODEL = os.environ.get("VISION_MODEL", "qwen/qwen3-vl-8b-instruct")

# Prompt disalin dari api.py (_extract_brand_vision, _identify_logo_vision,
# _extract_premise_name_vision). Jika prompt dalam api.py diubah, ubah di sini juga.
PROMPTS = {
    "produk": (
        "You are analyzing a product packaging image.\n\n"
        "Identify the following:\n"
        "1. BRAND: The manufacturer or brand name — usually the LARGEST or most "
        "prominent text on the packaging. Examples: 'Ricola', 'Nestle', \"Munchy's\", "
        "'F&N', 'Dutch Lady', 'Gardenia'\n"
        "2. PRODUCT: The specific product name or variant — usually smaller text "
        "below the brand. Examples: 'Original', 'Swiss Herb Candy', 'Full Cream Milk'\n"
        "3. KEYWORDS: Any other important text visible on the label that could help "
        "identify this product (ingredients, certifications, product line). "
        "Maximum 3 keywords.\n\n"
        "Reply ONLY in this exact JSON format, no explanation:\n"
        "{\"brand\": \"brand name here\", \"product\": \"product name here\", "
        "\"keywords\": [\"keyword1\", \"keyword2\"]}"
    ),
    "logo": (
        "You are analyzing a HALAL CERTIFICATION LOGO from a food product.\n\n"
        "Look carefully at ALL text and symbols in the logo and identify:\n"
        "1. ABBREVIATION: The short code/acronym of the halal certification body "
        "(e.g. 'JAKIM', 'MUIS', 'HFA', 'IFANCA', 'MUI', 'ESMA', 'BPOM')\n"
        "2. COUNTRY: The country this certification is from "
        "(e.g. 'Malaysia', 'Singapore', 'USA', 'Indonesia')\n"
        "3. ORGANIZATION_NAME: Full name of the certifying body if visible\n"
        "4. ALL_TEXT: List of ALL text you can read in the logo\n\n"
        "If you cannot identify it as a halal logo, reply with abbreviation: 'UNKNOWN'.\n\n"
        "Reply ONLY in this exact JSON format, no explanation:\n"
        "{\"abbreviation\": \"JAKIM\", \"country\": \"Malaysia\", "
        "\"organization_name\": \"Department of Islamic Development Malaysia\", "
        "\"all_text\": [\"HALAL\", \"JAKIM\", \"MALAYSIA\"]}"
    ),
    "premis": (
        "Read ALL text visible in this image.\n\n"
        "List every word or phrase you can see, one per line.\n"
        "Include any language: English, Malay, Arabic, Jawi (romanize Jawi/Arabic).\n"
        "If truly no text exists, reply exactly: NO_TEXT\n\n"
        "Reply ONLY with the text lines, nothing else."
    ),
}
MAX_TOKENS = {"produk": 200, "logo": 300, "premis": 200}

if not KEY:
    sys.exit("OPENROUTER_API_KEY belum ditetapkan. Jalankan: export OPENROUTER_API_KEY=\"sk-or-v1-...\"")
if len(sys.argv) < 3 or sys.argv[1] not in PROMPTS:
    sys.exit("Guna: python3 backend/test_openrouter_vision.py <produk|logo|premis> <fail_gambar.jpg>")

mode, path = sys.argv[1], sys.argv[2]
ext = os.path.splitext(path)[1].lower().lstrip(".") or "jpeg"
mime = "image/" + ("jpeg" if ext == "jpg" else ext)
with open(path, "rb") as f:
    image_b64 = base64.b64encode(f.read()).decode()

payload = {
    "model": MODEL,
    "messages": [{
        "role": "user",
        "content": [
            {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{image_b64}"}},
            {"type": "text", "text": PROMPTS[mode]},
        ],
    }],
    "max_tokens": MAX_TOKENS[mode],
    "temperature": 0.1,
}
req = urllib.request.Request(
    "https://openrouter.ai/api/v1/chat/completions",
    data=json.dumps(payload).encode(),
    headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json"},
)

print(f"[{mode}] Menghantar {path} ke {MODEL} ...")
t0 = time.time()
try:
    with urllib.request.urlopen(req, timeout=60) as r:
        status, data = r.status, json.load(r)
except urllib.error.HTTPError as e:
    status, data = e.code, json.loads(e.read() or b"{}")
print(f"HTTP {status} dalam {time.time() - t0:.1f}s")

if "error" in data:
    print("RALAT:", data["error"])
    sys.exit(1)

content = data["choices"][0]["message"]["content"]
content = re.sub(r"<think>.*?</think>", "", content, flags=re.DOTALL).strip()
print("Jawapan mentah model:", repr(content))

# Baca jawapan dengan cara yang SAMA seperti api.py
if mode in ("produk", "logo"):
    match = re.search(r"\{.*?\}", content, re.DOTALL)
    try:
        parsed = json.loads(match.group()) if match else None
    except json.JSONDecodeError:
        parsed = None
    if parsed:
        print("✅ api.py boleh baca jawapan ini:", parsed)
    else:
        print("❌ api.py TIDAK dapat baca jawapan ini — server akan guna OCR sandaran")
else:
    lines = [l.strip() for l in content.splitlines() if len(l.strip()) >= 2]
    print("✅ Baris teks yang akan dicari dalam JAKIM:", lines)

usage = data.get("usage", {})
print(f"Token: input {usage.get('prompt_tokens')} / output {usage.get('completion_tokens')}")
