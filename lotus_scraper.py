"""
Lotus's Malaysia Product Image Scraper
Downloads product images from Lotus's grocery/food categories for AI training
"""
import os
import re
import csv
import time
import requests
from playwright.sync_api import sync_playwright

SAVE_DIR = "lotus_images"
CSV_FILE = "lotus_products.csv"
os.makedirs(SAVE_DIR, exist_ok=True)

# Keywords in alt text that indicate non-product images
SKIP_KEYWORDS = [
    'icon', 'cart', 'chevron', 'arrow', 'cross', 'logo', 'banner',
    'badge', 'promotion', 'delivery', 'order-history', 'download',
    'menu', 'truemoney', 'rewards', 'catalogue', 'wallet',
    'next day', 'menang', 'pulangan', 'koongsi', 'ceria', 'senyum',
    'disney', 'pixar', 'disney/pixar'
]

# Food-related categories on Lotus's
CATEGORIES = [
    "snacks",
    "bakery",
    "beverages",
]


def is_product_image(alt, src):
    """Return True if this looks like a real product image"""
    if len(alt) < 5:
        return False
    if not src or not src.startswith("http"):
        return False
    # Real product names on Lotus's are ALL CAPS (e.g. "ANGEL OYSTER SAUCE 780G")
    # Non-product images have mixed case (e.g. "QrCode", "Lotus's", "Facebook")
    letters = [c for c in alt if c.isalpha()]
    if not letters or sum(1 for c in letters if c.isupper()) / len(letters) < 0.8:
        return False
    alt_lower = alt.lower()
    # Skip if alt contains non-product keywords
    for skip in SKIP_KEYWORDS:
        if skip in alt_lower:
            return False
    # Skip promotion/badge variant images
    if alt.endswith('-promotion') or alt.endswith('-badgeImage') or '-badge' in alt:
        return False
    return True


def sanitize_filename(name):
    """Make a safe filename from product name"""
    safe = re.sub(r'[^\w\s-]', '', name)
    safe = re.sub(r'\s+', '_', safe.strip())
    return safe[:80]


def download_image(url, filepath, session):
    """Download an image to filepath, return True if successful"""
    try:
        resp = session.get(url, timeout=15)
        if resp.status_code == 200 and len(resp.content) > 2000:
            with open(filepath, 'wb') as f:
                f.write(resp.content)
            return True
    except Exception:
        pass
    return False


def scroll_to_load_all(page, max_scrolls=30):
    """Scroll page repeatedly to trigger infinite scroll loading"""
    prev_count = 0
    no_change_streak = 0

    for i in range(max_scrolls):
        imgs = page.query_selector_all("img[alt]")
        products = [
            img for img in imgs
            if is_product_image(
                img.get_attribute("alt") or "",
                img.get_attribute("src") or ""
            )
        ]
        curr_count = len(products)
        print(f"  Scroll {i+1}: {curr_count} product images loaded")

        if curr_count == prev_count:
            no_change_streak += 1
            if no_change_streak >= 3:
                print(f"  No new products for 3 scrolls — done loading")
                break
        else:
            no_change_streak = 0

        prev_count = curr_count
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
        page.wait_for_timeout(2000)

    return prev_count


def scrape_category(page, category_name):
    """Scrape all product names + image URLs from a category"""
    url = f"https://www.lotuss.com.my/en/category/{category_name}"
    print(f"\n{'='*55}")
    print(f"Category: {category_name}  ({url})")

    page.goto(url, timeout=60000)
    page.wait_for_timeout(3000)

    # Scroll to load all products via infinite scroll
    scroll_to_load_all(page)

    # Extract unique product images
    imgs = page.query_selector_all("img[alt]")
    products = []
    seen_names = set()

    for img in imgs:
        alt = (img.get_attribute("alt") or "").strip()
        src = img.get_attribute("src") or img.get_attribute("data-src") or ""

        if is_product_image(alt, src) and alt not in seen_names:
            seen_names.add(alt)
            products.append({"name": alt, "url": src, "category": category_name})

    print(f"  Unique products: {len(products)}")
    return products


# ── Load already downloaded products for resume support ──────────────────────
downloaded_names = set()
if os.path.exists(CSV_FILE):
    with open(CSV_FILE, 'r', encoding='utf-8') as f:
        for row in csv.DictReader(f):
            downloaded_names.add(row['name'])
    print(f"Resume mode: {len(downloaded_names)} already downloaded")

# Open CSV for appending
csv_exists = os.path.exists(CSV_FILE)
csv_out = open(CSV_FILE, 'a', newline='', encoding='utf-8')
csv_writer = csv.DictWriter(csv_out, fieldnames=['name', 'category', 'filename', 'url'])
if not csv_exists:
    csv_writer.writeheader()

# HTTP session for image downloads
dl_session = requests.Session()
dl_session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0',
    'Referer': 'https://www.lotuss.com.my/'
})

# ── Main scraping loop ───────────────────────────────────────────────────────
total_new = 0

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page(
        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    )

    # Bootstrap: open homepage to get cookies and dismiss any popup
    print("Initializing — opening homepage...")
    page.goto("https://www.lotuss.com.my/en", timeout=30000)
    page.wait_for_timeout(2000)
    try:
        page.click("#select-delivery-method", timeout=3000)
        page.wait_for_timeout(1500)
        page.click("button:has(svg)", timeout=3000)
        page.wait_for_timeout(1000)
    except Exception:
        pass
    page.keyboard.press("Escape")
    page.wait_for_timeout(1000)
    print("Browser ready\n")

    for category in CATEGORIES:
        try:
            products = scrape_category(page, category)
        except Exception as e:
            print(f"  ERROR scraping {category}: {e}")
            continue

        for product in products:
            name = product['name']
            if name in downloaded_names:
                continue  # already have it

            filename = sanitize_filename(name) + ".jpg"
            filepath = os.path.join(SAVE_DIR, filename)

            ok = download_image(product['url'], filepath, dl_session)
            if ok:
                csv_writer.writerow({
                    'name': name,
                    'category': category,
                    'filename': filename,
                    'url': product['url']
                })
                csv_out.flush()
                downloaded_names.add(name)
                total_new += 1
                print(f"  [{total_new:4d}] ✓ {name[:60]}")
            else:
                print(f"         ✗ FAIL  {name[:60]}")

    browser.close()

csv_out.close()

print(f"\n{'='*55}")
print(f"DONE — {total_new} new images downloaded")
print(f"Total in dataset: {len(downloaded_names)}")
print(f"Images folder : {SAVE_DIR}/")
print(f"CSV file      : {CSV_FILE}")
