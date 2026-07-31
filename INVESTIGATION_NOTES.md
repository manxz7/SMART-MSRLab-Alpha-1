# How I Investigated the Portal Halal Malaysia Website
## Learning Notes — Web Scraping Investigation Process

---

## PROJECT BIG PICTURE — What We Are Building

**Goal:** A mobile/web app where a user points their camera at a product in a supermarket and instantly sees:
- Is this product **Halal certified**? (from JAKIM data)
- **When does the Halal certificate expire?**
- **Company/brand info** — who makes it, where are they from
- Any other details scraped from Portal Halal JAKIM

---

### Full System Workflow (End-to-End)

```
[User points camera at product]
        ↓
[App captures image]
        ↓
[AI reads the product — via barcode scan OR text recognition (OCR)]
        ↓
[App sends product name/barcode to our Backend API]
        ↓
[Backend searches our Halal Database]
        ↓
[Result returned to app — Halal ✓ / Not found / Expired]
        ↓
[User sees the result on screen]
```

---

### The 5 Phases — Step by Step

#### PHASE 1 — Data Collection ✅ COMPLETED
**What:** Scrape all halal-certified products, companies, and brands from Portal Halal JAKIM  
**Tool:** Python (`halal_scraper.py`)  
**Output:** `halal_data.csv` — 348,844 records across 11 JAKIM categories  
**Status:** Complete

```
Portal Halal JAKIM website
        ↓  (halal_scraper.py makes POST requests)
halal_data.csv  (raw scraped data)
```

---

#### PHASE 2 — Database Setup ✅ COMPLETED
**What:** Load the CSV data into a proper database so it can be searched quickly  
**Why not just use CSV?** A CSV is just a text file — you can't efficiently search 300,000+ records in it. A database is built for fast lookups.  
**Tool used:** PostgreSQL 18 — database `halal_jakim`, table `halal_products`  
**Output:** 348,844 rows loaded, full-text search index created

```
halal_data.csv
        ↓  (import/load script)
Database (e.g. SQLite / PostgreSQL)
        ↓
Can now answer: "Is 'Milo' halal?" in milliseconds
```

---

#### PHASE 3 — Backend API ✅ COMPLETED
**What:** A Python FastAPI server that the app talks to. Receives search queries, searches the database, returns JSON results.  
**Tool used:** FastAPI + Uvicorn  
**File:** `api.py`  
**Endpoints built:** `/health`, `/search?q=`, `/company/{code}`, `/categories`  
**Run command:** `uvicorn api:app --reload`  
**Test page:** `http://localhost:8000/docs`

---

#### PHASE 4 — AI / Product Recognition
**What:** Given a photo of a product, figure out WHAT product it is  
**Two approaches (we'll decide later which fits best):**

| Method | How it works | Best for |
|---|---|---|
| **Barcode scanning** | Read the barcode number from the image → look up in database | Products with visible barcodes |
| **OCR (Text recognition)** | Read brand/product name text from the image | When barcode is hidden or not available |
| **Image recognition (AI)** | Train a model to recognize products visually | Advanced — used by big apps like Google Lens |

**Tools we will likely use:** Google ML Kit (for mobile), or an AI API like Google Vision / OpenAI Vision

```
[Photo of product]
        ↓  (AI model / OCR / barcode reader)
Product name or barcode number (text)
        ↓
Feed into database search
```

---

#### PHASE 5 — Frontend App ✅ COMPLETED (Phase A — Text Search)
**What:** The actual app the user interacts with — search screen, results list, detail screen.  
**Tool used:** Flutter (Dart) — runs on Android phone and Windows desktop  
**Folder:** `C:\Users\hilmanxz\Documents\Projek Intern\halal_app\`  
**Main file:** `halal_app\lib\main.dart`  
**Screens built:** Search screen + Detail screen  
**Run command:** `cd "C:\Users\hilmanxz\Documents\Projek Intern\halal_app"` then `flutter run`

```
[Search screen]  →  type "Milo"  →  [Results list]  →  tap card  →  [Detail screen]
                                          HALAL ✓                      HALAL CERTIFIED
                                          Nestle Sdn Bhd               Company: Nestle...
                                          Produk | Exp: 31/12/2026      Expiry: 31/12/2026
                                                                        Address: PJ...
```

**Phase B (Next):** Add camera scanning — barcode + OCR

---

### Where Each File Fits

| File | Phase | Status | Purpose |
|---|---|---|---|
| `halal_scraper.py` | Phase 1 | ✅ Done | Scrapes Portal Halal JAKIM and saves to CSV |
| `halal_data.csv` | Phase 1 → 2 | ✅ Done | Raw scraped data — 348,844 rows |
| `progress_*.txt` | Phase 1 | ✅ Done | Tracks scraping progress per category |
| `db_setup.sql` | Phase 2 | ✅ Done | Creates the PostgreSQL database and table |
| `import_to_db.py` | Phase 2 | ✅ Done | Loads CSV into the `halal_products` table |
| `api.py` | Phase 3 | ✅ Done | FastAPI backend server with 4 endpoints |
| `INVESTIGATION_NOTES.md` | All | Always updated | Learning notes — documents how everything works |
| `halal_app/lib/main.dart` | Phase 5 | ✅ Done | Flutter app — search + detail screens |

---

### Right Now — Focus on Phase 5B (Camera Scanning)

All phases are complete for the text-search version! The full system is working end-to-end:

```
Portal Halal JAKIM  →  halal_data.csv  →  PostgreSQL  →  api.py  →  halal_app (Flutter)
```

The **next improvement** is to add camera scanning to the app:
- **Phase 5B:** Barcode scan — point camera at barcode → auto-search
- **Phase 5C:** OCR scan — point camera at product text → auto-search
- **Phase 5D:** AI/ML — smarter product recognition

---

## IMPORTANT — Development vs Production

### Current Setup (Development / Testing Only)

Right now, the system looks like this:

```
Phone (on WiFi)
    ↓  http://192.168.1.10:8000
Your computer (running uvicorn api:app)
    ↓
PostgreSQL (on your computer)
```

**This only works when:**
- Your phone and computer are on the SAME WiFi network
- Your computer is on and the API is running
- You are at home/office

**This is NOT suitable for a national app.** This is purely for **testing and development**.

---

### Real Production Setup (What We Need for National Use)

For a real app that anyone in Malaysia can use, the API must run on a **cloud server** — a computer on the internet that is always on.

```
User's phone (anywhere in Malaysia, any network)
    ↓  https://api.halalchecker.com.my/search?q=milo
Cloud Server (always on, public internet IP)
    ↓
PostgreSQL database (on the cloud server or cloud database service)
```

**Cloud options (cheapest to most expensive):**

| Option | Cost | Good for |
|---|---|---|
| **Railway.app** | Free tier available | Quick deployment, easy setup |
| **Render.com** | Free tier available | Similar to Railway, good for FastAPI |
| **DigitalOcean** | ~$6/month | More control, reliable |
| **AWS / Google Cloud** | Pay per use | Enterprise scale, used by big apps |
| **Supabase** | Free tier for database | Managed PostgreSQL in the cloud |

**For this project (intern/prototype), Railway or Render is the best starting point** — free, easy to deploy, and handles everything automatically.

---

### What Changes When We Go to Production?

| What | Development | Production |
|---|---|---|
| API URL in app | `http://192.168.1.10:8000` | `https://api.yourdomain.com` |
| API runs on | Your laptop | Cloud server (always on) |
| Database | Your laptop | Cloud database (Supabase/RDS) |
| SSL/HTTPS | No (plain HTTP) | Yes (required for real apps) |
| Cost | Free | ~$0–$20/month depending on plan |

---

### Development Phases Summary

| Stage | What it is | Status |
|---|---|---|
| **Local Development** | Build and test on your own machine | ✅ Done — working now |
| **Cloud Deployment** | Put the API on a public server | Phase 6 (future) |
| **Domain + HTTPS** | Get a real URL like halalchecker.com.my | Phase 6 (future) |
| **App Store Release** | Publish to Google Play / Apple App Store | Final phase |

---

### So When You Search Now — Is It Real?

Yes! The data is 100% real JAKIM data (348,844 records). The **only limitation** is that you must be on the same WiFi. The search results, the halal status, the company info — all real. Once we deploy to the cloud, it works exactly the same way but from anywhere.

---


---

## UNDERSTANDING SERVERS, PORTS & .VENV

### What is a "Server"?

A **server** is just a program that **sits and waits** for someone to ask it something, then replies.
It is NOT special hardware. Your laptop is currently running two servers at once.

---

### The 2 Servers Running on Your Laptop

**Server 1 — PostgreSQL (Database Server)**
- Starts automatically when Windows boots (runs as a Windows Service)
- Listens on **port 5432**
- Job: store and retrieve data from the `halal_products` table
- Who talks to it: only your Python code (`api.py`)

**Server 2 — Uvicorn (API Server)**
- You start it manually: `uvicorn api:app --host 0.0.0.0 --port 8000`
- Listens on **port 8000**
- Job: receive search requests from the app, ask PostgreSQL, send results back
- Who talks to it: your Flutter app (the phone)

---

### How They All Connect

```
[Phone App]
    ↓  WiFi, port 8000
[Uvicorn — api.py]        ← you start this manually
    ↓  localhost, port 5432
[PostgreSQL]              ← Windows starts this automatically
    ↓
[halal_products table]    ← 348,844 rows of JAKIM data
```

The phone **never** talks to PostgreSQL directly. It only talks to Uvicorn (the API). Uvicorn talks to PostgreSQL on its behalf. This is the correct and secure pattern.

---

### What is `.venv`?

`.venv` is **NOT a server**. It is a **toolbox folder** that contains a private copy of Python and all packages installed for this project.

```
.venv/
├── Scripts/python.exe     ← Python interpreter
├── Scripts/uvicorn.exe    ← the uvicorn start command
├── Lib/fastapi/           ← FastAPI package
├── Lib/psycopg2/          ← PostgreSQL connection driver
└── Lib/requests/          ← HTTP requests package (used in scraper)
```

- Without activating `.venv` → Python doesn't have FastAPI → `uvicorn` command not found
- With `.venv` activated → all tools available → everything works

---

### What is a "Port"?

A port is like a **door number** on your computer. Your computer has thousands of doors.

| Port | Used by | Notes |
|---|---|---|
| `5432` | PostgreSQL | Back door — only local Python talks to it |
| `8000` | Uvicorn/API | Front door — phone connects here over WiFi |
| `80` | Normal websites (HTTP) | Standard web port |
| `443` | Secure websites (HTTPS) | Used in production/cloud |

When the app connects to `http://192.168.1.10:8000` — it means: "go to computer `192.168.1.10`, knock on door number `8000`".

---

### Development vs Cloud (Production)

**Now (development):**
```
Phone → same WiFi → Your laptop (API + Database)
```
Limitation: only works at home/office, laptop must be on

**Future (production/national use):**
```
Phone → internet → Cloud Server (API + Database, always on, public URL)
```
The app code changes by just one line — the API URL. Everything else stays the same.

**Cloud deployment options:**
| Option | Cost | Notes |
|---|---|---|
| Railway.app | Free tier | Easiest for beginners |
| Render.com | Free tier | Good for FastAPI |
| DigitalOcean | ~$6/month | More control |
| Supabase | Free tier (DB only) | Managed PostgreSQL |

---

## PHASE 2 — Database Setup (COMPLETED)

### What is a Database and Why Not Just Use CSV?

The CSV file (`halal_data.csv`) is just a text file. It works fine to store data, but it has a big problem:

- To search for "Milo" in a CSV, Python has to **read every single row** one by one from top to bottom — all 354,000 of them.
- In a database, an **index** works like a book index — it lets the database jump straight to the answer without reading everything.
- With 348,000+ records and thousands of users hitting the app at once, CSV is too slow. Database is the right tool.

---

### What is PostgreSQL?

PostgreSQL (pronounced "Postgres") is a **database server** — a program that runs on your computer and stores data in an organised way.

Think of it like Excel, but:
- Handles millions of rows without slowing down
- Multiple people/apps can read from it at the same time
- You talk to it using a language called **SQL**
- It runs as a background **Windows Service** (always on, even when you close the terminal)

---

### How to Open and Use PostgreSQL

**Open a connection to PostgreSQL from PowerShell:**
```powershell
$env:Path += ";C:\Program Files\PostgreSQL\18\bin"
$env:PGPASSWORD = "Huffazpro1!"
psql -U postgres -d halal_jakim
```

| Part | Meaning |
|---|---|
| `$env:Path += "..."` | Adds PostgreSQL's folder to your terminal so `psql` command works |
| `$env:PGPASSWORD = "..."` | Sets your password so it doesn't ask every time |
| `psql` | The PostgreSQL command-line client (like a terminal inside the database) |
| `-U postgres` | Login as the user named `postgres` (the admin user created during install) |
| `-d halal_jakim` | Connect to the database named `halal_jakim` (our database) |

Once inside, your prompt looks like:
```
halal_jakim=#
```

---

### Useful psql Commands (type these inside psql)

| Command | What it does |
|---|---|
| `\l` | List all databases on this server |
| `\c halal_jakim` | Connect/switch to the `halal_jakim` database |
| `\dt` | List all tables in the current database |
| `\d halal_products` | Show the structure (columns) of the `halal_products` table |
| `\q` | Quit psql and go back to PowerShell |

---

### What is SQL?

SQL (Structured Query Language) is the language you use to talk to databases. It looks like plain English.

**Basic SQL commands used in this project:**

**1. See how many rows are in the table:**
```sql
SELECT COUNT(*) FROM halal_products;
```

**2. See the first 5 rows:**
```sql
SELECT * FROM halal_products LIMIT 5;
```

**3. Search for a company by name (case-insensitive):**
```sql
SELECT * FROM halal_products WHERE company ILIKE '%milo%';
```
`ILIKE` = case-insensitive LIKE. The `%` means "anything before/after". So `%milo%` matches `MILO`, `Nestle Milo 3in1`, etc.

**4. Count records per category:**
```sql
SELECT category, COUNT(*) as total
FROM halal_products
GROUP BY category
ORDER BY total DESC;
```

**5. Find records expiring soon:**
```sql
SELECT company, expiry FROM halal_products
WHERE expiry LIKE '%2026%'
LIMIT 10;
```

**To run SQL without entering psql interactively:**
```powershell
psql -U postgres -d halal_jakim -c "SELECT COUNT(*) FROM halal_products;"
```
The `-c` flag means "run this one command and exit".

---

### What Files Were Created in Phase 2

**`db_setup.sql`** — Creates the database and table structure. Think of it as the "blueprint" for the database.
```
CREATE DATABASE halal_jakim          ← creates the database
CREATE TABLE halal_products (...)    ← creates the table with all columns
CREATE INDEX idx_fts ON ...          ← creates search indexes for speed
```

**`import_to_db.py`** — Reads `halal_data.csv` and loads every row into PostgreSQL.
```
halal_data.csv  →  import_to_db.py  →  halal_products table
```

---

### What the Table Looks Like

```
halal_products table
┌────┬──────────────────┬────────────────────────┬─────────┬────────────────────┬─────────┬────────────┐
│ id │ category         │ company                │ brand   │ address            │ expiry  │ comp_code  │
├────┼──────────────────┼────────────────────────┼─────────┼────────────────────┼─────────┼────────────┤
│  1 │ Produk           │ NESTLE PRODUCTS SDN... │ MILO    │ Petaling Jaya...   │ 31/12/… │ COMP-xxx   │
│  2 │ Premis Makanan   │ RESTORAN ABC SDN BHD   │         │ Kuala Lumpur...    │ 30/06/… │ COMP-xxx   │
└────┴──────────────────┴────────────────────────┴─────────┴────────────────────┴─────────┴────────────┘
```

- `id` — auto-number, PostgreSQL assigns this automatically
- `category` — which JAKIM category (Produk, Syarikat, etc.)
- `comp_code` — JAKIM's unique certificate ID (e.g. `COMP-20250211-072429`)
- `company` — company/establishment name
- `brand` — brand name (not always filled)
- `address` — full address
- `expiry` — certificate expiry date

---

### Current Database Stats (Phase 2 Result)

| Category | Records |
|---|---|
| Produk | 295,948 |
| Premis Makanan | 13,969 |
| Syarikat | 10,909 |
| Barang Gunaan | 10,674 |
| Kosmetik | 8,122 |
| Farmaseutikal | 5,751 |
| OEM | 2,040 |
| Hotel & Resort | 797 |
| Rumah Sembelih | 253 |
| Peranti Perubatan | 195 |
| Logistik | 186 |
| **TOTAL** | **348,844** |

---

### Try It Yourself — Practice Queries

Open psql and try these yourself:

```powershell
# Open psql
$env:Path += ";C:\Program Files\PostgreSQL\18\bin"
$env:PGPASSWORD = "Huffazpro1!"
psql -U postgres -d halal_jakim
```

Then inside psql, try:
```sql
-- See the table structure
\d halal_products

-- Count everything
SELECT COUNT(*) FROM halal_products;

-- Search for a brand you know
SELECT company, brand, expiry FROM halal_products WHERE brand ILIKE '%milo%' LIMIT 5;

-- See all categories
SELECT category, COUNT(*) FROM halal_products GROUP BY category;

-- Exit
\q
```

---

## PHASE 3 — Backend API (NEXT STEP)

### What is an API?

An **API** (Application Programming Interface) is like a waiter in a restaurant.

- **You (the app)** are the customer
- **The database** is the kitchen
- **The API** is the waiter — you don't go into the kitchen yourself, you tell the waiter what you want, and they bring back the result

In our case:
- The **mobile app** asks: "Is 'Milo' halal?"
- The **API** receives that question, searches the database, and replies: `{ "halal": true, "expiry": "31/12/2026" }`
- The **app** shows the result to the user

The app never touches the database directly — the API is the safe, controlled middleman.

---

### What is FastAPI?

**FastAPI** is a Python library that lets you build an API in just a few lines of code. It's:
- Written in Python (you already know Python)
- Very fast (one of the fastest Python frameworks)
- Automatically creates a **test page** at `/docs` so you can test your API in the browser without writing any extra code

---

### What We Will Build

A simple API with 2 endpoints (URLs your app can call):

| Endpoint | What it does | Example |
|---|---|---|
| `GET /search?q=milo` | Search halal products by name/brand | Returns all products matching "milo" |
| `GET /company/{comp_code}` | Get full company info by certificate code | Returns one company's full record |

When the API runs, you can open your browser and go to:
- `http://localhost:8000/search?q=milo` → see live JSON results
- `http://localhost:8000/docs` → interactive test page (auto-generated!)

---

### STEP 1 — Install FastAPI

Open PowerShell, activate the venv, then install:

```powershell
# Step A: Navigate to project folder
cd "C:\Users\hilmanxz\Documents\Projek Intern\Web Crawling"

# Step B: Activate the virtual environment
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
& ".venv\Scripts\Activate.ps1"

# Step C: Install FastAPI and its dependencies
pip install fastapi uvicorn psycopg2-binary
```

| Package | What it does |
|---|---|
| `fastapi` | The framework that lets you build APIs |
| `uvicorn` | The server that *runs* your FastAPI app (like a web server) |
| `psycopg2-binary` | Already installed — connects Python to PostgreSQL |

---

### STEP 2 — Create the API file

We will create a new file called `api.py` in the project folder. Here is what it will contain:

**You don't need to type this yourself — tell me and I'll create it for you.**

```
api.py
 ├── Connect to PostgreSQL database
 ├── GET /search?q={text}   → search halal_products by company/brand
 ├── GET /company/{code}    → get one company by comp_code
 └── GET /health            → simple test to confirm API is running
```

---

### STEP 3 — Run the API

After creating `api.py`, run it with:

```powershell
uvicorn api:app --reload
```

| Part | Meaning |
|---|---|
| `uvicorn` | The web server |
| `api:app` | "In the file `api.py`, find the variable named `app`" |
| `--reload` | Auto-restart when you change the code (useful during development) |

Then open your browser and go to: **`http://localhost:8000/docs`**

You'll see a beautiful test page where you can click and test your API without any extra tools.

---

### STEP 4 — Test It

In the browser at `http://localhost:8000/docs`:
1. Click on `GET /search`
2. Click **"Try it out"**
3. Type `milo` in the `q` field
4. Click **Execute**
5. Scroll down — you'll see real results from your database in JSON format!

---

---

## PHASE 3 — Building the API (COMPLETED)

### File Created: `api.py`

The file `api.py` has been created in your project folder. Here is what each part does:

---

### Part 1 — Imports (top of the file)

```python
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
import psycopg2
import psycopg2.extras
```

| Import | What it is |
|---|---|
| `FastAPI` | The main framework — lets you build API endpoints |
| `HTTPException` | Used to send error responses (e.g. "404 Not Found") |
| `Query` | Lets you define URL parameters like `?q=milo` |
| `CORSMiddleware` | Allows the mobile app to call this API from a different device |
| `psycopg2` | Connects Python to PostgreSQL |
| `psycopg2.extras` | Extra helpers — `RealDictCursor` makes rows return as dictionaries |

---

### Part 2 — Database Config

```python
DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "dbname": "halal_jakim",
    "user": "postgres",
    "password": "Huffazpro1!",
}
```

This is exactly the same database connection info we used in `import_to_db.py`. The API reads from the same database we already loaded.

---

### Part 3 — The 4 Endpoints (URLs the app can call)

#### Endpoint 1: `/health`
```
GET http://localhost:8000/health
```
Just checks that the API is alive and connected to the database. Returns total record count.

**Example response:**
```json
{
  "status": "ok",
  "message": "API is running",
  "total_records": 348844
}
```

---

#### Endpoint 2: `/search?q=milo`
```
GET http://localhost:8000/search?q=milo
GET http://localhost:8000/search?q=nestle&category=Produk
```
Searches the `company` and `brand` columns for your keyword. Optional `category` filter.

**Example response:**
```json
{
  "query": "milo",
  "count": 5,
  "results": [
    {
      "category": "Produk",
      "company": "NESTLE PRODUCTS SDN BHD",
      "brand": "MILO",
      "expiry": "31/12/2026",
      ...
    }
  ]
}
```

---

#### Endpoint 3: `/company/{comp_code}`
```
GET http://localhost:8000/company/COMP-20250211-072429
```
Get all records for one specific company by their JAKIM certificate code.

---

#### Endpoint 4: `/categories`
```
GET http://localhost:8000/categories
```
Returns all 11 JAKIM categories and how many records each has.

---

### How to Run the API

**Step 1 — Open PowerShell and activate venv:**
```powershell
cd "C:\Users\hilmanxz\Documents\Projek Intern\Web Crawling"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
& ".venv\Scripts\Activate.ps1"
```

**Step 2 — Run the API:**
```powershell
uvicorn api:app --reload
```

You will see output like:
```
INFO:     Uvicorn running on http://127.0.0.1:8000 (Press CTRL+C to quit)
INFO:     Started reloader process
```

**The API is now running.** Keep this PowerShell window open (it's the server).

---

### How to Test the API in Browser

Open your browser and go to these URLs:

| URL | What you see |
|---|---|
| `http://localhost:8000/docs` | Interactive test page (auto-generated by FastAPI!) |
| `http://localhost:8000/health` | JSON showing API is alive + record count |
| `http://localhost:8000/search?q=milo` | Search results for "milo" |
| `http://localhost:8000/categories` | All 11 JAKIM categories with counts |

---

### The `/docs` Page (Most Important)

When you open `http://localhost:8000/docs`, FastAPI automatically creates a beautiful interactive page where you can:
1. See all your endpoints listed
2. Click **"Try it out"** on any endpoint
3. Fill in parameters and click **Execute**
4. See the real JSON response from your database

**You do not need any extra tools — the browser is enough to test everything.**

---

### What is JSON?

JSON (JavaScript Object Notation) is the format that APIs use to send data. It looks like this:

```json
{
  "company": "NESTLE PRODUCTS SDN BHD",
  "brand": "MILO",
  "expiry": "31/12/2026",
  "category": "Produk"
}
```

Think of it as a structured way to send data between the server and the app. The app receives this JSON and displays it nicely to the user.

---

### Try It Yourself

```powershell
# 1. Activate venv
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
& ".venv\Scripts\Activate.ps1"

# 2. Run the API
uvicorn api:app --reload

# 3. Open browser at http://localhost:8000/docs
# 4. Test the /search endpoint with a brand name you know
```

---

---

## PHASE 4 — Flutter Mobile App (COMPLETED — Text Search Version)

### What is Flutter?

**Flutter** is a toolkit made by Google that lets you write **one codebase** and run it on:
- Android phone
- iPhone (iOS)
- Windows desktop
- Web browser

You write in a language called **Dart** (similar to JavaScript/Java). Flutter compiles it into a real native app.

---

### What is a Widget?

Everything in a Flutter app is a **Widget** — think of widgets like LEGO blocks.

| Widget | What it is |
|---|---|
| `Scaffold` | The base frame of a screen (has AppBar + body) |
| `AppBar` | The top bar (green bar with "Halal Checker" title) |
| `Column` | Stacks widgets vertically |
| `Row` | Places widgets side by side horizontally |
| `TextField` | The text input box |
| `ElevatedButton` | A clickable button |
| `ListView.builder` | A scrollable list |
| `Card` | A rounded box — used for each search result |
| `Navigator.push` | Opens a new screen on top |

---

### StatelessWidget vs StatefulWidget

| Type | When to use | Example |
|---|---|---|
| `StatelessWidget` | Screen never changes after it's built | Detail screen — just shows fixed data |
| `StatefulWidget` | Screen needs to update (show results, spinners, errors) | Search screen — changes when user searches |

`setState()` = tells Flutter "something changed, please redraw the screen"

---

### What We Built — 3 Main Parts

#### 1. `HalalProduct` — Data Model
A class that represents one row from the database. The API returns JSON, and `fromJson()` converts it into a `HalalProduct` object.

```dart
class HalalProduct {
  final String company;
  final String brand;
  final String expiry;
  // ...
  
  factory HalalProduct.fromJson(Map<String, dynamic> json) {
    return HalalProduct(company: json['company'] ?? '', ...);
  }
}
```

#### 2. `ApiService` — API Caller
One class that handles all API calls. The search screen calls `ApiService.search("milo")` and gets back a list of `HalalProduct` objects.

```dart
class ApiService {
  static Future<List<HalalProduct>> search(String query) async {
    final response = await http.get(Uri.parse('$apiBaseUrl/search?q=$query'));
    // parse JSON and return list of products
  }
}
```

`Future<>` = means "this will complete later" (async operation — like waiting for internet)  
`async/await` = lets you wait for the result without freezing the app

#### 3. Two Screens

**SearchScreen** (StatefulWidget):
- Green search bar at top
- Loading spinner while waiting
- List of `ProductCard` results
- Error message if API is offline

**DetailScreen** (StatelessWidget):
- Big green "HALAL CERTIFIED" banner
- All product details: company, brand, category, certificate code, expiry, address

---

### How the Screens Connect

```
SearchScreen
    │
    │  user taps a ProductCard
    ▼
DetailScreen  (opened on top, back button returns to SearchScreen)
```

`Navigator.push()` opens a new screen.  
`Navigator.pop()` (or the back button) closes it and returns.

---

### Project Folder Structure

```
halal_app/
├── lib/
│   └── main.dart          ← ALL our app code lives here (one file for now)
├── android/
│   └── app/src/main/
│       └── AndroidManifest.xml  ← Android config (added INTERNET permission)
├── pubspec.yaml            ← App config: name, packages, dependencies
└── ...
```

---

### How to Run the App

**Step 1 — Make sure the API is running** (in the Web Crawling project folder):
```powershell
cd "C:\Users\hilmanxz\Documents\Projek Intern\Web Crawling"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
& ".venv\Scripts\Activate.ps1"
uvicorn api:app --reload
```

**Step 2 — In a NEW PowerShell window, run the app:**
```powershell
cd "C:\Users\hilmanxz\Documents\Projek Intern\halal_app"
flutter run
```

Flutter will ask which device — choose your Android phone (PCT L29) or Windows.

**Important:** Your phone and computer must be on the **same WiFi network**. The app connects to `http://192.168.1.10:8000` (your computer's IP).

---

### What `withValues(alpha: 0.7)` means

You might see this in the code. It sets the **opacity** (transparency) of a color.
- `alpha: 1.0` = fully opaque (solid)
- `alpha: 0.5` = 50% transparent
- `alpha: 0.0` = fully invisible

We use `.withValues(alpha: 0.7)` instead of the old `.withOpacity(0.7)` because the newer Flutter version prefers it.

---

## STEP 1 — Decode the URL first

**URL we started with:**
```
https://myehalal.halal.gov.my/portal-halal/v1/index.php?data=ZGlyZWN0b3J5L2luZGV4X2RpcmVjdG9yeTs7Ozs=
```

That `data=` part looks like **Base64 encoding** (you can tell by the `=` at the end and it only uses letters/numbers/+/).

**Command to decode Base64 in Python:**
```python
import base64
print(base64.b64decode('ZGlyZWN0b3J5L2luZGV4X2RpcmVjdG9yeTs7Ozs=').decode())
# Output: directory/index_directory;;;;
```

**What we learned:** The URL encodes a string with 4 semicolon-separated parameters. We guessed they might be: page, state, category, keyword.

---

## STEP 2 — Try if pagination is in the URL (GET request)

We encoded different page numbers into each of the 4 parameter slots and tried fetching them:

```python
import base64

patterns = [
    'directory/index_directory;2;;;',   # page in slot 1
    'directory/index_directory;;2;;',   # page in slot 2
    'directory/index_directory;;;2;',   # page in slot 3
    'directory/index_directory;;;;2',   # page in slot 4
]
for p in patterns:
    encoded = base64.b64encode(p.encode()).decode()
    print(f'{p}  =>  {encoded}')
```

Then we fetched each URL and checked if the response said "Page 2". **All returned Page 1** — so pagination is NOT in the GET URL parameters.

---

## STEP 3 — SSL Error problem and how to fix it

When using Python's `requests` library to fetch the site, we got:
```
SSLError: [SSL: SSLV3_ALERT_HANDSHAKE_FAILURE]
```

**Why this happens:** The Malaysian government server uses an **older TLS configuration** that Python's modern SSL library rejects by default for security reasons.

**Fix — Custom SSL Adapter:**
```python
import ssl
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.ssl_ import create_urllib3_context

class LegacySSLAdapter(HTTPAdapter):
    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        ctx.set_ciphers('DEFAULT:@SECLEVEL=1')  # Lower security level to allow older ciphers
        kwargs['ssl_context'] = ctx
        return super().init_poolmanager(*args, **kwargs)

session = requests.Session()
session.mount('https://', LegacySSLAdapter())
```

`SECLEVEL=1` tells OpenSSL to allow older cipher suites that this server needs.

---

## STEP 4 — Inspect the HTML source (forms, hidden inputs, scripts)

Since GET params didn't control pagination, we fetched the raw HTML and looked for:
1. `<form>` tags — to find what forms exist and how they submit
2. Hidden `<input>` fields — often carry state/session data
3. `onclick` JavaScript handlers — often reveal what happens when you click buttons

**Key findings in the HTML:**
```html
<!-- Two forms found: -->
<form name="halal" action="index.php?data=..." method="post">  <!-- search form -->
<form method="post" name="direktori">                           <!-- navigation form -->

<!-- Hidden inputs in the direktori form: -->
<input type="hidden" name="hdnCounter" value=21>
<input type="hidden" name="t" value="">
<input type="hidden" name="a" value="">
<input type="hidden" name="ty" value="" />    <!-- <-- THIS IS KEY -->
```

---

## STEP 5 — Find the JavaScript that handles navigation

We searched all `<script>` blocks for functions. Found this critical function:

```javascript
function do_menu1(URL, ty) {
    var cari = document.halal.cari.value;      // gets search keyword from search box
    var gcari = cari.replace("&","@d");         // sanitize & character
    document.direktori.ty.value = ty;           // sets hidden field 'ty' = category code
    document.direktori.action = URL + '&cari=' + gcari;  // sets form action URL
    document.direktori.target = '_self';
    document.direktori.submit();               // SUBMITS the form as POST
}
```

**What this tells us:**
- Every time you click a category tab or page number, it calls `do_menu1(URL, categoryCode)`
- It **POSTs** the `direktori` form with `ty` = the category code
- The page number and keyword are appended to the URL as query params

---

## STEP 6 — Find all category codes

The category buttons each call `do_menu1` with a 2-3 letter code:

```html
<a onClick="do_menu1('...&category=', 'CO')">Syarikat(10,909)</a>
<a onClick="do_menu1('...&category=', 'HO')">Hotel & Resort(797)</a>
<a onClick="do_menu1('...&category=', 'PE')">Premis Makanan(13,969)</a>
<a onClick="do_menu1('...&category=', 'PR')">Produk(296,528)</a>
...
```

**We extracted them automatically with regex:**
```python
cats = re.findall(r"do_menu1\('[^']+','(\w+)'\)[^>]*>([^<]+)", r.text)
for code, name in cats:
    print(f"'{code}' => {name.strip()}")
```

**Full list of category codes:**
| Category | Code | Records |
|---|---|---|
| Syarikat | `CO` | 10,909 |
| Hotel & Resort | `HO` | 797 |
| Premis Makanan | `PE` | 13,969 |
| Produk | `PR` | 296,528 |
| Kosmetik | `KO` | 8,084 |
| Farmaseutikal | `FM` | 5,747 |
| Barang Gunaan | `BG` | 10,674 |
| Rumah Sembelih | `RS` | 253 |
| Logistik | `LO` | 186 |
| Peranti Perubatan | `MD` | 195 |
| OEM | `OEM` | 2,039 |
| International | `IN` | ? |

---

## STEP 7 — Find the exact pagination URL format

Looking at the pagination HTML:
```html
<a onClick="do_menu1('index.php?data=ZGlyZWN0b3J5...&negeri=&category=&page=2', 'CO')">2</a>
<a onClick="do_menu1('index.php?data=ZGlyZWN0b3J5...&negeri=&category=&page=3', 'CO')">3</a>
```

**The page number is a GET param `&page=N` appended to the URL.**

So the full POST request to get page 2 of Syarikat is:
```
POST https://myehalal.halal.gov.my/portal-halal/v1/index.php?data=ZGlyZWN0b3J5...&negeri=&category=&page=2&cari=
Body: ty=CO
```

**We verified this works:**
```python
post_url = 'https://...&page=2&cari='
r2 = session.post(post_url, data={'ty': 'CO'}, headers=headers, timeout=15)
# Output: Success! Page 2 of 546
```

---

## STEP 8 — Understand the row HTML structure

Finally, we used BeautifulSoup to inspect the actual data rows:

```html
<tr class="border-b border-l border-r cursor-pointer hover:bg-cyan-100"
    onclick="openModal('directory/slm_viewdetail.php?comp_code=COMP-20250211-072429&type=C&ty=CO', ...)">
  <td class="text-center font-semibold">1</td>
  <td class="py-6">
    <span class="company-name">CBE ENGINEERING SDN. BHD.</span>
    <br/>
    <span class="company-address">NO.77 (6)<br/>TINGKAT 1<br/>JALAN AHMAD SHAH<br/>28000<br/>TEMERLOH<br/>Pahang</span>
    <span class="company-brand"></span>
  </td>
  <td class="text-center py-6">30/06/2027</td>
</tr>
```

**Key CSS classes for parsing:**
- `span.company-name` → company name
- `span.company-address` → address (multiline `<br/>` separated)
- `td.text-center.py-6` (3rd column) → expiry date
- `onclick` attribute → contains `comp_code` (unique certificate ID)

---

## Summary: Final Request Formula

```
Method: POST
URL:    https://myehalal.halal.gov.my/portal-halal/v1/index.php?data=ZGlyZWN0b3J5L2luZGV4X2RpcmVjdG9yeTs7Ozs=&negeri=&category=&page={PAGE}&cari=
Body:   ty={CATEGORY_CODE}
```

To get total pages, parse: `Total Record : XXXX - Page N From TOTAL`

---

## Key Lessons from This Investigation

1. **Always decode obfuscated URL params** — Base64 is common on older PHP sites
2. **When GET pagination fails, look for POST forms** — Many sites use JS form submission
3. **Read JavaScript functions** — `do_menu1()` revealed the entire navigation mechanism
4. **Hidden `<input>` fields carry state** — the `ty` field was the critical missing piece
5. **Government sites often have old SSL** — use `SECLEVEL=1` cipher fix
6. **Use regex to extract patterns** — `re.findall(r"do_menu1\(...\)")` got all category codes at once
7. **Verify your theory before building** — always test a POST to page 2 before writing the full scraper

---

## SETUP — Python Virtual Environment & Installing Packages

This section explains how to set up Python properly before running the scraper. This is a one-time setup step.

---

### What is a Virtual Environment (`.venv`)?

When you run Python scripts, they need extra **libraries** (also called **packages** or **modules**) that are not built into Python by default. For example, `requests` is a library that lets Python make HTTP requests to websites — it does not come pre-installed.

A **virtual environment** is an isolated folder (`.venv/`) inside your project that holds:
- Its own copy of Python
- All the packages you install for **this project only**

This means packages installed here do **not** affect other Python projects on your computer, and vice versa. It keeps everything clean and contained.

```
Web Crawling/
├── .venv/              ← the virtual environment lives here
│   └── Scripts/
│       ├── python.exe  ← the Python inside this environment
│       └── pip.exe     ← the installer inside this environment
├── halal_scraper.py
└── INVESTIGATION_NOTES.md
```

---

### The Error — `ModuleNotFoundError: No module named 'requests'`

```
ModuleNotFoundError: No module named 'requests'
```

**What this means:** Python ran the script but could not find the `requests` library. This happens when:
- The library is not installed at all, OR
- The library is installed in a different Python (e.g. global Python), but you are running the `.venv` Python which is a separate isolated environment

In this project, the `.venv` environment existed but the packages had not been installed inside it yet.

---

### Step 1 — Activate the Virtual Environment

Before installing or running anything, you must **activate** the `.venv` so that your terminal uses the Python inside it (not the global one).

**Command (PowerShell):**
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
& ".venv\Scripts\Activate.ps1"
```

**What each part does:**

| Command | Purpose |
|---|---|
| `Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned` | Windows blocks running `.ps1` scripts by default. This temporarily allows it just for the current terminal session (`-Scope Process` = only this window, not permanent). |
| `& ".venv\Scripts\Activate.ps1"` | Runs the activation script inside `.venv`. The `&` means "run this file". |

**How you know it worked:** Your terminal prompt will change to show `(.venv)` at the start:
```
(.venv) PS C:\Users\hilmanxz\Documents\Projek Intern\Web Crawling>
```
That `(.venv)` prefix means you are now inside the virtual environment.

---

### Step 2 — Install the Required Packages

With the environment activated, use `pip` to install the missing packages.

**Command:**
```powershell
pip install requests beautifulsoup4
```

**What this does:**

| Part | Meaning |
|---|---|
| `pip` | Python's built-in package installer (stands for "Pip Installs Packages") |
| `install` | The action — download and install a package |
| `requests` | The HTTP library used to fetch web pages in the scraper |
| `beautifulsoup4` | The HTML parser used to read and extract data from the fetched pages |

`pip` downloads these from the internet (from [pypi.org](https://pypi.org), Python's official package repository) and installs them into your active `.venv`.

---

### Step 3 — Run the Script

Once packages are installed, run the scraper normally:

```powershell
python halal_scraper.py
```

Because the `.venv` is active, this uses the `.venv` Python which now has all the packages it needs.

---

### Quick Reference — Commands to Run Every Time

Every time you open a new terminal to work on this project, do these two things first:

```powershell
# 1. Allow script execution (only needed once per terminal session)
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned

# 2. Activate the virtual environment
& ".venv\Scripts\Activate.ps1"

# 3. Then run the scraper
python halal_scraper.py
```

You do **not** need to run `pip install` again after the first time — packages stay installed inside `.venv` permanently.

---

### Libraries Used in This Project and Why

| Library | Import name | What it does |
|---|---|---|
| `requests` | `import requests` | Makes HTTP GET/POST requests to websites — this is how we fetch the halal portal pages |
| `beautifulsoup4` | `from bs4 import BeautifulSoup` | Parses HTML — lets us find specific tags like `<span class="company-name">` easily |
| `ssl` | `import ssl` | Built into Python — handles SSL/TLS certificates for HTTPS connections |
| `re` | `import re` | Built into Python — regular expressions for pattern matching in text |
| `csv` | `import csv` | Built into Python — reads and writes `.csv` files to save scraped data |
| `time` | `import time` | Built into Python — `time.sleep()` adds delay between requests so we don't overload the server |
| `os` | `import os` | Built into Python — checks if files exist (`os.path.exists`) for resume/progress tracking |
