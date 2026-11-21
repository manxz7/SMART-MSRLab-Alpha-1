#!/usr/bin/env python3
"""
ultra_fast_fix.py - Using Python csv module (C-accelerated) for speed
"""

import csv
import sys

INPUT = "halal_data.csv"
OUTPUT = "halal_data_fixed.csv"

print("[*] Fast CSV parsing using csv module...")
print()

# Read with csv.DictReader - handles quoted fields & line breaks correctly
rows_read = 0
rows_written = 0

try:
    with open(INPUT, 'r', encoding='utf-8-sig', newline='') as infile:
        # Use csv.reader to get raw rows (handles quotes & line breaks)
        reader = csv.reader(infile)
        
        with open(OUTPUT, 'w', encoding='utf-8-sig', newline='') as outfile:
            writer = csv.writer(outfile)
            
            # Write header
            header = next(reader)
            print(f"[*] Header: {header}")
            writer.writerow(['id', 'company', 'brand', 'category', 'address', 'expiry'])
            
            record_id = 1
            
            for row in reader:
                rows_read += 1
                
                if len(row) != len(header):
                    # Skip malformed rows
                    if rows_read % 10000 == 0:
                        print(f"    Progress: {rows_read:,} rows read, {rows_written:,} written...")
                    continue
                
                if len(row) >= 3:
                    company = row[0].strip() if row[0] else ""
                    address = row[1].strip() if len(row) > 1 and row[1] else ""
                    expiry = row[2].strip() if len(row) > 2 and row[2] else ""
                    
                    if not company or not address or not expiry:
                        continue
                    
                    # Extract brand (first word)
                    brand = company.split()[0] if company.split() else "UNKNOWN"
                    
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
                    
                    # Write clean row
                    writer.writerow([record_id, company, brand, category, address, expiry])
                    rows_written += 1
                    record_id += 1
                
                # Progress indicator
                if rows_read % 50000 == 0:
                    print(f"[Progress] {rows_read:,} rows processed, {rows_written:,} valid records...")

except Exception as e:
    print(f"[ERROR] {e}")
    sys.exit(1)

print()
print(f"[DONE]")
print(f"  Rows read: {rows_read:,}")
print(f"  Rows written: {rows_written:,}")
print(f"  Output: {OUTPUT}")
