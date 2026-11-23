# SMART SandboxPlayground — Complete Project Guide
> Panduan lengkap dari zero: Mobile App + Backend + Data Pipeline
> Written by Claude for Hilman — future mobile developer 🚀

---

## Daftar Isi

1. [Apa Projek Ni?](#1-apa-projek-ni)
2. [Tech Stack — Tools Yang Digunakan](#2-tech-stack)
3. [Architecture — Macam Mana Semua Benda Connect](#3-architecture)
4. [Part 1: Data Pipeline (Halal JAKIM Data)](#4-part-1-data-pipeline)
5. [Part 2: Backend API](#5-part-2-backend-api)
6. [Part 3: Mobile App (Flutter)](#6-part-3-mobile-app-flutter)
7. [Cara Run Semua](#7-cara-run-semua)
8. [Cara Debug](#8-cara-debug)
9. [Cara Tambah Feature Baru](#9-cara-tambah-feature-baru)
10. [Git — Simpan & Track Code](#10-git)
11. [Jadi Mobile Developer — Next Steps](#11-next-steps)

---

## 1. Apa Projek Ni?

**SMART SandboxPlayground** adalah sebuah **Super App untuk Umat Islam** — satu app yang ada banyak features dalam satu tempat.

### Features dalam app:
| Feature | Apa buat |
|---|---|
| Halal Checker | Search produk halal dari database JAKIM |
| Scan Product | Ambil gambar label produk → AI cari halal status |
| Qibla Compass | Tunjuk arah kiblat guna GPS + kompas |
| Prayer Times | Waktu solat ikut lokasi GPS |
| Tasbeeh | Tasbih digital untuk zikir |
| Hijri Calendar | Takwim Islam |

### Kenapa projek ni menarik?
Ini bukan sekadar app biasa. Di belakang app ni ada:
- **AI/LLM pipeline** yang process 348K data JAKIM
- **Web scraper** yang crawl data dari portal kerajaan
- **REST API backend** yang serve data ke app
- **Sensor integration** (GPS, kompas, accelerometer)

---

## 2. Tech Stack

### Analogy mudah:
```
Flutter    = badan kereta (UI yang user nampak)
Dart       = enjin kereta (language yang drive Flutter)
FastAPI    = kilang (server yang proses request)
PostgreSQL = gudang (simpan semua data)
Python     = pekerja kilang (buat semua kerja backend)
Qwen/LLM   = AI assistant yang bersihkan data
```

### Full Stack Overview:
```
┌─────────────────────────────────────────────────────────┐
│                    USER PHONE                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │          Flutter App (Dart)                     │   │
│  │  • UI Screens    • GPS Sensor                   │   │
│  │  • API calls     • Compass Sensor               │   │
│  └──────────────────┬──────────────────────────────┘   │
└─────────────────────│───────────────────────────────────┘
                      │ HTTP Request (internet/wifi)
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   SERVER (Cloud)                        │
│  ┌─────────────┐    ┌──────────────────────────────┐   │
│  │  FastAPI    │    │  PostgreSQL Database         │   │
│  │  (api.py)   │◄───│  halal_products table        │   │
│  │             │    │  348,778 rows                │   │
│  └─────────────┘    └──────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Architecture

### Macam mana semua benda connect:

```
JAKIM Website
     │
     │ halal_scraper.py (web crawling)
     ▼
halal_data.csv (raw data 348K rows)
     │
     │ import_to_db.py
     ▼
PostgreSQL Database
     │
     │ ner_pipeline.py (AI cleaning)
     │ Qwen3.6-27B LLM (LM Studio, DGX Server)
     ▼
PostgreSQL (cleaned data — company_ner, brand_ner, etc.)
     │
     │ export_clean.py
     ▼
halal_data_cleaned.csv
     │
     │ FastAPI (api.py)
     ▼
REST API Endpoints (/search, /scan-image)
     │
     │ HTTP calls dari Flutter
     ▼
Flutter Mobile App → User nampak results
```

---

## 4. Part 1: Data Pipeline

### 4.1 Web Scraping — `halal_scraper.py`

**Apa itu web scraping?**
Scraping = suruh Python buka website macam browser, baca HTML, extract data.

```
Browser manusia:        Python scraper:
1. Buka chrome          1. requests.get(url)
2. Nampak UI cantik     2. dapat raw HTML
3. Baca data            3. BeautifulSoup parse HTML
4. Copy manual          4. Save ke CSV automatik
```

**Kenapa pakai POST, bukan GET?**
JAKIM portal guna JavaScript form submission. Bila klik page number:
- JavaScript set hidden field `ty=PR` (category code)
- POST request hantar form ke server
- Server return HTML dengan data

```python
# GET = ambil page biasa
requests.get("https://example.com/page")

# POST = hantar form dengan data
requests.post("https://example.com/page", data={"ty": "PR"})
```

**Kenapa ada LegacySSLAdapter?**
Government servers selalu guna SSL/TLS version lama. Python by default reject connection lama ni (security risk). Kita lower the security level sikit supaya boleh connect:

```python
class LegacySSLAdapter(HTTPAdapter):
    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        ctx.set_ciphers('DEFAULT:@SECLEVEL=1')  # lower dari SECLEVEL=2
        kwargs['ssl_context'] = ctx
        return super().init_poolmanager(*args, **kwargs)
```

**Kenapa kena ada delay antara requests?**
Kalau hantar 17,000 requests dalam masa singkat → server detect bot → IP kena ban.
Human browsing = 2-5 saat per page. Kita simulate tu:

```python
time.sleep(random.uniform(2.0, 5.0))  # random macam manusia
```

**Produk category — kenapa field dia lain?**
JAKIM portal pakai HTML structure sama untuk semua category, tapi untuk Produk:
- `company-name` span = nama produk (bukan company!)
- `company-address` span = nama manufacturer (bukan address!)

Kita fix ni dalam scraper:
```python
if category_name == 'Produk':
    company      = raw_address   # address span actually = manufacturer
    product_name = raw_product or raw_name
    address      = ''
else:
    company      = raw_name
    product_name = raw_product
    address      = raw_address
```

---

### 4.2 Database Setup — PostgreSQL

**Apa itu PostgreSQL?**
PostgreSQL = sistem untuk simpan data dalam bentuk table (macam Excel tapi lebih powerful).

```sql
-- Ini macam Excel sheet tapi dalam database
CREATE TABLE halal_products (
    id          SERIAL PRIMARY KEY,    -- auto-number (1, 2, 3...)
    category    VARCHAR(100),          -- "Produk", "Syarikat" etc.
    comp_code   VARCHAR(100),          -- JAKIM certificate code
    company     TEXT,                  -- nama syarikat
    brand       TEXT,                  -- nama brand
    product_name TEXT,                 -- nama produk
    address     TEXT,                  -- alamat
    expiry      TEXT,                  -- tarikh luput sijil
    -- NER columns (hasil AI cleaning):
    company_ner  TEXT,
    brand_ner    TEXT,
    product_ner  TEXT,
    address_ner  TEXT,
    ner_confidence FLOAT,
    ner_status   VARCHAR(20) DEFAULT 'pending'
);
```

**Commands PostgreSQL yang selalu guna:**
```bash
# Connect ke database
psql -U postgres -d halal_jakim

# Check berapa rows
SELECT COUNT(*) FROM halal_products;

# Check progress NER
SELECT ner_status, COUNT(*) FROM halal_products GROUP BY ner_status;

# Reset error rows
UPDATE halal_products SET ner_status='pending' WHERE ner_status='error';
```

---

### 4.3 Import Data — `import_to_db.py`

**Apa itu pandas?**
pandas = library Python untuk manipulate data dalam bentuk table (DataFrame).

```python
import pandas as pd

# Load CSV ke DataFrame (macam Excel dalam Python)
df = pd.read_csv('halal_data.csv')

# Tengok 5 rows pertama
print(df.head())

# Berapa rows?
print(len(df))  # 348,778

# Hantar ke PostgreSQL
df.to_sql('halal_products', engine, if_exists='append', index=False)
```

**Apa itu SQLAlchemy?**
SQLAlchemy = jambatan antara Python dan database. Kita tak perlu tulis SQL raw, boleh guna Python objects.

```python
from sqlalchemy import create_engine

# Connection string format:
# postgresql://username:password@host:port/database
engine = create_engine("postgresql://postgres:password@localhost:5432/halal_jakim")
```

---

### 4.4 NER Pipeline — `ner_pipeline.py`

**Apa itu NER (Named Entity Recognition)?**
NER = AI classify text jadi categories.

```
Input:  "SHEKIN FOOD SDN BHD, JENAMA: SHEKIN, ABOK-ABOK"
Output: company="SHEKIN FOOD SDN BHD", brand="SHEKIN", product="ABOK-ABOK"
```

**Macam mana pipeline berfungsi:**

```
Step 1: Fetch 100 rows dari PostgreSQL (WHERE ner_status='pending')
Step 2: Preprocess — clean strings (strip whitespace, remove JENAMA: prefix)
Step 3a: is_clean() check — kalau row dah clean, skip API call (confidence=0.90)
Step 3b: Kalau perlu cleaning → hantar ke Qwen3 via LM Studio API
Step 4: Parse JSON response dari Qwen
Step 5: Save results ke PostgreSQL (UPDATE ner_status='done')
Step 6: Repeat sampai habis semua rows
```

**Apa itu LLM (Large Language Model)?**
LLM = AI yang faham dan generate teks. Kita guna Qwen3.6-27B (27 billion parameters).

```
"27B parameters" = 27 billion numbers dalam neural network
Lebih besar = lebih bijak, tapi lebih slow dan perlu lebih RAM
Qwen3.6-27B perlu ~20GB RAM minimum
```

**Kenapa pakai LM Studio?**
LM Studio = software yang run LLM models locally/on-server.
Company kita ada DGX Spark server (NVIDIA GPU, 128GB RAM) yang run LM Studio.
API dia compatible dengan OpenAI format, so kita boleh guna OpenAI Python library.

**Kenapa perlu custom User-Agent header?**
```python
LM_HEADERS = {"User-Agent": "curl/8.6.0"}
```
Server (Nginx reverse proxy) block default User-Agent Python pakai (`AsyncOpenAI/Python`).
Bila kita tukar ke `curl/8.6.0`, server bagi laluan. Tip dari staff IT.

**Rule-based skip — macam mana buat:**
```python
COMPANY_RE = re.compile(r'\b(SDN BHD|BERHAD|ENTERPRISE|TRADING...)\b')
ADDRESS_RE  = re.compile(r'\b(JALAN|LORONG|TAMAN...)\b|\b\d{5}\b')

def is_clean(row):
    # Kalau company ada suffix SDN BHD etc. → probably clean
    if not COMPANY_RE.search(row['company']):
        return False
    # Kalau address ada Jalan/postcode → probably real address
    if row['address'] and not ADDRESS_RE.search(row['address']):
        return False
    return True
```

**Concurrent threading — macam mana buat:**
```python
from concurrent.futures import ThreadPoolExecutor, as_completed

# 3 API calls at the same time instead of 1
with ThreadPoolExecutor(max_workers=3) as executor:
    # Submit all rows as tasks
    futures = {executor.submit(call_qwen, row): row for row in api_rows}
    
    # Collect results as they complete
    for future in as_completed(futures):
        result = future.result()
```

---

### 4.5 Export — `export_clean.py`

Selepas pipeline habis, export cleaned data:
```bash
python export_clean.py
# Output: halal_data_cleaned.csv
```

Uses COALESCE — ambil NER result kalau ada, otherwise ambil original:
```sql
SELECT
    COALESCE(NULLIF(company_ner, ''), company) AS company,
    COALESCE(NULLIF(brand_ner,   ''), brand)   AS brand
FROM halal_products
WHERE ner_status = 'done'
```

---

## 5. Part 2: Backend API

### Apa itu REST API?

**Analogy:**
```
Restaurant:                    API:
Pelanggan order makanan    =   App hantar HTTP request
Waiter ambil order         =   API receive request
Dapur masak                =   Server process (query DB, etc.)
Waiter bagi makanan        =   API return JSON response
```

**HTTP Methods:**
```
GET    = ambil data          (baca menu restaurant)
POST   = hantar data baru    (buat order baru)
PUT    = update data          (tukar order)
DELETE = padam data           (cancel order)
```

### FastAPI — `api.py`

```python
from fastapi import FastAPI

app = FastAPI()

# GET /search?q=milo
@app.get("/search")
async def search(q: str, limit: int = 50):
    # Query PostgreSQL
    results = db.execute("SELECT * FROM halal_products WHERE company ILIKE %s", f"%{q}%")
    return results

# POST /scan-image
@app.post("/scan-image")
async def scan_image(file: UploadFile):
    # 1. OCR extract text dari gambar
    # 2. Search dalam database
    return {"results": [...]}
```

### Cara run backend:
```bash
uvicorn api:app --reload --host 0.0.0.0 --port 8000
```

### API Endpoints:
| Method | Endpoint | Function |
|---|---|---|
| GET | `/search?q=milo` | Search halal products |
| POST | `/scan-image` | Upload image for OCR |
| GET | `/product/{id}` | Get single product detail |

---

## 6. Part 3: Mobile App (Flutter)

### 6.1 Apa itu Flutter?

Flutter = Google's framework untuk build mobile app (Android + iOS) guna satu codebase.

```
Tanpa Flutter:              Dengan Flutter:
Android app (Kotlin/Java)   ┐
                            ├── Satu code → Flutter compile ke kedua-dua
iOS app (Swift)             ┘
```

### 6.2 Dart Language Basics

```dart
// Variable
String name = "Hilman";
int age = 22;
bool isLoading = false;
double confidence = 0.95;

// List (array)
List<String> categories = ["Produk", "Syarikat", "Hotel"];

// Map (dictionary)
Map<String, dynamic> product = {
  "company": "NESTLE",
  "brand": "MILO",
};

// Function
String getGreeting(String name) {
  return "Hello, $name!";  // string interpolation dengan $
}

// Async function (untuk API calls)
Future<List<Product>> fetchProducts() async {
  final response = await http.get(Uri.parse(url));  // await = tunggu response
  return parseJson(response.body);
}
```

### 6.3 Widget — Building Blocks Flutter

Dalam Flutter, SEMUA benda adalah Widget.

```dart
// Widget = component UI
Text("Hello")          // teks
Icon(Icons.home)       // icon
Image.network(url)     // gambar dari internet
ElevatedButton(...)    // button
TextField(...)         // input box
ListView(...)          // scrollable list
Column(...)            // susun vertical
Row(...)               // susun horizontal
Container(...)         // box dengan padding/margin/color
Scaffold(...)          // layout screen (appbar + body)
```

**Widget tree — macam HTML:**
```
Scaffold
  ├── AppBar
  │     └── Text("Halal Checker")
  └── Body
        └── Column
              ├── TextField (search bar)
              └── ListView
                    ├── ProductCard("MILO")
                    ├── ProductCard("NESCAFE")
                    └── ProductCard("MAGGI")
```

### 6.4 StatelessWidget vs StatefulWidget

```dart
// StatelessWidget = TIDAK berubah selepas dibina
// Guna untuk: cards, icons, static text
class ProductCard extends StatelessWidget {
  final String company;
  
  const ProductCard({required this.company});
  
  @override
  Widget build(BuildContext context) {
    return Card(child: Text(company));
  }
}

// StatefulWidget = BOLEH berubah (ada state)
// Guna untuk: loading spinner, search results, countdown
class SearchScreen extends StatefulWidget {
  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  bool _isLoading = false;
  List<Product> _results = [];
  
  void _search(String query) async {
    setState(() { _isLoading = true; });  // trigger rebuild UI
    
    _results = await ApiService.search(query);
    
    setState(() { _isLoading = false; });  // trigger rebuild lagi
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _isLoading 
        ? CircularProgressIndicator()   // kalau loading
        : ListView(children: _results.map((p) => ProductCard(p.company)).toList()),
    );
  }
}
```

### 6.5 Project File Structure

```
lib/
├── main.dart                 ← Entry point + core models + API service
│
├── screens/                  ← Satu file = satu screen
│   ├── home_screen.dart      ← Dashboard (6 feature tiles)
│   ├── prayer_screen.dart    ← Waktu solat
│   ├── qibla_screen.dart     ← Kompas kiblat
│   ├── scanner_screen.dart   ← Scan produk
│   ├── tasbeeh_screen.dart   ← Tasbih digital
│   ├── calendar_screen.dart  ← Takwim hijri
│   └── settings_screen.dart  ← Tetapan
│
├── services/                 ← Logic, bukan UI
│   ├── prayer_service.dart   ← Kira waktu solat
│   ├── location_service.dart ← GPS
│   └── notification_service.dart ← Push notification
│
└── widgets/                  ← Reusable components
    └── location_picker_dialog.dart
```

### 6.6 main.dart — Entry Point

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Init timezone untuk notifications
  tz.initializeTimeZones();
  
  // Init SharedPreferences (local storage)
  gPrefs = await SharedPreferences.getInstance();
  
  // Start app
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SMART',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Color(0xFF2E7D32)),
      ),
      home: const HomeScreen(),  // screen pertama
    );
  }
}
```

### 6.7 Data Models

```dart
// Model = blueprint untuk data dari API
class HalalProduct {
  final int id;
  final String company;
  final String brand;
  final String productName;
  final String category;
  final String expiry;
  
  // Constructor
  HalalProduct({
    required this.id,
    required this.company,
    // ...
  });
  
  // Parse dari JSON (API response)
  factory HalalProduct.fromJson(Map<String, dynamic> json) {
    return HalalProduct(
      id:          json['id'] ?? 0,
      company:     json['company'] ?? '',
      brand:       json['brand'] ?? '',
      productName: json['product_name'] ?? '',
      category:    json['category'] ?? '',
      expiry:      json['expiry'] ?? '',
    );
  }
}
```

### 6.8 API Service

```dart
class ApiService {
  static String get apiBaseUrl => 
    gPrefs.getString('api_url') ?? 'https://halal-api.careerlab.my';
  
  // Search products
  static Future<List<HalalProduct>> search(String query) async {
    final uri = Uri.parse('$apiBaseUrl/search')
        .replace(queryParameters: {'q': query, 'limit': '50'});
    
    final response = await http.get(uri);
    
    if (response.statusCode == 200) {
      final List data = json.decode(response.body);
      return data.map((item) => HalalProduct.fromJson(item)).toList();
    }
    return [];
  }
  
  // Scan image
  static Future<ScanResult?> scanImage(File imageFile) async {
    final request = http.MultipartRequest(
      'POST', 
      Uri.parse('$apiBaseUrl/scan-image')
    );
    request.files.add(await http.MultipartFile.fromPath('file', imageFile.path));
    
    final response = await request.send();
    // parse response...
  }
}
```

### 6.9 Navigation

```dart
// Pergi ke screen baru
Navigator.push(
  context,
  MaterialPageRoute(builder: (_) => DetailScreen(product: product)),
);

// Balik
Navigator.pop(context);

// Replace screen (tak boleh balik)
Navigator.pushReplacement(
  context,
  MaterialPageRoute(builder: (_) => HomeScreen()),
);
```

### 6.10 Sensors

**GPS (geolocator package):**
```dart
import 'package:geolocator/geolocator.dart';

Future<Position> getLocation() async {
  // Check permission
  LocationPermission permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
  }
  
  // Get position
  return await Geolocator.getCurrentPosition(
    desiredAccuracy: LocationAccuracy.high,
  );
}
// position.latitude, position.longitude
```

**Compass (flutter_compass package):**
```dart
import 'package:flutter_compass/flutter_compass.dart';

StreamBuilder<CompassEvent>(
  stream: FlutterCompass.events,
  builder: (context, snapshot) {
    double? heading = snapshot.data?.heading;  // 0-360 degrees
    // 0 = North, 90 = East, 180 = South, 270 = West
    return Transform.rotate(
      angle: heading! * (pi / 180),  // convert degrees to radians
      child: Image.asset('assets/compass.png'),
    );
  },
)
```

**Geomagnetic Declination (Android native):**
Untuk arah kiblat yang tepat, kita perlu magnetic declination (beza antara magnetic north dan true north). Flutter tak ada API ni — kita kena guna Android native via MethodChannel:

```
Dart → MethodChannel → Kotlin (Android) → GeomagneticField API → balik ke Dart
```

```dart
// Dart side
static const _channel = MethodChannel('smart.app/geomagnetic');

final declination = await _channel.invokeMethod<double>('getDeclination', {
  'lat': latitude,
  'lon': longitude,
  'altitude': altitude,
  'time': DateTime.now().millisecondsSinceEpoch,
});
```

```kotlin
// Kotlin side (MainActivity.kt)
MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "smart.app/geomagnetic")
  .setMethodCallHandler { call, result ->
    if (call.method == "getDeclination") {
      val lat  = call.argument<Double>("lat")!!.toFloat()
      val lon  = call.argument<Double>("lon")!!.toFloat()
      val alt  = call.argument<Double>("altitude")!!.toFloat()
      val time = call.argument<Long>("time")!!
      
      val field = GeomagneticField(lat, lon, alt, time)
      result.success(field.declination.toDouble())
    }
  }
```

### 6.11 pubspec.yaml — Dependencies

```yaml
dependencies:
  flutter:
    sdk: flutter
  
  # HTTP calls ke backend
  http: ^1.6.0
  
  # Kamera dan gallery
  image_picker: ^1.1.2
  
  # Local storage (settings)
  shared_preferences: ^2.3.5
  
  # GPS
  geolocator: ^14.0.0
  
  # Convert GPS coordinates → nama tempat
  geocoding: ^4.0.0
  
  # Kompas magnetik
  flutter_compass: ^0.8.1
  
  # Accelerometer, gyroscope
  sensors_plus: ^6.1.1
  
  # Tarikh Hijri
  hijri: ^3.0.0
  
  # Push notification (azan)
  flutter_local_notifications: ^18.0.1
  
  # Timezone handling
  timezone: ^0.10.0
```

Selepas tambah/tukar package:
```bash
flutter pub get   # install packages
```

---

## 7. Cara Run Semua

### Setup pertama kali:

```bash
# 1. Install Flutter (download dari flutter.dev)
# 2. Install Dart (comes with Flutter)
# 3. Install Android Studio (untuk emulator/Android SDK)
# 4. Install VS Code + Flutter extension

# 5. Check semua ok
flutter doctor
```

### Run setiap bahagian:

**Database (PostgreSQL):**
```bash
# Windows — start PostgreSQL service
# (usually auto-start, check Services)

# Test connection
psql -U postgres -d halal_jakim -c "SELECT COUNT(*) FROM halal_products;"
```

**Backend API:**
```bash
cd "SMART SandboxPlayground"
uvicorn api:app --reload --host 0.0.0.0 --port 8000
# API ready at http://localhost:8000
# Docs at http://localhost:8000/docs
```

**NER Pipeline (bila perlu):**
```bash
python ner_pipeline.py
```

**Flutter App:**
```bash
cd "SMART SandboxPlayground"
flutter pub get        # install packages (first time)
flutter run            # run in debug mode
```

### Hot Reload vs Hot Restart:

```
Hot Reload   (r)  = update UI tanpa restart app — FAST (< 1 second)
Hot Restart  (R)  = restart app state — slower
Full Restart      = stop dan start semula
```

---

## 8. Cara Debug

### Flutter Debug:

```dart
// Print ke console
print("Value: $variable");
debugPrint("Debug: $variable");  // lebih safe untuk long strings

// Conditional check
assert(variable != null, "variable should not be null");
```

### Common Errors dan Fix:

**Error 1: `Connection refused`**
```
Punca: Backend server tak run
Fix: uvicorn api:app --reload
```

**Error 2: `Null check operator used on null value`**
```dart
// SALAH — crash kalau null
String name = product.company!;

// BETUL — safe dengan default value
String name = product.company ?? 'Unknown';
```

**Error 3: `setState() called after dispose()`**
```dart
// SALAH — screen dah tutup tapi still update state
void loadData() async {
  final data = await fetchData();
  setState(() { _data = data; }); // ← CRASH kalau screen dah tutup
}

// BETUL
void loadData() async {
  final data = await fetchData();
  if (mounted) {  // check dulu screen masih ada
    setState(() { _data = data; });
  }
}
```

**Error 4: `MissingPluginException`**
```
Punca: Package baru ditambah tapi tak flutter pub get
Fix: flutter pub get, then restart app (bukan hot reload)
```

**Error 5: `Permissions denied` (GPS/Camera)**
```xml
<!-- android/app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.CAMERA"/>
```

### Python Debug:

```python
# Print untuk debug
print(f"[DEBUG] Variable: {variable}")

# Try-except untuk catch errors
try:
    result = some_function()
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()  # full stack trace
```

---

## 9. Cara Tambah Feature Baru

### Contoh: Tambah "Doa Harian" feature

**Step 1: Buat screen baru**
```bash
# Buat fail baru
# lib/screens/doa_screen.dart
```

```dart
import 'package:flutter/material.dart';

class DoaScreen extends StatefulWidget {
  const DoaScreen({super.key});

  @override
  State<DoaScreen> createState() => _DoaScreenState();
}

class _DoaScreenState extends State<DoaScreen> {
  final List<Map<String, String>> _doas = [
    {'title': 'Doa Makan', 'arabic': 'بِسْمِ اللَّهِ', 'rumi': 'Bismillah'},
    {'title': 'Doa Tidur', 'arabic': 'بِاسْمِكَ اللَّهُمَّ', 'rumi': 'Bismika Allahumma'},
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Doa Harian')),
      body: ListView.builder(
        itemCount: _doas.length,
        itemBuilder: (context, index) {
          return ListTile(
            title: Text(_doas[index]['title']!),
            subtitle: Text(_doas[index]['rumi']!),
          );
        },
      ),
    );
  }
}
```

**Step 2: Tambah dalam HomeScreen**
```dart
// lib/screens/home_screen.dart
// Dalam _featureTiles() function, tambah:
_FeatureInfo(
  label: 'Doa Harian',
  icon: Icons.menu_book_rounded,
  color: const Color(0xFF8B4513),
  subtitle: 'Kumpulan doa harian',
  screen: const DoaScreen(),
),
```

**Step 3: Import**
```dart
// Di bahagian atas home_screen.dart
import 'doa_screen.dart';
```

**Step 4: Test**
```bash
flutter run
# Tekan 'r' untuk hot reload
```

---

## 10. Git

### Apa itu Git?
Git = sistem untuk track perubahan dalam code. Macam "save history" untuk code.

```
Tanpa Git:              Dengan Git:
code_v1.dart            main branch (production)
code_v2.dart            ├── feature/doa-screen
code_v2_fixed.dart      └── fix/compass-bug
code_FINAL.dart
code_FINAL_v2.dart ← chaos!
```

### Commands Git yang selalu guna:

```bash
# Check status — fail mana yang dah berubah
git status

# Tengok perubahan
git diff

# Stage fail untuk commit
git add lib/screens/doa_screen.dart
git add .   # add semua fail

# Commit — save snapshot
git commit -m "Add doa harian screen"

# Push ke GitHub/GitLab
git push

# Pull update terbaru dari server
git pull

# Buat branch baru untuk feature
git checkout -b feature/doa-screen

# Switch ke branch lain
git checkout main
```

### .gitignore — fail yang tak perlu di-track:
```
# build files
build/
.dart_tool/

# environment (jangan commit password!)
.env
*.env

# OS files
.DS_Store
Thumbs.db
```

---

## 11. Jadi Mobile Developer — Next Steps

### Apa yang dah kau belajar dari projek ni:

```
✅ Flutter basics (Widget, State, Navigation)
✅ Dart language
✅ REST API calls (http package)
✅ GPS + Compass sensors
✅ Android native bridge (MethodChannel)
✅ Python scripting
✅ PostgreSQL + SQL
✅ Web scraping
✅ LLM/AI API integration
✅ Data pipeline
✅ Problem solving (debugging, fixing errors)
```

### Roadmap Mobile Developer:

```
Sekarang (Intern):
  ✅ Flutter basics
  ✅ API integration
  ✅ GPS/Sensor
  
Next (3-6 bulan):
  → State management (Provider/Riverpod/Bloc)
  → Firebase (auth, database, push notification)
  → App deployment (Play Store/App Store)
  → Unit testing
  
Intermediate (6-12 bulan):
  → Custom animations
  → Platform channels (lebih dalam)
  → Performance optimization
  → CI/CD pipeline
  
Senior (1-2 tahun):
  → Architecture patterns (Clean Architecture, MVVM)
  → Multi-module apps
  → Lead team
```

### Resources untuk belajar:

| Resource | Untuk apa |
|---|---|
| flutter.dev/docs | Official docs — paling reliable |
| pub.dev | Cari Flutter packages |
| stackoverflow.com | Debug errors |
| youtube: "Flutter Mapp" | Video tutorials |
| dart.dev | Belajar Dart language |

### Tips dari pengalaman projek ni:

1. **Google setiap error** — hampir semua error dah ada jawapan di StackOverflow
2. **Baca error message betul-betul** — error message selalu cakap exactly apa masalah
3. **Test sikit-sikit** — jangan tulis 100 baris lepas baru test, test setiap 10 baris
4. **Git commit selalu** — commit setiap kali ada sesuatu yang berfungsi
5. **Tanya Claude** — AI boleh explain, debug, dan guide step by step

---

## Quick Reference

### Files penting dan fungsi:

| File | Fungsi |
|---|---|
| `lib/main.dart` | Entry point, data models, API service |
| `lib/screens/home_screen.dart` | Dashboard utama |
| `lib/screens/qibla_screen.dart` | Kompas kiblat |
| `android/.../MainActivity.kt` | Android sensor bridge |
| `pubspec.yaml` | Package dependencies |
| `api.py` | Backend FastAPI server |
| `ner_pipeline.py` | AI data cleaning pipeline |
| `halal_scraper.py` | Web crawler untuk JAKIM data |
| `import_to_db.py` | Import CSV ke PostgreSQL |
| `export_clean.py` | Export cleaned data ke CSV |

### Commands yang selalu guna:

```bash
# Flutter
flutter pub get          # install packages
flutter run              # run app
flutter build apk        # build APK

# Python backend
uvicorn api:app --reload # start API server
python ner_pipeline.py   # run NER pipeline

# Database
psql -U postgres -d halal_jakim   # connect ke DB
SELECT ner_status, COUNT(*) FROM halal_products GROUP BY ner_status;  # check progress

# Git
git status    # check changes
git add .     # stage all
git commit -m "message"   # commit
git push      # push to remote
```

---

---

## 12. Performance Optimization — Search Laju

### 12.1 Masalah Asal: Search Lambat

Bila user search "milo", app ambil masa 3-5 saat untuk keluar results. Punca sebenar bukan satu — ada tiga lapisan masalah:

```
Lapisan 1: SQL terlalu lambat     → ILIKE tanpa index = full table scan
Lapisan 2: Connection baru tiap kali → psycopg2.connect() ambil 100-200ms
Lapisan 3: Response terlalu besar → hantar ribuan produk sekaligus ke phone
```

### 12.2 Fix 1: Trigram Index (pg_trgm)

**Apa itu index dalam database?**
Index = macam index dalam buku. Tanpa index, kena baca semua muka surat (348K rows) untuk cari perkataan. Dengan index, terus lompat ke halaman betul.

**Kenapa ILIKE perlukan trigram index khusus?**

```sql
-- Query yang kita buat:
WHERE company_ner ILIKE '%milo%'
--                       ↑ ada wildcard DI DEPAN

-- B-tree index biasa (yang PostgreSQL buat secara default):
-- Boleh guna untuk: WHERE company = 'MILO' (exact match)
-- Boleh guna untuk: WHERE company LIKE 'MILO%' (wildcard BELAKANG sahaja)
-- TIDAK boleh guna: WHERE company LIKE '%MILO%' (wildcard DEPAN)
-- Sebab: B-tree sort alphabetically, tak tahu nak start dari mana
```

**Trigram (pg_trgm) = cara lain index teks:**

Trigram = pecah perkataan kepada 3-letter chunks:
```
"MILO" → ['MIL', 'ILO']
"NESTLE MILO" → ['NES', 'EST', 'STL', 'TLE', ' MI', 'MIL', 'ILO']
```

PostgreSQL simpan semua chunks ni dalam index (GIN = Generalized Inverted Index).
Bila search `%milo%`, PostgreSQL cari chunk `MIL` dan `ILO` → terus jumpa rows yang ada kedua-dua chunks.

**Cara buat trigram index:**
```sql
-- Step 1: Enable extension (sekali je, permanent)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Step 2: Buat GIN index untuk setiap column yang di-search
CREATE INDEX idx_company_ner_trgm
  ON halal_products USING GIN (company_ner gin_trgm_ops);

CREATE INDEX idx_brand_ner_trgm
  ON halal_products USING GIN (brand_ner gin_trgm_ops);

CREATE INDEX idx_product_ner_trgm
  ON halal_products USING GIN (product_ner gin_trgm_ops);
```

**Kenapa tak guna CONCURRENTLY?**
```sql
-- CONCURRENTLY = bina index tanpa lock table
-- Masalah: Tak boleh run dalam transaction block
-- pgAdmin auto-wrap semua query dalam transaction
-- Penyelesaian: guna tanpa CONCURRENTLY (lock sekejap, ok untuk development)
CREATE INDEX idx_company_ner_trgm ON halal_products USING GIN (company_ner gin_trgm_ops);
```

**Hasil:**
```
Sebelum index: Seq Scan → 557ms
Selepas index: Bitmap Index Scan → 4ms
Improvement: 125x laju!
```

**Cara verify index dipakai:**
```sql
EXPLAIN ANALYZE
SELECT company_ner, COUNT(*)
FROM halal_products
WHERE company_ner ILIKE '%milo%'
GROUP BY company_ner;

-- Tengok output:
-- "Seq Scan" = index TAK dipakai (lambat)
-- "Bitmap Index Scan using idx_company_ner_trgm" = index DIPAKAI (laju)
```

---

### 12.3 Fix 2: Connection Pool

**Masalah: buka connection baru setiap request**

```python
# Cara lama (api.py asal):
def get_db_connection():
    conn = psycopg2.connect(**DB_CONFIG)  # ← buka connection baru = 100-200ms
    return conn

# Setiap kali user search → buka connection → query → tutup connection
# Connection handshake ambil masa lama!
```

**Penyelesaian: Connection Pool**

Pool = simpan beberapa connection yang sedia guna. Macam taxi standby, bukan panggil dari jauh setiap kali.

```python
import psycopg2.pool

# Buka 2-10 connections SEKALI masa start server
_db_pool = psycopg2.pool.ThreadedConnectionPool(
    minconn=2,   # minimum 2 connections sentiasa ready
    maxconn=10,  # maximum 10 connections boleh buka serentak
    **DB_CONFIG,
)

# Ambil connection dari pool (pantas — dah ready)
def get_db_connection():
    return _db_pool.getconn()

# Pulangkan balik ke pool (bukan tutup — simpan untuk guna lagi)
def release_db_connection(conn):
    _db_pool.putconn(conn)
```

**Kenapa pakai ThreadedConnectionPool dan bukan SimpleConnectionPool?**
FastAPI handle multiple requests serentak (concurrent). `ThreadedConnectionPool` thread-safe — boleh guna dari multiple threads tanpa conflict. `SimpleConnectionPool` hanya untuk single-thread.

**Penting: Kena pulangkan connection lepas guna!**
```python
# Setiap endpoint kena panggil release selepas selesai
conn = get_db_connection()
try:
    cur = conn.cursor()
    cur.execute("SELECT ...")
    rows = cur.fetchall()
    cur.close()
finally:
    release_db_connection(conn)  # ← MESTI pulangkan, kalau tidak pool penuh
```

---

### 12.4 Fix 3: Grouped Response — Hantar Syarikat Sahaja

**Masalah asal: Response terlalu besar**

Bila search "milo", `/search` return semua produk yang ada perkataan "milo" — boleh jadi ratusan records dengan semua fields (nama, alamat, expiry, confidence, etc.). Hantar semua ni ke phone melalui WiFi = lambat.

**Penyelesaian: Dua peringkat fetch**

```
Peringkat 1 (search screen):
  → Hantar query ke /search-grouped
  → Server return SYARIKAT sahaja (nama, bilangan produk)
  → Response kecil = laju

Peringkat 2 (bila tap syarikat):
  → Hantar query ke /company-products?company=NESTLE&q=milo
  → Server return produk untuk syarikat tersebut sahaja
  → User nampak loading spinner sebentar (acceptable)
```

---

## 13. Company-Grouped Search — Feature Baru

### 13.1 Konsep

Sebelum: Search "milo" → keluar flat list 50 produk (berulang-ulang)
Selepas: Search "milo" → keluar senarai syarikat yang ada produk milo → tap → keluar produk

Ini lebih user-friendly sebab:
- Nestle ada 50 produk milo → dalam satu card je
- User tahu terus "oh Nestle tu popular" tanpa scroll panjang

### 13.2 Endpoint Baru: `/search-grouped`

```python
@app.get("/search-grouped")
def search_grouped(q: str, limit: int = 50):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

    # SQL GROUP BY = kumpulkan rows yang sama company jadi satu baris
    # COUNT(*) = kira berapa produk dalam kumpulan tu
    # array_agg = kumpulkan semua categories jadi satu array
    cur.execute("""
        SELECT company_ner          AS company,
               COUNT(*)             AS product_count,
               array_agg(DISTINCT category) AS categories
        FROM halal_products
        WHERE company_ner ILIKE %s
           OR brand_ner   ILIKE %s
           OR product_ner ILIKE %s
        GROUP BY company_ner
        ORDER BY COUNT(*) DESC
        LIMIT 100
    """, (f"%{q}%", f"%{q}%", f"%{q}%"))
    
    rows = cur.fetchall()
    release_db_connection(conn)
    
    # Score each company by popularity
    result_list = []
    for row in rows:
        score = _popularity_score(row["company"])
        result_list.append({
            "company":          row["company"],
            "product_count":    row["product_count"],
            "popularity_score": score,
            "categories":       row["categories"],
            "products":         [],  # kosong — fetch bila tap
        })
    
    # Sort: syarikat popular dulu, then yang ada banyak produk
    result_list.sort(
        key=lambda x: (x["popularity_score"], x["product_count"]),
        reverse=True
    )
    
    return {"query": q, "company_count": len(result_list), "companies": result_list[:limit]}
```

**Cara SQL GROUP BY berfungsi:**
```sql
-- Data asal:
-- id | company_ner  | product_ner
-- 1  | NESTLE       | MILO 3IN1
-- 2  | NESTLE       | MILO ORIGINAL
-- 3  | DUTCH LADY   | MILO SUSU

-- Selepas GROUP BY company_ner:
-- company_ner | COUNT(*) | array_agg(categories)
-- NESTLE      | 2        | {Produk, Produk}
-- DUTCH LADY  | 1        | {Produk}
```

### 13.3 Popularity Score System

```python
# Dictionary syarikat terkenal Malaysia dengan score
POPULAR_COMPANIES = {
    'NESTLE':      100,
    'DUTCH LADY':   95,
    'F&N':          92,
    'GARDENIA':     90,
    'MASSIMO':      88,
    'CADBURY':      87,
    'MAMEE':        85,
    'APOLLO':       84,
    # ... dan lain-lain
}

def _popularity_score(company_name: str) -> int:
    upper = company_name.upper()
    best = 0
    for brand, score in POPULAR_COMPANIES.items():
        if brand in upper:          # cek kalau brand name ada dalam company name
            best = max(best, score) # ambil score tertinggi (kalau ada 2 match)
    return best

# Contoh:
# "NESTLE MALAYSIA SDN BHD" → 'NESTLE' ada dalam string → score 100
# "DUTCH LADY MILK INDUSTRIES" → 'DUTCH LADY' ada → score 95
# "ABC ENTERPRISE SDN BHD" → tiada match → score 0
```

### 13.4 Endpoint Baru: `/company-products`

Dipanggil bila user tap sebuah syarikat dalam app:

```python
@app.get("/company-products")
def company_products(company: str, q: str):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    
    cur.execute("""
        SELECT id, category, comp_code,
               company_ner AS company,
               brand_ner   AS brand,
               product_ner AS product_name,
               address_ner AS address,
               expiry, ner_confidence
        FROM halal_products
        WHERE company_ner = %s                           -- exact match untuk syarikat
          AND (company_ner ILIKE %s                      -- tapi product kena ada keyword
               OR brand_ner   ILIKE %s
               OR product_ner ILIKE %s)
        ORDER BY product_ner
    """, (company, f"%{q}%", f"%{q}%", f"%{q}%"))
    
    rows = cur.fetchall()
    release_db_connection(conn)
    return {"company": company, "query": q, "count": len(rows), "products": rows}
```

Perbezaan `= %s` vs `ILIKE %s`:
- `company_ner = %s` → exact match (case-sensitive), cari syarikat TEPAT sama nama
- `company_ner ILIKE %s` → case-insensitive, cari mana-mana yang ada substring

---

## 14. Flutter — Model & UI Untuk Grouped Search

### 14.1 Model Baru: `CompanyResult`

```dart
class CompanyResult {
  final String company;        // nama syarikat
  final int productCount;      // berapa produk dalam DB
  final int popularityScore;   // 0-100, dari POPULAR_COMPANIES dict
  final List<String> categories;
  final List<HalalProduct> products; // kosong masa search, isi bila tap

  factory CompanyResult.fromJson(Map<String, dynamic> json) {
    return CompanyResult(
      company:         json['company'] ?? '',
      productCount:    json['product_count'] ?? 0,
      popularityScore: json['popularity_score'] ?? 0,
      categories:      List<String>.from(json['categories'] ?? []),
      products:        (json['products'] as List? ?? [])
                         .map((e) => HalalProduct.fromJson(e))
                         .toList(),
    );
  }
}
```

### 14.2 API Service: `searchGrouped()`

```dart
static Future<List<CompanyResult>> searchGrouped(String query) async {
  final uri = Uri.parse('$apiBaseUrl/search-grouped').replace(
    queryParameters: {'q': query, 'limit': '50'},
  );
  
  final response = await http.get(uri);
  
  if (response.statusCode == 200) {
    final data   = jsonDecode(response.body);
    final list   = data['companies'] as List;
    return list.map((e) => CompanyResult.fromJson(e)).toList();
  }
  throw Exception('Search failed: ${response.statusCode}');
}
```

### 14.3 CompanyCard Widget

```dart
class CompanyCard extends StatelessWidget {
  final CompanyResult company;
  final String query;          // perlu untuk buka CompanyProductsScreen
  
  const CompanyCard({required this.company, required this.query});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: () {
          // Bila tap → buka screen produk untuk syarikat ni
          Navigator.push(context, MaterialPageRoute(
            builder: (_) => CompanyProductsScreen(
              company: company,
              query: query,
            ),
          ));
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            children: [
              Row(
                children: [
                  // Nama syarikat — bold, besar
                  Expanded(
                    child: Text(
                      company.company,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
                  ),
                  // Badge "HALAL ✓" hijau
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.green.shade50,
                      border: Border.all(color: Colors.green.shade300),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: const Text('HALAL ✓'),
                  ),
                ],
              ),
              // Baris kedua: bilangan produk + badge Popular
              Row(
                children: [
                  Text('${company.productCount} produk'),
                  if (company.popularityScore > 0)
                    Container(
                      // Badge amber "Popular" kalau score > 0
                      child: const Text('Popular'),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
```

### 14.4 CompanyProductsScreen — StatefulWidget dengan Lazy Load

**Kenapa StatefulWidget, bukan StatelessWidget?**

`StatelessWidget` = tak boleh berubah selepas dibina. Tapi kita perlu:
1. Show loading spinner (state: isLoading = true)
2. Fetch produk dari API (async operation)
3. Update UI bila data dah sampai (state: isLoading = false, products = [...])

Semua tu perlukan state yang boleh berubah → kena `StatefulWidget`.

```dart
class CompanyProductsScreen extends StatefulWidget {
  final CompanyResult company;
  final String query;

  const CompanyProductsScreen({required this.company, required this.query});

  @override
  State<CompanyProductsScreen> createState() => _CompanyProductsScreenState();
}

class _CompanyProductsScreenState extends State<CompanyProductsScreen> {
  List<HalalProduct> _products = [];
  bool _isLoading = true;
  String _error = '';

  @override
  void initState() {
    super.initState();
    _fetchProducts(); // auto-fetch bila screen dibuka
  }
  
  // initState() = dipanggil SEKALI bila screen mula-mula dibuka
  // Macam "constructor" untuk screen

  Future<void> _fetchProducts() async {
    try {
      final uri = Uri.parse('$apiBaseUrl/company-products').replace(
        queryParameters: {
          'company': widget.company.company,  // widget. untuk access parent fields
          'q':       widget.query,
        },
      );
      
      final response = await http.get(uri);
      
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final list = (data['products'] as List)
            .map((e) => HalalProduct.fromJson(e))
            .toList();
        
        setState(() {
          _products  = list;
          _isLoading = false;
        });
      }
    } catch (e) {
      setState(() {
        _error     = 'Tidak dapat sambung ke server.';
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.company.company)),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())  // spinner
          : _error.isNotEmpty
              ? Center(child: Text(_error))                   // error message
              : ListView.builder(                             // senarai produk
                  itemCount: _products.length,
                  itemBuilder: (context, i) => ProductCard(product: _products[i]),
                ),
    );
  }
}
```

**Flow lengkap:**
```
User search "milo"
    ↓
SearchScreen panggil ApiService.searchGrouped("milo")
    ↓
API /search-grouped → return 20 syarikat (kecil, laju)
    ↓
SearchScreen papar CompanyCard untuk setiap syarikat
    ↓
User tap "NESTLE MALAYSIA SDN BHD"
    ↓
Navigator.push → buka CompanyProductsScreen
    ↓
initState() dipanggil → _fetchProducts() start
    ↓
API /company-products?company=NESTLE...&q=milo → return produk
    ↓
setState() → UI update → papar senarai produk
```

---

## 15. Docker & Server Deployment

### 15.1 Apa itu Docker?

Docker = cara pack app dalam "container" — kotak yang ada semua yang diperlukan (Python, packages, settings) supaya app jalan sama di mana-mana komputer.

```
Tanpa Docker:
  "Jalan kat laptop saya tapi tak jalan kat server!" (masalah biasa)
  Sebab: version Python lain, package lain, OS lain

Dengan Docker:
  App + Python + semua packages → satu container
  Container tu jalan sama je di mana-mana
```

**Analogy:**
```
Tanpa Docker = masak di dapur sendiri, bawa makanan ke rumah orang
              (boleh jadi sejuk, tumpah, rasa lain)

Dengan Docker = masak dalam container sealed, hantar container tu
              (sampai dalam keadaan sama macam semula)
```

### 15.2 docker-compose.yml — Konfigurasi Project Kita

```yaml
services:
  db:
    image: postgres:15          # guna image PostgreSQL version 15 dari Docker Hub
    container_name: halal_db    # nama container untuk kita refer
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: Huffazpro1!
      POSTGRES_DB: halal_jakim
    volumes:
      - postgres_data:/var/lib/postgresql/data     # simpan data secara permanent
      - ./halal_jakim_dump.sql:/docker-entrypoint-initdb.d/01_import.sql  # import SQL masa mula
    ports:
      - "5431:5432"             # port laptop:port dalam container
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]  # check DB dah ready
      interval: 10s

  api:
    build: .                    # bina image dari Dockerfile dalam folder ni
    container_name: halal_api
    ports:
      - "8000:8000"
    depends_on:
      db:
        condition: service_healthy  # tunggu DB ready dulu sebelum start API

volumes:
  postgres_data:                # volume untuk simpan data DB secara permanent
```

**Kenapa ada dua services?**
- `db` = PostgreSQL database (data store)
- `api` = FastAPI server (web server)

Dipisahkan supaya boleh:
- Restart API tanpa kena restart database
- Scale API ke multiple instances kalau perlu
- Replace API version tanpa kehilangan data

### 15.3 Dockerfile — Cara Bina Image API

```dockerfile
FROM python:3.11-slim          # start dari Python base image

WORKDIR /app                   # semua file simpan dalam /app

# Install system dependencies (untuk psycopg2)
RUN apt-get update && apt-get install -y gcc libpq-dev

COPY requirements.txt .        # copy requirements.txt dulu (untuk caching)
RUN pip install --no-cache-dir -r requirements.txt  # install packages

COPY api.py .                  # copy code kita
COPY embeddings.npz .          # copy embeddings file

CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Kenapa copy requirements.txt dulu sebelum api.py?**
Docker guna layer caching. Kalau requirements.txt tak berubah, dia guna cache `pip install` yang lama. Kalau copy api.py dulu, setiap kali api.py berubah → pip install semula (lambat!).

### 15.4 Workflow Deploy Update

```
1. Edit api.py kat laptop
   ↓
2. SCP (Secure Copy) ke server
   scp "api.py" setup@192.168.1.37:/home/setup/halal-app/api.py
   ↓
3. SSH masuk server
   ssh setup@192.168.1.37
   ↓
4. Rebuild + restart container
   cd /home/setup/halal-app
   docker compose build api && docker compose up -d api
   ↓
5. Container baru running dengan code terbaru
```

**Kenapa rebuild pantas kali kedua?**
Docker cache layer `pip install` — packages tak berubah, jadi skip step tu. Hanya copy api.py baru dan bina layer tu sahaja.

### 15.5 Commands Docker Yang Selalu Guna

```bash
# Check status semua containers
docker compose ps

# Tengok logs API
docker compose logs api

# Tengok logs real-time
docker compose logs -f api

# Restart satu service
docker compose restart api

# Stop semua
docker compose down

# Start semula
docker compose up -d

# Masuk dalam container (untuk debug)
docker exec -it halal_api bash
docker exec -it halal_db psql -U postgres -d halal_jakim

# Run SQL dalam container DB
docker exec -it halal_db psql -U postgres -d halal_jakim -c "SELECT COUNT(*) FROM halal_products;"
```

### 15.6 Migrate NER Data ke Server

**Masalah:**
Database dalam Docker server ada schema lama (columns asal dari JAKIM). NER columns yang kita bersihkan (`company_ner`, `brand_ner` dll) hanya ada dalam laptop kita.

**Penyelesaian — Export dan Import via CSV:**

```bash
# LAPTOP (pgAdmin) — export NER columns sahaja
COPY (
  SELECT id, company_ner, brand_ner, product_ner, address_ner
  FROM halal_products
) TO 'C:/Temp/ner_export.csv' CSV HEADER;

# LAPTOP (PowerShell) — SCP ke server
scp "C:\Temp\ner_export.csv" setup@192.168.1.37:/home/setup/halal-app/ner_export.csv

# SERVER — copy ke dalam container
docker cp /home/setup/halal-app/ner_export.csv halal_db:/tmp/ner_export.csv

# SERVER — import dan update dalam database
docker exec -it halal_db psql -U postgres -d halal_jakim -c "
CREATE TEMP TABLE ner_import (id int, company_ner text, brand_ner text, product_ner text, address_ner text);
COPY ner_import FROM '/tmp/ner_export.csv' CSV HEADER;
UPDATE halal_products h SET
  company_ner = n.company_ner,
  brand_ner   = n.brand_ner,
  product_ner = n.product_ner,
  address_ner = n.address_ner
FROM ner_import n WHERE h.id = n.id;
"
```

**Penjelasan SQL:**
- `CREATE TEMP TABLE` = buat table sementara (hilang bila session tutup)
- `COPY ... FROM` = import data dari CSV file
- `UPDATE ... FROM` = update rows dengan JOIN kepada table lain (SQL JOIN dalam UPDATE)
- `WHERE h.id = n.id` = match by ID supaya setiap row update dengan NER data yang betul

---

## 16. Cara Debug Masalah Network & Server

### 16.1 App Tak Boleh Connect ke Server

```
Checklist:
1. Server running?
   → ssh masuk server, docker compose ps → tengok STATUS column

2. Port 8000 accessible?
   → Dari browser phone: http://[IP]:8000/health
   → Kalau tak keluar, cuba dari laptop dulu: http://localhost:8000/health

3. Firewall block?
   → Server perlu ada port 8000 open

4. IP betul?
   → Kalau laptop jadi server: ipconfig → ambil IPv4 WiFi address
   → Kalau DGX server: domain halal-api.careerlab.my

5. uvicorn bind betul?
   → Mesti guna --host 0.0.0.0 (bukan localhost/127.0.0.1)
   → localhost = hanya boleh access dari komputer sama
   → 0.0.0.0 = boleh access dari mana-mana dalam network
```

### 16.2 Test Endpoint Dari Browser

Boleh test API terus dari browser — lebih senang dari app:

```
http://192.168.1.8:8000/health
http://192.168.1.8:8000/search?q=milo&limit=5
http://192.168.1.8:8000/search-grouped?q=milo
https://halal-api.careerlab.my/search-grouped?q=milo
```

Kalau browser return JSON → API ok, masalah dalam Flutter app.
Kalau browser pun tak boleh access → masalah server/network.

### 16.3 Check Database Column

Bila dapat error `column "company_ner" does not exist`:
```bash
# Check semua columns dalam table
docker exec -it halal_db psql -U postgres -d halal_jakim -c "\d halal_products"

# Tambah column kalau tak ada
docker exec -it halal_db psql -U postgres -d halal_jakim -c "
ALTER TABLE halal_products ADD COLUMN company_ner text;
UPDATE halal_products SET company_ner = company;
"
```

---

*Guide ni akan updated bila ada perkembangan baru dalam projek.* 
*Semoga berjaya jadi mobile developer yang hebat, Hilman! 💪*
