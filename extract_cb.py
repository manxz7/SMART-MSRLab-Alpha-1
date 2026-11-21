"""
extract_cb.py — Extract JAKIM Certified Bodies dari PDF → jakim_cb.csv
Guna crop bounding box untuk asingkan kolum Organization dari kolum Contact
"""

import pdfplumber
import csv
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

PDF_PATH = "JAKIM Halal CB.pdf"
CSV_OUT  = "jakim_cb.csv"

COUNTRIES = [
    "ARGENTINA","AUSTRALIA","AUSTRIA","BANGLADESH","BOSNIA AND HERZEGOVINA",
    "BRAZIL","BRUNEI","CANADA","CHINA","CHILE","EGYPT","GERMANY",
    "INDIA","INDONESIA","IRAN","IRELAND","ITALY","JAPAN","JORDAN",
    "KAZAKHSTAN","KENYA","KINGDOM OF SAUDI ARABIA","LITHUANIA","MALDIVES",
    "MONGOLIA","MOROCCO","NETHERLANDS","NEW ZEALAND","PAKISTAN",
    "PHILIPPINES","POLAND","PORTUGAL","RUSSIA","SINGAPORE","SOUTH AFRICA",
    "SOUTH KOREA","SPAIN","SRI LANKA","SWITZERLAND","TAIWAN","THAILAND",
    "TUNISIA","TURKEY","UKRAINE","UNITED KINGDOM","UNITED STATES OF AMERICA",
    "URUGUAY","VIETNAM"
]

COUNTRY_DISPLAY = {
    "KINGDOM OF SAUDI ARABIA": "Saudi Arabia",
    "UNITED STATES OF AMERICA": "USA",
    "UNITED KINGDOM": "United Kingdom",
    "BOSNIA AND HERZEGOVINA": "Bosnia and Herzegovina",
}

def extract_abbr(name):
    match = re.search(r'\(([A-Z]{2,12})\)', name)
    return match.group(1) if match else ""

def extract_emails(text):
    emails = re.findall(r'[\w\.\-]+@[\w\.\-]+\.[a-zA-Z]{2,}', text)
    return ", ".join(sorted(set(emails))) if emails else ""

def extract_website(text):
    match = re.search(r'(?:https?://|www\.)[^\s\n]+', text)
    return match.group(0).rstrip('.,)') if match else ""

def extract_phone(text):
    match = re.search(r'(?:Tel|Phone|T)[\s:]*([+\d\s\-\(\)\/]{7,30})', text)
    return match.group(1).strip().split('\n')[0] if match else ""

def clean_org_name(raw):
    """
    Ambil baris-baris yang termasuk nama org sahaja.
    Stop bila jumpa: address (nombor/jalan), contact title, Tel, Email, dll.
    """
    stop_patterns = [
        r'^(Mr|Mrs|Ms|Mdm|Dr|Sheikh|Prof|Hj|Hajj|Assoc)\b',
        r'^(Tel|Fax|Mobile|Email|Website|Phone|T:)',
        r'^\d+[\s,]',  # address starting with number
        r'^(Level|Floor|Unit|Suite|Block|Lot|No\.\s+\d)',
        r'@',           # email address
        r'^https?://',
        r'^www\.',
        r'^\+\d',       # phone starting with +
    ]
    lines = [l.strip() for l in raw.strip().split('\n') if l.strip()]
    org_lines = []
    for line in lines:
        should_stop = any(re.match(p, line, re.IGNORECASE) for p in stop_patterns)
        if should_stop:
            break
        org_lines.append(line)
    return ' '.join(org_lines).strip() if org_lines else lines[0] if lines else ""

def parse_pdf():
    records = []
    current_country = ""
    entry_id = 0

    print("Extracting PDF...\n")

    with pdfplumber.open(PDF_PATH) as pdf:
        page_width  = pdf.pages[3].width   # ~595
        page_height = pdf.pages[3].height  # ~841

        # Kolum Organisation & Address = kiri (~40% lebar)
        # Kolum Contact = tengah (~30%)
        # Logo = kanan (~30%)
        org_x1 = page_width * 0.42

        for page_num, page in enumerate(pdf.pages):
            if page_num < 3:   # skip cover & TOC
                continue
            if page_num >= 64: # skip appendix & index
                continue

            # Extract full text untuk detect country & email/website
            full_text = page.extract_text(x_tolerance=3, y_tolerance=3) or ""

            # Extract HANYA kolum Organization (crop kiri)
            org_region = page.crop((0, 0, org_x1, page_height))
            org_text   = org_region.extract_text(x_tolerance=3, y_tolerance=3) or ""

            # Detect country
            for country in COUNTRIES:
                if country in full_text.upper()[:200]:
                    raw_name = country.title()
                    current_country = COUNTRY_DISPLAY.get(country, raw_name)
                    break

            # Split ikut entry number dalam org column
            entry_blocks = re.split(r'\n(?=\d+\.)', org_text)

            for block in entry_blocks:
                block = block.strip()
                if not block:
                    continue

                num_match = re.match(r'^(\d+)\.\s*(.+)', block, re.DOTALL)
                if not num_match:
                    continue

                content = num_match.group(2)

                # Buang header baris
                content = re.sub(r'As at 30th January.*?\n', '', content)
                content = re.sub(r'Page \d+ of 68', '', content)
                content = re.sub(r'No\.?\s+Organization.*?\n', '', content)

                org_name = clean_org_name(content)
                if not org_name or len(org_name) < 4:
                    continue

                abbreviation = extract_abbr(org_name)
                email        = extract_emails(full_text)  # dari full page text
                website      = extract_website(full_text)
                phone        = extract_phone(full_text)

                entry_id += 1
                records.append({
                    'id':                entry_id,
                    'country':           current_country,
                    'organization_name': org_name,
                    'abbreviation':      abbreviation,
                    'email':             email,
                    'website':           website,
                    'phone':             phone,
                })
                print(f"  [{entry_id:3d}] {current_country:<25} {org_name[:55]}")

    return records

def save_csv(records):
    fieldnames = ['id','country','organization_name','abbreviation','email','website','phone']
    with open(CSV_OUT, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(records)
    print(f"\n✓ Saved {len(records)} records → {CSV_OUT}")

if __name__ == "__main__":
    records = parse_pdf()
    save_csv(records)
