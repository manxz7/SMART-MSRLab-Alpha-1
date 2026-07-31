import csv
from collections import defaultdict

print("[DEEP DATA ANALYSIS] Checking for misaligned fields...")
print()

# Patterns for different field types
company_patterns = ['SDN', 'BHD', 'LTD', 'CO', 'ENTERPRISE', 'TRADING', 'GROUP', 'MANUFACTURING']
address_patterns = ['JALAN', 'JLN', 'JAGA', 'CEGAT', 'LORONG', 'LEBUH', 'JAMBATAN', 'LOT', 'NO.', 'BANGUNAN', 'TINGKAT', 'BLOK']
product_patterns = ['SAUCE', 'DRINK', 'JUICE', 'COFFEE', 'TEA', 'NOODLE', 'RICE', 'SNACK', 'BISCUIT', 'CAKE', 'BREAD']

# Find suspicious entries
suspicious_company_empty = []  # Company is empty but brand/product not
suspicious_address_missing = []  # Address looks like company/product
suspicious_brand_missing = []  # Brand is missing but should be there
data_scramble = []  # Data seems misaligned

with open('halal_products_with_names.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    
    for row in reader:
        company = row['company'].strip()
        brand = row['brand'].strip()
        product = row['product_name'].strip()
        address = row['address'].strip()
        category = row['category']
        
        # ISSUE 1: Company is empty but brand/product/address has company-like text
        if not company and (brand or product):
            if any(kw in brand.upper() for kw in company_patterns) if brand else False:
                suspicious_company_empty.append({
                    'id': row['id'],
                    'category': category,
                    'company': '(EMPTY)',
                    'brand': brand[:60],
                    'product': product[:60],
                    'address': address[:60]
                })
        
        # ISSUE 2: Address looks like company name (no address keywords)
        if address and not any(kw in address.upper() for kw in address_patterns):
            if any(kw in address.upper() for kw in company_patterns):
                suspicious_address_missing.append({
                    'id': row['id'],
                    'category': category,
                    'company': company[:60],
                    'address': address[:80],
                    'note': 'Address field contains company-like text'
                })
        
        # ISSUE 3: Brand is empty but company/product has brand-like text
        if not brand and category == 'Produk':
            suspicious_brand_missing.append({
                'id': row['id'],
                'category': category,
                'company': company[:60],
                'product': product[:60],
                'note': 'Produk category missing brand'
            })
        
        # ISSUE 4: Data seems scrambled across fields
        if not company and not brand and not product:
            if any(kw in address.upper() for kw in company_patterns):
                data_scramble.append({
                    'id': row['id'],
                    'category': category,
                    'address': address[:80],
                    'note': 'All name fields empty, address might have name'
                })

print("[ISSUE 1] EMPTY COMPANY - Brand field has company-like text")
print("=" * 110)
if suspicious_company_empty:
    for entry in suspicious_company_empty[:15]:
        print(f"ID {entry['id']:>6} | {entry['category']:<20} | Brand: {entry['brand'][:40]}")
        print(f"         | Product: {entry['product'][:40] if entry['product'] else '(empty)'}")
        print("-" * 110)
else:
    print("✓ None found")

print()
print("[ISSUE 2] ADDRESS FIELD LOOKS LIKE COMPANY NAME")
print("=" * 110)
if suspicious_address_missing:
    for entry in suspicious_address_missing[:15]:
        print(f"ID {entry['id']:>6} | {entry['category']:<20} | Company: {entry['company'][:40]}")
        print(f"         | Address (suspicious): {entry['address']}")
        print("-" * 110)
else:
    print("✓ None found")

print()
print("[ISSUE 3] MISSING BRAND IN 'PRODUK' CATEGORY")
print("=" * 110)
if suspicious_brand_missing:
    print(f"Total 'Produk' entries with missing brand: {len(suspicious_brand_missing):,}")
    for entry in suspicious_brand_missing[:15]:
        print(f"ID {entry['id']:>6} | Company: {entry['company'][:45]} | Product: {entry['product'][:35]}")
    print("-" * 110)
else:
    print("✓ None found")

print()
print("[ISSUE 4] ALL NAME FIELDS EMPTY - ADDRESS CONTAINS COMPANY NAME")
print("=" * 110)
if data_scramble:
    for entry in data_scramble[:10]:
        print(f"ID {entry['id']:>6} | {entry['category']:<20} | Address: {entry['address'][:80]}")
    print("-" * 110)
else:
    print("✓ None found")

# Now analyze the CSV structure - check samples from each category
print()
print("[SAMPLE ANALYSIS] 5 rows from each category to verify alignment:")
print("=" * 150)

with open('halal_products_with_names.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    
    category_samples = defaultdict(list)
    for row in reader:
        cat = row['category']
        if len(category_samples[cat]) < 5:
            category_samples[cat].append(row)
    
    for cat in sorted(category_samples.keys()):
        print(f"\n[{cat}]")
        for idx, row in enumerate(category_samples[cat], 1):
            print(f"  Row {idx}:")
            print(f"    ID:       {row['id']}")
            print(f"    Company:  {row['company'][:70] if row['company'] else '(EMPTY)'}")
            print(f"    Brand:    {row['brand'][:70] if row['brand'] else '(EMPTY)'}")
            print(f"    Product:  {row['product_name'][:70] if row['product_name'] else '(EMPTY)'}")
            print(f"    Address:  {row['address'][:70] if row['address'] else '(EMPTY)'}")
            print(f"    Expiry:   {row['expiry']}")
            print()

print("\n[SUMMARY]")
print(f"Total suspicious company-empty entries: {len(suspicious_company_empty)}")
print(f"Total suspicious address fields: {len(suspicious_address_missing)}")
print(f"Total missing brand in Produk: {len(suspicious_brand_missing)}")
print(f"Total data scramble cases: {len(data_scramble)}")
