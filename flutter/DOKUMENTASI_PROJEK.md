# SMART MSRLab Alpha — Dokumentasi Projek (Dari Awal)

Dokumen ini terangkan projek dari kosong: apa dia, bagaimana ia berfungsi, dari mana data datang, dan bagaimana nak jalankan semula semuanya. Ditulis berdasarkan kod semasa dalam repo (Oktober 2026).

> Untuk langkah deploy & build ringkas, rujuk [README.md](README.md). Dokumen lain (`PROJECT_DOCUMENTATION.md`, `PROJECT_NOTES.md`, `NOTES.md`) adalah nota sejarah pembangunan — sebahagiannya sudah lapuk (contoh: masih sebut EfficientNet sebagai pipeline utama).

---

## Kandungan

1. [Apa Projek Ini](#1-apa-projek-ini)
2. [Seni Bina Keseluruhan](#2-seni-bina-keseluruhan)
3. [Teknologi Digunakan](#3-teknologi-digunakan)
4. [Struktur Repo](#4-struktur-repo)
5. [Pipeline Data (Dari Portal Halal ke Database)](#5-pipeline-data-dari-portal-halal-ke-database)
6. [Database](#6-database)
7. [Backend API (`api.py`)](#7-backend-api-apipy)
8. [Aplikasi Mobile (Flutter)](#8-aplikasi-mobile-flutter)
9. [Integrasi Drone DJI](#9-integrasi-drone-dji)
10. [Setup Dari Kosong](#10-setup-dari-kosong)
11. [Konfigurasi Penting](#11-konfigurasi-penting)
12. [Isu Diketahui & Perkara Perlu Dibaiki](#12-isu-diketahui--perkara-perlu-dibaiki)
13. [Glosari](#13-glosari)

---

## 1. Apa Projek Ini

**SMART MSRLab Alpha** (nama app: *SMART SandboxPlayground*) ialah "super app" Muslim untuk Android, dengan fokus utama **menyemak status halal** produk dan premis menggunakan data rasmi JAKIM.

### Masalah yang diselesaikan

Portal Halal Malaysia ada ~348K rekod pensijilan, tapi:
- Carian di portal perlu taip nama tepat syarikat/jenama.
- Pengguna di kedai biasanya hanya ada **produk di tangan**, bukan nama syarikat.

App ini membolehkan pengguna **ambil gambar** produk / logo / papan tanda premis, dan AI akan baca jenama lalu cari dalam database JAKIM.

### Ciri-ciri

| Ciri | Cara kerja ringkas | Endpoint / Sumber |
|---|---|---|
| Halal Checker (carian teks) | Taip nama → cari dalam DB | `GET /search-grouped`, `/company-products` |
| Scan Produk | Gambar → Qwen Vision baca jenama → cari DB | `POST /scan-image` |
| Scan Logo Halal | Gambar logo → kenal pasti badan pensijilan → semak senarai diiktiraf JAKIM | `POST /scan-logo` |
| Drone Scanner | Frame kamera DJI Mini 2 → cari premis | `POST /scan-premises` |
| Waktu Solat | API WaktuSolat (fallback: kiraan tempatan) | `api.waktusolat.app` |
| Kompas Kiblat | GPS + kompas + magnetic declination | Sensor fon |
| Kalendar Hijri | Pakej `hijri` | Tempatan |
| Tasbih Digital | Kaunter zikir | Tempatan |
| Notifikasi Solat | `flutter_local_notifications` | Tempatan |

---

## 2. Seni Bina Keseluruhan

```
┌─────────────────────────────┐        ┌──────────────────────┐
│  Flutter App (Android)      │        │  DJI Mini 2 + RC     │
│  lib/                       │◄──────►│  (DJI Mobile SDK v4) │
│  + Kotlin native (DJI)      │  USB   └──────────────────────┘
└──────────────┬──────────────┘
               │ HTTPS (multipart image / query string)
               ▼
┌─────────────────────────────┐   HTTP (OpenAI-compatible)  ┌───────────────────────┐
│  FastAPI — api.py           │ ──────────────────────────► │  Qwen3-VL-8B          │
│  + EasyOCR (en, ms)         │   /v1/chat/completions      │  (RunPod / LM Studio) │
│  + jakim_cb.csv (in-memory) │                             └───────────────────────┘
└──────────────┬──────────────┘
               │ psycopg2 (connection pool 2–10)
               ▼
┌─────────────────────────────┐
│  PostgreSQL — halal_jakim   │
│  jadual halal_products      │
│  (~348K rekod, pg_trgm)     │
└─────────────────────────────┘
```

### Aliran scan produk (contoh paling penting)

```
Pengguna ambil gambar
   │
   ▼
POST /scan-image
   │
   ├─► [1] Qwen Vision: "Apa brand & produk dalam gambar?"
   │        → {"brand": "...", "product": "...", "keywords": [...]}
   │        → _search_jakim_by_texts(brand, product, keywords)
   │
   ├─► [2] Jika [1] tiada padanan → EasyOCR baca semua teks
   │        → _search_jakim_by_texts(teks OCR)
   │
   ▼
Gabung & buang duplikat → pulangkan halal_records + detection_method
                           ("vision" | "ocr" | "none")
```

---

## 3. Teknologi Digunakan

### Mobile
| Komponen | Versi | Guna untuk |
|---|---|---|
| Flutter / Dart | SDK ^3.9.2 | UI app |
| `http` | ^1.6.0 | Panggil API |
| `image_picker` | ^1.1.2 | Kamera / galeri |
| `shared_preferences` | ^2.3.5 | Simpan tetapan (URL API, kaedah kiraan solat, mazhab) |
| `geolocator`, `geocoding` | 14 / 4 | Lokasi GPS & nama tempat |
| `flutter_compass`, `sensors_plus` | — | Kompas kiblat |
| `hijri` | ^3.0.0 | Tarikh Hijri |
| `flutter_local_notifications`, `timezone` | — | Peringatan solat |
| DJI Mobile SDK | 4.18 | Kawal drone (Kotlin) |

### Backend
| Komponen | Guna untuk |
|---|---|
| FastAPI 0.115 + Uvicorn | Web server API |
| psycopg2 | Sambungan PostgreSQL (dengan `ThreadedConnectionPool`) |
| EasyOCR (`en`, `ms`, CPU) | OCR fallback |
| Pillow | Proses gambar |
| requests | Panggil Qwen Vision |
| Qwen3-VL-8B | Model vision-language untuk baca packaging/logo |
| PostgreSQL 15 + `pg_trgm` | Database + fuzzy matching |
| Docker / Docker Compose | Deploy |

---

## 4. Struktur Repo

```
SMART-MSRLab-Alpha-1/
├── api.py                     # ★ Backend utama (FastAPI)
├── requirements.txt           # Dependencies Python untuk api.py
├── Dockerfile                 # Image untuk api.py
├── docker-compose.yml         # api + postgres
│
├── lib/                       # ★ Kod Flutter
│   ├── main.dart              # Entry point, apiBaseUrl, model data, ApiService,
│   │                          #   SearchScreen, ScanScreen, CompanyProductsScreen
│   ├── screens/
│   │   ├── home_screen.dart       # Hub menu (grid ciri)
│   │   ├── scanner_screen.dart    # Scan produk
│   │   ├── logo_scan_screen.dart  # Scan logo halal
│   │   ├── drone_screen.dart      # Drone: live feed + scan premis
│   │   ├── prayer_screen.dart     # Waktu solat + countdown
│   │   ├── qibla_screen.dart      # Kompas kiblat
│   │   ├── calendar_screen.dart   # Kalendar Hijri
│   │   ├── tasbeeh_screen.dart    # Tasbih
│   │   └── settings_screen.dart   # Tetapan (URL API, kaedah solat, mazhab)
│   ├── services/
│   │   ├── drone_service.dart        # MethodChannel "smart.app/dji"
│   │   ├── location_service.dart     # GPS, arah kiblat, declination
│   │   ├── prayer_service.dart       # Waktu solat (API + fallback)
│   │   └── notification_service.dart # Notifikasi tempatan
│   └── widgets/location_picker_dialog.dart
│
├── android/app/src/main/kotlin/com/halalcheck/halal_app/
│   ├── MainActivity.kt           # Method channels (DJI + geomagnetic)
│   ├── DJIApplication.kt         # Init DJI SDK
│   ├── DroneCameraView.kt        # Paparan live feed drone
│   └── DroneCameraViewFactory.kt # Daftar view ke Flutter
│
├── windows/                   # Target Windows desktop (untuk ujian)
├── test/widget_test.dart
│
├── ── Skrip pipeline data (dijalankan sekali / bila perlu) ──
├── halal_scraper.py           # Scrape Portal Halal Malaysia
├── clean_halal_data.py, fix_halal_csv.py, quick_fix_csv.py, ultra_fast_fix.py
├── extract_from_sql_dump.py, extract_product_names.py
├── import_to_db.py            # CSV → PostgreSQL
├── ner_pipeline.py            # Bersihkan rekod guna Qwen (NER)
├── export_clean.py            # DB → halal_data_cleaned.csv
│
├── ── Eksperimen / lapuk ──
├── build_embeddings.py, download_product_images.py   # Pendekatan EfficientNet (tak digunakan lagi)
├── lotus_scraper.py, inspect_lotus*.py               # Scrape gambar produk Lotus's
├── scanner_server/server.py   # Server berasingan PaddleOCR (prototaip awal)
├── backup_before_brand_feature/  # Salinan sebelum ciri brand-first
├── test_*.py, investigate.py, check_db.py, deep_data_analysis.py, analyze_company.py
│
└── *.md                       # Dokumentasi & nota
```

---

## 5. Pipeline Data (Dari Portal Halal ke Database)

Data tidak datang dari API rasmi — ia di-*scrape* dari Portal Halal Malaysia, kemudian dibersihkan. Langkah ini **tidak perlu diulang** untuk deploy biasa (database sudah wujud), tapi perlu jika nak kemas kini data.

```
Portal Halal Malaysia
   │  halal_scraper.py   (POST request, ikut kategori & muka surat)
   ▼
CSV mentah (lajur berselerak, nama syarikat berbilang baris)
   │  clean_halal_data.py / fix_halal_csv.py / quick_fix_csv.py / ultra_fast_fix.py
   ▼
CSV bersih
   │  import_to_db.py
   ▼
PostgreSQL: halal_products (lajur mentah: company, brand, product, address)
   │  ner_pipeline.py   (Qwen3 via LM Studio; baris "bersih" dilangkau guna peraturan)
   ▼
PostgreSQL: lajur *_ner diisi (company_ner, brand_ner, product_ner, address_ner, ner_confidence)
   │  export_clean.py
   ▼
halal_data_cleaned.csv   ← digunakan oleh docker-compose untuk init DB baru
```

Perincian teknikal scraper (format POST, kod kategori) ada dalam [INVESTIGATION_NOTES.md](INVESTIGATION_NOTES.md). Panduan pembersihan CSV ada dalam [CSV_CLEANING_GUIDE.md](CSV_CLEANING_GUIDE.md).

### Senarai Badan Pensijilan (CB)

`jakim_cb.csv` — senarai ~94 badan pensijilan halal luar negara (48 negara) yang diiktiraf JAKIM. Dimuat ke memori semasa API start dan digunakan oleh `/scan-logo`.

---

## 6. Database

- **Nama DB:** `halal_jakim`
- **Jadual utama:** `halal_products`
- **Extension:** `pg_trgm` (wajib — untuk fuzzy match `similarity()`)

### Lajur yang digunakan oleh API

| Lajur | Maksud |
|---|---|
| `id` | Primary key |
| `category` | Kategori JAKIM (cth. `Produk`, `Premis Makanan`, `Hotel & Resort`, `Rumah Sembelih`, `Kosmetik`) |
| `comp_code` | Kod syarikat JAKIM |
| `company_ner` | Nama syarikat (sudah dibersihkan) |
| `brand_ner` | Jenama |
| `product_ner` | Nama produk |
| `address_ner` | Alamat |
| `expiry` | Tarikh tamat sijil |
| `ner_confidence` | Keyakinan proses NER |

Lajur mentah (`company`, `brand`, `product`, `address`) dan `ner_status` juga wujud, digunakan oleh `ner_pipeline.py`.

---

## 7. Backend API (`api.py`)

### Endpoint

| Method | Path | Fungsi |
|---|---|---|
| GET | `/` | Liveness check |
| GET | `/health` | Semak DB + jumlah rekod |
| GET | `/search?q=&category=&limit=` | Carian ILIKE pada company/brand/product |
| GET | `/search-grouped?q=` | Carian dikumpul ikut syarikat (dengan skor populariti) |
| GET | `/company-products?...` | Semua produk satu syarikat |
| GET | `/company/{comp_code}` | Maklumat satu syarikat |
| GET | `/categories` | Senarai kategori |
| POST | `/scan-image` | Scan produk (Vision → OCR fallback) |
| POST | `/scan-logo` | Kenal pasti logo badan pensijilan |
| POST | `/scan-premises` | Scan premis (dari drone) |

Dokumentasi interaktif: `http://<server>:8000/docs`

Semua endpoint POST terima `multipart/form-data` dengan medan `file` (gambar, max 10 MB).

### Fungsi dalaman penting

| Fungsi | Peranan |
|---|---|
| `_extract_brand_vision()` | Hantar gambar ke Qwen, minta JSON `{brand, product, keywords}` |
| `_identify_logo_vision()` | Hantar logo ke Qwen, minta `{abbreviation, country, organization_name, all_text}` |
| `_extract_text_ocr()` | EasyOCR; besarkan gambar < 1000px; tapis keyakinan > 0.3; susun atas→bawah |
| `_normalize_text()` | Buang accent & simbol (`NESTLÉ`→`NESTLE`, `MUNCHY'S`→`MUNCHYS`) |
| `_search_jakim_by_texts()` | **Carian brand-first 4 peringkat** (lihat bawah) |
| `_search_certified_body()` | Padankan logo dengan `jakim_cb.csv` (singkatan → nama org → token teks, tapis negara) |
| `_popularity_score()` | Bonus susunan untuk jenama popular (`POPULAR_COMPANIES`) |

### Carian brand-first (`_search_jakim_by_texts`)

1. **Stage 1** — Kesan jenama: padankan setiap terma dengan `brand_ner` (asal + versi normalized).
2. **Stage 2a** — Jika jenama dijumpai, tapis lagi guna terma produk dalam jenama itu.
3. **Stage 2b** — Jika tapisan produk kosong, pulangkan semua rekod jenama itu.
4. **Stage 3** — Fallback: cari dalam `company_ner` + `brand_ner`.
5. **Stage 4** — Fuzzy match `pg_trgm similarity()` untuk ejaan hampir sama.

### Model AI

Qwen dipanggil melalui endpoint **OpenAI-compatible** (`{LM_STUDIO_URL}/v1/chat/completions`), gambar dihantar sebagai base64 data URL. Tag `<think>...</think>` dibuang dari respons. Ini bermakna backend boleh guna **mana-mana server** yang serasi: RunPod, LM Studio, vLLM, Ollama (mod OpenAI).

---

## 8. Aplikasi Mobile (Flutter)

### Permulaan app (`main.dart`)

1. Init timezone & `SharedPreferences` (global `gPrefs`).
2. Migrasi: buang URL API lama yang disimpan (ngrok / `192.168.*` / `careerlab.my`) supaya default baru digunakan.
3. Init notifikasi.
4. Buka `HomeScreen`.

### URL API

```dart
String get apiBaseUrl =>
    gPrefs.getString('api_url') ??
    'https://halal-jakim-backend-...sslip.io';
```

Boleh ditukar dalam app: **Settings → API Server** (berguna untuk uji dengan server tempatan / ngrok).

### Skrin utama (HomeScreen)

Grid menu: Prayer Times, Qibla Compass, Halal Checker, Scan Product, Tasbeeh, Hijri Calendar, Drone Scanner, Scan Logo Halal.

### Waktu Solat

`prayer_service.dart` panggil `https://api.waktusolat.app/v2/solat/gps/{lat}/{lng}` (zon JAKIM dikesan automatik). Jika gagal, waktu dikira secara tempatan mengikut kaedah & mazhab dalam Settings (default: `Singapore`, `Shafi`).

### Kiblat

`location_service.dart` kira bearing ke Kaabah (21.4225°N) dan dapatkan **magnetic declination** melalui MethodChannel `smart.app/geomagnetic` (Android `GeomagneticField`) supaya arah kompas betul.

---

## 9. Integrasi Drone DJI

**Perkakasan diuji:** DJI Mini 2 (melalui RC, OcuSync 2.0), fon disambung ke RC dengan USB.

```
drone_screen.dart
   │  DroneService (MethodChannel "smart.app/dji")
   ▼
MainActivity.kt
   │  DJI Mobile SDK v4.18
   ▼
DJI Mini 2
```

### Method channel

| Method | Fungsi |
|---|---|
| `registerSDK` | Daftar App Key DJI (perlu internet), mula sambungan ke drone |
| `isSDKRegistered` | Semak status pendaftaran |
| `getDroneStatus` | `"connected:<model>"` atau `"disconnected"` |
| `captureFrame` | Ambil frame semasa live feed sebagai JPEG |
| `capturePhoto` | Ambil gambar ke SD card drone |
| `startRecording` / `stopRecording` | Rakam video |

### Aliran scan premis

1. Skrin semak status drone setiap 5 saat sehingga bersambung.
2. Live feed dipaparkan melalui `DroneCameraView` (platform view).
3. Tekan scan → `captureFrame()` → POST ke `/scan-premises` (timeout 60s).
4. Papar sama ada premis dijumpai dalam rekod JAKIM.

### Nota DJI

- App Key DJI dalam `AndroidManifest.xml` (`com.dji.sdk.API_KEY`), terikat pada package `com.halalcheck.halal_app`.
- Firmware baru Mini 2: `startShootPhoto` tidak disokong semasa streaming — guna butang fizikal RC.
- `getBitmap()` pulangkan frame hitam dengan hardware decoder, sebab itu `captureFrame` guna kaedah lain (lihat `MainActivity.kt`).

---

## 10. Setup Dari Kosong

### A. Backend + Database (Docker)

```bash
git clone https://github.com/manxz7/SMART-MSRLab-Alpha-1.git
cd SMART-MSRLab-Alpha-1
```

Sediakan fail data berikut dalam root repo (**tidak termasuk dalam git** — dapatkan dari pembangun / server lama):

| Fail | Diperlukan oleh |
|---|---|
| `jakim_cb.csv` | `Dockerfile` (COPY) & `api.py` |
| `halal_data_cleaned.csv` | Init DB |
| `init.sql` | Cipta jadual `halal_products` + import CSV |
| `init_cb.sql` | Import senarai CB |

Kemudian:

```bash
docker-compose up -d --build
curl http://localhost:8000/health    # patut pulangkan total_records
```

Pastikan `pg_trgm` diaktifkan: `CREATE EXTENSION IF NOT EXISTS pg_trgm;`

### B. Server AI (Qwen3-VL-8B)

Pilih satu:
- **RunPod** (sekarang) — berbayar ikut jam GPU.
- **Server sendiri dengan GPU** — jalankan LM Studio / vLLM / Ollama dengan model Qwen3-VL-8B, dedahkan endpoint OpenAI-compatible.

Kemudian kemas kini `LM_STUDIO_URL` dalam `api.py` dan rebuild.

Tanpa server AI, API masih berfungsi tapi hanya guna EasyOCR (ketepatan lebih rendah).

### C. Jalankan backend tanpa Docker (untuk pembangunan)

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export DB_HOST=localhost DB_PORT=5432 DB_PASSWORD=...
uvicorn api:app --reload --port 8000
```

### D. App Android

```bash
flutter pub get
flutter run                     # fon disambung USB
flutter build apk --release     # → build/app/outputs/flutter-apk/app-release.apk
```

Keperluan: Flutter 3.x, Android Studio, Java 17, Android SDK. Jika server baru, tukar default `apiBaseUrl` dalam `lib/main.dart` atau set dalam Settings app.

### E. iOS

Folder `ios/` **tiada** dalam repo. Perlu jana dahulu di Mac: `flutter create --platforms=ios .`, kemudian `cd ios && pod install`. Ciri drone tidak akan berfungsi di iOS (integrasi DJI hanya ditulis untuk Android).

---

## 11. Konfigurasi Penting

| Tetapan | Lokasi | Nota |
|---|---|---|
| URL server Qwen | `api.py` → `LM_STUDIO_URL` | Hardcoded; tukar bila server AI bertukar |
| Nama model | `api.py` → `VISION_MODEL` | `qwen/qwen3-vl-8b` |
| Sambungan DB | Env: `DATABASE_URL` **atau** `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Default dalam `api.py` jika env tiada |
| URL API (app) | `lib/main.dart` → `apiBaseUrl`, atau Settings app | |
| DJI App Key | `android/app/src/main/AndroidManifest.xml` | Daftar di developer.dji.com |
| Package Android | `android/app/build.gradle.kts` → `com.halalcheck.halal_app` | Mesti sama dengan App Key DJI |

---

## 12. Isu Diketahui & Perkara Perlu Dibaiki

**Keselamatan**
- Kata laluan DB ditulis terus dalam `api.py` dan `docker-compose.yml`, dan IP server DB terdedah dalam kod. Patut dipindah ke fail `.env` (tidak di-commit) dan tukar kata laluan.
- `allow_origins=["*"]` pada CORS — boleh disempitkan.

**Repo tidak lengkap**
- `jakim_cb.csv`, `halal_data_cleaned.csv`, `init.sql`, `init_cb.sql` tiada dalam git → `docker-compose up` akan gagal pada repo yang baru di-clone.
- Folder `ios/` tiada.

**Kod**
- `/scan-premises` guna `_extract_brand_vision()` (prompt untuk packaging produk). Fungsi `_extract_premise_name_vision()` (prompt khusus papan tanda) dan `_search_premises_by_texts()` (tapis kategori premis) sudah ditulis tapi **tidak dipanggil**.
- Komen & medan `note` dalam `api.py` masih sebut "PaddleOCR" dan "Qwen2.5-VL", padahal yang digunakan ialah EasyOCR dan Qwen3-VL-8B.
- Banyak skrip sekali-guna / eksperimen di root (`inspect_lotus*.py`, `test_*.py`, `backup_before_brand_feature/`, `new.sh` kosong) — boleh dipindah ke folder `scripts/` atau `archive/`.
- Package Kotlin lama `com/hilmanxz/qibla_flutter/MainActivity.kt` masih wujud selain `com/halalcheck/halal_app/`.

**Operasi**
- RunPod URL berubah bila pod dicipta semula → scan akan jatuh ke OCR sahaja tanpa amaran jelas kepada pengguna.
- EasyOCR jalan di CPU (`gpu=False`) — lambat untuk gambar besar.

---

## 13. Glosari

| Istilah | Maksud |
|---|---|
| **JAKIM** | Jabatan Kemajuan Islam Malaysia — badan pensijilan halal Malaysia |
| **CB** | Certification Body — badan pensijilan halal luar negara yang diiktiraf JAKIM |
| **NER** | Named Entity Recognition — asingkan nama syarikat/jenama/produk dari teks bercampur |
| **OCR** | Optical Character Recognition — baca teks dari gambar |
| **VLM** | Vision-Language Model — model AI yang faham gambar + teks (Qwen3-VL) |
| **pg_trgm** | Extension PostgreSQL untuk padanan teks kabur (trigram) |
| **MethodChannel** | Jambatan Flutter ↔ kod native Android (Kotlin) |
| **RunPod** | Perkhidmatan sewa GPU awan |

---

**Pembangun:** Hilman — mohamadhilman1620@gmail.com
