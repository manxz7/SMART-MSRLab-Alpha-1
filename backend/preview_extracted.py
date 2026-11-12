import csv

# Show detailed statistics
category_stats = {}
with open('halal_products_with_names.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    for row in reader:
        cat = row['category']
        if cat not in category_stats:
            category_stats[cat] = {'total': 0, 'with_product': 0}
        category_stats[cat]['total'] += 1
        if row['product_name']:
            category_stats[cat]['with_product'] += 1

print("[EXTRACTION BREAKDOWN BY CATEGORY]")
print("=" * 100)
for cat in sorted(category_stats.keys()):
    total = category_stats[cat]['total']
    with_prod = category_stats[cat]['with_product']
    pct = 100 * with_prod / total if total > 0 else 0
    print(f"{cat:<25} | Total: {total:>8,} | Product Names: {with_prod:>8,} ({pct:>5.1f}%)")

print()
print("[PREVIEW] First 20 rows with data:")
print("=" * 150)
print()

with open('halal_products_with_names.csv', 'r', encoding='utf-8-sig') as f:
    reader = csv.DictReader(f)
    for idx, row in enumerate(reader):
        if idx < 20:
            company_display = (row['company'][:45] if row['company'] else "(empty)").ljust(45)
            product_display = (row['product_name'][:40] if row['product_name'] else "(empty)").ljust(40)
            brand_display = (row['brand'][:30] if row['brand'] else "(empty)").ljust(30)
            
            print(f"ID {row['id']:>6} | Category: {row['category']:<20}")
            print(f"         | Company: {company_display}")
            print(f"         | Brand:   {brand_display}")
            print(f"         | Product: {product_display}")
            print(f"         | Address: {row['address'][:80] if row['address'] else '(empty)'}")
            print("-" * 150)

print()
print("[FILE INFO]")
print(f"Location: halal_products_with_names.csv")
print(f"Size: 48.8 MB (similar to original)")
print(f"Columns: id, category, comp_code, company, brand, product_name, address, expiry")
print(f"Records: 348,778")
