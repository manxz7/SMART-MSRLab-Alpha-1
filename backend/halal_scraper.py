# =============================================================================
# Portal Halal Malaysia — Full Database Scraper
# =============================================================================
# HOW IT WORKS (see INVESTIGATION_NOTES.md for full details):
#
# - The site uses POST requests (not GET) for all navigation
# - POST URL format:  ...index.php?data=BASE64...&negeri=&category=&page={N}&cari=
# - POST body:        ty={CATEGORY_CODE}
# - The `ty` field is a hidden input that tells the server which category to show
# - The site has an old SSL config, so we need a custom SSL adapter
# =============================================================================

import requests
import ssl
import re
import time
import random
import os
import csv
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.ssl_ import create_urllib3_context

# -----------------------------------------------------------------------------
# SSL FIX
# The government server uses older TLS ciphers that Python blocks by default.
# This custom adapter lowers the security level (SECLEVEL=1) so the connection works.
# -----------------------------------------------------------------------------
class LegacySSLAdapter(HTTPAdapter):
    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        ctx.set_ciphers('DEFAULT:@SECLEVEL=1')
        kwargs['ssl_context'] = ctx
        return super().init_poolmanager(*args, **kwargs)

# Build a persistent session with our SSL fix
session = requests.Session()
session.mount('https://', LegacySSLAdapter())

# Mimic a real browser — full headers like a real Chrome request
HEADERS = {
    'User-Agent':      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept':          'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.9,ms;q=0.8',
    'Accept-Encoding': 'gzip, deflate, br',
    'Referer':         'https://myehalal.halal.gov.my/portal-halal/v1/index.php',
    'Connection':      'keep-alive',
}

# Base URL — the data= param is Base64 for "directory/index_directory;;;;"
BASE_URL = (
    'https://myehalal.halal.gov.my/portal-halal/v1/index.php'
    '?data=ZGlyZWN0b3J5L2luZGV4X2RpcmVjdG9yeTs7Ozs='
    '&negeri=&category=&page={page}&cari='
)

# -----------------------------------------------------------------------------
# CATEGORY CODES
# Discovered by reading the onclick attributes of the category tab buttons.
# Each button calls: do_menu1(URL, 'XX') where XX is the category code.
# See INVESTIGATION_NOTES.md Step 6 for how we found these.
# -----------------------------------------------------------------------------
CATEGORIES = {
    'CO':  'Syarikat',
    'HO':  'Hotel & Resort',
    'PE':  'Premis Makanan',
    'PR':  'Produk',
    'KO':  'Kosmetik',
    'FM':  'Farmaseutikal',
    'BG':  'Barang Gunaan',
    'RS':  'Rumah Sembelih',
    'LO':  'Logistik',
    'MD':  'Peranti Perubatan',
    'OEM': 'OEM',
    'IN':  'International',
}

OUTPUT_FILE = 'halal_data.csv'
DELAY_MIN = 2.0   # minimum seconds between requests
DELAY_MAX = 5.0   # maximum seconds between requests
LONG_PAUSE_EVERY = 50   # take a longer break every N pages
LONG_PAUSE_MIN = 15.0   # minimum seconds for long pause
LONG_PAUSE_MAX = 30.0   # maximum seconds for long pause


def init_session():
    """Do a GET first to establish cookies — browser always does this before POST."""
    home = 'https://myehalal.halal.gov.my/portal-halal/v1/index.php'
    try:
        session.get(home, headers=HEADERS, timeout=30)
    except Exception:
        pass  # continue even if GET fails — POST might still work


def fetch_page(category_code, page_number):
    """
    Fetch one page of results for a given category.

    This simulates what the browser does when you click a page number:
    - JavaScript calls do_menu1(URL, categoryCode)
    - Which sets the hidden 'ty' field and POSTs the form
    - We replicate that exact POST request here
    """
    url = BASE_URL.format(page=page_number)
    response = session.post(
        url,
        data={'ty': category_code},
        headers=HEADERS,
        timeout=60,
    )
    response.raise_for_status()
    return response.text


def get_total_pages(html):
    """
    Extract the total number of pages from the pagination text.
    The HTML contains: "Total Record : 10894 - Page 1 From 545"
    We extract the "545" part.
    """
    match = re.search(r'Page\s+\d+\s+From\s+(\d+)', html)
    if match:
        return int(match.group(1))
    return 1


def parse_rows(html, category_name):
    """
    Parse all company rows from one page of HTML.

    The data rows look like:
      <tr class="border-b ...">
        <td>1</td>
        <td>
          <span class="company-name">COMPANY NAME</span>
          <span class="company-address">ADDRESS LINE 1<br/>LINE 2...</span>
          <span class="company-brand">BRAND</span>
        </td>
        <td class="text-center py-6">31/12/2027</td>
      </tr>

    The comp_code (certificate ID) is in the onclick attribute.
    """
    soup = BeautifulSoup(html, 'html.parser')
    rows = soup.find_all('tr', class_=lambda c: c and 'border-b' in c)

    records = []
    for row in rows:
        # Raw field reads from HTML spans
        name_tag    = row.find('span', class_='company-name')
        addr_tag    = row.find('span', class_='company-address')
        brand_tag   = row.find('span', class_='company-brand')
        product_tag = row.find('span', class_='company-product')

        raw_name    = name_tag.get_text(strip=True) if name_tag else ''
        raw_address = ', '.join(
            p.strip() for p in addr_tag.stripped_strings if p.strip()
        ) if addr_tag else ''
        raw_brand   = brand_tag.get_text(strip=True) if brand_tag else ''
        raw_product = product_tag.get_text(strip=True) if product_tag else ''

        # Strip JENAMA: / BRAND: prefix from brand at scrape time
        raw_brand = re.sub(r'^(?:JENAMA|BRAND)\s*:\s*', '', raw_brand, flags=re.IGNORECASE).strip()

        # Produk category: JAKIM portal swaps the field meanings
        #   company-name span  = product name  (not company!)
        #   company-address span = manufacturer = real company
        if category_name == 'Produk':
            company      = raw_address   # address span holds manufacturer name
            product_name = raw_product or raw_name  # prefer company-product, fallback to company-name
            address      = ''
        else:
            company      = raw_name
            product_name = raw_product
            address      = raw_address

        # Expiry date — 3rd <td>
        tds    = row.find_all('td')
        expiry = tds[2].get_text(strip=True) if len(tds) >= 3 else ''

        # Certificate code from onclick="openModal('...comp_code=COMP-xxx...')"
        onclick   = row.get('onclick', '')
        comp_match = re.search(r'comp_code=([^&\']+)', onclick)
        comp_code  = comp_match.group(1) if comp_match else ''

        # Skip completely empty rows (header rows etc.)
        if not company and not product_name:
            continue

        records.append({
            'Category':    category_name,
            'CompCode':    comp_code,
            'Company':     company,
            'Brand':       raw_brand,
            'ProductName': product_name,
            'Address':     address,
            'Expiry':      expiry,
        })

    return records


def already_scraped_pages(category_code):
    """
    Resume support: check which pages we already scraped for this category.
    We track this in a small progress file so if the script crashes,
    we can restart without re-downloading everything.
    """
    progress_file = f'progress_{category_code}.txt'
    if not os.path.exists(progress_file):
        return set()
    with open(progress_file, 'r') as f:
        return set(int(line.strip()) for line in f if line.strip().isdigit())


def mark_page_done(category_code, page_number):
    """Write the completed page number to the progress file."""
    with open(f'progress_{category_code}.txt', 'a') as f:
        f.write(f'{page_number}\n')


FIELDNAMES = ['Category', 'CompCode', 'Company', 'Brand', 'ProductName', 'Address', 'Expiry']

def init_csv():
    """Create the CSV file with headers if it doesn't exist yet."""
    if not os.path.exists(OUTPUT_FILE):
        with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
            writer.writeheader()
        print(f'Created {OUTPUT_FILE}')


def append_to_csv(records):
    """Append a batch of records to the CSV without loading the whole file."""
    with open(OUTPUT_FILE, 'a', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        writer.writerows(records)


# =============================================================================
# MAIN SCRAPING LOOP
# =============================================================================
def main():
    init_csv()
    print("Establishing session with server...")
    init_session()
    print("Session ready. Starting scrape...\n")

    for code, name in CATEGORIES.items():
        print(f'\n{"="*60}')
        print(f'Category: {name} ({code})')
        print(f'{"="*60}')

        done_pages = already_scraped_pages(code)

        # --- Get page 1 to find total pages ---
        try:
            html = fetch_page(code, 1)
        except Exception as e:
            print(f'  ERROR fetching page 1: {e}')
            continue

        total_pages = get_total_pages(html)
        print(f'  Total pages: {total_pages}')

        # --- Loop through all pages ---
        for page in range(1, total_pages + 1):

            # Skip pages we already have (resume support)
            if page in done_pages:
                print(f'  Page {page}/{total_pages} — already done, skipping')
                continue

            try:
                # Reuse the page 1 HTML we already fetched
                if page == 1:
                    page_html = html
                else:
                    page_html = fetch_page(code, page)

                records = parse_rows(page_html, name)

                if records:
                    append_to_csv(records)
                    mark_page_done(code, page)
                    print(f'  Page {page}/{total_pages} — saved {len(records)} records')
                else:
                    print(f'  Page {page}/{total_pages} — WARNING: 0 records parsed')

                # Every 50 pages take a longer break (simulate human reading)
                if page % LONG_PAUSE_EVERY == 0:
                    pause = random.uniform(LONG_PAUSE_MIN, LONG_PAUSE_MAX)
                    print(f'  [break] Resting {pause:.0f}s after {page} pages...')
                    time.sleep(pause)
                else:
                    time.sleep(random.uniform(DELAY_MIN, DELAY_MAX))

            except Exception as e:
                print(f'  Page {page}/{total_pages} — ERROR: {e}')
                pause = random.uniform(8.0, 15.0)
                print(f'  Waiting {pause:.0f}s before continuing...')
                time.sleep(pause)
                continue

    print(f'\nDone! All data saved to {OUTPUT_FILE}')


if __name__ == '__main__':
    main()
