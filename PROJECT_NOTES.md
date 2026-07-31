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

*Guide ni akan updated bila ada perkembangan baru dalam projek.* 
*Semoga berjaya jadi mobile developer yang hebat, Hilman! 💪*
