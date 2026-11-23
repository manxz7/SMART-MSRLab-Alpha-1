# ============================================================
# api.py — Halal Product Checker API
# ============================================================
# This file is a web server (API) that:
#   1. Connects to our PostgreSQL database (halal_jakim)
#   2. Listens for requests from apps/browsers
#   3. Searches the database and returns results
#
# To run this API:
#   uvicorn api:app --reload
#
# Then open browser at:
#   http://localhost:8000/docs   ← interactive test page
# ============================================================

from fastapi import FastAPI, HTTPException, Query, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from urllib.parse import urlparse
import psycopg2
import psycopg2.extras
import psycopg2.pool
import os
import io
import json
import base64
import re
import csv
import requests as http_requests
from PIL import Image

# ─────────────────────────────────────────────
# QWEN VISION — LM Studio config
# Model: qwen/qwen2.5-vl-7b (vision model)
# Guna untuk baca nama brand/produk dari gambar
# ─────────────────────────────────────────────
LM_STUDIO_URL = "https://lvq934y3nbv8so-5000.proxy.runpod.net/"
LM_STUDIO_KEY = ""
VISION_MODEL  = "qwen/qwen3-vl-8b"


def _vision_chat_completions_url() -> str:
    """Build the vision endpoint without introducing redirect-prone double slashes."""
    return f"{LM_STUDIO_URL.rstrip('/')}/v1/chat/completions"

# ─────────────────────────────────────────────
# DATABASE CONFIGURATION
# These are the settings to connect to PostgreSQL
# ─────────────────────────────────────────────
DEFAULT_DB_HOST = "159.65.136.40"
DEFAULT_DB_PORT = 5434
DEFAULT_DB_NAME = "halal_jakim"
DEFAULT_DB_USER = "postgres"
DEFAULT_DB_PASSWORD = "Huffazpro1"


def _build_db_config() -> dict:
    database_url = (
        os.environ.get("DATABASE_URL", "")
        or os.environ.get("POSTGRES_URL", "")
        or os.environ.get("POSTGRESQL_URL", "")
    ).strip()

    if database_url:
        parsed_db_url = urlparse(database_url)
        return {
            "host": parsed_db_url.hostname or DEFAULT_DB_HOST,
            "port": parsed_db_url.port or DEFAULT_DB_PORT,
            "dbname": (parsed_db_url.path or f"/{DEFAULT_DB_NAME}").lstrip("/"),
            "user": parsed_db_url.username or DEFAULT_DB_USER,
            "password": parsed_db_url.password or DEFAULT_DB_PASSWORD,
        }

    return {
        "host":     os.environ.get("DB_HOST", DEFAULT_DB_HOST),
        "port":     int(os.environ.get("DB_PORT", DEFAULT_DB_PORT)),
        "dbname":   os.environ.get("DB_NAME", DEFAULT_DB_NAME),
        "user":     os.environ.get("DB_USER", DEFAULT_DB_USER),
        "password": os.environ.get("DB_PASSWORD", DEFAULT_DB_PASSWORD),
    }


DB_CONFIG = _build_db_config()

# ─────────────────────────────────────────────
# CONNECTION POOL
# Buka 5 connection ke PostgreSQL sekali masa start.
# Setiap request ambil connection dari pool (pantas)
# dan pulangkan balik lepas selesai — tak perlu
# buka/tutup connection baru setiap kali (lambat).
# ─────────────────────────────────────────────
_db_pool = psycopg2.pool.ThreadedConnectionPool(
    minconn=2,
    maxconn=10,
    **DB_CONFIG,
)


def get_db_connection():
    """Ambil connection dari pool. Lagi pantas dari buka connection baru."""
    return _db_pool.getconn()


def release_db_connection(conn):
    """Pulangkan connection ke pool selepas query selesai."""
    _db_pool.putconn(conn)


# ─────────────────────────────────────────────
# JAKIM CERTIFIED BODIES — loaded from jakim_cb.csv at startup
# Senarai 94 badan pensijilan halal dari 48 negara yang diiktiraf JAKIM
# ─────────────────────────────────────────────
_certified_bodies: list[dict] = []


def _load_certified_bodies():
    """Load jakim_cb.csv into memory once at startup."""
    global _certified_bodies
    csv_path = os.path.join(os.path.dirname(__file__), "jakim_cb.csv")
    if not os.path.exists(csv_path):
        print("[CB] WARNING: jakim_cb.csv not found")
        return
    with open(csv_path, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        _certified_bodies = [row for row in reader]
    print(f"[CB] Loaded {len(_certified_bodies)} certified bodies")


# ─────────────────────────────────────────────
# OCR — PaddleOCR for text extraction from images
# ─────────────────────────────────────────────
_ocr = None   # PaddleOCR instance


def _load_ocr_model():
    """Load PaddleOCR model into memory (called once at startup)."""
    global _ocr
    try:
        # Env vars MESTI set sebelum paddle diimport — fix PIR executor bug
        os.environ.setdefault('FLAGS_enable_pir_in_executor', '0')
        os.environ.setdefault('FLAGS_pir_apply_shape_optimization_pass', '0')
        from paddleocr import PaddleOCR
        print("[OCR] Loading PaddleOCR (en)...")
        _ocr = PaddleOCR(use_textline_orientation=False, lang='en', show_log=False)
        print("[OCR] PaddleOCR ready")
    except Exception as e:
        print(f"[OCR] WARNING: PaddleOCR not available — {e}")
        _ocr = None


def _normalize_text(text: str) -> str:
    """
    OPTION A — Normalization: Bersihkan teks sebelum dibuat carian.

    Masalah yang diselesaikan:
      - "MUNCHY'S" tak match "MUNCHYS" sebab apostrophe beza
      - "NESTLÉ" tak match "NESTLE" sebab huruf accent berbeza
      - "F&N" tak match "FN" sebab & dibuang

    Cara kerja:
      1. NFD decomposition — pisahkan huruf accent dari accent dia
         Contoh: "é" → "e" + combining_accent_char
      2. Buang semua combining accent characters (kategori 'Mn')
         Contoh: "e" + combining_accent → "e" sahaja → "NESTLÉ" jadi "NESTLE"
      3. Buang semua special chars (apostrophe, &, -, etc.)
         Contoh: "MUNCHY'S" → "MUNCHYS", "F&N" → "FN"
      4. Compress multiple spaces jadi satu

    Return: teks yang sudah dibersihkan, huruf besar (uppercase dikekalkan)
    """
    import unicodedata
    # Langkah 1: NFD = pisah accent dari huruf asas
    # Contoh: "é" dalam NFD jadi "e" + U+0301 (combining acute accent)
    text = unicodedata.normalize('NFD', text)
    # Langkah 2: Buang semua "Mark, Nonspacing" chars (accent, diacritic, etc.)
    # Kategori 'Mn' = combining chars yang buat accent
    text = ''.join(c for c in text if unicodedata.category(c) != 'Mn')
    # Langkah 3: Buang semua char yang bukan huruf, angka, atau space
    text = re.sub(r"[^a-zA-Z0-9\s]", '', text)
    # Langkah 4: Buang extra spaces
    return ' '.join(text.split()).strip()


def _extract_brand_vision(image_bytes: bytes) -> dict:
    """
    OPTION C — Hantar gambar ke Qwen Vision LLM untuk kenal pasti
    nama brand dan produk dari packaging.

    Prompt yang diperbaiki:
      - Terang beza antara BRAND (nama pengeluar) vs PRODUCT (nama produk/varian)
      - Contoh diberikan supaya model faham konteks
      - Minta keywords tambahan untuk bantu carian database

    Return: {"brand": "Ricola", "product": "Original", "keywords": ["Swiss Alpine", "Herb Candy"]}
    atau {} kalau gagal.
    """
    try:
        # Tukar gambar ke base64 — format yang API faham
        # Base64 = cara encode binary data (gambar) jadi text string
        image_b64 = base64.b64encode(image_bytes).decode('utf-8')

        # Detect image format untuk set content type yang betul (jpeg/png/webp)
        img = Image.open(io.BytesIO(image_bytes))
        fmt = img.format.lower() if img.format else "jpeg"
        mime = f"image/{fmt}"

        print(f"[Vision] Sending image to {VISION_MODEL}...")

        # ── OPTION C: Prompt yang lebih detail dan spesifik ──────────────────
        # Prompt lama: terlalu ringkas, model kadang confuse brand dengan product
        # Prompt baru:
        #   - Terang definisi BRAND vs PRODUCT dengan contoh
        #   - Minta keywords tambahan (nama lain yang nampak pada label)
        #   - Contoh output diberikan supaya model ikut format dengan betul
        improved_prompt = (
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
        )

        response = http_requests.post(
            _vision_chat_completions_url(),
            headers={
                "Authorization": f"Bearer {LM_STUDIO_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": VISION_MODEL,
                "messages": [
                    {
                        "role": "user",
                        "content": [
                            # Bahagian 1: gambar dalam format base64
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:{mime};base64,{image_b64}"}
                            },
                            # Bahagian 2: soalan/prompt untuk model
                            {
                                "type": "text",
                                "text": improved_prompt
                            }
                        ]
                    }
                ],
                "max_tokens": 200,   # tambah sikit sebab ada keywords sekarang
                "temperature": 0.1,  # rendah = lebih consistent, kurang "creative"
            },
            timeout=30,
        )

        resp_json = response.json()
        print(f"[Vision] Full response: {resp_json}")
        if "error" in resp_json:
            print(f"[Vision] API error: {resp_json['error']}")
            return {}

        content = resp_json["choices"][0]["message"]["content"]
        # Strip <think>...</think> — Qwen3 adalah thinking model, output reasoning dulu
        content = re.sub(r'<think>.*?</think>', '', content, flags=re.DOTALL).strip()
        print(f"[Vision] Response (stripped): {content!r}")

        match = re.search(r'\{.*?\}', content, re.DOTALL)
        if match:
            result = json.loads(match.group())
            print(f"[Vision] Extracted: {result}")
            return result

    except Exception as e:
        print(f"[Vision] Error: {e}")

    return {}


def _extract_text_ocr(image_bytes: bytes) -> list:
    """Extract text strings from image using PaddleOCR, sorted top-to-bottom."""
    if _ocr is None:
        print("[OCR] _ocr is None — model not loaded!")
        return []
    try:
        import numpy as np
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        # Resize kalau terlalu kecil
        w, h = img.size
        if max(w, h) < 1000:
            scale = 1000 / max(w, h)
            img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
        img_array = np.array(img)

        # PaddleOCR: returns [[[bbox, (text, confidence)], ...]]
        results = _ocr.ocr(img_array)
        if not results or not results[0]:
            print("[OCR] PaddleOCR: no text detected")
            return []

        print(f"[OCR] PaddleOCR raw: {len(results[0])} items")

        # Sort top-to-bottom by Y coordinate of bbox
        sorted_lines = sorted(results[0], key=lambda r: r[0][0][1])

        texts = []
        for line in sorted_lines:
            bbox, (text, confidence) = line
            print(f"  [{confidence:.2f}] {text!r}")
            if confidence > 0.3 and len(text.strip()) > 1:
                texts.append(text.strip())

        print(f"[OCR] Extracted {len(texts)} texts: {texts}")
        return texts
    except Exception as e:
        print(f"[OCR] Extraction error: {e}")
        return []


def _identify_logo_vision(image_bytes: bytes) -> dict:
    """
    Hantar gambar logo halal ke Qwen Vision untuk kenal pasti
    nama badan pensijilan dan negara asal.

    Return: {
        "abbreviation": "JAKIM",
        "country": "Malaysia",
        "organization_name": "Department of Islamic Development Malaysia",
        "all_text": ["HALAL", "JAKIM", "MALAYSIA"]
    }
    atau {} kalau gagal.
    """
    try:
        image_b64 = base64.b64encode(image_bytes).decode('utf-8')
        img = Image.open(io.BytesIO(image_bytes))
        fmt = img.format.lower() if img.format else "jpeg"
        mime = f"image/{fmt}"

        print(f"[Logo] Sending logo image to {VISION_MODEL}...")

        logo_prompt = (
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
        )

        response = http_requests.post(
            _vision_chat_completions_url(),
            headers={
                "Authorization": f"Bearer {LM_STUDIO_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": VISION_MODEL,
                "messages": [{
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{image_b64}"}},
                        {"type": "text", "text": logo_prompt}
                    ]
                }],
                "max_tokens": 300,
                "temperature": 0.1,
            },
            timeout=30,
        )

        resp_json = response.json()
        print(f"[Logo] Vision response: {resp_json}")
        if "error" in resp_json:
            print(f"[Logo] API error: {resp_json['error']}")
            return {}

        content = resp_json["choices"][0]["message"]["content"]
        content = re.sub(r'<think>.*?</think>', '', content, flags=re.DOTALL).strip()
        print(f"[Logo] Content (stripped): {content!r}")

        match = re.search(r'\{.*?\}', content, re.DOTALL)
        if match:
            result = json.loads(match.group())
            print(f"[Logo] Parsed: {result}")
            return result

    except Exception as e:
        print(f"[Logo] Vision error: {e}")

    return {}


def _search_certified_body(vision_result: dict) -> list[dict]:
    """
    Cari badan pensijilan dalam senarai _certified_bodies berdasarkan
    hasil Qwen Vision (abbreviation, country, all_text).

    Tiga peringkat:
      1. Exact match abbreviation (case-insensitive)
      2. Partial match organization name dengan mana-mana all_text token
      3. Country + any text token partial match
    """
    if not _certified_bodies:
        return []

    abbreviation = (vision_result.get("abbreviation") or "").strip().upper()
    country_hint = (vision_result.get("country") or "").strip().lower()
    org_hint = (vision_result.get("organization_name") or "").strip().lower()
    all_text = [t.strip().upper() for t in (vision_result.get("all_text") or []) if len(t.strip()) >= 2]

    found = []
    seen_ids = set()

    def add(body):
        bid = body.get("id", "")
        if bid not in seen_ids:
            seen_ids.add(bid)
            found.append(body)

    # Stage 1: exact abbreviation match
    if abbreviation and abbreviation != "UNKNOWN":
        for body in _certified_bodies:
            abbr = (body.get("abbreviation") or "").strip().upper()
            if abbr == abbreviation:
                add(body)

    # Stage 2: partial match abbreviation in organization_name, or org_hint match
    if len(found) == 0 and (abbreviation or org_hint):
        for body in _certified_bodies:
            body_name = (body.get("organization_name") or "").upper()
            body_abbr = (body.get("abbreviation") or "").upper()
            if abbreviation and abbreviation != "UNKNOWN":
                if abbreviation in body_name or abbreviation in body_abbr:
                    add(body)
            if org_hint:
                if org_hint[:6] in body_name.lower():
                    add(body)

    # Stage 3: match any all_text token against organization_name or abbreviation
    if len(found) == 0 and all_text:
        SKIP = {"HALAL", "CERTIFIED", "LOGO", "THE", "INC", "LTD", "SDN", "BHD",
                "AND", "FOR", "OF", "BY", "PTY", "LLC", "CO", "HALALAN", "TOYYIBAN"}
        tokens = [t for t in all_text if len(t) >= 3 and t not in SKIP]
        for body in _certified_bodies:
            body_name = (body.get("organization_name") or "").upper()
            body_abbr = (body.get("abbreviation") or "").upper()
            body_country = (body.get("country") or "").upper()
            score = 0
            for token in tokens:
                if token in body_name or token in body_abbr or token in body_country:
                    score += 1
            if score >= 1:
                add(body)

    # Country filter — kalau ada country hint, prioritise yang match
    if country_hint and len(found) > 1:
        matched_country = [b for b in found if country_hint in (b.get("country") or "").lower()]
        if matched_country:
            found = matched_country

    return found[:5]


def _search_jakim_by_texts(texts: list, category: str = None) -> list:
    """
    Brand-first search pipeline dengan 4 peringkat:

      STAGE 1 — Detect brand (exact + normalized ILIKE)
      STAGE 2a — Filter by product terms dalam brand yang dah jumpa
      STAGE 2b — Return semua rekod brand kalau product filter kosong
      STAGE 3 — Fallback: cari company + brand (exact + normalized)
      STAGE 4 — OPTION B: pg_trgm fuzzy match untuk catch near-matches
                 (guna similarity() dari PostgreSQL extension pg_trgm)

    OPTION A diterapkan dalam Stage 1 & 3:
      Setiap search term dicari DUA kali:
        1. Cara biasa:    brand_ner ILIKE '%Munchys%'
        2. Cara normalize: REGEXP_REPLACE(lower(brand_ner),'[^a-z0-9 ]','') LIKE '%munchys%'
      Ini handle kes MUNCHY'S ↔ Munchys, F&N ↔ FN, etc.
    """
    if not texts:
        return []

    # ── Stopword list — perkataan biasa yang BUKAN nama brand ────────────────
    BRAND_STOPWORDS = {
        'assorted', 'jumbo', 'easy', 'pack', 'packs', 'new', 'look',
        'the', 'for', 'and', 'original', 'premium', 'fresh', 'natural',
        'special', 'extra', 'super', 'mega', 'mini', 'classic', 'royal',
        'gold', 'silver', 'lite', 'light', 'hot', 'cool', 'sweet', 'sour',
        'crispy', 'crunchy', 'creamy', 'mix', 'mixed', 'variety', 'biscuits',
        'biscuit', 'crackers', 'cracker', 'cookies', 'cookie', 'chips', 'snack',
        'drink', 'drinks', 'water', 'juice', 'milk', 'tea', 'coffee', 'food',
        'halal', 'certified', 'malaysia', 'product', 'products', 'brand',
        # retailer names — muncul dalam nama produk sebagai saluran jualan
        'speedmart', 'tesco', 'aeon', 'giant', 'mydin', 'lotus', 'econsave',
        'jaya', 'pasaraya', 'supermarket', 'hypermarket', 'store', 'stores',
        'mart', 'market', 'shopping', 'mall',
    }

    search_terms = []
    norm_terms   = []
    seen = set()

    for text in texts[:15]:
        text = text.strip()
        if len(text) < 3:
            continue
        if text.lower() not in seen:
            seen.add(text.lower())
            search_terms.append(text)
            norm_terms.append(_normalize_text(text))
        if " " in text:
            for word in text.split():
                word = word.strip()
                if len(word) >= 5 and not re.fullmatch(r'[\d\.\,\-\(\)]+', word):
                    if word.lower() not in seen:
                        seen.add(word.lower())
                        search_terms.append(word)
                        norm_terms.append(_normalize_text(word))

    if not search_terms:
        return []

    NORM_COL = "REGEXP_REPLACE(LOWER({col}), '[^a-z0-9 ]', '', 'g')"

    try:
        conn = get_db_connection()
        cur  = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        results  = []
        seen_ids = set()

        detected_brands     = []
        brand_trigger_terms = set()

        for term, norm in zip(search_terms[:12], norm_terms[:12]):
            if term.lower() in BRAND_STOPWORDS:
                continue

            norm_lower = norm.lower()
            cur.execute(
                f"""
                SELECT DISTINCT brand_ner FROM halal_products
                WHERE (
                    brand_ner ILIKE %s
                    OR {NORM_COL.format(col='brand_ner')} LIKE %s
                )
                AND brand_ner IS NOT NULL
                AND brand_ner <> ''
                AND brand_ner NOT LIKE '%%,%%'
                AND LENGTH(brand_ner) < 80
                LIMIT 3
                """,
                (f"%{term}%", f"%{norm_lower}%")
            )
            rows = cur.fetchall()
            if rows:
                brand_trigger_terms.add(term.lower())
                for r in rows:
                    if r["brand_ner"] not in detected_brands:
                        detected_brands.append(r["brand_ner"])

        print(f"[Search] Detected brands: {detected_brands}")

        if detected_brands:
            product_terms = [t for t in search_terms if t.lower() not in brand_trigger_terms]
            print(f"[Search] Product terms after brand strip: {product_terms}")

            if product_terms:
                for brand in detected_brands[:3]:
                    for term in product_terms[:6]:
                        cur.execute(
                            """
                            SELECT id, category, comp_code,
                                   company_ner AS company, brand_ner AS brand,
                                   product_ner AS product_name, address_ner AS address,
                                   expiry, ner_confidence
                            FROM halal_products
                            WHERE brand_ner ILIKE %s
                              AND (company_ner ILIKE %s OR product_ner ILIKE %s)
                            ORDER BY company_ner LIMIT 10
                            """,
                            (f"%{brand}%", f"%{term}%", f"%{term}%")
                        )
                        for row in cur.fetchall():
                            if row["id"] not in seen_ids:
                                seen_ids.add(row["id"])
                                results.append(dict(row))

            if not results:
                for brand in detected_brands[:3]:
                    cur.execute(
                        """
                        SELECT id, category, comp_code,
                               company_ner AS company, brand_ner AS brand,
                               product_ner AS product_name, address_ner AS address,
                               expiry, ner_confidence
                        FROM halal_products
                        WHERE brand_ner ILIKE %s
                        ORDER BY company_ner LIMIT 15
                        """,
                        (f"%{brand}%",)
                    )
                    for row in cur.fetchall():
                        if row["id"] not in seen_ids:
                            seen_ids.add(row["id"])
                            results.append(dict(row))

        if not results:
            print("[Search] No brand detected — fallback to company+brand search")
            for term, norm in zip(search_terms[:12], norm_terms[:12]):
                norm_lower = norm.lower()
                cur.execute(
                    f"""
                    SELECT id, category, comp_code,
                           company_ner AS company, brand_ner AS brand,
                           product_ner AS product_name, address_ner AS address,
                           expiry, ner_confidence
                    FROM halal_products
                    WHERE company_ner ILIKE %s OR brand_ner ILIKE %s
                       OR {NORM_COL.format(col='company_ner')} LIKE %s
                       OR {NORM_COL.format(col='brand_ner')} LIKE %s
                    ORDER BY company_ner LIMIT 5
                    """,
                    (f"%{term}%", f"%{term}%", f"%{norm_lower}%", f"%{norm_lower}%")
                )
                for row in cur.fetchall():
                    if row["id"] not in seen_ids:
                        seen_ids.add(row["id"])
                        results.append(dict(row))

        if not results:
            print("[Search] Trying fuzzy (pg_trgm) match...")
            for term in search_terms[:5]:
                if len(term) < 4:
                    continue
                try:
                    cur.execute(
                        """
                        SELECT id, category, comp_code,
                               company_ner AS company, brand_ner AS brand,
                               product_ner AS product_name, address_ner AS address,
                               expiry, ner_confidence,
                               GREATEST(
                                   similarity(COALESCE(brand_ner, ''), %s),
                                   similarity(COALESCE(company_ner, ''), %s)
                               ) AS sim_score
                        FROM halal_products
                        WHERE similarity(COALESCE(brand_ner, ''), %s) > 0.35
                           OR similarity(COALESCE(company_ner, ''), %s) > 0.35
                        ORDER BY sim_score DESC
                        LIMIT 5
                        """,
                        (term, term, term, term)
                    )
                    fuzzy_rows = cur.fetchall()
                    print(f"[Search] Fuzzy '{term}': {len(fuzzy_rows)} results")
                    for row in fuzzy_rows:
                        if row["id"] not in seen_ids:
                            seen_ids.add(row["id"])
                            row_dict = dict(row)
                            row_dict.pop("sim_score", None)
                            results.append(row_dict)
                except Exception as fuzzy_err:
                    print(f"[Search] Fuzzy error (pg_trgm mungkin tak install): {fuzzy_err}")
                    break

        cur.close()
        release_db_connection(conn)

        def relevance(record):
            combined = (
                f"{record.get('company','') or ''} "
                f"{record.get('brand','') or ''} "
                f"{record.get('product_name','') or ''}"
            ).lower()
            exact_score = sum(1 for t in search_terms if t.lower() in combined)
            norm_score  = sum(0.5 for n in norm_terms if n.lower() in _normalize_text(combined).lower())
            return exact_score + norm_score

        results.sort(key=relevance, reverse=True)
        return results[:20]

    except Exception as e:
        print(f"[Search] Error: {e}")
        return []


def _extract_premise_name_vision(image_bytes: bytes) -> list[str]:
    """
    Hantar gambar papan tanda kedai/restoran ke Qwen Vision.
    Prompt fokus pada nama premis, bukan nama produk.
    Return: list teks yang dikesan (nama kedai, dll)
    """
    try:
        image_b64 = base64.b64encode(image_bytes).decode('utf-8')
        img = Image.open(io.BytesIO(image_bytes))
        fmt = img.format.lower() if img.format else "jpeg"
        mime = f"image/{fmt}"

        premise_prompt = (
            "Read ALL text visible in this image.\n\n"
            "List every word or phrase you can see, one per line.\n"
            "Include any language: English, Malay, Arabic, Jawi (romanize Jawi/Arabic).\n"
            "If truly no text exists, reply exactly: NO_TEXT\n\n"
            "Reply ONLY with the text lines, nothing else."
        )

        response = http_requests.post(
            _vision_chat_completions_url(),
            headers={
                "Authorization": f"Bearer {LM_STUDIO_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": VISION_MODEL,
                "messages": [{
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{image_b64}"}},
                        {"type": "text", "text": premise_prompt}
                    ]
                }],
                "max_tokens": 200,
                "temperature": 0.1,
            },
            timeout=30,
        )

        content = response.json()["choices"][0]["message"]["content"]
        # Strip <think>...</think> tags if present
        content = re.sub(r'<think>.*?</think>', '', content, flags=re.DOTALL).strip()
        lines = [l.strip() for l in content.splitlines() if len(l.strip()) >= 2]

        # Filter out responses yang bermaksud "tiada teks" — jangan search string ni
        NO_TEXT_SIGNALS = [
            'no_text', 'no text', 'no visible', 'cannot read', 'unable to', 'there is no',
            'i cannot', 'no clear', 'no readable', 'not visible', 'no writing',
            'cannot detect', 'no signage', 'not able', 'no words', 'no readable text'
        ]
        combined = ' '.join(lines).lower()
        if any(sig in combined for sig in NO_TEXT_SIGNALS):
            print(f"[Premise Vision] Model reported no text: {lines}")
            return []

        print(f"[Premise Vision] Extracted: {lines}")
        return lines

    except Exception as e:
        print(f"[Premise Vision] Error: {e}")
        return []


def _search_premises_by_texts(texts: list) -> list:
    """
    Cari premis makanan halal berdasarkan nama yang dikesan dari gambar.
    Filter kategori: Premis Makanan, Hotel & Resort, Rumah Sembelih.
    Cari dalam company_ner (nama premis) menggunakan ILIKE dan fuzzy match.
    """
    if not texts or not any(len(t.strip()) >= 2 for t in texts):
        return []

    PREMISE_CATEGORIES = ('Premis Makanan', 'Hotel & Resort', 'Rumah Sembelih')
    SKIP = {'restoran', 'kedai', 'makan', 'cafe', 'restaurant', 'food',
            'dan', 'and', 'the', 'sdn', 'bhd', 'enterprise', 'ent'}

    search_terms = []
    seen = set()
    for text in texts[:15]:
        text = text.strip()
        if len(text) < 2:
            continue
        if text.lower() not in seen:
            seen.add(text.lower())
            search_terms.append(text)
        for word in text.split():
            word = word.strip()
            if len(word) >= 3 and word.lower() not in SKIP and word.lower() not in seen:
                seen.add(word.lower())
                search_terms.append(word)

    if not search_terms:
        return []

    try:
        conn = get_db_connection()
        cur  = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        results  = []
        seen_ids = set()

        cat_placeholders = ','.join(['%s'] * len(PREMISE_CATEGORIES))

        for term in search_terms[:10]:
            cur.execute(
                f"""
                SELECT id, category, comp_code,
                       company_ner AS company, brand_ner AS brand,
                       product_ner AS product_name, address_ner AS address,
                       expiry, ner_confidence
                FROM halal_products
                WHERE category IN ({cat_placeholders})
                  AND (company_ner ILIKE %s OR brand_ner ILIKE %s)
                ORDER BY company_ner
                LIMIT 8
                """,
                (*PREMISE_CATEGORIES, f"%{term}%", f"%{term}%")
            )
            for row in cur.fetchall():
                if row["id"] not in seen_ids:
                    seen_ids.add(row["id"])
                    results.append(dict(row))

        # Fuzzy fallback kalau exact match tak jumpa
        if not results:
            for term in search_terms[:5]:
                if len(term) < 4:
                    continue
                try:
                    cur.execute(
                        f"""
                        SELECT id, category, comp_code,
                               company_ner AS company, brand_ner AS brand,
                               product_ner AS product_name, address_ner AS address,
                               expiry, ner_confidence,
                               similarity(COALESCE(company_ner,''), %s) AS sim
                        FROM halal_products
                        WHERE category IN ({cat_placeholders})
                          AND similarity(COALESCE(company_ner,''), %s) > 0.3
                        ORDER BY sim DESC
                        LIMIT 5
                        """,
                        (term, *PREMISE_CATEGORIES, term)
                    )
                    for row in cur.fetchall():
                        if row["id"] not in seen_ids:
                            seen_ids.add(row["id"])
                            r = dict(row)
                            r.pop("sim", None)
                            results.append(r)
                except Exception:
                    break

        cur.close()
        release_db_connection(conn)
        return results[:10]

    except Exception as e:
        print(f"[Premise Search] Error: {e}")
        return []


# ─────────────────────────────────────────────
# CREATE THE FASTAPI APP
# This is the main "app" object — everything plugs into this
# ─────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load AI models when API starts, release on shutdown."""
    _load_certified_bodies()
    _load_ocr_model()
    yield


app = FastAPI(
    title="Halal Product Checker API",
    description="Search JAKIM halal-certified products, companies, and brands from Portal Halal Malaysia.",
    version="1.0.0",
    lifespan=lifespan,
)

# ─────────────────────────────────────────────
# CORS MIDDLEWARE
# ─────────────────────────────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def root():
    """Root endpoint for quick liveness check."""
    return {"status": "ok", "message": "Halal Product Checker API"}


# ─────────────────────────────────────────────
# ENDPOINT 1: Health Check
# ─────────────────────────────────────────────
@app.get("/health")
def health_check():
    """Check if the API is running and can connect to the database."""
    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM halal_products;")
        total = cur.fetchone()[0]
        cur.close()
        release_db_connection(conn)
        return {
            "status": "ok",
            "message": "API is running",
            "total_records": total
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database connection failed: {str(e)}")


# ─────────────────────────────────────────────
# POPULAR COMPANY SCORES
# ─────────────────────────────────────────────
POPULAR_COMPANIES = {
    'NESTLE': 100, 'DUTCH LADY': 95, 'F&N': 92, 'GARDENIA': 90,
    'MASSIMO': 88, 'CADBURY': 87, 'MAMEE': 85, 'APOLLO': 84,
    'MUNCHY': 82, 'COCON': 80, 'ADABI': 79, 'AEON': 78,
    'MYDIN': 77, 'BRANDS': 76, 'SECRET RECIPE': 75, 'OLDTOWN': 74,
    'PIZZA HUT': 73, 'KFC': 72, 'MCDONALD': 71, 'SUBWAY': 70,
    'DEWINA': 68, 'JAYA GROCER': 65, 'SPRITZER': 64, 'MARIGOLD': 63,
    'YEOS': 62, 'POKKA': 61, 'MILO': 60, 'NESCAFE': 60,
    'LIPTON': 58, 'MAGGI': 57, 'SCOTT': 55, 'ANLENE': 54,
    'DUMEX': 53, 'FERNLEAF': 52, 'FRISO': 51, 'ENFALAC': 50,
    'SUNSHINE': 48, 'HOSEN': 47, 'AYAMAS': 60, 'RAMLY': 58,
    'JALEN': 45, 'SERI MURNI': 44, 'CAP TANGAN': 43,
}

def _popularity_score(company_name: str) -> int:
    """Return popularity bonus for a company name."""
    upper = company_name.upper()
    best = 0
    for brand, score in POPULAR_COMPANIES.items():
        if brand in upper:
            best = max(best, score)
    return best


# ─────────────────────────────────────────────
# ENDPOINT 2: Search Products
# ─────────────────────────────────────────────
@app.get("/search")
def search_products(
    q: str = Query(..., min_length=2, description="Search keyword (company name or brand)"),
    category: str = Query(None, description="Filter by category (optional). E.g: Produk, Syarikat, Kosmetik"),
    limit: int = Query(50, ge=1, le=200, description="Max number of results to return (default 50, max 200)"),
):
    try:
        conn = get_db_connection()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

        if category:
            cur.execute(
                """
                SELECT id, category, comp_code,
                       company_ner AS company, brand_ner AS brand,
                       product_ner AS product_name, address_ner AS address,
                       expiry, ner_confidence
                FROM halal_products
                WHERE (company_ner ILIKE %s OR brand_ner ILIKE %s OR product_ner ILIKE %s)
                  AND category = %s
                ORDER BY company_ner
                LIMIT %s
                """,
                (f"%{q}%", f"%{q}%", f"%{q}%", category, limit)
            )
        else:
            cur.execute(
                """
                SELECT id, category, comp_code,
                       company_ner AS company, brand_ner AS brand,
                       product_ner AS product_name, address_ner AS address,
                       expiry, ner_confidence
                FROM halal_products
                WHERE company_ner ILIKE %s OR brand_ner ILIKE %s OR product_ner ILIKE %s
                ORDER BY company_ner
                LIMIT %s
                """,
                (f"%{q}%", f"%{q}%", f"%{q}%", limit)
            )

        rows = cur.fetchall()
        cur.close()
        release_db_connection(conn)

        return {
            "query": q,
            "category_filter": category,
            "count": len(rows),
            "results": rows
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


# ─────────────────────────────────────────────
# ENDPOINT 2b: Search Grouped by Company
# ─────────────────────────────────────────────
@app.get("/search-grouped")
def search_grouped(
    q: str = Query(..., min_length=2),
    limit: int = Query(100, ge=1, le=500, description="Max syarikat untuk return"),
):
    try:
        conn = get_db_connection()
        cur  = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

        cur.execute(
            """
            SELECT company_ner AS company,
                   COUNT(*)              AS product_count,
                   array_agg(DISTINCT category) AS categories
            FROM halal_products
            WHERE company_ner ILIKE %s OR brand_ner ILIKE %s OR product_ner ILIKE %s
            GROUP BY company_ner
            ORDER BY COUNT(*) DESC
            LIMIT 100
            """,
            (f"%{q}%", f"%{q}%", f"%{q}%"),
        )
        company_rows = cur.fetchall()

        result_list = []
        for row in company_rows:
            company = row["company"] or "Unknown"
            pop     = _popularity_score(company)
            result_list.append({
                "company":          company,
                "product_count":    row["product_count"],
                "popularity_score": pop,
                "categories":       [c for c in (row["categories"] or []) if c],
                "products":         [],
            })

        result_list.sort(
            key=lambda x: (x["popularity_score"], x["product_count"]),
            reverse=True,
        )
        result_list = result_list[:limit]

        cur.close()
        release_db_connection(conn)

        return {
            "query":         q,
            "company_count": len(result_list),
            "companies":     result_list,
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Grouped search failed: {str(e)}")


# ─────────────────────────────────────────────
# ENDPOINT 2c: Get Products for a Specific Company
# ─────────────────────────────────────────────
@app.get("/company-products")
def company_products(
    company: str = Query(..., min_length=1),
    q: str = Query(..., min_length=1),
):
    try:
        conn = get_db_connection()
        cur  = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(
            """
            SELECT id, category, comp_code,
                   company_ner AS company, brand_ner AS brand,
                   product_ner AS product_name, address_ner AS address,
                   expiry, ner_confidence
            FROM halal_products
            WHERE company_ner = %s
              AND (company_ner ILIKE %s OR brand_ner ILIKE %s OR product_ner ILIKE %s)
            ORDER BY product_ner
            """,
            (company, f"%{q}%", f"%{q}%", f"%{q}%"),
        )
        rows = cur.fetchall()
        cur.close()
        release_db_connection(conn)
        return {"company": company, "query": q, "count": len(rows), "products": rows}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ─────────────────────────────────────────────
# ENDPOINT 3: Get Company by Certificate Code
# ─────────────────────────────────────────────
@app.get("/company/{comp_code}")
def get_company(comp_code: str):
    try:
        conn = get_db_connection()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

        cur.execute(
            """
            SELECT id, category, comp_code,
                   company_ner AS company, brand_ner AS brand,
                   product_ner AS product_name, address_ner AS address,
                   expiry, ner_confidence
            FROM halal_products
            WHERE comp_code = %s
            """,
            (comp_code,)
        )

        rows = cur.fetchall()
        cur.close()
        release_db_connection(conn)

        if not rows:
            raise HTTPException(
                status_code=404,
                detail=f"No records found for certificate code: {comp_code}"
            )

        return {
            "comp_code": comp_code,
            "count": len(rows),
            "results": rows
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Query failed: {str(e)}")


# ─────────────────────────────────────────────
# ENDPOINT 4: List All Categories
# ─────────────────────────────────────────────
@app.get("/categories")
def list_categories():
    try:
        conn = get_db_connection()
        cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

        cur.execute(
            """
            SELECT category, COUNT(*) as total
            FROM halal_products
            GROUP BY category
            ORDER BY total DESC
            """
        )

        rows = cur.fetchall()
        cur.close()
        release_db_connection(conn)

        return {"categories": rows}

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Query failed: {str(e)}")


# ─────────────────────────────────────────────
# ENDPOINT 5: Scan Product Image
# ─────────────────────────────────────────────
@app.post("/scan-image")
async def scan_image(file: UploadFile = File(...)):
    ct = (file.content_type or "").lower()
    if ct and not ct.startswith("image/") and ct != "application/octet-stream":
        raise HTTPException(status_code=400, detail="Only image files are accepted.")

    try:
        image_bytes = await file.read()
        if len(image_bytes) > 10 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="Image too large. Maximum 10 MB.")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Could not read image: {str(e)}")

    # METHOD 1: Qwen Vision
    vision_result = _extract_brand_vision(image_bytes)
    vision_halal  = []
    if vision_result.get("brand") or vision_result.get("product"):
        vision_texts = [v for v in [
            vision_result.get("brand", ""),
            vision_result.get("product", ""),
        ] if v]
        keywords = vision_result.get("keywords", [])
        if isinstance(keywords, list):
            vision_texts += [k for k in keywords if isinstance(k, str) and len(k) >= 3]
        vision_halal = _search_jakim_by_texts(vision_texts)
        print(f"[Vision] Search terms: {vision_texts}")
        print(f"[Vision] JAKIM matches: {len(vision_halal)}")

    # METHOD 2: PaddleOCR fallback
    ocr_texts = []
    ocr_halal = []
    if len(vision_halal) == 0:
        print("[OCR] Vision found nothing, trying PaddleOCR...")
        ocr_texts = _extract_text_ocr(image_bytes)
        ocr_halal = _search_jakim_by_texts(ocr_texts)
        print(f"[OCR] Extracted {len(ocr_texts)} texts: {ocr_texts}")
        print(f"[OCR] JAKIM matches: {len(ocr_halal)}")

    seen_ids = set()
    merged_halal = []
    for record in vision_halal + ocr_halal:
        if record["id"] not in seen_ids:
            seen_ids.add(record["id"])
            merged_halal.append(record)

    if vision_halal:
        detection_method = "vision"
    elif ocr_halal:
        detection_method = "ocr"
    else:
        detection_method = "none"

    return {
        "vision_result":    vision_result,
        "ocr_texts":        ocr_texts,
        "halal_records":    merged_halal,
        "halal_count":      len(merged_halal),
        "detection_method": detection_method,
        "note": (
            "Vision LLM (Qwen2.5-VL) reads brand/product name from image. "
            "PaddleOCR is fallback if Vision finds nothing."
        ),
    }


# ─────────────────────────────────────────────
# ENDPOINT 6: JAKIM Certified Body Logo Scanner
# ─────────────────────────────────────────────
@app.post("/scan-logo")
async def scan_logo(file: UploadFile = File(...)):
    ct = (file.content_type or "").lower()
    if ct and not ct.startswith("image/") and ct != "application/octet-stream":
        raise HTTPException(status_code=400, detail="Only image files are accepted.")

    try:
        image_bytes = await file.read()
        if len(image_bytes) > 10 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="Image too large. Maximum 10 MB.")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Could not read image: {str(e)}")

    # Step 1: Qwen Vision identify the logo
    vision_result = _identify_logo_vision(image_bytes)
    detection_method = "vision"

    abbreviation = (vision_result.get("abbreviation") or "UNKNOWN") if vision_result else "UNKNOWN"

    # Step 2: OCR fallback — if Vision failed or returned UNKNOWN
    if not vision_result or abbreviation == "UNKNOWN":
        print("[Logo] Vision failed or UNKNOWN — trying OCR fallback...")
        ocr_texts = _extract_text_ocr(image_bytes)
        print(f"[Logo] OCR texts: {ocr_texts}")
        if ocr_texts:
            detection_method = "ocr"
            vision_result = {
                "abbreviation": "UNKNOWN",
                "country": "",
                "organization_name": "",
                "all_text": ocr_texts,
            }

    # Step 3: Search certified bodies list
    matches = _search_certified_body(vision_result) if vision_result else []
    is_recognised = len(matches) > 0

    return {
        "vision_result": vision_result,
        "is_recognised_by_jakim": is_recognised,
        "matches": matches,
        "match_count": len(matches),
        "detection_method": detection_method,
        "note": (
            "Halal certification bodies are cross-referenced against JAKIM's "
            "official list of recognised Certification Bodies (CBs) from 48 countries."
        ),
    }


# ─────────────────────────────────────────────
# ENDPOINT 7: Drone Premise Scanner
# Sama seperti /scan-image tapi cari dalam Premis Makanan JAKIM
# ─────────────────────────────────────────────
@app.post("/scan-premises")
async def scan_premises(file: UploadFile = File(...)):
    ct = (file.content_type or "").lower()
    if ct and not ct.startswith("image/") and ct != "application/octet-stream":
        raise HTTPException(status_code=400, detail="Only image files are accepted.")

    try:
        image_bytes = await file.read()
        if len(image_bytes) > 10 * 1024 * 1024:
            raise HTTPException(status_code=400, detail="Image too large. Maximum 10 MB.")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Could not read image: {str(e)}")

    # Guna prompt khusus untuk papan tanda/signboard dari drone
    vision_texts  = _extract_premise_name_vision(image_bytes)
    vision_matches = []

    if vision_texts:
        vision_matches = _search_premises_by_texts(vision_texts)
        print(f"[DroneVision] Terms: {vision_texts}, matches: {len(vision_matches)}")

    ocr_texts  = []
    ocr_matches = []
    if not vision_matches:
        ocr_texts   = _extract_text_ocr(image_bytes)
        ocr_matches = _search_premises_by_texts(ocr_texts)
        print(f"[DroneOCR] Texts: {ocr_texts}, matches: {len(ocr_matches)}")

    seen_ids = set()
    merged = []
    for record in vision_matches + ocr_matches:
        if record["id"] not in seen_ids:
            seen_ids.add(record["id"])
            merged.append(record)

    detection_method = "vision" if vision_matches else ("ocr" if ocr_matches else "none")

    return {
        "vision_texts":     vision_texts,
        "ocr_texts":        ocr_texts,
        "is_halal_premise": len(merged) > 0,
        "matches":          merged,
        "match_count":      len(merged),
        "detection_method": detection_method,
    }
