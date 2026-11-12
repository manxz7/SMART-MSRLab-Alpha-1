#!/usr/bin/env python3
# ============================================================
# clean_halal_data.py — Automatic CSV Data Cleaner
# ============================================================
# Cleans 350k JAKIM halal product data using pattern recognition.
# Fixes misaligned columns, extracts brand/company names properly.
# ============================================================

import pandas as pd
import numpy as np
import re
import os
from pathlib import Path

# ─────────────────────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────────────────────

INPUT_FILE = "halal_data.csv"
OUTPUT_FILE = "halal_data_cleaned.csv"

# Keywords for detecting company name endings (Malaysia-specific)
COMPANY_SUFFIXES = [
    'SDN BHD',
    'SDN. BHD',
    'SDN. BHD.',
    'BHD',
    'ENTERPRISE',
    'TRADING',
    'CO.',
    'CO',
    'LLC',
    'PTY LTD',
    'MANUFACTURING',
    'RESTAURANT',
    'BAKERY',
    'STORE',
]

# Keywords that indicate brand/product name
BRAND_KEYWORDS = ['JENAMA:', 'BRAND:', 'PRODUK:', 'PRODUCT:', 'NAMA JENAMA:']

# ─────────────────────────────────────────────────────────────
# STEP 1: Load data with careful handling of multiline cells
# ─────────────────────────────────────────────────────────────

def load_csv_carefully():
    """Load CSV with error_bad_lines='skip' to handle malformed rows."""
    print("📂 Loading CSV file (lenient mode)...")
    
    try:
        # More lenient parsing — skip malformed rows
        df = pd.read_csv(
            INPUT_FILE, 
            encoding='utf-8-sig',
            on_bad_lines='skip',  # Skip rows with wrong number of columns
            engine='python',       # Python engine is more forgiving
            quotechar='"',
            quoting=1,  # QUOTE_ALL — respect quoted fields
            sep=',',
        )
    except Exception as e:
        print(f"⚠️  UTF-8 failed, trying latin-1: {e}")
        try:
            df = pd.read_csv(
                INPUT_FILE, 
                encoding='latin-1',
                on_bad_lines='skip',
                engine='python',
                quotechar='"',
                quoting=1,
                sep=',',
            )
        except Exception as e2:
            print(f"⚠️  latin-1 failed, trying raw text parsing: {e2}")
            df = load_csv_from_raw_text()
    
    print(f"✅ Loaded {len(df):,} rows, {len(df.columns)} columns")
    print(f"📋 Columns: {list(df.columns)}")
    return df


def load_csv_from_raw_text():
    """
    Last resort: Parse CSV manually from raw text.
    Handles embedded line breaks in quoted fields.
    """
    print("📝 Raw text parsing...")
    
    rows = []
    with open(INPUT_FILE, 'r', encoding='utf-8-sig', errors='replace') as f:
        lines = f.readlines()
    
    # Reconstruct rows handling quoted fields with line breaks
    current_row = []
    in_quotes = False
    current_field = ''
    
    for line in lines:
        for char in line:
            if char == '"':
                in_quotes = not in_quotes
                current_field += char
            elif char == ',' and not in_quotes:
                current_row.append(current_field.strip('"').strip())
                current_field = ''
            elif char in '\n\r' and not in_quotes:
                if current_field or current_row:
                    current_row.append(current_field.strip('"').strip())
                    if current_row and any(current_row):  # Non-empty row
                        rows.append(current_row)
                    current_row = []
                    current_field = ''
            else:
                current_field += char
    
    # Finalize last row
    if current_field or current_row:
        current_row.append(current_field)
        if current_row and any(current_row):
            rows.append(current_row)
    
    # Find header (first row)
    headers = rows[0] if rows else ['Column1', 'Column2', 'Column3']
    
    # Pad/trim all rows to match header length
    data_rows = []
    for row in rows[1:]:
        while len(row) < len(headers):
            row.append('')
        data_rows.append(row[:len(headers)])
    
    df = pd.DataFrame(data_rows, columns=headers)
    print(f"✅ Raw parsed: {len(df):,} rows")
    return df


# ─────────────────────────────────────────────────────────────
# STEP 2: Clean multiline cells (remove \n and extra whitespace)
# ─────────────────────────────────────────────────────────────

def clean_whitespace(text):
    """Remove line breaks, extra spaces; normalize whitespace."""
    if pd.isna(text) or text == '':
        return ''
    
    text = str(text)
    # Replace line breaks with space
    text = re.sub(r'[\n\r\t]+', ' ', text)
    # Remove extra spaces
    text = re.sub(r'\s+', ' ', text)
    # Strip leading/trailing spaces
    text = text.strip()
    return text


# ─────────────────────────────────────────────────────────────
# STEP 3: Extract brand name from various patterns
# ─────────────────────────────────────────────────────────────

def extract_brand(row_data):
    """
    Extract brand from company, product, or address fields.
    
    Patterns:
    - "JENAMA: SPRITZER" → "SPRITZER"
    - Company name if it's a well-known brand
    - First word of product name
    """
    company = clean_whitespace(str(row_data.get('Company', '')))
    product = clean_whitespace(str(row_data.get('Product', '')))
    brand_field = clean_whitespace(str(row_data.get('Brand', '')))
    
    # Priority 1: Explicit brand field
    if brand_field and brand_field != '':
        match = re.search(r'JENAMA[:\s]+(\w+)', brand_field, re.IGNORECASE)
        if match:
            return match.group(1).upper()
        return brand_field.upper()
    
    # Priority 2: Look for JENAMA: pattern in company
    match = re.search(r'JENAMA[:\s]+(\w+)', company, re.IGNORECASE)
    if match:
        return match.group(1).upper()
    
    # Priority 3: First word of company (before SDN/BHD)
    company_core = company.split('SDN')[0].split('BHD')[0].split('(')[0].strip()
    if company_core:
        brand = company_core.split()[0] if company_core.split() else ''
        if len(brand) > 2:  # Not too short
            return brand.upper()
    
    # Priority 4: Product name's first word
    if product:
        brand = product.split()[0]
        if len(brand) > 2:
            return brand.upper()
    
    return 'UNKNOWN'


# ─────────────────────────────────────────────────────────────
# STEP 4: Extract company name properly
# ─────────────────────────────────────────────────────────────

def extract_company(text):
    """
    Extract core company name, removing agent info and duplicates.
    
    Example:
    "NESTLE PRODUCTS SDN. BHD. (EJEN: NESTLE MANUFACTURING)"
    → "NESTLE PRODUCTS SDN. BHD."
    """
    text = clean_whitespace(str(text))
    
    # Remove agent info in parentheses (keep main company)
    text = re.sub(r'\s*\([^)]*EJEN[^)]*\)', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\s*\([^)]*AGENT[^)]*\)', '', text, flags=re.IGNORECASE)
    
    # Remove duplicate company names
    parts = text.split(' ')
    unique_parts = []
    for part in parts:
        if part not in unique_parts:
            unique_parts.append(part)
    
    text = ' '.join(unique_parts)
    
    # Normalize spacing around SDN/BHD
    text = re.sub(r'\s+SDN\s+\.?\s*BHD\.?', ' SDN BHD', text, flags=re.IGNORECASE)
    
    return text.strip()


# ─────────────────────────────────────────────────────────────
# STEP 5: Extract product name (if separate column)
# ─────────────────────────────────────────────────────────────

def extract_product_name(row_data):
    """
    Extract product name. If not present, derive from pattern.
    """
    product = clean_whitespace(str(row_data.get('Product', '')))
    
    if product and product != '':
        return product
    
    # Fallback: use company + category hint
    company = str(row_data.get('Company', ''))
    category = str(row_data.get('Category', ''))
    
    # Very basic fallback
    if category:
        return f"{category} Product".title()
    
    return ''


# ─────────────────────────────────────────────────────────────
# STEP 6: Clean address (remove extra line breaks)
# ─────────────────────────────────────────────────────────────

def clean_address(text):
    """
    Clean address: normalize line breaks, keep structure but compact.
    """
    text = clean_whitespace(str(text))
    # Replace remaining spaces with actual structure
    text = re.sub(r',\s+', ', ', text)  # Compact comma spacing
    return text


# ─────────────────────────────────────────────────────────────
# STEP 7: Parse category (if available)
# ─────────────────────────────────────────────────────────────

def parse_category(text):
    """
    Categorize product (Makanan, Minuman, Ubat, dll).
    """
    text = str(text).lower()
    
    if any(word in text for word in ['makanan', 'food', 'cereal', 'biscuit', 'cake']):
        return 'Makanan'
    elif any(word in text for word in ['minuman', 'drink', 'beverage', 'juice', 'water']):
        return 'Minuman'
    elif any(word in text for word in ['ubat', 'medicine', 'pharmaceutical']):
        return 'Ubat-ubatan'
    elif any(word in text for word in ['kosmetik', 'cosmetic', 'skincare']):
        return 'Kosmetik'
    else:
        return 'Lain-lain'


# ─────────────────────────────────────────────────────────────
# STEP 8: Main cleaning function
# ─────────────────────────────────────────────────────────────

def clean_dataset(df):
    """Main cleaning pipeline."""
    
    print("\n🧹 Cleaning dataset...")
    
    # Create new cleaned dataframe
    cleaned = pd.DataFrame()
    
    # Map existing columns (adjust based on actual CSV structure)
    for col in df.columns:
        df[col] = df[col].apply(clean_whitespace)
    
    # Extract/derive fields
    print("  → Extracting company names...")
    cleaned['company'] = df.get('Company', '').apply(extract_company)
    
    print("  → Extracting brands...")
    cleaned['brand'] = df.apply(extract_brand, axis=1)
    
    print("  → Extracting product names...")
    cleaned['product'] = df.apply(extract_product_name, axis=1)
    
    print("  → Cleaning addresses...")
    cleaned['address'] = df.get('Address', '').apply(clean_address)
    
    print("  → Parsing categories...")
    cleaned['category'] = df.get('Category', '').apply(parse_category)
    
    print("  → Preserving expiry dates...")
    cleaned['expiry'] = df.get('Expiry', '')
    
    # Add ID if not exists
    if 'ID' in df.columns:
        cleaned['id'] = df['ID']
    else:
        cleaned['id'] = range(1, len(cleaned) + 1)
    
    # Reorder columns logically
    cleaned = cleaned[['id', 'category', 'company', 'brand', 'product', 'address', 'expiry']]
    
    return cleaned


# ─────────────────────────────────────────────────────────────
# STEP 9: Validate & remove duplicates
# ─────────────────────────────────────────────────────────────

def validate_and_deduplicate(df):
    """Remove duplicates while preserving data integrity."""
    
    print("\n✔️  Validating data...")
    
    # Remove completely empty rows
    df = df.dropna(how='all')
    
    # Remove duplicates based on company+brand+product (keep first)
    initial_count = len(df)
    df = df.drop_duplicates(subset=['company', 'brand', 'product'], keep='first')
    
    duplicates_removed = initial_count - len(df)
    print(f"  → Removed {duplicates_removed:,} duplicate records")
    
    # Fill missing values
    df['product'] = df['product'].fillna('N/A')
    df['address'] = df['address'].fillna('N/A')
    df['expiry'] = df['expiry'].fillna('N/A')
    
    return df


# ─────────────────────────────────────────────────────────────
# STEP 10: Export cleaned data
# ─────────────────────────────────────────────────────────────

def export_cleaned_data(df):
    """Save cleaned CSV."""
    
    print(f"\n💾 Exporting cleaned data to {OUTPUT_FILE}...")
    
    df.to_csv(OUTPUT_FILE, index=False, encoding='utf-8-sig')
    
    file_size = os.path.getsize(OUTPUT_FILE) / (1024 * 1024)  # MB
    print(f"✅ Exported {len(df):,} clean records ({file_size:.2f} MB)")
    
    return OUTPUT_FILE


# ─────────────────────────────────────────────────────────────
# STEP 11: Generate summary report
# ─────────────────────────────────────────────────────────────

def generate_report(df_original, df_cleaned):
    """Print data quality report."""
    
    print("\n" + "="*70)
    print("📊 DATA QUALITY REPORT")
    print("="*70)
    
    print(f"\n📈 Record Count:")
    print(f"   Original: {len(df_original):,}")
    print(f"   Cleaned:  {len(df_cleaned):,}")
    print(f"   Removed:  {len(df_original) - len(df_cleaned):,} (duplicates + empty)")
    
    print(f"\n📂 Column Breakdown (Cleaned):")
    for col in df_cleaned.columns:
        non_null = df_cleaned[col].notna().sum()
        null_pct = (1 - non_null / len(df_cleaned)) * 100
        print(f"   {col:12} → {non_null:7,} filled ({100-null_pct:5.2f}%)")
    
    print(f"\n🏢 Top 10 Companies:")
    top_companies = df_cleaned['company'].value_counts().head(10)
    for i, (company, count) in enumerate(top_companies.items(), 1):
        print(f"   {i:2}. {company:40} ({count:4,} products)")
    
    print(f"\n🏷️  Top 10 Brands:")
    top_brands = df_cleaned['brand'].value_counts().head(10)
    for i, (brand, count) in enumerate(top_brands.items(), 1):
        print(f"   {i:2}. {brand:30} ({count:4,} products)")
    
    print(f"\n📑 Category Distribution:")
    categories = df_cleaned['category'].value_counts()
    for category, count in categories.items():
        pct = (count / len(df_cleaned)) * 100
        bar = "█" * int(pct / 2)
        print(f"   {category:12} → {count:7,} ({pct:5.2f}%) {bar}")
    
    print("\n" + "="*70 + "\n")


# ─────────────────────────────────────────────────────────────
# MAIN EXECUTION
# ─────────────────────────────────────────────────────────────

def main():
    """Run the cleaning pipeline."""
    
    print("\n" + "="*70)
    print("🧹 JAKIM HALAL DATA CLEANER v1.0")
    print("="*70)
    
    # Check file exists
    if not os.path.exists(INPUT_FILE):
        print(f"❌ ERROR: {INPUT_FILE} not found!")
        return
    
    # Load original data
    df_original = load_csv_carefully()
    
    print(f"\n🔍 Preview of original data (first 3 rows):")
    print(df_original.head(3))
    print(f"\n")
    
    # Clean data
    df_cleaned = clean_dataset(df_original)
    df_cleaned = validate_and_deduplicate(df_cleaned)
    
    # Generate report
    generate_report(df_original, df_cleaned)
    
    # Export
    output_path = export_cleaned_data(df_cleaned)
    
    print(f"✨ Success! Cleaned file ready: {output_path}")
    print(f"\nNext step: Import {OUTPUT_FILE} into database:")
    print(f"  psql halal_jakim < import_clean_data.sql")


if __name__ == "__main__":
    main()
