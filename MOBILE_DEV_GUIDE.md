# SMART SandboxPlayground — Mobile Development Guide
> Flutter app for Muslims: Halal Checker, Qibla, Prayer Times, Scanner, Tasbeeh, Hijri Calendar

---

## 1. Technology Stack — Apa Yang Digunakan

```
Flutter (framework)   ← tulis UI sekali, run kat Android & iOS
Dart (language)       ← bahasa programming Flutter
Python + FastAPI      ← backend server (API)
PostgreSQL            ← database (halal data JAKIM)
```

**Analogy:**
- Flutter = bengkel kereta (tools untuk buat app)
- Dart = bahasa arahan kepada bengkel tu
- FastAPI = server yang simpan & bagi data
- PostgreSQL = fail kabinet (simpan 348K halal records)

---

## 2. Project Structure — Fail & Folder

```
SMART SandboxPlayground/
│
├── lib/                          ← SEMUA kod Flutter ada sini
│   ├── main.dart                 ← ENTRY POINT — app start dari sini
│   │                               + data models (HalalProduct, ScanResult)
│   │                               + ApiService (call backend)
│   │                               + SearchScreen, ScanScreen, DetailScreen
│   │
│   ├── screens/                  ← Setiap screen = satu fail
│   │   ├── home_screen.dart      ← Dashboard utama app
│   │   ├── prayer_screen.dart    ← Waktu solat
│   │   ├── qibla_screen.dart     ← Kompas kiblat
│   │   ├── scanner_screen.dart   ← Scan produk (kamera)
│   │   ├── tasbeeh_screen.dart   ← Tasbih digital
│   │   ├── calendar_screen.dart  ← Takwim hijri
│   │   └── settings_screen.dart  ← Tetapan app
│   │
│   ├── services/                 ← Logic yang bukan UI
│   │   ├── prayer_service.dart   ← Kira waktu solat
│   │   ├── location_service.dart ← Ambil GPS location
│   │   └── notification_service.dart ← Push notification solat
│   │
│   └── widgets/                  ← Komponen UI boleh guna semula
│       └── location_picker_dialog.dart
│
├── android/                      ← Kod khusus Android
│   └── app/src/main/kotlin/
│       └── MainActivity.kt       ← Bridge Flutter ↔ Android sensor
│
├── pubspec.yaml                  ← Senarai dependencies (macam requirements.txt)
├── api.py                        ← Backend FastAPI server
└── halal_data.csv                ← Data halal JAKIM
```

---

## 3. Dependencies — Apa Setiap Package Buat

Semua dependencies ada dalam `pubspec.yaml`:

```yaml
dependencies:
  flutter:
    sdk: flutter
```

| Package | Versi | Kegunaan | Digunakan Dalam |
|---|---|---|---|
| `http` | ^1.6.0 | Buat API calls ke backend (GET/POST) | `main.dart` — ApiService |
| `image_picker` | ^1.1.2 | Buka kamera atau gallery | `main.dart` — ScanScreen |
| `shared_preferences` | ^2.3.5 | Simpan settings (API URL, preferences) | `main.dart` — gPrefs |
| `geolocator` | ^14.0.0 | Ambil GPS coordinates (lat/lng) | `location_service.dart` |
| `geocoding` | ^4.0.0 | Tukar coordinates → nama tempat | `location_service.dart` |
| `flutter_compass` | ^0.8.1 | Baca data kompas magnetik | `qibla_screen.dart` |
| `sensors_plus` | ^6.1.1 | Baca accelerometer + gyroscope | `qibla_screen.dart` |
| `hijri` | ^3.0.0 | Tukar tarikh Gregorian → Hijri | `home_screen.dart`, `calendar_screen.dart` |
| `flutter_local_notifications` | ^18.0.1 | Notification azan solat | `notification_service.dart` |
| `timezone` | ^0.10.0 | Handle timezone untuk notification | `main.dart`, `notification_service.dart` |

### Cara install / update dependencies:
```bash
flutter pub get          # install semua packages dalam pubspec.yaml
flutter pub upgrade      # update ke versi terbaru
flutter pub add http     # tambah package baru
```

---

## 4. App Features — Di Mana Dalam Kod

### Feature 1: Home Screen (Dashboard)
**File:** `lib/screens/home_screen.dart`

```
Apa ada:
  ✓ Greeting berubah ikut masa (Pagi/Tengahari/Petang/Malam)
  ✓ Tarikh Gregorian + Hijri
  ✓ Banner waktu solat seterusnya + countdown
  ✓ Grid 6 feature tiles
```

**Key functions:**
```dart
// line 64 — load prayer times dari GPS
Future<void> _loadPrayerTimes() async { ... }

// line 86 — kira prayer mana seterusnya
void _updateNextPrayer() { ... }

// line 119 — greeting berdasarkan jam
String get _greeting { ... }

// line 365 — buat 6 feature tiles
List<Widget> _featureTiles(BuildContext context) { ... }
```

---

### Feature 2: Prayer Times (Waktu Solat)
**File:** `lib/screens/prayer_screen.dart`
**Service:** `lib/services/prayer_service.dart`
**Location:** `lib/services/location_service.dart`

```
Workflow:
  GPS (geolocator) → LocationService.getLocation()
       ↓
  PrayerService.getPrayerTimes(lat, lng, country)
       ↓
  Kira waktu Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha
       ↓
  Papar dalam screen + set notification
```

---

### Feature 3: Qibla Compass (Arah Kiblat)
**File:** `lib/screens/qibla_screen.dart`
**Android Bridge:** `android/.../MainActivity.kt`

```
Workflow:
  GPS → dapat coordinates pengguna
       ↓
  MainActivity.kt → GeomagneticField API (Android)
  → dapat declination (pembetulan kompas magnetik)
       ↓
  flutter_compass → baca heading (arah kompas)
  sensors_plus → baca accelerometer (stabilize compass)
       ↓
  Kira bearing ke Kaabah (lat: 21.4225, lng: 39.8262)
       ↓
  Rotate gambar kompas dalam UI
```

**Kenapa ada kod Kotlin (MainActivity.kt)?**

Flutter tak boleh akses Android `GeomagneticField` API secara langsung.
Jadi kita guna **MethodChannel** — jambatan antara Dart dan Android:

```
Dart (Flutter)                    Kotlin (Android)
     │                                  │
     │── channel.invokeMethod ──────────▶│
     │   "getDeclination"               │ GeomagneticField(lat, lon, alt, time)
     │   {lat, lon, altitude, time}     │        ↓
     │◀─────────── result.success ──────│ return declination value
```

---

### Feature 4: Halal Checker (Search)
**File:** `lib/main.dart` — class `SearchScreen`

```
Workflow:
  User taip keyword
       ↓
  ApiService.search(query)
  → GET https://halal-api.careerlab.my/search?q=milo
       ↓
  Backend (api.py) cari dalam PostgreSQL
       ↓
  Return JSON list of HalalProduct
       ↓
  Papar ProductCard untuk setiap result
       ↓
  Tap card → DetailScreen (full info)
```

**Key code:**
```dart
// main.dart:170 — API call
static Future<List<HalalProduct>> search(String query) async {
  final uri = Uri.parse('$apiBaseUrl/search')
      .replace(queryParameters: {'q': query, 'limit': '50'});
  final response = await http.get(uri);
  ...
}

// main.dart:111 — parse JSON jadi object
factory HalalProduct.fromJson(Map<String, dynamic> json) {
  return HalalProduct(
    id: json['id'] ?? 0,
    company: json['company'] ?? '',
    ...
  );
}
```

---

### Feature 5: Scan Product (Kamera + OCR + AI)
**File:** `lib/main.dart` — class `ScanScreen`
**Also:** `lib/screens/scanner_screen.dart`

```
Workflow:
  User ambil gambar (kamera/gallery)
       ↓
  image_picker → dapat File path
       ↓
  ApiService.scanImage(imageFile)
  → POST /scan-image (multipart form, hantar gambar)
       ↓
  Backend (api.py):
    EasyOCR → extract text dari label
    EfficientNet → image similarity matching
       ↓
  Return: teks OCR + halal records
       ↓
  Papar hasil dalam ScanResult widget
```

---

### Feature 6: Tasbeeh Digital
**File:** `lib/screens/tasbeeh_screen.dart`

Counter digital untuk zikir. Guna `shared_preferences` untuk simpan count.

---

### Feature 7: Hijri Calendar
**File:** `lib/screens/calendar_screen.dart`

Guna package `hijri` untuk convert tarikh dan papar takwim Islam.

---

## 5. How Flutter Works — Konsep Asas

### StatelessWidget vs StatefulWidget

```dart
// StatelessWidget — TIDAK berubah selepas dibina
// Guna untuk: cards, icons, teks tetap
class ProductCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(...);   // dibina sekali, tak berubah
  }
}

// StatefulWidget — BOLEH berubah (ada state)
// Guna untuk: loading spinner, search results, countdown
class SearchScreen extends StatefulWidget {
  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> {
  bool _isLoading = false;    // state variable

  void _doSearch() {
    setState(() {              // setState = trigger rebuild UI
      _isLoading = true;
    });
    // ... fetch data
    setState(() {
      _isLoading = false;
    });
  }
}
```

### Widget Tree (macam HTML DOM)

```
Scaffold
  ├── AppBar
  │     └── Text("Halal Checker")
  └── Body
        └── Column
              ├── SearchBar
              └── ListView
                    ├── ProductCard #1
                    ├── ProductCard #2
                    └── ProductCard #3
```

### Navigation (pindah screen)

```dart
// Pergi ke screen baru
Navigator.push(
  context,
  MaterialPageRoute(builder: (_) => DetailScreen(product: product)),
);

// Balik ke screen sebelum
Navigator.pop(context);
```

---

## 6. Android Integration — MainActivity.kt

**File:** `android/app/src/main/kotlin/com/halalcheck/halal_app/MainActivity.kt`

```kotlin
class MainActivity : FlutterActivity() {
  override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)

    // Buat "channel" dengan nama unik
    MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "smart.app/geomagnetic")
      .setMethodCallHandler { call, result ->

        if (call.method == "getDeclination") {
          // Ambil arguments dari Dart
          val lat  = call.argument<Double>("lat")!!.toFloat()
          val lon  = call.argument<Double>("lon")!!.toFloat()
          val alt  = call.argument<Double>("altitude")!!.toFloat()
          val time = call.argument<Long>("time")!!

          // Guna Android API
          val field = GeomagneticField(lat, lon, alt, time)

          // Hantar balik ke Dart
          result.success(field.declination.toDouble())
        }
      }
  }
}
```

**Dari Dart side (qibla_screen.dart):**
```dart
static const _channel = MethodChannel('smart.app/geomagnetic');

final declination = await _channel.invokeMethod<double>('getDeclination', {
  'lat': latitude,
  'lon': longitude,
  'altitude': altitude,
  'time': DateTime.now().millisecondsSinceEpoch,
});
```

---

## 7. Sensors — Yang Digunakan

| Sensor | Package | Digunakan Untuk | File |
|---|---|---|---|
| **GPS** | `geolocator` | Dapatkan lat/lng pengguna | `location_service.dart` |
| **Compass (Magnetometer)** | `flutter_compass` | Arah kompas (heading 0-360°) | `qibla_screen.dart` |
| **Accelerometer** | `sensors_plus` | Stabilkan kompas dari getar | `qibla_screen.dart` |
| **GeomagneticField** | Android native (MainActivity.kt) | Magnetic declination correction | `MainActivity.kt` |

### Kenapa Perlu Magnetic Declination?

```
Kompas biasa tunjuk → Magnetic North (bukan True North)
Magnetic North ≠ True North (ada beza sudut)

Contoh KL:
  Compass tunjuk: 295°
  Declination:    +0.5°
  True bearing:   294.5°

Tanpa declination → arah kiblat silap sikit
Dengan declination → arah kiblat tepat
```

---

## 8. Workflow — Cara Run & Build

### Development (test dalam emulator/phone)

```bash
# 1. Install dependencies
flutter pub get

# 2. Run dalam debug mode (hot reload aktif)
flutter run

# 3. Hot reload (simpan fail → UI update dalam 1 saat)
# Tekan 'r' dalam terminal semasa flutter run

# 4. Hot restart (restart penuh)
# Tekan 'R' dalam terminal
```

### Build APK (untuk install dalam phone)

```bash
# Debug APK (untuk test)
flutter build apk --debug

# Release APK (untuk publish)
flutter build apk --release

# File output:
# build/app/outputs/flutter-apk/app-release.apk
```

### Run Backend Server (kena run sebelum test API features)

```bash
# Dalam SMART SandboxPlayground folder:
uvicorn api:app --reload --host 0.0.0.0 --port 8000
```

---

## 9. Bug Fixing — Cara Debug

### Common Errors & Fixes

**Error 1: `Connection refused` / `SocketException`**
```
Punca: Backend server tak run, atau IP salah
Fix:
  1. Run: uvicorn api:app --reload
  2. Settings → API Server → tukar ke IP betul
```

**Error 2: `Null check operator used on null`**
```dart
// SALAH — akan crash kalau null
final name = product.company!;

// BETUL — provide default value
final name = product.company ?? 'Unknown';
```

**Error 3: `setState() called after dispose()`**
```dart
// SALAH
Future<void> loadData() async {
  final data = await fetchSomething();
  setState(() { _data = data; });  // screen mungkin dah tutup!
}

// BETUL
Future<void> loadData() async {
  final data = await fetchSomething();
  if (mounted) {                    // check dulu
    setState(() { _data = data; });
  }
}
```

**Error 4: Permission denied (GPS / Camera)**
```
Android kena declare permission dalam AndroidManifest.xml
File: android/app/src/main/AndroidManifest.xml

Tambah:
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.CAMERA"/>
```

### Debug Tools

```bash
# Tengok logs dalam terminal
flutter run --verbose

# Inspect widget tree (powerful debug tool)
# Dalam VS Code: Flutter DevTools → Widget Inspector

# Print debug (temporary)
debugPrint('Value: $variable');
```

---

## 10. Full Data Flow — From Phone to Database

```
USER TAP "SEARCH MILO"
        │
        ▼
lib/main.dart → SearchScreen._doSearch()
        │
        ▼
ApiService.search("milo")
→ GET https://halal-api.careerlab.my/search?q=milo
        │
        ▼  (internet/wifi)
api.py → FastAPI server
        │
        ▼
PostgreSQL → halal_products table
SELECT * FROM halal_products
WHERE company ILIKE '%milo%' OR brand ILIKE '%milo%'
        │
        ▼
Return JSON:
[{"id":1,"company":"NESTLE","brand":"MILO",...}]
        │
        ▼
HalalProduct.fromJson() → parse JSON to Dart object
        │
        ▼
setState() → rebuild UI
        │
        ▼
ListView → ProductCard untuk setiap result
        │
        ▼
USER NAMPAK HASIL CARIAN
```

---

## 11. Adding New Feature — Step by Step

Contoh: nak tambah feature baru "Doa Harian"

**Step 1:** Buat screen baru
```bash
# Buat fail baru
lib/screens/doa_screen.dart
```

**Step 2:** Tulis screen
```dart
// lib/screens/doa_screen.dart
import 'package:flutter/material.dart';

class DoaScreen extends StatelessWidget {
  const DoaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Doa Harian')),
      body: const Center(child: Text('Doa content here')),
    );
  }
}
```

**Step 3:** Tambah dalam HomeScreen feature grid
```dart
// lib/screens/home_screen.dart — dalam _featureTiles()
_FeatureInfo(
  label: 'Doa Harian',
  icon: Icons.menu_book_rounded,
  color: const Color(0xFF8B4513),
  subtitle: 'Kumpulan doa harian',
  screen: const DoaScreen(),    // ← import fail baru
),
```

**Step 4:** Import screen dalam home_screen.dart
```dart
import 'doa_screen.dart';
```

**Step 5:** Test
```bash
flutter run
# Tekan 'r' untuk hot reload
```

---

## 12. Quick Reference

| Nak buat apa | Pergi ke mana |
|---|---|
| Tukar warna tema | `main.dart` line 71 — `ColorScheme.fromSeed` |
| Tukar API URL default | `main.dart` line 30 — `apiBaseUrl` |
| Tambah feature baru | `home_screen.dart` — `_featureTiles()` |
| Tukar waktu solat logic | `prayer_service.dart` |
| Tukar kompas logic | `qibla_screen.dart` |
| Tukar Android sensor | `MainActivity.kt` |
| Tambah package baru | `pubspec.yaml` → `flutter pub get` |
| Push notification | `notification_service.dart` |
