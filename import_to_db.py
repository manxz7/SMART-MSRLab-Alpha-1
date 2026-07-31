# =============================================================================
# Portal Halal JAKIM — CSV to PostgreSQL Import Script
# =============================================================================
# HOW TO RUN:
#   1. Run db_setup.sql first to create the database and table
#   2. Activate venv:  .venv\Scripts\Activate.ps1
#   3. pip install psycopg2-binary pandas
#   4. python import_to_db.py
# =============================================================================

import pandas as pd
from sqlalchemy import create_engine, text

# =============================================================================
# CONFIG
# =============================================================================
DB_URL  = "postgresql://postgres:Huffazpro1!@localhost:5432/halal_jakim"
CSV_FILE = "halal_data.csv"
TABLE    = "halal_products"
CHUNK    = 2000   # rows per insert batch

# =============================================================================
# MAIN
# =============================================================================
def main():
    print(f"Connecting to PostgreSQL...")
    engine = create_engine(DB_URL)

    # Test connection
    with engine.connect() as conn:
        conn.execute(text("SELECT 1"))
    print("Connected.")

    print(f"Reading {CSV_FILE}...")
    df = pd.read_csv(CSV_FILE, encoding="utf-8-sig", dtype=str).fillna("")

    # Normalise column names to lowercase
    df.columns = df.columns.str.lower().str.replace(" ", "_")

    print(f"Columns found: {list(df.columns)}")
    print(f"Total rows   : {len(df):,}")

    # Keep only the columns that exist in the table
    keep = ["category", "comp_code", "company", "brand", "product_name", "address", "expiry"]
    df = df[[c for c in keep if c in df.columns]]

    # Clear existing rows and reset ID sequence
    with engine.connect() as conn:
        conn.execute(text("TRUNCATE TABLE halal_products RESTART IDENTITY;"))
        conn.commit()
    print("Existing data cleared.")

    # Insert in chunks
    df.to_sql(TABLE, engine, if_exists="append", index=False,
              chunksize=CHUNK, method="multi")

    print()
    print("=" * 50)
    print(f"DONE!  {len(df):,} rows imported into '{TABLE}'")
    print("=" * 50)


if __name__ == "__main__":
    main()
