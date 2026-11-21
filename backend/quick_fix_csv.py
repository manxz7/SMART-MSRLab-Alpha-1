#!/usr/bin/env python3
"""
quick_fix_csv.py - Fast CSV repair for JAKIM data
Strategy: Use simple string replacement to quote company field with line breaks
"""

import re

INPUT = "halal_data.csv"
OUTPUT = "halal_data_fixed.csv"

print("[*] Reading file...")
with open(INPUT, 'r', encoding='utf-8-sig') as f:
    content = f.read()

print(f"[*] File size: {len(content) / (1024*1024):.2f} MB")

# Strategy: Find pattern of:
# COMPANY_NAME (with possible \n in middle)
# ,"ADDRESS..."
# ,EXPIRY\n

# Split by lines that start with quoted address (safety check)
lines = content.split('\n')
print(f"[*] Total lines: {len(lines)}")

# Reconstruct: if line has unmatched quotes/format, join with previous
fixed_lines = []
buffer = ""

for i, line in enumerate(lines):
    if i == 0:
        # Header
        fixed_lines.append(line)
        continue
    
    # Count commas and quotes
    commas = line.count(',')
    quotes = line.count('"')
    
    if buffer:
        # We're accumulating a broken row
        buffer += '\n' + line
        
        # Check if this completes the row (address field ends with ",DIGIT)
        if re.search(r'",\s*\d+\s*$', line):
            # Row is complete
            fixed_lines.append(buffer)
            buffer = ""
    else:
        # Check if line is valid (should start with company, have quotes for address, end with number)
        # Valid format: COMPANY,"ADDRESS",EXPIRY
        
        # If line doesn't have proper structure, might be split
        if not ('"' in line and re.search(r'",\s*\d+\s*$', line)):
            # Incomplete - start accumulating
            buffer = line
        else:
            # Complete row
            fixed_lines.append(line)

if buffer:
    fixed_lines.append(buffer)

print(f"[+] Reconstructed to {len(fixed_lines)} rows (including header)")

# Now clean up: fix line breaks in company field
cleaned_lines = []
for i, line in enumerate(fixed_lines):
    if i == 0:
        cleaned_lines.append(line)
        continue
    
    # Pattern: COMPANY_TEXT,"ADDRESS_TEXT",EXPIRY
    # We need to find where address starts (the quote before address)
    
    # Find the last quote followed by comma and digit
    match = re.search(r'",\s*(\d+)\s*$', line)
    if match:
        expiry = match.group(1)
        # Everything before the address quote
        before_address = line[:match.start()]
        
        # Find the opening quote of address (should be preceded by comma or nothing special)
        # The address starts after some comma. Let's find from the right.
        # Split on last " before ",DIGIT pattern
        
        # Actually simpler: last 2 chars before address are: ," or just "
        # So everything before '," ... ",' is company
        # Extract address content (between quotes)
        
        address_content = line[match.start()+2:-len(str(expiry))-2]  # between ," and ",NUM
        
        # Entire line structure:
        # ...company_stuff...,"address_stuff",NUM
        # So before address: find the final quote before the address quote
        
        # Let me use a different approach: extract company, address, expiry with regex
        m = re.match(r'^([^"]*)"([^"]+(?:\n[^"]+)*)"\s*,\s*(\d+)\s*$', line, re.DOTALL)
        if m:
            company = m.group(1).strip()
            address = m.group(2)
            expiry = m.group(3)
            
            # Normalize line breaks in fields
            company = re.sub(r'\s+', ' ', company)  # Replace newlines & multiple spaces
            address = re.sub(r'\s+', ' ', address)
            
            # Reconstruct line properly quoted
            company_clean = company.replace('"', '""')  # Escape quotes
            address_clean = address.replace('"', '""')
            
            new_line = f'"{company_clean}","{address_clean}",{expiry}'
            cleaned_lines.append(new_line)
        else:
            # Fallback: just clean line breaks
            cleaned_lines.append(line)

print(f"[+] Cleaned {len(cleaned_lines)-1} data rows")

# Extract additional fields from company names
results = []
for i, line in enumerate(cleaned_lines[1:], 1):  # Skip header
    parts = re.match(r'"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)', line)
    if not parts:
        continue
    
    company = parts.group(1)
    address = parts.group(2)
    expiry = parts.group(3)
    
    # Extract brand (first word before SDN/BHD)
    brand_match = re.match(r'(\w+)', company)
    brand = brand_match.group(1) if brand_match else "UNKNOWN"
    
    # Derive category
    company_lower = company.lower()
    if any(x in company_lower for x in ['food', 'makanan', 'cereal', 'biscuit']):
        category = 'Makanan'
    elif any(x in company_lower for x in ['drink', 'minuman', 'beverage']):
        category = 'Minuman'
    elif any(x in company_lower for x in ['pharmaceutical', 'ubat']):
        category = 'Ubat-ubatan'
    else:
        category = 'Lain-lain'
    
    results.append({
        'id': i,
        'company': company,
        'brand': brand,
        'category': category,
        'address': address,
        'expiry': expiry
    })

print(f"[+] Extracted {len(results)} complete records")

# Write output
print(f"[*] Writing to {OUTPUT}...")
with open(OUTPUT, 'w', encoding='utf-8-sig', newline='') as f:
    f.write('id,company,brand,category,address,expiry\n')
    for r in results:
        f.write(f"{r['id']},\"{r['company']}\",\"{r['brand']}\",\"{r['category']}\",\"{r['address']}\",{r['expiry']}\n")

print(f"[OK] Done! Wrote {len(results)} records to {OUTPUT}")
print()

# Summary
brands = {}
categories = {}
for r in results:
    brands[r['brand']] = brands.get(r['brand'], 0) + 1
    categories[r['category']] = categories.get(r['category'], 0) + 1

print("[SUMMARY]")
print(f"Total records: {len(results)}")
print(f"\nTop brands:")
for brand in sorted(brands, key=lambda x: -brands[x])[:10]:
    print(f"  {brand}: {brands[brand]}")
print(f"\nCategories:")
for cat in categories:
    print(f"  {cat}: {categories[cat]}")
