import requests, ssl, re
from requests.adapters import HTTPAdapter
from urllib3.util.ssl_ import create_urllib3_context

class LegacySSLAdapter(HTTPAdapter):
    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        ctx.set_ciphers('DEFAULT:@SECLEVEL=1')
        kwargs['ssl_context'] = ctx
        return super().init_poolmanager(*args, **kwargs)

session = requests.Session()
session.mount('https://', LegacySSLAdapter())
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0'}

url = 'https://myehalal.halal.gov.my/portal-halal/v1/index.php?data=ZGlyZWN0b3J5L2luZGV4X2RpcmVjdG9yeTs7Ozs='
r = session.get(url, headers=headers, timeout=15)

# Find the main data table rows
from bs4 import BeautifulSoup
soup = BeautifulSoup(r.text, 'html.parser')

# Find the data table (not the category/pagination table)
tables = soup.find_all('table')
print(f'Total tables found: {len(tables)}')
for i, t in enumerate(tables):
    rows = t.find_all('tr')
    print(f'Table {i}: {len(rows)} rows')

# Get the biggest table (likely the data table)
data_table = max(tables, key=lambda t: len(t.find_all('tr')))
rows = data_table.find_all('tr')
print(f'\n=== FIRST 3 DATA ROWS HTML ===')
for row in rows[1:4]:  # skip header
    print(row.prettify()[:600])
    print('---')



# Find the direktori form and everything inside it
dir_form = re.search(r'<form[^>]*name=["\']direktori["\'][^>]*>(.*?)</form>', r.text, re.DOTALL)
if dir_form:
    print('=== DIREKTORI FORM CONTENT ===')
    print(dir_form.group(1)[:2000])

# Find all pagination-related anchors (links with page numbers)
pag_section = re.search(r'(\[\s*1.*?Total Record.*?\])', r.text, re.DOTALL)
if pag_section:
    print('\n=== PAGINATION HTML ===')
    print(pag_section.group(1)[:1000])

# Find all onclick attributes
import re as RE
onclick_all = RE.findall(r'onclick=["\']([^"\']+)["\']', r.text)
print('\n=== ALL ONCLICK HANDLERS ===')
for o in onclick_all[:20]:
    print(o)

# Find all JavaScript functions
scripts = RE.findall(r'<script[^>]*>(.*?)</script>', r.text, RE.DOTALL)
print('\n=== JS WITH FUNCTIONS ===')
for s in scripts:
    if 'function' in s or 'direktori' in s or 'halNmb' in s or 'page' in s.lower():
        print(s[:1500])
        print('---')

