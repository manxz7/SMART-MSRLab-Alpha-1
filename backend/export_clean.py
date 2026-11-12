# =============================================================================
# Export cleaned NER data from PostgreSQL → CSV
# =============================================================================
# Run after ner_pipeline.py finishes.
# Outputs: halal_data_cleaned.csv
# =============================================================================

import pandas as pd
from sqlalchemy import create_engine, text

DB_URL = "postgresql://postgres:Huffazpro1!@localhost:5432/halal_jakim"

def main():
    engine = create_engine(DB_URL)

    with engine.connect() as conn:
        # Summary stats
        stats = conn.execute(text("""
            SELECT ner_status, COUNT(*) AS total
            FROM halal_products
            GROUP BY ner_status
            ORDER BY total DESC
        """)).fetchall()

    print("=== NER Status Summary ===")
    for row in stats:
        print(f"  {row[0]:10s}: {row[1]:,}")
    print()

    # Export only successfully cleaned rows
    df = pd.read_sql("""
        SELECT
            id,
            category,
            comp_code,
            COALESCE(NULLIF(company_ner, ''), company)      AS company,
            COALESCE(NULLIF(brand_ner,   ''), brand)        AS brand,
            COALESCE(NULLIF(product_ner, ''), product_name) AS product_name,
            COALESCE(NULLIF(address_ner, ''), address)      AS address,
            expiry,
            ner_confidence
        FROM halal_products
        WHERE ner_status = 'done'
        ORDER BY id
    """, engine)

    out = "halal_data_cleaned.csv"
    df.to_csv(out, index=False, encoding="utf-8-sig")

    print(f"Exported {len(df):,} cleaned rows → {out}")
    print(f"Avg NER confidence: {df['ner_confidence'].mean():.2f}")
    print()
    print("Column fill rate:")
    for col in ["company", "brand", "product_name", "address"]:
        filled = df[col].str.strip().str.len().gt(0).sum()
        print(f"  {col:15s}: {filled:,} / {len(df):,} ({filled/len(df)*100:.1f}%)")

if __name__ == "__main__":
    main()
