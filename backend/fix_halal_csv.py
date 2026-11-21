#!/usr/bin/env python3
"""
fix_halal_csv.py — Fix malformed CSV from JAKIM crawling
Handles: unquoted line breaks in Company field
Result: Clean CSV dengan Company, Address, Expiry (+ derived fields)
"""

import re
import os

INPUT_FILE = "halal_data.csv"
OUTPUT_FILE = "halal_data_fixed.csv"

print("[*] Fixing malformed CSV with unquoted line breaks...")
print()

# Read entire file as raw text
with open(INPUT_FILE, 'r', encoding='utf-8-sig') as f:
    content = f.read()

# Pattern to detect and fix CSV structure:
# Company field: ends with ) or BHD or similar before newline
# Address field: starts with " (quoted), contains newlines, ends with ",number
# We need to reconstruct so Company is properly quoted if it has newlines

lines = content.split('\n')

# Track which lines belong together
reconstructed_rows = []
current_row = []
in_quoted_field = False
buffer = ""

for i, line in enumerate(lines):
    # Count quotes to track if we're inside quoted field
    quote_count = line.count('"')
    
    # Check if this is part of a quoted field (address)
    if '"' in line:
        in_quoted_field = (quote_count % 2 == 1)
    
    # If we're in a quoted field or buffer has content, accumulate
    if in_quoted_field or buffer:
        buffer += line + '\n' if buffer else line
        
        # Check if quoted field ends on this line
        if in_quoted_field and '"' in line and line.count('"') % 2 == 1:
            in_quoted_field = False
    else:
        # Not in quoted field - check if this line is complete row
        parts = line.split(',')
        
        # A complete line should have 3 parts (Company, Address, Expiry)
        # But if Company has line break, it won't
        # Address should be quoted
        if len(parts) >= 2 and '"' in line:
            # Likely complete row
            if buffer:
                buffer += '\n' + line
                reconstructed_rows.append(buffer)
                buffer = ""
            else:
                current_row.append(line)
                reconstructed_rows.append(line)
        else:
            # Incomplete - accumulate
            if buffer:
                buffer += '\n' + line
            else:
                buffer = line

if buffer:
    reconstructed_rows.append(buffer)

print(f"[*] Reconstructed {len(reconstructed_rows)} rows")
print()

# Now parse properly
parsed_rows = []
header_row = None

for idx, raw_row in enumerate(reconstructed_rows):
    if idx == 0:
        header_row = raw_row.strip()
        continue
    
    # Extract Company, Address, Expiry
    # Pattern: Company (unquoted, may have newlines), 
    #          Address (quoted, may have newlines), 
    #          Expiry (number)
    
    # Strategy: find pattern of ","quoted_content", then extract based on that
    
    # Find address field (starts with quote, ends with quote+comma+number)
    match = re.search(r'"([^"]+(?:\n[^"]+)*)"\s*,\s*(\d+)\s*$', raw_row, re.DOTALL)
    
    if match:
        address = match.group(1)
        expiry = match.group(2)
        # Everything before address field is company
        company_end = raw_row.find('"')
        company = raw_row[:company_end].strip()
        
        # Clean up company (remove line breaks, normalize spaces)
        company = re.sub(r'\s+', ' ', company.replace('\n', ' '))
        
        # Clean up address (normalize line breaks)
        address = address.replace('\n', ' ')
        address = re.sub(r'\s+', ' ', address)
        
        parsed_rows.append({
            'company': company,
            'address': address,
            'expiry': expiry.strip()
        })

print(f"[+] Parsed {len(parsed_rows)} valid data rows")
print()

if len(parsed_rows) == 0:
    print("[!] No rows parsed! File structure might be different.")
    print("[*] Showing first 5 raw reconstructed rows:")
    for i, row in enumerate(reconstructed_rows[:5]):
        print(f"\n--- Row {i} ---")
        print(row[:200])
    exit(1)

# Show sample
print("[*] Sample parsed data (first 3 rows):")
for i, row in enumerate(parsed_rows[:3]):
    print()
    print(f"Row {i+1}:")
    print(f"  Company: {row['company'][:60]}...")
    print(f"  Address: {row['address'][:60]}...")
    print(f"  Expiry:  {row['expiry']}")

print()
print("[*] Deriving additional fields from Company name...")
print()

# Derive: Brand, Category (from company name patterns)
def extract_brand(company):
    """Extract brand from company name."""
    # Remove manufacturer info in parentheses
    clean = re.sub(r'\s*\([^)]*\)', '', company)
    
    # Take first meaningful word(s)
    words = clean.split()
    if words:
        brand = words[0]
        if len(brand) > 2:
            return brand.upper()
    return company.split()[0] if company.split() else "UNKNOWN"

def derive_category(company):
    """Guess category from company name."""
    company_lower = company.lower()
    
    if any(w in company_lower for w in ['food', 'makanan', 'snack', 'cereal', 'biscuit']):
        return 'Makanan'
    elif any(w in company_lower for w in ['drink', 'minuman', 'beverage', 'juice', 'water']):
        return 'Minuman'
    elif any(w in company_lower for w in ['medicine', 'ubat', 'pharmaceutical']):
        return 'Ubat-ubatan'
    elif any(w in company_lower for w in ['cosmetic', 'kosmetik', 'skincare', 'beauty']):
        return 'Kosmetik'
    elif any(w in company_lower for w in ['restaurant', 'bakery', 'cafe']):
        return 'Makanan'
    else:
        return 'Lain-lain'

# Enrich data
for row in parsed_rows:
    row['brand'] = extract_brand(row['company'])
    row['category'] = derive_category(row['company'])

# Write fixed CSV
print(f"[*] Writing cleaned CSV to {OUTPUT_FILE}...")

with open(OUTPUT_FILE, 'w', encoding='utf-8-sig', newline='') as f:
    f.write('id,company,brand,category,address,expiry\n')
    
    for idx, row in enumerate(parsed_rows, 1):
        # Escape quotes in company/address
        company_esc = row['company'].replace('"', '""')
        address_esc = row['address'].replace('"', '""')
        
        f.write(f'{idx},'
                f'"{company_esc}",'
                f'"{row["brand"]}",'
                f'"{row["category"]}",'
                f'"{address_esc}",'
                f'{row["expiry"]}\n')

print(f"[+] SUCCESS! Wrote {len(parsed_rows)} clean records to {OUTPUT_FILE}")
print()
print(f"[*] File size: {os.path.getsize(OUTPUT_FILE) / (1024*1024):.2f} MB")
print()

# Summary
categories = {}
brands = {}
for row in parsed_rows:
    categories[row['category']] = categories.get(row['category'], 0) + 1
    brands[row['brand']] = brands.get(row['brand'], 0) + 1

print("[SUMMARY]")
print(f"Total records: {len(parsed_rows)}")
print()
print("Categories:")
for cat, count in sorted(categories.items(), key=lambda x: -x[1]):
    print(f"  {cat}: {count}")
print()
print("Top 10 Brands:")
for brand, count in sorted(brands.items(), key=lambda x: -x[1])[:10]:
    print(f"  {brand}: {count}")
print()
print(f"[OK] Next: Import to database using PostgreSQL COPY command")
print(f"     psql -d halal_jakim -c \"COPY halal_products FROM '{OUTPUT_FILE}' CSV HEADER;\"")
