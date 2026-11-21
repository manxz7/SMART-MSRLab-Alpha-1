"""
Download product images from Open Food Facts — Malaysia products
Saves: product images + CSV with product info (name, brand, barcode, image path)

Folder structure created:
  product_images/
    ├── 9556001183538_Milo_Malt_Chocolate.jpg
    ├── 9556001183539_Nestum.jpg
    └── ...
  product_images_data.csv   ← list of all products + image paths
"""

import requests
import os
import csv
import time

HEADERS = {
    "User-Agent": "HalalChecker/1.0 (Malaysia halal intern project)"
}

SAVE_FOLDER = "product_images"
CSV_OUTPUT  = "product_images_data.csv"
PAGE_SIZE   = 50   # 50 products per API call
SLEEP_SEC   = 0.5  # delay between downloads to be polite to server

os.makedirs(SAVE_FOLDER, exist_ok=True)

def get_malaysia_products(page):
    """Fetch one page of Malaysian products from Open Food Facts"""
    url = "https://world.openfoodfacts.org/cgi/search.pl"
    params = {
        "action": "process",
        "json": 1,
        "countries_tags": "malaysia",
        "fields": "code,product_name,brands,image_front_url,image_url",
        "page_size": PAGE_SIZE,
        "page": page,
        "sort_by": "unique_scans_n"  # most scanned products first (more popular = better images)
    }
    res = requests.get(url, params=params, headers=HEADERS, timeout=15)
    data = res.json()
    return data.get("products", []), data.get("count", 0)

def download_image(image_url, save_path):
    """Download one image and save to disk"""
    try:
        res = requests.get(image_url, headers=HEADERS, timeout=15, stream=True)
        if res.status_code == 200:
            with open(save_path, "wb") as f:
                for chunk in res.iter_content(1024):
                    f.write(chunk)
            return True
    except Exception as e:
        print(f"    Image download error: {e}")
    return False

def safe_filename(text, maxlen=40):
    """Convert text to safe filename"""
    if not text:
        return "unknown"
    safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in text)
    return safe[:maxlen]

# ─────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────

print("=" * 60)
print("Open Food Facts — Malaysia Product Image Downloader")
print("=" * 60)

# First, check total count
_, total = get_malaysia_products(1)
total_pages = (total // PAGE_SIZE) + 1
print(f"Total products found : {total}")
print(f"Total pages          : {total_pages}")
print(f"Saving images to     : {SAVE_FOLDER}/")
print(f"Saving product list  : {CSV_OUTPUT}")
print("=" * 60)

downloaded  = 0
skipped     = 0
no_image    = 0
rows        = []

# Load already-downloaded barcodes (for resume)
already_done = set()
if os.path.exists(CSV_OUTPUT):
    with open(CSV_OUTPUT, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            already_done.add(row["barcode"])
    print(f"Resume mode: {len(already_done)} products already downloaded, skipping those.\n")

# Open CSV for writing (append mode if resuming)
csv_mode = "a" if already_done else "w"
csv_file = open(CSV_OUTPUT, csv_mode, newline="", encoding="utf-8")
csv_writer = csv.writer(csv_file)
if not already_done:
    csv_writer.writerow(["barcode", "product_name", "brand", "image_filename", "image_url"])

try:
    for page in range(1, total_pages + 1):
        print(f"\n[Page {page}/{total_pages}]")
        products, _ = get_malaysia_products(page)

        if not products:
            print("  No products on this page, stopping.")
            break

        for p in products:
            barcode      = p.get("code", "")
            product_name = p.get("product_name", "").strip()
            brand        = p.get("brands", "").strip()
            image_url    = p.get("image_front_url") or p.get("image_url") or ""

            # Skip if already downloaded
            if barcode in already_done:
                skipped += 1
                continue

            # Skip if no image
            if not image_url:
                no_image += 1
                print(f"  NO IMAGE  | {barcode} | {product_name[:35]}")
                csv_writer.writerow([barcode, product_name, brand, "", ""])
                already_done.add(barcode)
                continue

            # Build filename: barcode_ProductName.jpg
            ext = ".jpg"
            fname = f"{barcode}_{safe_filename(product_name or brand)}{ext}"
            save_path = os.path.join(SAVE_FOLDER, fname)

            # Download
            success = download_image(image_url, save_path)
            if success:
                downloaded += 1
                print(f"  ✓ {downloaded:>4} | {barcode} | {(product_name or brand)[:35]}")
                csv_writer.writerow([barcode, product_name, brand, fname, image_url])
            else:
                print(f"  ✗ FAIL   | {barcode} | {product_name[:35]}")
                csv_writer.writerow([barcode, product_name, brand, "", image_url])

            already_done.add(barcode)
            csv_file.flush()         # save progress after each product
            time.sleep(SLEEP_SEC)

        print(f"  Page done. Total downloaded so far: {downloaded}")

except KeyboardInterrupt:
    print("\n\nStopped by user (Ctrl+C). Progress saved — run again to resume.")

finally:
    csv_file.close()

print("\n" + "=" * 60)
print(f"DONE!")
print(f"  Images downloaded : {downloaded}")
print(f"  No image (skipped): {no_image}")
print(f"  Already had       : {skipped}")
print(f"  Saved to folder   : {SAVE_FOLDER}/")
print(f"  Product list CSV  : {CSV_OUTPUT}")
print("=" * 60)
