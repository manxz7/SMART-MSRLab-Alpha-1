import csv
from collections import defaultdict

# Analyze company patterns
company_keywords = [
    'SDN BHD', 'SDN. BHD', 'SDN.BHD',
    'BHD', 'ENTERPRISE', 'TRADING', 'CO',
    'CORPORATION', 'COMPANY', 'GROUP', 'HOLDINGS',
    'LIMITED', 'LTD', 'PTE', 'SERHAD',
    'MANUFAKTUR', 'FABRIK', 'PABRIK'
]

company_patterns = defaultdict(int)
suspicious_entries = []

print("[ANALYZING] Reading CSV and identifying company vs product names...")
print()

with open('halal_products_complete.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    
    total = 0
    has_company_keyword = 0
    no_company_keyword = 0
    
    for idx, row in enumerate(reader):
        total += 1
        company = row['company'].upper()
        
        # Check if company contains typical company endings
        found_keyword = False
        for keyword in company_keywords:
            if keyword in company:
                has_company_keyword += 1
                found_keyword = True
                # Extract pattern
                for kw in company_keywords:
                    if kw in company:
                        company_patterns[kw] += 1
                break
        
        if not found_keyword:
            no_company_keyword += 1
            # Store suspicious entries
            if len(suspicious_entries) < 15:
                suspicious_entries.append({
                    'id': row['id'],
                    'company': row['company'][:100],
                    'brand': row['brand'][:50],
                    'category': row['category']
                })

print(f"[STATS] Total records analyzed: {total:,}")
print(f"[OK]   With company keywords: {has_company_keyword:,} ({100*has_company_keyword/total:.1f}%)")
print(f"[⚠️]   WITHOUT company keywords: {no_company_keyword:,} ({100*no_company_keyword/total:.1f}%)")
print()
print("[PATTERNS FOUND]")
for keyword, count in sorted(company_patterns.items(), key=lambda x: x[1], reverse=True)[:15]:
    print(f"  {keyword}: {count:,} ({100*count/total:.1f}%)")

print()
print("[SUSPICIOUS ENTRIES - Possible Product Names in Company Field]")
print("=" * 120)
for entry in suspicious_entries:
    print(f"ID {entry['id']:>6} | Category: {entry['category']:<20} | Company: {entry['company'][:80]}")
    if entry['brand']:
        print(f"{'':>8} | Brand: {entry['brand'][:80]}")
    print("-" * 120)
