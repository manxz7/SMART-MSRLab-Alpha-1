# =============================================================================
# Halal JAKIM — NER Cleaning Pipeline
# =============================================================================
# FLOW:
#   PostgreSQL → Pandas → Preprocessing → Qwen 3 (LM Studio) → PostgreSQL
#
# SPEED IMPROVEMENTS:
#   1. Rule-based skip  — clean rows bypass API entirely (~40% of data)
#   2. Concurrent calls — 3 parallel API calls instead of 1 sequential
#
# HOW TO RUN:
#   1. python ner_pipeline.py
#
# RESUME SAFE: Only processes rows where ner_status = 'pending'.
# =============================================================================

import json
import re
import pandas as pd
from concurrent.futures import ThreadPoolExecutor, as_completed
from sqlalchemy import create_engine, text
from openai import OpenAI
from tqdm import tqdm

# =============================================================================
# CONFIG
# =============================================================================
DB_URL        = "postgresql://postgres:Huffazpro1!@localhost:5432/halal_jakim"
LM_STUDIO_URL = "https://lm-studio.careerlab.my/v1"
LM_API_KEY    = "sk-iC0mk0nZQtB1IAV0Cbri1g"
LM_MODEL      = "qwen3.6-27b"
LM_HEADERS    = {"User-Agent": "curl/8.6.0"}

BATCH_SIZE   = 100   # rows fetched from DB per loop
MAX_WORKERS  = 3     # concurrent API calls — increase if server handles it well

# =============================================================================
# RULE-BASED SKIP — patterns for detecting already-clean rows
# =============================================================================
COMPANY_RE = re.compile(
    r'\b(SDN\.?\s*BHD\.?|BERHAD|ENTERPRISE|TRADING|PLT|LLP|VENTURE|'
    r'HOLDINGS|INDUSTRIES|CORPORATION|RESTAURANT|HOTEL|RESORT)\b',
    re.IGNORECASE
)
ADDRESS_RE = re.compile(
    r'\b(JALAN|JLN|LORONG|LRG|TAMAN|BLOK|LOT|TINGKAT|BANDAR|KAMPUNG|KG|NO\.)\b'
    r'|\b\d{5}\b',
    re.IGNORECASE
)

def is_clean(row: dict) -> bool:
    """
    True = row fields are already well-structured, skip API call.
    Copies original values with confidence=0.90.
    """
    company = row.get('company', '')
    address = row.get('address', '')
    brand   = row.get('brand', '')

    # Must have a recognizable company suffix
    if not company or not COMPANY_RE.search(company):
        return False
    # Address must look like a real address OR be empty (Produk rows)
    if address and not ADDRESS_RE.search(address):
        return False
    # Brand must not still have JENAMA: prefix
    if re.match(r'^(JENAMA|BRAND)\s*:', brand, re.IGNORECASE):
        return False
    return True


def make_clean_result(row: dict) -> dict:
    """Build a done result directly from existing fields — no API call."""
    return {
        "id":          int(row["id"]),
        "company_ner": row.get("company", ""),
        "brand_ner":   row.get("brand", ""),
        "product_ner": row.get("product_name", ""),
        "address_ner": row.get("address", ""),
        "confidence":  0.90,
        "status":      "done",
    }


# =============================================================================
# SYSTEM PROMPT
# =============================================================================
SYSTEM_PROMPT = """
Extract fields from a JAKIM halal database row. Return ONLY this JSON object, nothing else:
{"company_ner":"","brand_ner":"","product_ner":"","address_ner":"","confidence":0.0}

Rules:
- company_ner: company/manufacturer name (SDN BHD, ENTERPRISE, TRADING, BERHAD, etc.)
- brand_ner: brand name, strip any "JENAMA:" or "BRAND:" prefix
- product_ner: product name, empty string if none
- address_ner: street address only (Jalan, Lorong, No., Taman, postcode 5 digits)
- confidence: 0.0-1.0
""".strip()


# =============================================================================
# STEP 1 — PREPROCESSING
# =============================================================================
def preprocess(df: pd.DataFrame) -> pd.DataFrame:
    str_cols = ["company", "brand", "product_name", "address"]

    for col in str_cols:
        if col not in df.columns:
            continue
        df[col] = (
            df[col]
            .fillna("")
            .str.strip()
            .str.replace(r"\s+", " ", regex=True)
            .str.strip(",- ")
            .pipe(lambda s: s if col != "brand"
                  else s.str.replace(r"^(?:JENAMA|BRAND)\s*:\s*", "", regex=True, flags=re.IGNORECASE))
        )

    before = len(df)
    df = df[
        (df["company"].str.len() > 0) |
        (df["product_name"].str.len() > 0) |
        (df["brand"].str.len() > 0)
    ].copy()
    after = len(df)

    if before != after:
        print(f"  Preprocessing: removed {before - after} empty rows")

    return df


# =============================================================================
# STEP 2 — CALL QWEN (NER)
# =============================================================================
client = OpenAI(base_url=LM_STUDIO_URL, api_key=LM_API_KEY, default_headers=LM_HEADERS)


def call_qwen(row: dict) -> dict | None:
    """Send one row to Qwen and return parsed JSON result. Thread-safe."""
    user_msg = (
        f"category: {row.get('category', '')}\n"
        f"comp_code: {row.get('comp_code', '')}\n"
        f"company: {row.get('company', '')}\n"
        f"brand: {row.get('brand', '')}\n"
        f"product_name: {row.get('product_name', '')}\n"
        f"address: {row.get('address', '')}"
    )

    try:
        response = client.chat.completions.create(
            model=LM_MODEL,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user",   "content": user_msg},
            ],
            temperature=0.1,
            max_tokens=1024,
        )
        raw = response.choices[0].message.content.strip()
        raw = re.sub(r"<think>.*?</think>", "", raw, flags=re.DOTALL).strip()

        if not raw:
            rc = getattr(response.choices[0].message, "reasoning_content", None) or ""
            raw = rc.strip()

        match = re.search(r"\{.*\}", raw, re.DOTALL)
        if not match:
            return None

        result = json.loads(match.group())

        required = {"company_ner", "brand_ner", "product_ner", "address_ner", "confidence"}
        if not required.issubset(result.keys()):
            return None

        conf = result["confidence"]
        if isinstance(conf, dict):
            values = [v for v in conf.values() if isinstance(v, (int, float))]
            result["confidence"] = round(sum(values) / len(values), 3) if values else 0.0
        else:
            result["confidence"] = float(conf)

        for key in ("company_ner", "brand_ner", "product_ner", "address_ner"):
            if result.get(key) is None:
                result[key] = ""

        return result

    except Exception:
        return None


# =============================================================================
# STEP 3 — WRITE RESULTS BACK TO POSTGRESQL
# =============================================================================
def save_batch(conn, results: list[dict]):
    if not results:
        return
    conn.execute(text("""
        UPDATE halal_products SET
            company_ner    = :company_ner,
            brand_ner      = :brand_ner,
            product_ner    = :product_ner,
            address_ner    = :address_ner,
            ner_confidence = :confidence,
            ner_status     = :status
        WHERE id = :id
    """), results)
    conn.commit()


# =============================================================================
# MAIN PIPELINE
# =============================================================================
def main():
    print("=" * 60)
    print("Halal JAKIM — NER Pipeline (threaded + rule-based skip)")
    print(f"  Workers : {MAX_WORKERS} concurrent API calls")
    print("=" * 60)

    engine = create_engine(DB_URL)

    with engine.connect() as conn:
        total_pending = conn.execute(
            text("SELECT COUNT(*) FROM halal_products WHERE ner_status = 'pending'")
        ).scalar()

    print(f"Rows pending NER: {total_pending:,}\n")
    if total_pending == 0:
        print("Nothing to process. All rows already cleaned.")
        return

    done = 0
    errors = 0
    skipped = 0  # rows handled by rule-based skip

    with engine.connect() as conn:
        while True:
            # -- Fetch next batch --
            rows = conn.execute(text("""
                SELECT id, category, comp_code, company, brand, product_name, address
                FROM   halal_products
                WHERE  ner_status = 'pending'
                LIMIT  :limit
            """), {"limit": BATCH_SIZE}).mappings().fetchall()

            if not rows:
                break

            df = preprocess(pd.DataFrame([dict(r) for r in rows]))

            batch_results = []
            api_rows      = []
            batch_skipped = 0

            # -- Split: clean rows skip API, messy rows go to Qwen --
            for _, row in df.iterrows():
                row_dict = row.to_dict()
                if is_clean(row_dict):
                    batch_results.append(make_clean_result(row_dict))
                    batch_skipped += 1
                else:
                    api_rows.append(row_dict)

            # -- Concurrent API calls for messy rows --
            error_ids = []
            if api_rows:
                with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
                    future_to_row = {
                        executor.submit(call_qwen, row): row
                        for row in api_rows
                    }
                    for future in tqdm(
                        as_completed(future_to_row),
                        total=len(api_rows),
                        desc=f"Qwen NER (done={done:,})",
                        leave=False,
                    ):
                        row    = future_to_row[future]
                        result = future.result()
                        if result:
                            batch_results.append({
                                "id":          int(row["id"]),
                                "company_ner": result.get("company_ner", ""),
                                "brand_ner":   result.get("brand_ner", ""),
                                "product_ner": result.get("product_ner", ""),
                                "address_ner": result.get("address_ner", ""),
                                "confidence":  float(result.get("confidence", 0.0)),
                                "status":      "done",
                            })
                            done += 1
                        else:
                            error_ids.append(int(row["id"]))
                            errors += 1

            # -- Save done results --
            save_batch(conn, batch_results)
            done += batch_skipped
            skipped += batch_skipped

            # -- Mark errors --
            if error_ids:
                for eid in error_ids:
                    conn.execute(
                        text("UPDATE halal_products SET ner_status='error' WHERE id=:id"),
                        {"id": eid}
                    )
                conn.commit()

            pct = ((done + errors) / total_pending) * 100
            print(
                f"  Progress: {done + errors:,}/{total_pending:,} ({pct:.1f}%) "
                f"| done={done:,} skipped={skipped:,} errors={errors:,}"
            )

    print()
    print("=" * 60)
    print(f"Pipeline complete!")
    print(f"  Successfully cleaned : {done:,}")
    print(f"  Rule-based skipped   : {skipped:,}")
    print(f"  Errors (review)      : {errors:,}")
    print("=" * 60)


if __name__ == "__main__":
    main()
