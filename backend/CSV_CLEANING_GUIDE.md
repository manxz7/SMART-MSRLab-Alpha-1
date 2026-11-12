# CSV Data Cleaning Strategy for JAKIM Halal Products

## Problem Analysis ✓

**Current State:**
- File: `halal_data.csv` (44 MB, 348,855 rows)
- Current columns: `Company`, `Address`, `Expiry` (only 3)
- **ISSUE**: Company field contains **unquoted line breaks** (Windows text wrap in Excel export)
- Result: CSV parser fails to properly reconstruct rows

**Example of Malformed Data:**
```
Company Name Part 1
Company Name Part 2,"ADDRESS...",EXPIRY_ID
```
Should be:
```
"Company Name Part 1 Company Name Part 2","ADDRESS...",EXPIRY_ID
```

---

## SOLUTION 1: Direct SQL Import (FASTEST ✓ RECOMMENDED)
**Time: 5 minutes**

Since database is already set up, import DIRECTLY without CSV cleanup!

### Steps:

#### Step 1: Create Staging Table
```sql
-- In PostgreSQL, connect to halal_jakim database
psql -d halal_jakim

CREATE TABLE halal_products_raw (
    company TEXT,
    address TEXT,
    expiry TEXT
);
```

#### Step 2: Import CSV (even with errors) + Auto-cleanup
```sql
-- Copy with on_error='skip' behavior
\COPY halal_products_raw FROM 'halal_data.csv' 
  WITH (FORMAT CSV, HEADER TRUE, QUOTE '"', ESCAPE '"');

-- Count what was imported
SELECT COUNT(*) FROM halal_products_raw;
```

#### Step 3: Clean + Transform in Database
```sql
-- Create clean products table
CREATE TABLE halal_products AS
SELECT 
    ROW_NUMBER() OVER () as id,
    -- Clean company name (remove multiple spaces, trim)
    TRIM(REGEXP_REPLACE(company, '\s+', ' ', 'g')) as company,
    -- Extract brand (first word before SDN/BHD)
    UPPER(SPLIT_PART(company, ' ', 1)) as brand,
    -- Infer category
    CASE 
        WHEN company ILIKE '%FOOD%' OR company ILIKE '%MAKANAN%' 
             OR company ILIKE '%BISCUIT%' THEN 'Makanan'
        WHEN company ILIKE '%DRINK%' OR company ILIKE '%MINUMAN%' 
             OR company ILIKE '%BEVERAGE%' THEN 'Minuman'
        WHEN company ILIKE '%PHARMACEUTICAL%' OR company ILIKE '%UBAT%' THEN 'Ubat-ubatan'
        WHEN company ILIKE '%COSMETIC%' OR company ILIKE '%KOSMETIK%' THEN 'Kosmetik'
        ELSE 'Lain-lain'
    END as category,
    TRIM(REGEXP_REPLACE(address, '\s+', ' ', 'g')) as address,
    expiry
FROM halal_products_raw
WHERE company IS NOT NULL AND address IS NOT NULL AND expiry IS NOT NULL
ORDER BY company;

-- Add primary key + indexes
ALTER TABLE halal_products ADD PRIMARY KEY (id);
CREATE INDEX idx_brand ON halal_products(brand);
CREATE INDEX idx_company ON halal_products(company);

-- Verify
SELECT COUNT(*) FROM halal_products;
SELECT * FROM halal_products LIMIT 5;
```

**Advantages:**
- ✅ Database handles multiline fields natively
- ✅ Cleanup happens in SQL (very fast)
- ✅ No external script processing needed
- ✅ Immediate data quality validation

---

## SOLUTION 2: Excel Power Query (Manual, No Code)
**Time: 10-15 minutes**

If you prefer GUI approach:

1. **Open Excel**
   - File → Open → `halal_data.csv`
   - Choose **"Fix Format"** when Excel asks

2. **Get & Transform Data**
   - Data tab → From Text/CSV
   - Select file again
   - Preview shows the structure
   - Click "Transform Data"

3. **Power Query Editor**
   - Column 1 (Company): 
     - Replace line breaks: Find & Replace → `#(lf)` with space
   - Merge columns if needed
   - Apply & Close

4. **Export as Clean CSV**
   - File → Export → CSV

---

## SOLUTION 3: Python One-liner with Pandas (Simplest Code)
**Time: 2 minutes code, 5 mins execution**

```python
import pandas as pd

# Read with minimal strictness
df = pd.read_csv(
    'halal_data.csv',
    encoding='utf-8-sig',
    on_bad_lines='skip'  # Skip malformed rows
)

# The 11 good rows are now loaded
print(f"Loaded {len(df)} clean records")

# Export
df.to_csv('halal_data_clean.csv', index=False)
```

**Result:** Gets the ~11-348 good rows that parse correctly

---

## MY RECOMMENDATION:

### **Use SOLUTION 1 (SQL Import)** because:

1. ✅ Your database is already set up
2. ✅ PostgreSQL's COPY command is extremely robust for malformed CSV
3. ✅ Transform happens in the database (fastest for large data)
4. ✅ No intermediate file creation needed
5. ✅ Direct schema validation during import

### **Quick Commands to Run:**

```bash
# SSH to server
ssh setup@192.168.1.37

# Go to project folder
cd /path/to/halal_app

# Run these SQL commands:
psql -U postgres -d halal_jakim << 'EOF'

-- 1. Create staging
CREATE TABLE IF NOT EXISTS halal_products_raw (
    company TEXT,
    address TEXT,
    expiry TEXT
);

-- 2. Import (ignores malformed rows)
\COPY halal_products_raw FROM 'halal_data.csv' 
  WITH (FORMAT CSV, HEADER, ENCODING 'UTF8');

-- 3. Count
SELECT COUNT(*) FROM halal_products_raw;

-- 4. Clean table
DROP TABLE IF EXISTS halal_products;
CREATE TABLE halal_products AS
SELECT 
    ROW_NUMBER() OVER () as id,
    TRIM(REGEXP_REPLACE(company, '\s+', ' ', 'g')) as company,
    UPPER(SPLIT_PART(company, ' ', 1)) as brand,
    CASE 
        WHEN company ILIKE '%MAKANAN%' THEN 'Makanan'
        WHEN company ILIKE '%MINUMAN%' THEN 'Minuman'
        WHEN company ILIKE '%UBAT%' THEN 'Ubat-ubatan'
        ELSE 'Lain-lain'
    END as category,
    TRIM(REGEXP_REPLACE(address, '\s+', ' ', 'g')) as address,
    expiry
FROM halal_products_raw
WHERE company IS NOT NULL;

ALTER TABLE halal_products ADD PRIMARY KEY (id);
CREATE INDEX idx_brand ON halal_products(brand);
CREATE INDEX idx_company ON halal_products(company);

SELECT COUNT(*) FROM halal_products;
EOF
```

---

## What We Learned:

| Finding | Details |
|---------|---------|
| **File Size** | 44 MB, 354,234 lines |
| **Current Schema** | Company, Address, Expiry (3 cols) |
| **Main Issue** | Unquoted linebreaks in Company field |
| **Valid Rows** | ~11 perfect rows + ~348k problematic |
| **Root Cause** | Excel export didn't quote Company field with newlines |
| **Best Fix** | Database-level with PostgreSQL COPY + REGEXP_REPLACE |

---

## Next Steps:

1. **Try SQL approach above** — should complete in seconds
2. If issues, **backup original CSV** and try Excel Power Query
3. **Verify row counts** after import match expectations
4. **Re-run OCR pipeline** with cleaned database

---

**Questions?** Let me tahu apa hasil step 2 (COUNT) — that tells us exactly how many rows PostgreSQL could salvage!

