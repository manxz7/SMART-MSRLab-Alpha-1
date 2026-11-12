#!/usr/bin/env python3
"""
Extract JAKIM halal products from SQL dump and create clean CSV
"""

import csv
import re

INPUT_SQL = "halal_jakim_dump.sql"
OUTPUT_CSV = "halal_products_complete.csv"

print("[*] Extracting data from SQL dump...")
print()

# Read SQL file (try multiple encodings)
try:
    with open(INPUT_SQL, 'r', encoding='utf-8-sig') as f:
        content = f.read()
except UnicodeDecodeError:
    try:
        with open(INPUT_SQL, 'r', encoding='utf-16-le') as f:
            content = f.read()
    except UnicodeDecodeError:
        with open(INPUT_SQL, 'r', encoding='latin-1') as f:
            content = f.read()

# Find COPY section
copy_start = content.find("COPY public.halal_products")
if copy_start == -1:
    print("[ERROR] Could not find COPY statement")
    exit(1)

# Find data start (after "FROM stdin;")
data_start = content.find("FROM stdin;", copy_start) + len("FROM stdin;")
data_start = content.find("\n", data_start) + 1  # Move to next line

# Find data end (\.  marks end of COPY)
data_end = content.find("\n\\.\n", data_start)

data_section = content[data_start:data_end]

print(f"[*] Found data section: {len(data_section)} bytes")
print()

# Parse tab-separated data
rows = []
for line in data_section.split('\n'):
    if not line.strip() or line.startswith('\\'):
        continue
    
    # Split by tabs (COPY uses tabs by default)
    parts = line.split('\t')
    
    if len(parts) >= 7:  # Should have at least 7 columns (excluding created_at which we can skip)
        try:
            record = {
                'id': parts[0].strip(),
                'category': parts[1].strip(),
                'comp_code': parts[2].strip(),
                'company': parts[3].strip(),
                'brand': parts[4].strip(),
                'address': parts[5].strip(),
                'expiry': parts[6].strip()
            }
            
            # Clean up: remove null markers, normalize whitespace
            for key in record:
                if record[key] == '\\N':
                    record[key] = ''
                record[key] = record[key].replace('\n', ' ').replace('\r', '')
                record[key] = ' '.join(record[key].split())  # Normalize spaces
            
            # Only add if has company + expiry
            if record['company'] and record['expiry']:
                rows.append(record)
        
        except Exception as e:
            print(f"[!] Error parsing row: {e}")
            continue

print(f"[+] Parsed {len(rows):,} valid records")
print()

# Write clean CSV
print(f"[*] Writing to {OUTPUT_CSV}...")

with open(OUTPUT_CSV, 'w', encoding='utf-8-sig', newline='') as f:
    fieldnames = ['id', 'category', 'comp_code', 'company', 'brand', 'address', 'expiry']
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    
    writer.writeheader()
    writer.writerows(rows)

print(f"[OK] Done! Wrote {len(rows):,} records")
print()

# Summary
categories = {}
brands = {}
for row in rows:
    cat = row['category']
    brand = row['brand']
    categories[cat] = categories.get(cat, 0) + 1
    brands[brand] = brands.get(brand, 0) + 1

print("[SUMMARY]")
print(f"Total records: {len(rows):,}")
print()
print("Categories:")
for cat in sorted(categories, key=lambda x: -categories[x]):
    print(f"  {cat}: {categories[cat]:,}")
print()
print("Top 20 Brands:")
for brand in sorted(brands, key=lambda x: -brands[x])[:20]:
    print(f"  {brand}: {brands[brand]:,}")
