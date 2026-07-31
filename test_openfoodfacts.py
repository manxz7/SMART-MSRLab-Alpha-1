import requests
import time

# Open Food Facts REQUIRES a User-Agent header — without it you get 403 Forbidden
HEADERS = {
    "User-Agent": "HalalChecker/1.0 (Malaysia halal intern project; hilman@example.com)"
}

def cari_barcode(nama_produk):
    url = "https://world.openfoodfacts.org/cgi/search.pl"
    params = {
        "search_terms": nama_produk,
        "search_simple": 1,
        "action": "process",
        "json": 1,
        "countries_tags": "malaysia",
        "fields": "code,product_name,brands,countries_tags",
        "page_size": 5
    }
    res = requests.get(url, params=params, headers=HEADERS, timeout=15)
    data = res.json()
    return data.get("products", [])

def cari_barcode_terus(barcode):
    """Lookup terus guna barcode number"""
    url = f"https://world.openfoodfacts.org/api/v2/product/{barcode}.json"
    res = requests.get(url, headers=HEADERS, timeout=15)
    data = res.json()
    if data.get("status") == 1:
        p = data["product"]
        return {
            "barcode": barcode,
            "product_name": p.get("product_name", "N/A"),
            "brands": p.get("brands", "N/A"),
            "countries": p.get("countries_tags", []),
            "image_url": p.get("image_url", "N/A")
        }
    return None

print("=" * 60)
print("TEST 1: Search by product name (Malaysia filter)")
print("=" * 60)

for nama in ["Milo", "Gardenia", "Maggi", "Nestle", "100Plus"]:
    print(f"\n--- {nama} ---")
    try:
        products = cari_barcode(nama)
        print(f"  Jumpa: {len(products)} produk")
        for p in products[:3]:
            print(f"  Barcode: {p.get('code','N/A')} | Name: {p.get('product_name','N/A')[:40]} | Brand: {p.get('brands','N/A')[:20]}")
        time.sleep(1)  # jangan spam API
    except Exception as e:
        print(f"  ERROR: {e}")

print("\n" + "=" * 60)
print("TEST 2: Lookup terus dengan barcode (contoh Milo Malaysia)")
print("=" * 60)

# Barcode Milo 3-in-1 Malaysia
test_barcodes = [
    "9556001183538",  # Milo 3in1
    "9556001100047",  # Milo
    "9555023010037",  # Gardenia
]

for bc in test_barcodes:
    try:
        result = cari_barcode_terus(bc)
        if result:
            print(f"\nBarcode {bc}:")
            print(f"  Nama: {result['product_name']}")
            print(f"  Brand: {result['brands']}")
            print(f"  Countries: {result['countries']}")
        else:
            print(f"\nBarcode {bc}: Tidak jumpa dalam database")
        time.sleep(1)
    except Exception as e:
        print(f"\nBarcode {bc}: ERROR - {e}")

print("\n" + "=" * 60)
print("KESIMPULAN: Boleh tengok result di atas!")
print("=" * 60)
