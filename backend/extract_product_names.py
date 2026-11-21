import csv
import re
from pathlib import Path

# Company identifier keywords (in order of priority)
COMPANY_SUFFIXES = [
    'SDN BHD', 'SDN. BHD', 'SDN.BHD',
    'SERHAD', 'LIMITED', 'LTD',
    'CORPORATION', 'CORP',
    'ENTERPRISE', 'ENTERPRISES',
    'TRADING', 'TRADERS',
    'HOLDINGS', 'HOLDING',
    'GROUP', 'GROUPS',
    'COMPANY', 'COMPANIES',
    'CO.', 'CO',
    'MANUFACTURING', 'MANUFAKTUR',
    'FOOD', 'FOODS',
    'RESTAURANT', 'CAFE', 'CAFE\'S',
    'KITCHEN', 'CATERING',
    'SERVICES', 'SERVICE',
    'SUPPLIES', 'SUPPLY',
    'PRODUCTION', 'PRODUCTS',
    'VENTURE', 'VENTURES',
    'INC', 'INTERNATIONAL',
    'PABRIK', 'FABRIK'
]

# Product name keywords (usually in company field if misplaced)
PRODUCT_KEYWORDS = [
    'SAUCE', 'SAUS',
    'DRINK', 'BEVERAGE', 'MINUMAN',
    'JUICE', 'COFFEE', 'TEA', 'KOPI', 'TEH',
    'SNACK', 'CRISP', 'CHIP',
    'BISCUIT', 'COOKIE', 'KUKI',
    'CHOCOLATE', 'COKLAT',
    'NOODLE', 'MINA', 'INDOMIE',
    'RICE', 'BERAS',
    'SWEET', 'CANDY', 'PERMEN',
    'BREAD', 'ROTI',
    'CAKE', 'KUEIH', 'KUE',
    'SOAP', 'SABUN',
    'SHAMPOO', 'CONDITIONER',
    'CREAM', 'LOTION', 'SKINCARE',
    'SPICE', 'SEASONING', 'REMPAH',
    'OIL', 'MINYAK',
    'BUTTER', 'MENTEGA',
    'CHICKEN', 'BEEF', 'MEAT', 'DAGING',
    'FISH', 'SEAFOOD', 'IKAN',
    'INSTANT', 'FROZEN', 'FRESH'
]

def extract_company_and_product(company_str, brand_str, category):
    """
    Intelligently extract company name and product name from mixed data.
    """
    original_company = company_str.strip()
    
    # If empty, return as is
    if not original_company:
        return original_company, ""
    
    company_upper = original_company.upper()
    
    # Check 1: If brand starts with "JENAMA:" or "BRAND:", it's already properly marked
    if brand_str and (brand_str.startswith('JENAMA:') or brand_str.startswith('BRAND:')):
        # Company is likely correct
        return original_company, ""
    
    # Check 2: If company has standard company suffix, keep it as company
    for suffix in COMPANY_SUFFIXES:
        if f' {suffix}' in company_upper or company_upper.endswith(suffix):
            return original_company, ""
    
    # Check 3: If category is "Produk" (Product), likely the company field contains product name
    if category == "Produk":
        # Check if it has product keywords
        for keyword in PRODUCT_KEYWORDS:
            if f' {keyword}' in company_upper or company_upper.startswith(keyword):
                # This is a product name, not a company
                return "", original_company
    
    # Check 4: If it's a food category, check for restaurant/food venue names
    if category in ["Premis Makanan", "Hotel & Resort", "Rumah Sembelih"]:
        # These are venue names, could be correct as company
        return original_company, ""
    
    # Check 5: Check for obvious product patterns (short, no company structure)
    # If it's very short and has product keywords
    if len(original_company) < 50:
        for keyword in PRODUCT_KEYWORDS:
            if keyword in company_upper:
                return "", original_company
    
    # Check 6: Check if it looks like a restaurant/cafe chain or food establishment
    food_indicators = ['RESTAURANT', 'CAFE', 'KITCHEN', 'FOOD', 'CATERING', 'RUMAH', 'DAPUR']
    for indicator in food_indicators:
        if indicator in company_upper:
            return original_company, ""
    
    # Default: Keep as company if not clearly a product
    return original_company, ""

print("[PROCESSING] Analyzing and extracting company vs product names...")
print()

# Read original CSV
rows = []
with open('halal_products_complete.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    rows = list(reader)

print(f"[OK] Read {len(rows):,} records")

# Process each row
extracted_companies = 0
extracted_products = 0

new_rows = []
for idx, row in enumerate(rows):
    original_company = row['company']
    original_brand = row['brand']
    category = row['category']
    
    company, product = extract_company_and_product(original_company, original_brand, category)
    
    if company != original_company or product != "":
        if product:
            extracted_products += 1
        if not company:
            extracted_companies += 1
    
    row['company'] = company
    row['product_name'] = product
    new_rows.append(row)

print(f"[+] Extracted {extracted_products:,} product names from company field")
print(f"[+] Cleared {extracted_companies:,} invalid company entries")
print()

# Write new CSV with product_name column
fieldnames = ['id', 'category', 'comp_code', 'company', 'brand', 'product_name', 'address', 'expiry']

print("[WRITING] Creating halal_products_with_names.csv...")
with open('halal_products_with_names.csv', 'w', newline='', encoding='utf-8-sig') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    
    for row in new_rows:
        writer.writerow({
            'id': row['id'],
            'category': row['category'],
            'comp_code': row['comp_code'],
            'company': row['company'],
            'brand': row['brand'],
            'product_name': row['product_name'],
            'address': row['address'],
            'expiry': row['expiry']
        })

print(f"[OK] Wrote halal_products_with_names.csv")
print()

# Show statistics
product_count = sum(1 for r in new_rows if r['product_name'])
company_count = sum(1 for r in new_rows if r['company'])
both_count = sum(1 for r in new_rows if r['company'] and r['product_name'])
neither_count = sum(1 for r in new_rows if not r['company'] and not r['product_name'])

print("[STATISTICS]")
print(f"Total records:          {len(new_rows):,}")
print(f"With company name:      {company_count:,} ({100*company_count/len(new_rows):.1f}%)")
print(f"With product name:      {product_count:,} ({100*product_count/len(new_rows):.1f}%)")
print(f"With both:              {both_count:,} ({100*both_count/len(new_rows):.1f}%)")
print(f"With neither:           {neither_count:,} ({100*neither_count/len(new_rows):.1f}%)")
print()

# Show sample extracted products
print("[SAMPLE] 20 Extracted Product Names:")
print("=" * 120)
count = 0
for row in new_rows:
    if row['product_name'] and count < 20:
        print(f"ID {row['id']:>6} | Category: {row['category']:<20} | Brand: {row['brand'][:30]:<30} | Product: {row['product_name'][:60]}")
        count += 1

print()
print("[SUCCESS] File ready: halal_products_with_names.csv")
