# SMART — Spesifikasi Bina Semula: Android Native (Kotlin + Jetpack Compose)

> **Untuk AI di Android Studio (Gemini / mana-mana pembantu kod):** Dokumen ini ialah spesifikasi lengkap untuk membina semula app Flutter **SMART SandboxPlayground** sebagai app **Android native dalam Kotlin**. Hasil akhir mesti **100% sama** dengan app Flutter dari segi skrin, teks, warna, susun atur, tingkah laku dan panggilan API. Jangan reka ciri baharu, jangan tukar teks, jangan "perbaiki" reka bentuk. Jika sesuatu tidak dinyatakan, ikut lalai Material 3 seperti dalam A2.4.
>
> **Peraturan untuk AI:**
> 1. Bahagian A = *apa* yang dibina (sumber kebenaran). Bahagian B = *bagaimana* membinanya di Android.
> 2. Bina mengikut **fasa** dalam B10. Siapkan dan pastikan build berjaya pada setiap fasa sebelum teruskan.
> 3. Salin semua teks UI dengan tepat dari dokumen ini, termasuk emoji dan aksara khas (`…`, `—`, `•`, `✓`, `×`).
> 4. Jangan tambah perpustakaan selain yang disenaraikan dalam B2 tanpa sebab kukuh.

---

## Panduan untuk pembangun (manusia)

### Langkah 1 — Pasang alat
1. Muat turun **Android Studio** (versi stabil terkini) dari developer.android.com/studio dan pasang.
2. Buka Android Studio → *More Actions → SDK Manager*:
   - **SDK Platforms**: tick Android 16 (API 36) dan Android 14 (API 34).
   - **SDK Tools**: tick *Android SDK Build-Tools*, *Android SDK Platform-Tools*, *Android Emulator*.
3. JDK: Android Studio sudah ada JDK terbina (JBR 17/21) — tiada pemasangan tambahan.

### Langkah 2 — Cipta projek
*File → New → New Project → **Empty Activity*** (yang guna Compose), kemudian isi:

| Medan | Nilai |
|---|---|
| Name | `SMART` |
| Package name | `com.halalcheck.halal_app` ← **wajib sama** (DJI App Key terikat pada nama ini) |
| Save location | cth. `~/Documents/SMART-Native/android` (folder berasingan dari repo Flutter) |
| Minimum SDK | API 24 (Android 7.0) |
| Build configuration language | Kotlin DSL (build.gradle.kts) |

> App native ini guna `applicationId` yang sama dengan app Flutter, jadi kedua-duanya tidak boleh dipasang serentak pada satu fon. Nyahpasang app Flutter dahulu.

### Langkah 3 — Salin aset dari repo Flutter
- Ikon app: `assets/icon/app_icon.png` → *File → New → Image Asset* (Launcher Icons) → pilih fail ini.
- Fon ikon Material (lihat B5): muat turun dari github.com/google/material-design-icons/tree/master/font → `MaterialIcons-Regular.ttf`, `MaterialIconsRound-Regular.otf`, `MaterialIconsOutlined-Regular.otf` → letak dalam `app/src/main/res/font/` dan namakan semula ke `material_icons.ttf`, `material_icons_round.otf`, `material_icons_outlined.otf`.
- DJI App Key: salin nilai `com.dji.sdk.API_KEY` dari `android/app/src/main/AndroidManifest.xml` dalam repo Flutter.

### Langkah 4 — Beri fail ini kepada AI
Buka panel Gemini (atau AI lain) dalam Android Studio, lampirkan fail ini, dan minta ia bina **satu fasa pada satu masa** (B10). Contoh arahan:
> "Baca SMART_ANDROID_KOTLIN.md sepenuhnya. Laksanakan Fasa 1 sahaja. Ikut Bahagian A untuk UI dan Bahagian B untuk teknikal. Tunjukkan semua fail yang diubah."

Selepas setiap fasa: tekan ▶ Run pada fon sebenar (USB debugging ON), bandingkan dengan app Flutter sebelah-menyebelah.

### Langkah 5 — Build APK
*Build → Generate Signed App Bundle / APK* → APK → cipta keystore baharu (simpan baik-baik!) → release.
---

# BAHAGIAN A — SPESIFIKASI BERSAMA (sumber kebenaran)

Bahagian ini menerangkan app Flutter sedia ada **sepenuhnya**. Semua teks UI dalam `"..."` mesti disalin **tepat** (huruf besar/kecil, tanda baca, emoji, bahasa campur Melayu/Inggeris — memang begitu dalam app asal). Semua nombor saiz dalam **dp/pt** (1 Flutter logical pixel = 1 dp Android = 1 pt iOS).

## A1. Peta skrin & navigasi

```
HomeScreen  (skrin mula, tiada app bar)
 ├─ ikon ⚙ (header)            → SettingsScreen
 ├─ banner solat "View all"     → PrayerScreen
 └─ grid 8 jubin:
     1. Prayer Times            → PrayerScreen
     2. Qibla Compass           → QiblaScreen
     3. Halal Checker           → SearchScreen
     │    ├─ ikon kamera app bar → ScanScreen
     │    │     └─ tap rekod     → DetailScreen
     │    └─ tap CompanyCard     → CompanyProductsScreen
     │          └─ tap ProductCard → DetailScreen
     4. Scan Product            → ScannerScreen
     5. Tasbeeh                 → TasbeehScreen
     6. Hijri Calendar          → CalendarScreen
     7. Drone Scanner           → DroneScreen
     8. Scan Logo Halal         → LogoScanScreen
PrayerScreen / QiblaScreen      → dialog LocationPicker
```

- Semua navigasi ialah **push** biasa (tumpukan/stack). Kembali = butang back sistem / butang back app bar.
- Skrin **dengan app bar** (ada butang back): SearchScreen, CompanyProductsScreen, DetailScreen, ScanScreen, ScannerScreen, LogoScanScreen, DroneScreen.
- Skrin **tanpa app bar** (tiada butang back kelihatan, kandungan dalam safe area): HomeScreen, PrayerScreen, QiblaScreen, CalendarScreen, TasbeehScreen, SettingsScreen. Pengguna kembali dengan gesture/butang back sistem.
- Orientasi: potret dan landskap dibenarkan (tiada kunci orientasi).
- Tema: **light sahaja** (app tidak menyokong dark mode; walaupun fon dalam dark mode, paparkan tema cerah). Nama app pada launcher: **`SMART`**.

## A2. Token reka bentuk

### A2.1 Warna jenama & skema (nilai sebenar dijana daripada Flutter `ColorScheme.fromSeed(0xFF1B7A4B)`, Material 3)

| Token | Hex | Digunakan untuk |
|---|---|---|
| `brandGreen` | `#1B7A4B` | Warna utama tetap (app bar, butang, ikon) — **seed** |
| `brandGreenAlt` | `#1A7A4A` | Digunakan di Prayer/Qibla/Calendar/Tasbeeh/Settings/LocationPicker (sengaja dibezakan dalam kod asal — kekalkan) |
| `primary` (M3) | `#2B6A46` | `colorScheme.primary` — app bar ScannerScreen, warna ikon/teks ScannerScreen, spinner lalai, TextButton lalai |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#AFF1C4` | |
| `secondary` | `#4F6354` | |
| `secondaryContainer` | `#D1E8D5` | |
| `surface` / latar Scaffold lalai / `cardColor` | `#F6FBF3` | Latar skrin lalai |
| `surfaceContainerLow` | `#F0F5EE` | Latar komponen **Card** lalai, ElevatedButton lalai |
| `surfaceContainerHigh` | `#E5EAE2` | Latar AlertDialog / popup menu |
| `surfaceContainerHighest` | `#DFE4DD` | |
| `onSurface` | `#181D19` | Teks lalai |
| `onSurfaceVariant` | `#414942` | Subtajuk ListTile lalai, teks dialog |
| `outline` | `#717971` | Sempadan OutlinedButton lalai, TextField |
| `outlineVariant` | `#C0C9C0` | Sempadan Chip lalai, Divider |
| `error` | `#BA1A1A` | Teks ralat dalam LocationPicker |
| `inverseSurface` | `#2C322D` | Latar SnackBar lalai |
| `onInverseSurface` | `#EDF2EB` | Teks SnackBar |

Warna latar khas: Home & LogoScan `#F0F4F0`, Calendar `#F5F5F5`, Drone `#000000`.

Warna teks khas: `#2D4A35`, `#1B4332`, `#1B2A1F`, `#4A7A5A`, `#5C35A0`, `#C62828`, `#0D1F14`, `#0D5C30`, `#0F5C38`.

### A2.2 Palet Material 2 Flutter (`Colors.x.shadeN`) — gunakan hex ini

| Nama Flutter | Hex |
|---|---|
| `Colors.green` 50 / 100 / 200 / 300 / 600 / 700 / 800 / 900 | `#E8F5E9` / `#C8E6C9` / `#A5D6A7` / `#81C784` / `#43A047` / `#388E3C` / `#2E7D32` / `#1B5E20` |
| `Colors.red` (asas) / 50 / 200 / 300 / 700 / 800 | `#F44336` / `#FFEBEE` / `#EF9A9A` / `#E57373` / `#D32F2F` / `#C62828` |
| `Colors.orange` 50 / 100 / 200 / 700 / 800 | `#FFF3E0` / `#FFE0B2` / `#FFCC80` / `#F57C00` / `#EF6C00` |
| `Colors.amber` 50 / 300 / 700 | `#FFF8E1` / `#FFD54F` / `#FFA000` |
| `Colors.blue` (asas) / 50 / 100 | `#2196F3` / `#E3F2FD` / `#BBDEFB` |
| `Colors.grey` (asas = 500) / 100 / 200 / 300 / 400 / 500 / 600 / 700 | `#9E9E9E` / `#F5F5F5` / `#EEEEEE` / `#E0E0E0` / `#BDBDBD` / `#9E9E9E` / `#757575` / `#616161` |
| `Colors.greenAccent` | `#69F0AE` |
| `Colors.blueAccent` | `#448AFF` |
| `Colors.white70` / `white54` / `white38` / `white30` / `white24` / `white12` | putih alpha 0.70 / 0.54 / 0.38 / 0.30 / 0.24 / 0.12 |
| `Colors.black87` / `black54` | hitam alpha 0.87 / 0.54 |

Notasi `X @ a` dalam dokumen ini = warna X dengan alpha `a` (cth. `#1B7A4B @ 0.08`).

### A2.3 Tipografi

Fon sistem (Roboto di Android, SF Pro di iOS — sama seperti Flutter pada setiap platform). Saiz lalai Material 3 bila kod asal tidak tetapkan:

| Gaya | Saiz / berat | Digunakan oleh |
|---|---|---|
| titleLarge | 22 / regular | Tajuk app bar lalai |
| bodyLarge | 16 / regular | Teks TextField, tajuk ListTile |
| bodyMedium | 14 / regular | Teks `Text` lalai, subtajuk ListTile, SnackBar, kandungan dialog |
| labelLarge | 14 / medium(500) | Label butang |
| headlineSmall | 24 / regular | Tajuk AlertDialog |

Jika spesifikasi skrin kata "bold" tanpa saiz, guna saiz lalai komponen itu dengan berat bold.

### A2.4 Komponen lalai Material 3 (bila kod asal tidak override)

- **App bar**: tinggi 56, tajuk kiri (kecuali dinyatakan `centerTitle`), latar mengikut spesifikasi skrin.
- **Card**: latar `#F0F5EE`, elevation 1 (bayang lembut), radius 12, margin luar 4 semua sisi.
- **ElevatedButton**: bentuk pil (stadium), tinggi min 40, padding mendatar 24, elevation 1.
- **OutlinedButton**: bentuk pil, sempadan 1 `#717971`, teks `primary`.
- **TextButton**: bentuk pil, teks `primary`.
- **Chip**: radius 8, sempadan 1 `#C0C9C0`, latar telus, padding label 8.
- **CircularProgressIndicator**: diameter 36, stroke 4, warna `primary` (`#2B6A46`) kecuali dinyatakan.
- **SnackBar**: latar `#2C322D`, teks `#EDF2EB` 14, muncul di bawah ~4 saat.
- **AlertDialog**: latar `#E5EAE2`, radius 28, tajuk 24, butang TextButton di kanan bawah.
- **Divider**: garisan 1 `#C0C9C0`.

### A2.5 Ikon

App asal guna **Material Icons** Flutter. Nama di bawah ialah nama ikon Material (ligature). Akhiran `_rounded` = set *Round*, `_outlined` = set *Outlined*, tanpa akhiran = set *Filled*. (Cara memaparkannya diterangkan di Bahagian B.)

## A3. Storan tempatan (key-value)

| Key | Jenis | Lalai | Ditulis oleh | Dibaca oleh |
|---|---|---|---|---|
| `api_url` | String | (tiada) | Settings | `apiBaseUrl` |
| `adhan_enabled` | Bool | `true` | Settings | PrayerScreen (jadual notifikasi) |
| `vibration_enabled` | Bool | `true` | Settings | (tidak digunakan di mana-mana) |
| `calc_method` | String | `"Singapore"` | Settings | (tidak digunakan dalam pengiraan) |
| `madhab` | String | `"Shafi"` | Settings | (tidak digunakan dalam pengiraan) |
| `tasbeeh_count` | Int | `0` | Tasbeeh | Tasbeeh |
| `tasbeeh_target` | Int | `33` | Tasbeeh | Tasbeeh |

**`apiBaseUrl`** (dibaca setiap kali panggilan dibuat, bukan sekali sahaja):
```
apiBaseUrl = prefs["api_url"] ?? "https://halal-jakim-backend-vfhol5-85579f-159-65-136-40.sslip.io"
```
URL dicantum terus: `"$apiBaseUrl/search-grouped"` (jangan tambah/buang `/`).

## A4. Urutan permulaan app

1. Muat data zon waktu.
2. Muat storan tempatan.
3. **Migrasi**: jika `api_url` tersimpan mengandungi `"ngrok"` ATAU `"192.168"` ATAU `"careerlab.my"` → buang key `api_url`.
4. Init sistem notifikasi tempatan (saluran Android: id `prayer_times`, nama `Prayer Times`, deskripsi `Notifications for each daily prayer time`, kepentingan HIGH).
5. Papar HomeScreen.

## A5. Backend API (FastAPI)

Semua respons JSON UTF-8. Tiada pengesahan (auth). Semua POST imbasan: `multipart/form-data`, satu medan **`file`**.

### A5.1 Model data

**HalalProduct** — dari objek JSON (semua medan boleh `null`/hilang → guna lalai):

| Medan app | Kunci JSON | Jenis | Lalai |
|---|---|---|---|
| id | `id` | Int | `0` |
| category | `category` | String | `""` |
| compCode | `comp_code` | String | `""` |
| company | `company` | String | `""` |
| brand | `brand` | String | `""` |
| productName | `product_name` | String | `""` |
| address | `address` | String | `""` |
| expiry | `expiry` | String | `""` (format JAKIM `DD/MM/YYYY`) |

(JSON juga ada `ner_confidence` — abaikan.)

**CompanyResult**: `company` (String, `""`), `product_count` (Int, `0`), `popularity_score` (Int, `0`), `categories` (List<String>, `[]`), `products` (List<HalalProduct>, `[]`).

**ScanResult** (dari `/scan-image`): `ocr_texts` (List, setiap item → string, `[]`), `matched_product` (String?), `matched_category` (String?), `confidence` (Double?, nombor), `detection_method` (String, `"none"`), `halal_records` (List<HalalProduct>, `[]`), `vision_result` (Map?, nilai sebarang).

> Parser mesti **toleran**: medan tidak dikenali diabaikan, `null` → lalai.

### A5.2 Endpoint yang digunakan app

**1. `GET /search-grouped?q={query}&limit=100`** — tiada timeout khusus (guna 30 s).
```json
{ "query": "milo", "company_count": 2,
  "companies": [ { "company": "NESTLE PRODUCTS SDN BHD", "product_count": 120,
                   "popularity_score": 100, "categories": ["Produk"], "products": [] } ] }
```
Status ≠ 200 → ralat. Senarai sudah disusun oleh server — **jangan susun semula**.

**2. `GET /company-products?company={company}&q={query}`**
```json
{ "company": "...", "query": "...", "count": 3, "products": [ HalalProduct, ... ] }
```

**3. `POST /scan-image`** — medan `file`, nama fail `scan.jpg`, timeout **120 s**.
```json
{ "vision_result": {"brand": "Milo", "product": "Activ-Go", "keywords": ["..."]},
  "ocr_texts": ["..."], "halal_records": [HalalProduct...], "halal_count": 1,
  "detection_method": "vision" | "ocr" | "none", "note": "..." }
```
Status 503 → mesej `AI model not ready. Run build_embeddings.py first.`; status lain ≠200 → `Scan failed {status}: {body}`.

**4. `POST /scan-logo`** — medan `file`, nama fail = nama fail asal imej, timeout **45 s**.
```json
{ "vision_result": {"abbreviation": "JAKIM", "country": "Malaysia",
                    "organization_name": "...", "all_text": ["HALAL","JAKIM"]},
  "is_recognised_by_jakim": true,
  "matches": [ {"id": "...", "abbreviation": "MUIS", "country": "Singapore",
                "organization_name": "...", "email": "...", "website": "...", "phone": "..."} ],
  "match_count": 1, "detection_method": "vision" | "ocr", "note": "..." }
```
`vision_result` boleh `{}` atau `null`. Medan dalam `matches` boleh hilang.

**5. `POST /scan-premises`** — medan `file`, nama fail `drone_frame.jpg`, timeout **60 s**.
```json
{ "vision_result": {...}, "vision_texts": ["..."], "ocr_texts": ["..."],
  "is_halal_premise": true, "matches": [HalalProduct...], "match_count": 1,
  "detection_method": "vision" | "ocr" | "none" }
```

### A5.3 API luar: waktu solat

`GET https://api.waktusolat.app/v2/solat/gps/{lat}/{lng}?year={YYYY}&month={M}` — timeout **8 s**. `lat`/`lng` ditulis sebagai nombor perpuluhan penuh (format `toString()` biasa, cth. `3.1412`).
```json
{ "zone": "WLY01", "prayers": [ { "day": 10, "hijri": "1448-05-19",
   "fajr": 1762811040, "syuruk": 1762815360, "dhuhr": 1762837080,
   "asr": 1762849260, "maghrib": 1762859400, "isha": 1762863780 } ] }
```
Masa = **Unix seconds** → tukar ke masa tempatan peranti. Cari entri dengan `day == hari ini (tarikh tempatan)`. Jika status ≠200, tiada `prayers`, tiada entri hari ini, timeout, atau ralat apa pun → `null` (guna kiraan offline).

## A6. Logik perkhidmatan

### A6.1 Lokasi — `getLocation()`

1. Jika perkhidmatan lokasi OFF → lempar ralat dengan teks **`Location services are disabled.`**
2. Semak kebenaran. Jika belum diberi → minta. Jika ditolak → **`Location permission denied.`** Jika ditolak kekal → **`Permission permanently denied.`**
3. Minta satu lokasi semasa, ketepatan **rendah** (≈ city block / 500 m), had masa **15 s**.
4. Jika gagal/timeout → guna lokasi terakhir diketahui. Jika tiada → **`GPS timed out. Are you indoors? Try "Choose location manually" below.`**
5. Pulangkan `LocationData(lat, lng, city="", region="", country="")`.

Teks ralat ini dipaparkan **terus** di skrin (tanpa awalan).

`geocodeInBackground(location, onResult)`: reverse geocode koordinat; jika ada hasil pertama → `city = locality ?? subAdministrativeArea ?? ""`, `region = administrativeArea ?? ""`, `country = isoCountryCode ?? ""`; panggil `onResult`. Senyap jika gagal.

**Bandar Malaysia (pilihan pantas, susunan ini):**

| Label | Lat | Lng | city | region |
|---|---|---|---|---|
| Kuala Lumpur / Putrajaya | 3.1412 | 101.6865 | Kuala Lumpur | Wilayah Persekutuan |
| Selangor (Shah Alam) | 3.0738 | 101.5183 | Shah Alam | Selangor |
| Johor Bahru | 1.4927 | 103.7414 | Johor Bahru | Johor |
| Penang (Georgetown) | 5.4164 | 100.3327 | Georgetown | Pulau Pinang |
| Ipoh (Perak) | 4.5975 | 101.0901 | Ipoh | Perak |
| Kota Bharu (Kelantan) | 6.1254 | 102.2386 | Kota Bharu | Kelantan |
| Kuching (Sarawak) | 1.5533 | 110.3592 | Kuching | Sarawak |
| Kota Kinabalu (Sabah) | 5.9804 | 116.0735 | Kota Kinabalu | Sabah |
| Alor Setar (Kedah) | 6.1248 | 100.3678 | Alor Setar | Kedah |
| Seremban (N. Sembilan) | 2.7297 | 101.9381 | Seremban | Negeri Sembilan |
| Melaka | 2.1896 | 102.2501 | Melaka | Melaka |
| Kuantan (Pahang) | 3.8077 | 103.3260 | Kuantan | Pahang |
| Kuala Terengganu | 5.3302 | 103.1408 | Kuala Terengganu | Terengganu |
| Kangar (Perlis) | 6.4414 | 100.1987 | Kangar | Perlis |

Semua `country = "MY"`.

### A6.2 Waktu solat — `getPrayerTimes(lat, lng)`

Sentiasa cuba API waktusolat (A5.3) dahulu → jika `null`, kira offline. `PrayerTimesData { fajr, sunrise, dhuhr, asr, maghrib, isha, source, zone?, hijri? }`; `source = "WaktuSolat"` (API) atau `"Calculated"` (offline). Untuk API: `sunrise = syuruk`, `zone = data.zone`, `hijri = entri.hijri`.

**Algoritma offline (salin tepat, semua dalam Double; `d2r = π/180`; `now` = masa tempatan):**
```
y = now.year; m = now.month; d = now.day
if m <= 2 { y -= 1; m += 12 }
a  = y / 100 (pembahagian integer)
b  = 2 - a + a / 4 (integer)
jd = floor(365.25*(y+4716)) + floor(30.6001*(m+1)) + d + b - 1524.5
dd = jd - 2451545.0
g  = (357.529 + 0.98560028*dd) mod 360        // mod = baki bertanda seperti Dart '%' (hasil sentiasa ≥ 0)
q  = (280.459 + 0.98564736*dd) mod 360
l  = (q + 1.915*sin(g*d2r) + 0.020*sin(2*g*d2r)) mod 360
e  = 23.439 - 0.00000036*dd
ra = atan2(cos(e*d2r)*sin(l*d2r), cos(l*d2r)) / d2r
decl = asin(sin(e*d2r)*sin(l*d2r))            // radian
eqT = q - ra
solarNoon = 12 - lng/15 - eqT/15              // jam UTC
latR = lat*d2r
hourAngle(angleDeg) = acos(clamp((sin(angleDeg*d2r) - sin(latR)*sin(decl)) / (cos(latR)*cos(decl)), -1, 1)) / d2r / 15
ha = hourAngle(-0.833); sunrise = solarNoon - ha; sunset = solarNoon + ha
noonAlt  = 90 - |lat| - |decl|/d2r
asrAngle = atan(1 / (1 + tan(|noonAlt|*d2r)))   // radian
asrHa    = acos(clamp((sin(asrAngle) - sin(latR)*sin(decl)) / (cos(latR)*cos(decl)), -1, 1)) / d2r / 15
asr  = solarNoon + asrHa
fajr = solarNoon - hourAngle(-18.0)
isha = solarNoon + hourAngle(-17.0)
toLocal(utcH): offsetH = (offset zon waktu peranti hari ini dalam minit)/60
               local = utcH + offsetH
               h   = floor(local) mod 24   (Dart: hasil ≥ 0)
               min = round((local - floor(local)) * 60)   // boleh jadi 60 → biar platform normalkan (Dart DateTime menormalkan 60 min → jam berikut)
               → DateTime(now.year, now.month, now.day, h, min)
dhuhr = toLocal(solarNoon); maghrib = toLocal(sunset)
```
Nota: Dart `%` untuk Double memulangkan nilai bukan-negatif (`-5 % 360 = 355`). Kotlin `%`/Swift `truncatingRemainder` **tidak** — guna `((x % n) + n) % n`.

> `calc_method` dan `madhab` dalam Settings **tidak** mempengaruhi pengiraan (kekalkan).

### A6.3 Kiblat & kompas

- Kaabah: `lat 21.4225`, `lng 39.8262`.
- Bearing kiblat (darjah dari utara benar):
```
lat1 = rad(userLat); lat2 = rad(21.4225); dLng = rad(39.8262 - userLng)
x = sin(dLng)*cos(lat2)
y = cos(lat1)*sin(lat2) - sin(lat1)*cos(lat2)*cos(dLng)
qibla = (deg(atan2(x, y)) + 360) mod 360
```
- Setiap bacaan kompas `(heading, accuracy)`:
  - `needleRotation = (qibla - heading + 360) mod 360`
  - `accuracy`: nilai darjah ketidakpastian; jika tidak diketahui → `999`.
  - Kadar kemas kini ≈ 30 Hz (sekurang-kurangnya 32 ms antara kemas kini).
- **Pusingan licin jarum** (kekalkan logik ini):
```
prevNorm = prevRotation mod 360   (bukan-negatif)
delta = needleRotation - prevNorm
if delta > 180 { delta -= 360 }; if delta < -180 { delta += 360 }
target = prevRotation + delta
animasi dari nilai semasa → target, 300 ms, easeOut
prevRotation = target
```
  Bila lokasi baharu dipilih: `prevRotation = 0`, animasi direset ke 0.
- Magnetic declination: dimuat di latar bila lokasi diguna (lihat Bahagian B untuk sumber platform). Gagal → `0.0`.

### A6.4 Notifikasi solat — `schedulePrayerNotifications(times[6], adhanEnabled)`

Dipanggil **hanya** oleh PrayerScreen selepas waktu solat berjaya dimuat (bukan oleh Home).
1. Batalkan notifikasi ID 0–5 (abaikan ralat).
2. Jika `!adhanEnabled` → tamat.
3. Untuk `i` dalam 0..5 (urutan Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha): jika masa sudah lepas → langkau. Jika tidak, jadualkan notifikasi tepat (exact) pada masa itu:
   - ID: `i`
   - Tajuk: `It is time for {nama}` — nama: `Fajr`, `Sunrise`, `Dhuhr`, `Asr`, `Maghrib`, `Isha`
   - Badan: `{arab} • Prayer time has come` — arab: `الفجر`, `الشروق`, `الظهر`, `العصر`, `المغرب`, `العشاء`
   - Kepentingan/keutamaan tinggi. Gagal jadual → abaikan.

> Menukar suis "Adhan Alerts" di Settings **tidak** membatalkan notifikasi serta-merta; ia hanya berkesan pada muat semula PrayerScreen seterusnya (kekalkan).

### A6.5 Tarikh Hijri

Guna kalendar **Umm al-Qura** (sama dengan pakej Dart `hijri`). Nama bulan berbeza mengikut skrin — lihat setiap skrin.

### A6.6 Pemilih imej (kamera/galeri)

| Skrin | Kualiti JPEG | Lebar maks (px) | Auto-imbas selepas pilih? |
|---|---|---|---|
| ScanScreen (dari Halal Checker) | 85 | 1024 | Ya |
| ScannerScreen (Scan Product) | 70 | 800 | **Tidak** (perlu tekan butang Imbas) |
| LogoScanScreen | 85 | 1200 | Ya |

Kecilkan imej secara berkadar hanya jika lebih lebar daripada had (tinggi tidak dihadkan). Batal pilih → tiada perubahan state.

---

## A7. Spesifikasi skrin

Notasi: `pad(L,T,R,B)`, `pad(h,v)`, `r=` jejari sudut, `gap N` = ruang kosong N.

### A7.1 HomeScreen

- Latar `#F0F4F0`. Kandungan dalam safe area, boleh skrol keseluruhannya (satu senarai menegak).
- **Jam**: tick setiap **1 s** → kemas kini `now` dan kiraan "Next prayer".
- Bila dibuka: `getLocation()` → `getPrayerTimes()` di latar. Gagal → senyap (banner tunjuk teks alternatif). **Tidak** geocode, **tidak** jadual notifikasi.

**Header** — gradient linear kiri-atas `#0D5C30` → kanan-bawah `#1B7A4B`, sudut bawah r=28 (atas tajam), `pad(20,20,20,28)`:
- Baris 1 (spaceBetween):
  - Kiri (kolum): `"SMART"` putih 28 berat 900 letterSpacing 3; `"SandboxPlayground"` putih@0.75, 12, letterSpacing 1.
  - Kanan: IconButton `settings_outlined` putih 26 → SettingsScreen.
- gap 20
- `"{greeting}!"` putih 22 bold. Greeting ikut jam tempatan: `<12` `Selamat Pagi`; `<15` `Selamat Tengahari`; `<18` `Selamat Petang`; selain itu `Selamat Malam`.
- `"Assalamu Alaikum Warahmatullahi Wabarakatuh"` white70 11.
- gap 16
- Kotak tarikh: latar putih@0.15, r=12, `pad(14,10)`, baris: ikon `calendar_today` white70 16, gap 8, kolum:
  - Gregorian `"{Hari}, {d} {Bul} {yyyy}"` — Hari: `Mon Tue Wed Thu Fri Sat Sun`; Bul: `Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec`. Putih 13 w600. Contoh `Tue, 10 Nov 2026`.
  - Hijri `"{hDay} {bulan} {hYear} AH"` white70 11. Bulan (1–12): `Muharram`, `Safar`, `Rabi' al-Awwal`, `Rabi' al-Thani`, `Jumada al-Awwal`, `Jumada al-Thani`, `Rajab`, `Sha'ban`, `Ramadan`, `Shawwal`, `Dhu al-Qi'dah`, `Dhu al-Hijjah`.

**Banner solat** — luar `pad(16,0,16,4)`, margin atas 16. Kotak putih r=16, bayang `#1B7A4B@0.10` blur 12 offset (0,4), `pad(18,14)`. Baris:
- Bulatan `pad 10` latar `#1B7A4B@0.10` berisi ikon `mosque_outlined` `#1B7A4B` 22. gap 14.
- Jika memuat: `"Loading prayer times…"` (guna aksara elipsis `…`) grey 13.
- Jika tiada data (gagal): `"Tap Prayer Times for details"` grey 13.
- Jika ada: kolum `"Next: {nama}  •  {masa}"` (dua ruang setiap sisi •) bold 14 `#1B4332`; gap 2; `"in {kiraan}"` `#1B7A4B` 12.
- TextButton `"View all"` 12 `#1B7A4B` → PrayerScreen.

Pengiraan "next" (Home): semak urutan Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha — yang pertama selepas `now`. Jika semua lepas → nama `Fajr (tomorrow)`, masa = fajr + 1 hari.
Format masa Home: `h:mm am/pm` huruf kecil — jam = `hour % 12`, 0→12; minit 2 digit; `am` jika hour<12. Cth `1:05 pm`.
Kiraan: jika ≥1 jam → `"{H}h {M}m"`; jika ≥1 minit → `"{M}m {S}s"`; selain itu `"{S}s"` (tiada pad sifar).

**Tajuk seksyen** `"Features"` bold 16 `#2D4A35`, `pad(20,16,20,8)`.

**Grid** `pad(16,0,16,24)`, 2 lajur, jarak 14×14, nisbah lebar:tinggi **1.05**. Jubin: latar putih, r=20, elevation 3 dengan warna bayang `{warnaJubin}@0.2`, ripple, `pad 18`, kolum kiri:
- Lencana: `pad 10`, latar `{warna}@0.12`, r=14, ikon `{warna}` 26.
- (ruang fleksibel)
- Label bold 14 `#1B2A1F`; gap 3; subtajuk 11 grey.500, 1 baris, elipsis.

| # | Label | Subtajuk | Ikon | Warna |
|---|---|---|---|---|
| 1 | Prayer Times | Waktu solat harian | access_time_rounded | `#1B7A4B` |
| 2 | Qibla Compass | Arah kiblat | explore_rounded | `#2E7D9A` |
| 3 | Halal Checker | Cari produk halal JAKIM | verified_rounded | `#2D6A4F` |
| 4 | Scan Product | Scan label produk | document_scanner_rounded | `#6B4C9A` |
| 5 | Tasbeeh | Zikir & tasbih digital | radio_button_checked_rounded | `#A05C2A` |
| 6 | Hijri Calendar | Takwim Islam | calendar_month_rounded | `#7B6D2F` |
| 7 | Drone Scanner | Scan halal label via DJI Mini 2 | flight_rounded | `#1565C0` |
| 8 | Scan Logo Halal | Kenal pasti badan pensijilan halal | verified_rounded | `#2E7D4F` |

### A7.2 SearchScreen ("Halal Checker")

- App bar latar `#1B7A4B`, tajuk **tengah** `"Halal Checker"` putih bold 20, ikon back putih. Aksi: IconButton `camera_alt` putih (tooltip `Scan product photo`) → ScanScreen.
- **Bar carian** (bersambung dengan app bar): latar `#1B7A4B`, `pad(16,0,16,16)`, baris:
  - TextField (isi ruang): teks putih; hint `"Search product or company..."` putih@0.7; latar putih@0.2; r=12; tiada sempadan; ikon awalan `search` putih. Tekan Enter/Search pada papan kekunci → cari.
  - gap 10
  - ElevatedButton `"Search"` bold: latar putih, teks `#1B7A4B`, `pad(16,14)`, r=12 (bukan pil).
- **Cari**: `query = trim(teks)`; kosong → tiada apa. Set loading, kosongkan ralat, `hasSearched = true`. Panggil `/search-grouped`. Berjaya → simpan senarai. Gagal (apa jua) → ralat `"Could not connect to server.\nMake sure the API is running."`, senarai dikosongkan.
- **Badan** (keutamaan atas ke bawah):
  1. Loading → spinner tengah warna `#1B7A4B`.
  2. Ralat → tengah, `pad 32`: ikon `wifi_off` 64 grey; gap 16; teks ralat tengah grey 16.
  3. `hasSearched && kosong` → ikon `search_off` 64 grey; gap 16; `"No halal-certified records found."` grey 16.
  4. Belum cari → ikon `verified_rounded` 80 `green.200`; gap 16; `"Search for a product or company\nto check halal status"` tengah grey 16; gap 8; `"348,844 certified records from JAKIM"` grey.400 13.
  5. Ada hasil → `"{n} syarikat ditemui"` grey.600 13 `pad(16,12,16,4)`, kemudian senarai `pad(12,4)` CompanyCard.

**CompanyCard** — Card (lalai M3) margin menegak 5 (mendatar 0), r=12, ripple, `pad 14`, kolum:
- Baris (atas-jajar): nama syarikat bold 15 (isi ruang); gap 8; lencana **HALAL**: `pad(8,3)` latar `green.50` sempadan `green.300` r=20, teks `"HALAL ✓"` `green.700` 11 bold.
- gap 6. Baris: ikon `inventory_2_outlined` 14 grey.600; gap 4; `"{product_count} produk bersijil"` grey.600 13; jika `popularity_score > 0`: gap 8 + lencana `pad(6,2)` latar `amber.50` sempadan `amber.300` r=8, ikon `star` 10 `amber.700`, gap 2, `"Popular"` 10 `amber.700` bold.
- gap 6. Baris: wrap cip kategori (jarak mendatar 4, tiada jarak menegak): `pad(8,2)` latar `#1B7A4B@0.08` r=20 teks `#1B7A4B` 11; di hujung ikon `chevron_right` grey.400.
- Tap → CompanyProductsScreen(company, query=teks medan carian semasa di-trim).

### A7.3 CompanyProductsScreen

- App bar `#1B7A4B`, ikon putih, tajuk = nama syarikat putih bold **14**, elipsis satu baris.
- Header penuh lebar: latar `#1B7A4B@0.06`, `pad(16,10,16,10)`, baris: ikon `verified_rounded` `green.600` 18; gap 8; `"{product_count} produk halal bersijil JAKIM"` w500 13 (guna `product_count` dari CompanyResult, bukan saiz senarai).
- Bila dibuka: `GET /company-products?company=..&q=..`.
  - Loading → spinner `#1B7A4B` tengah.
  - Status ≠200 → `"Ralat server: {status}"` merah (`#F44336`) tengah.
  - Exception → `"Tidak dapat sambung ke server."` merah tengah.
  - Kosong → `"Tiada produk ditemui."` tengah.
  - Ada → senarai `pad(12,8)` ProductCard.

**ProductCard** — Card margin menegak 5, r=12, ripple → DetailScreen, `pad 14`, kolum:
- Baris: syarikat bold 15 (isi ruang); lencana `"HALAL ✓"` sama seperti CompanyCard (tiada gap 8 sebelum lencana).
- Jika productName tidak kosong: gap 2, teks 13 w500, maks 2 baris elipsis.
- Jika brand tidak kosong: gap 2, teks grey.700 13.
- gap 8. Baris: cip kategori (`pad(8,2)` latar `#1B7A4B@0.08` r=20 teks `#1B7A4B` 11); ruang fleksibel; ikon `calendar_today` 12 grey.400; gap 4; `expiry` tidak kosong ? `"Exp: {expiry}"` : `"No expiry"` grey.500 12.

### A7.4 DetailScreen ("Halal Details")

- App bar `#1B7A4B`, ikon putih, tajuk `"Halal Details"` putih bold (22).
- Skrol, `pad 20`, kolum kiri:
  - Banner penuh lebar `#1B7A4B` r=16 `pad 20`, kolum tengah: ikon `verified_rounded` putih 48; gap 8; `"HALAL CERTIFIED"` putih 20 bold letterSpacing 2; `"JAKIM — Jabatan Kemajuan Islam Malaysia"` white70 12.
  - gap 24
  - Baris butiran (urutan): 
    1. `business` — `Syarikat` — company
    2. `label` — `Brand` — brand *(hanya jika tidak kosong)*
    3. `inventory_2` — `Produk` — productName *(hanya jika tidak kosong)*
    4. `category` — `Kategori` — category
    5. `badge` — `Certificate Code` — compCode
    6. `calendar_month` — `Expiry Date` — expiry atau `Not specified`
    7. `location_on` — `Address` — address atau `Not specified`
  - Setiap baris: `pad bawah 16`, baris atas-jajar: ikon `#1B7A4B` 22; gap 12; kolum: label grey 12 w500; gap 2; nilai 15; Divider (ruang 20 menegak, garis 1).

### A7.5 ScanScreen (dari ikon kamera Halal Checker)

- App bar `#1B7A4B`, ikon putih, `"Scan Product"` putih bold (22). Skrol `pad 20`, kolum regang:
- Baris dua butang (gap 12), setiap satu isi separuh:
  - `"Camera"` ikon `camera_alt`: latar `#1B7A4B`, teks/ikon putih, `pad(0,14)` menegak, r=12.
  - `"Gallery"` ikon `photo_library`: latar putih, teks/ikon `#1B7A4B`, sempadan 1 `#1B7A4B`, menegak 14, r=12.
- gap 20. Jika ada imej: pratonton tinggi 220, isi lebar, `cover`, r=12.
- gap 20.
- Loading: tengah — spinner `#1B7A4B`; gap 12; `"Analysing product..."` grey.
- Ralat: kotak `pad 16` latar `red.50` r=12 sempadan `red.200`, teks `red.700`. Teks = mesej exception **tanpa** awalan `Exception: `.
- Hasil (lihat bawah).
- Jika tiada imej dan tidak loading: placeholder tinggi 200 latar grey.100 r=12 sempadan grey.300; tengah: ikon `camera_alt_outlined` 56 grey.400; gap 12; `"Take or choose a photo of the product\nlabel to check its halal status"` tengah grey.500.
- Pilih imej → simpan, kosongkan hasil & ralat, **terus** imbas.

**Hasil ScanScreen** (`method = detection_method`):
- Kotak 1: `pad 16` latar `blue.50` r=12 sempadan `blue.100`. Baris: ikon (`visibility` jika vision / `text_fields` jika ocr / `image_search` lain) warna `blue`; gap 8; tajuk bold: vision `Produk Dikenal Pasti (Visual AI)` / ocr `Teks dikesan (OCR)` / lain `Best visual match`; ruang fleksibel; jika bukan ocr & bukan vision → pil `"{pct}% match"` (`pct = round(confidence*100)`, confidence null→0) `pad(8,3)` r=20, latar `green.100`/`orange.100` (pct≥70 / <70), teks 12 bold `green.700`/`orange.700`.
  - gap 8, kemudian:
  - vision: jika `vision_result.brand` tidak kosong → baris `"Brand: "` 13 grey.600 + brand 13 bold; gap 2; baris `"Produk: "` 13 grey.600 + (`vision_result.product` atau `-`) 13 bold. Jika brand kosong → `"Tiada maklumat brand/produk dikesan"` 13 grey.500.
  - ocr & `ocr_texts` tidak kosong → wrap cip (jarak 6) label 12, padat.
  - selain itu jika `matched_product` != null → teks 15 + `matched_category ?? ""` grey.600 13.
- gap 16
- Kotak 2: `pad 16` r=12; ada rekod → latar `green.50` sempadan `green.200`; tiada → `red.50` / `red.200`. Baris: ikon `verified_rounded`/`cancel_rounded` (`green.700`/`red.700`); gap 8; `"HALAL CERTIFIED (JAKIM)"` / `"Not found in JAKIM database"` bold warna sama.
  - Ada: gap 12; **3 rekod pertama sahaja**, setiap satu `pad bawah 8`, boleh tap → DetailScreen: kotak putih `pad 10` r=8 sempadan `green.100`, baris: kolum [company w600; brand (jika ada) grey.600 12; `"Exp: {expiry atau N/A}"` grey.500 11] + ikon `chevron_right` grey.400.
  - Tiada: gap 8; `"The matched product was not found in JAKIM's halal database. This does not necessarily mean it is haram — try searching by company name."` `red.700` 13.

### A7.6 ScannerScreen ("Scan Product" dari Home)

- App bar latar **`primary` `#2B6A46`**, teks/ikon putih, tajuk `"Imbas Produk Halal"` (22 regular).
- Skrol `pad 16`, kolum regang:
  - Banner info: `pad(12,8)` latar `#2B6A46@0.1` r=8 sempadan `#2B6A46@0.3`; baris: ikon `info_outline` 16 `#2B6A46`; gap 8; `"Imbas label produk untuk semak status halal JAKIM"` 12 `#2B6A46`.
  - gap 14
  - Kotak pratonton tinggi 240, latar `#EEEEEE`, r=12, sempadan: ada imej → 2 `#2B6A46`, tiada → 1 grey.400. **Tap kotak = buka galeri.** Ada imej → imej `cover` r=11. Tiada → tengah: ikon `image_outlined` 56 grey.500; gap 8; `"Tap untuk pilih dari galeri"` grey.500.
  - gap 12. Baris dua OutlinedButton (gap 10), isi separuh, padding menegak 12, sempadan & teks `#2B6A46`, bentuk pil: `"Kamera"` (`camera_alt`) dan `"Galeri"` (`photo_library`).
  - gap 16. ElevatedButton penuh lebar: latar `#2B6A46`, teks putih 16 bold, padding menegak 14, pil. Idle: ikon `document_scanner` + `"Imbas & Semak Halal"`. Loading: spinner putih 18×18 stroke 2 + `"Menganalisis..."`, butang **disabled**.
  - gap 20.
  - Ralat: Card latar `red.50`, `pad 12`, baris: ikon `error_outline` merah; gap 8; teks merah = `toString()` exception **dengan** awalan (cth. `Exception: Scan failed 500: {...}`).
  - Hasil (bawah).
- Tekan Imbas tanpa imej → SnackBar `"Pilih gambar dahulu"`.
- Imbas: loading, kosongkan ralat & hasil → `/scan-image`.

**Hasil ScannerScreen**:
- Card elevation 2, latar `#F0FFF4`, r=10, sempadan `#2B6A46@0.3`, `pad 14`:
  - Baris: ikon (vision `visibility` / ocr `text_fields` / lain `image_search`) 18 `#2B6A46`; gap 6; tajuk bold 14 `#2B6A46`: `Produk Dikenal Pasti (Visual AI)` / `Teks Dikesan (OCR)` / `Tiada Kaedah`.
  - Divider (ruang 16).
  - vision: brand ada → `"Brand:  "` (dua ruang) 13 grey.600 + brand 13 bold; gap 4; `"Produk: "` + (product atau `-`). Tiada brand → `"Tiada maklumat brand/produk dikesan"` 13 grey.500.
  - ocr & teks ada → wrap cip (jarak 6, baris 4): label 12, padat, latar `#2B6A46@0.1`, sempadan `#2B6A46@0.3`.
  - gap 8. Lencana: `pad(8,4)` r=12; ada rekod → latar `#1B7A4B` teks putih `"HALAL JAKIM"`; tiada → latar grey.300 teks grey.700 `"? Tiada Sijil"`; 11 bold.
- gap 10
- Ada rekod: `"Sijil Halal JAKIM ({n} rekod)"` bold `#2B6A46` `pad(L4,B6)`; kemudian **SEMUA** rekod sebagai Card (margin bawah 8) + ListTile tiga-baris:
  - leading: bulatan latar `#2B6A46@0.1` ikon `verified` 20 `#2B6A46`.
  - title: company w600.
  - subtitle kolum: `"Brand: {brand}"` (jika ada); `"Produk: {productName}"` (jika ada); `"Kategori: {category}"`; `"Kod: {compCode}"` 11; `"Luput: {expiry}"` 11 w500 — merah (`#F44336`) jika tamat, `green.700` jika belum.
  - Tamat: pecah `expiry` dengan `/`; jika 3 bahagian & semua integer → `DD/MM/YYYY` sebelum **sekarang** = tamat. Format lain → anggap belum tamat.
  - Rekod **tidak** boleh ditap.
- Tiada rekod: Card latar `orange.50` `pad 12`, baris: ikon `warning_amber_rounded` `orange.700`; gap 8; `"Tiada rekod halal JAKIM ditemui untuk produk ini.\nIni tidak bermakna produk tidak halal — mungkin tiada dalam pangkalan data."` 13.

### A7.7 LogoScanScreen ("Scan Logo Halal")

- Latar `#F0F4F0`. App bar `#1B7A4B`, ikon putih, `"Scan Logo Halal"` putih bold. Skrol `pad 20`, kolum regang.
- **Kad info**: `pad 16` latar `#1B7A4B@0.07` r=14 sempadan `#1B7A4B@0.2`; baris: bulatan `pad 10` latar `#1B7A4B@0.15` ikon `verified_rounded` 26 `#1B7A4B`; gap 14; kolum: `"Scan Logo Halal"` bold 14 `#1B4332`; gap 4; `"Ambil gambar logo halal pada produk untuk semak sama ada badan pensijilan diiktiraf JAKIM."` 12 `#4A7A5A` tinggi baris 1.4.
- gap 20. **Pratonton**: tinggi 220, putih, r=16, sempadan 2 (ada imej `#1B7A4B@0.4`, tiada grey.300). **Tap = buka KAMERA.** Imej `contain` r=14. Tiada → ikon `add_a_photo_rounded` 48 grey.400; gap 10; `"Ketuk untuk ambil gambar"` grey.500 13.
- gap 14. Dua butang (gap 12) isi separuh: permukaan putih r=12 elevation 1, ripple, padding menegak 12, baris tengah: ikon 18 `#1B7A4B`, gap 8, label w600 13 `#1B4332`. `"Kamera"` (`camera_alt_rounded`), `"Gallery"` (`photo_library_rounded`). Disabled semasa loading.
- gap 20.
- Loading: tengah spinner `#1B7A4B`; gap 12; `"Qwen Vision sedang menganalisis logo..."` `#4A7A5A` 13; gap 20.
- Ralat: kotak `pad 14` `red.50` r=12 sempadan `red.200` teks `red.700` 13; gap 20.
  - Status ≠200 → `"Server error {status}: {body}"`; exception → `"Gagal scan: {exception}"`.
- Hasil (bila tidak loading dan `is_recognised_by_jakim` != null):
  - **Banner status**: `pad(18,14)` r=14. Diiktiraf: warna `#1B7A4B`, latar `#1B7A4B@0.08`, ikon `check_circle_rounded`, teks `"Diiktiraf oleh JAKIM"`. Tidak: warna `#C62828`, latar `#C62828@0.07`, ikon `cancel_rounded`, teks `"Tidak Dijumpai dalam Senarai JAKIM"`. Sempadan `{warna}@0.3`, ikon 28, gap 14, teks bold 15 `{warna}`.
  - gap 16. Jika `vision_result` != null → **kad vision**: putih `pad 16` r=14 sempadan grey.200; baris ikon 16 + label bold 12 warna `#5C35A0`: `"Teks Dikesan ({kaedah})"` — kaedah = `OCR (PaddleOCR)` jika `detection_method=="ocr"` (ikon `text_fields_rounded`), selain itu `Qwen Vision` (ikon `psychology_rounded`); Divider (ruang 20); jika `extractedTexts` tidak kosong → infoRow `"Teks dalam logo"` = teks dicantum `", "`; jika kosong → `"Tiada teks dikesan"` 12 grey.500.
    - `extractedTexts` dibaca dari kunci `extracted_texts` — **kunci ini tiada dalam respons server**, jadi app asal sentiasa papar `Tiada teks dikesan`. **Pembaikan dibenarkan**: jika `extracted_texts` tiada, guna `vision_result.all_text`.
  - gap 16. Setiap match → **kad match** (margin bawah 12): putih `pad 16` r=14 sempadan `#1B7A4B@0.25` bayang `#1B7A4B@0.05` blur 8 offset (0,2). Baris: pil `pad(10,4)` `#1B7A4B` r=8 teks putih bold 12 = abbreviation atau `CB`; gap 10; country w600 13 `#4A7A5A` (atau `-`). gap 10. organization_name (atau `-`) bold 13 `#1B2A1F` tinggi baris 1.4. Kemudian, jika tidak kosong: gap 8 infoRow `Email` (ikon `email_outlined`); gap 6 `Website` (`language_rounded`); gap 6 `Telefon` (`phone_rounded`).
  - Tiada match → kad oren `pad 16` `orange.50` r=14 sempadan `orange.200`: baris ikon `help_outline_rounded` 20 `orange.700`, gap 8, `"Badan tidak dijumpai"` bold 13 `orange.800`; gap 8; `"Logo ini mungkin:\n• Bukan logo halal\n• Dari badan yang belum diiktiraf JAKIM\n• Gambar kurang jelas — cuba ambil gambar yang lebih dekat dan terang"` 12 `orange.700` tinggi baris 1.5.
- **infoRow(key, value, icon?)**: baris atas-jajar: [ikon 14 grey.500 + gap 6]; `"{key}: "` 12 w600 grey.600; nilai (isi ruang) 12 `#1B2A1F`. Teks biasa (tidak boleh ditap).
- Pilih imej → kosongkan hasil & ralat → terus imbas.

### A7.8 PrayerScreen

- Tiada app bar; latar `#F6FBF3`; safe area.
- Jam tick **1 s**.
- Muat: loading → `getLocation()` → `getPrayerTimes()` → simpan → **jadual notifikasi** (A6.4, `adhan_enabled` lalai true) → geocode di latar (kemas kini lokasi bila siap). Ralat → papar teks ralat.
- **Loading**: tengah spinner `#1A7A4A`; gap 16; `"Loading prayer times..."` grey.
- **Ralat**: tengah `pad 32`: ikon `error_outline` 64 merah; gap 16; teks ralat tengah grey; gap 24; ElevatedButton ikon `refresh` `"Try GPS Again"` latar `#1A7A4A` teks putih (pil) → muat semula; gap 12; OutlinedButton ikon `list_alt` `"Choose location manually"` teks & sempadan `#1A7A4A` → pemilih lokasi; gap 20; kotak tip `pad 12` latar `#1A7A4A@0.07` r=10: ikon `tips_and_updates_outlined` 18 `#1A7A4A`, gap 8, `"GPS is slow indoors. Pick your city manually — prayer times still use the official JAKIM data!"` 12 `#1A7A4A`.
- Pemilih lokasi dipilih → loading → muat dari lokasi itu (termasuk jadual notifikasi, **tanpa** geocode).
- **Kandungan**:
  - **Header** penuh lebar `#1A7A4A`, sudut bawah r=24, `pad 20`:
    - Baris spaceBetween. Kiri kolum: `"Prayer Times"` putih 22 bold; gap 2; baris ikon `location_on` white70 14, gap 4, `"{city}, {region}"` white70 13 (sebelum geocode siap teks ini memang `", "` — kekalkan).
    - Kanan baris: pil sumber `pad(10,4)` putih@0.2 r=20, teks putih 11: `"🏛 {zone atau MY}"` jika WaktuSolat, `"🌍 Calculated"` jika offline; gap 8; IconButton `edit_location_alt_outlined` putih, latar bulat putih@0.15, padding 0 (tooltip `Change location`) → pemilih.
    - gap 12. Tarikh `"{Hari}, {d} {Bulan} {yyyy}"` white70 13 — Hari penuh `Monday…Sunday`, Bulan penuh `January…December`.
    - Hijri putih 15 w500: jika `hijri` API ada (`"YYYY-MM-DD"`) → `"{int(DD)} {bulan} {YYYY}"` dengan bulan: `Muharram`, `Safar`, `Rabi Al-Awwal`, `Rabi Al-Akhir`, `Jumada Al-Ula`, `Jumada Al-Akhirah`, `Rajab`, `Sha'ban`, `Ramadan`, `Shawwal`, `Dhul Qa'dah`, `Dhul Hijjah`. Jika tiada → `"{tahunMasihi - 579} AH"`.
  - **Kad seterusnya** `pad(16,16,16,0)`: putih r=16 bayang hitam@0.07 blur 10 offset (0,3), `pad 16`, baris: kotak `pad 10` `#1A7A4A@0.1` r=12 ikon `access_time` 24 `#1A7A4A`; gap 12; kolum `"Next Prayer"` grey 12 + nama bold 16; ruang fleksibel; kiraan `HH:MM:SS` (pad 2 digit; negatif → 00:00:00) **monospace** 20 bold `#1A7A4A`.
  - **Senarai** `pad 16`, 6 baris (margin bawah 10), `pad(16,14)`, r=14, bayang hitam@0.05 blur 6 offset (0,2):
    - Semasa: latar `#1A7A4A`, ikon & teks putih, lencana `"NOW"` (`pad(8,3)` margin kanan 10 putih@0.25 r=10, putih 10 bold).
    - Seterusnya (dan bukan semasa): latar putih, sempadan 1.5 `#1A7A4A`, lencana `"NEXT"` (latar `#1A7A4A@0.1`, teks `#1A7A4A` 10 bold).
    - Lain: putih, ikon `#1A7A4A`, teks black87.
    - Kandungan: ikon 22; gap 14; nama 16 w600; ruang fleksibel; [lencana]; masa 15 bold.
    - Nama & ikon: Fajr `brightness_3`, Sunrise `wb_sunny_outlined`, Dhuhr `wb_sunny`, Asr `cloud_queue`, Maghrib `wb_twilight`, Isha `nights_stay`.
    - Masa: `h:mm AM/PM` — `hour>12 ? hour-12 : (hour==0 ? 12 : hour)`, `PM` jika hour≥12.
- **Semasa/seterusnya** (urutan 6 masa termasuk Sunrise):
```
current = -1; next = 0
for i in 0..4: if now > t[i] && now < t[i+1] { current = i; next = i+1; break }
if now > t[5] { current = 5; next = 0 }
```
  - Jika `next == 0`: nama kad = `"Fajr (tomorrow)"`, sasaran kiraan = fajr + 1 hari. (Sebelum Subuh: current = -1, next = 0 → juga "Fajr (tomorrow)" dan kiraan ~24 jam — tingkah laku asal, kekalkan.)

### A7.9 LocationPicker (dialog)

- AlertDialog tajuk `"Choose your location"`, kandungan lebar penuh, tinggi **500**, `pad(16,12,16,0)`. Aksi: TextButton `"Cancel"` → tutup (pulangkan null).
- TextField autofocus: hint `"Search city or country..."`, ikon awalan `search` `#1A7A4A`, ikon akhiran `clear` 18 (hanya bila ada teks; tekan → kosongkan teks, hasil, ralat, `hasSearched=false`), sempadan outline r=10, fokus `#1A7A4A`, `pad(12,10)`.
- Taip: debounce **600 ms**. `trim` < 2 aksara → kosongkan hasil/ralat, `hasSearched=false`.
- Cari: forward geocode (timeout 8 s). Tiada → ralat `No results for "{query}"`. Untuk maks **6** hasil: reverse geocode (timeout 4 s, gagal → langkau) → `city = locality ?? subAdministrativeArea ?? name ?? query`, `region`, `country` (nama), `countryCode`. Buang pendua `city|country`. Kosong → ralat sama. Exception → `Search failed. Check your internet connection.`
- Di bawah medan: jika mencari → bar kemajuan linear `#1A7A4A` (margin atas 6); jika tidak → ruang 6.
- Ralat: `pad(0,6)` teks 12 warna `error` `#BA1A1A`.
- Hasil (jika ada): gap 8; `"Results"` 11 bold `#1A7A4A` letterSpacing 1.1; gap 4; senarai ListTile padat: leading bendera emoji dari `countryCode` (2 huruf → regional indicator; lain → `🌍`) saiz 22; title city 14 w500; subtitle `"{region}, {country}"` (abaikan bahagian kosong) 12. Tap → pulangkan `LocationData(lat, lng, city, region, country=countryCode)`. Divider.
- Pilihan pantas (jika `!hasSearched` atau tiada hasil): gap 4; `"🇲🇾  Quick select — Malaysia"` (dua ruang) 11 bold `#1A7A4A` letterSpacing 1.1; gap 4; senarai 14 bandar (A6.1) ListTile padat: ikon `location_city` 20 `#1A7A4A`; title label 14; subtitle region 12. Tap → pulangkan.

### A7.10 QiblaScreen

- Tiada app bar; latar `#F6FBF3`; safe area. Kolum: header + kandungan.
- **Header** penuh lebar `#1A7A4A` sudut bawah r=24 `pad(20,20,12,16)`, baris: kolum [`"Qibla Compass"` putih 22 bold; gap 4; baris ikon `location_on` white70 14, gap 4, teks white70 13 elipsis = `"{city}, {region}"` atau `"Detecting location..."` jika tiada lokasi]; IconButton `edit_location_alt_outlined` putih latar putih@0.15 (tooltip `Change location`) → pemilih. **Sentiasa kelihatan.**
- Muat: loading → `getLocation()` → `applyLocation()` → geocode latar. Ralat → teks ralat.
- `applyLocation(loc)`: simpan, kira bearing, loading=false, ralat=null; muat declination di latar; hentikan langganan kompas lama; reset putaran; mula kompas baharu. Ralat strim → `"Compass error: {e}\n\nTry choosing location manually, or restart the app."`. Strim tamat & accuracy masih 999 → `"Compass sensor stopped. Tap \"Try GPS Again\" to restart."`. Tiada sensor → `"No compass sensor on this device.\n({mesej})"`.
- Pemilih → `applyLocation(pilihan)` + geocode latar.
- **Loading**: spinner `#1A7A4A`; gap 16; `"Getting your location..."` grey.
- **Ralat**: seperti PrayerScreen tetapi ikon `location_off` 64 merah dan tip: `"GPS is slow indoors. Use \"Choose location manually\" to pick your nearest city — the compass still works perfectly!"`.
- **Kompas** — susun atur responsif:
  - Telefon (lebar ≤ 600): skrol `pad 24`, kolum: [banner kalibrasi]; gap 16; dail; gap 16; panel info. Saiz dail = `clamp(lebar*0.75, 220, 300)`.
  - Tablet (> 600): skrol `pad 32`: [banner]; baris: kiri (flex 5) gap 16 + dail; gap 32; kanan (flex 5) panel info (dengan gap 16 di atas). Saiz = `clamp(lebar*0.40, 280, 420)`.
- **Banner kalibrasi** (jika accuracy > 45): margin `(16,8)`, `pad(14,10)`, latar `red.50` r=14 sempadan `red.300`; baris: animasi angka-8 (36×36); gap 12; kolum: tajuk bold 13 merah — accuracy ≥999 `"Compass unreliable — calibration required"` / lain `"Compass needs calibration"`; gap 2; `"Wave your phone in a figure-8 pattern to improve accuracy."` 11 merah. Muncul/hilang dengan fade 400 ms.
  - **Angka-8**: kitaran 2 s berulang. Laluan lemniskat: `x = cos t/(1+sin²t)*14 + 18`, `y = sin t cos t/(1+sin²t)*14 + 18`; lukis laluan (121 titik) garisan 1.5 `red.200`; titik bulat jejari 3 merah pada `t = progres*2π`.
- **Dail** (saiz S, kanvas S×S), lapisan dari bawah:
  1. Dail statik: pusat (S/2,S/2), `R = S/2 - 10`. Bulatan isi putih jejari R; lingkaran 2 `#1A7A4A@0.3`. 36 tanda setiap 10°: major bila `i % 9 == 0` (0°,90°,180°,270°), dari jejari `R-18` (major) / `R-10` (minor) ke `R-4`, garisan 1.5 grey.400. Sudut dari atas ikut arah jam: `(cx + r sinθ, cy - r cosθ)`. Label pada jejari `R-30`: `N` (0°) `#1A7A4A` 16 bold; `E` (90°), `S` (180°), `W` (270°) grey.600 13; dipusatkan. Titik pusat jejari 6 `#1A7A4A`. **Dail tidak berputar.**
  2. Anak panah (berputar sekitar pusat dengan sudut animasi): warna `#1A7A4A`. Badan segi empat pusat `(cx, cy - 85/2 + 10)` lebar 10 tinggi 65. Kepala segitiga `(cx, cy-85)`, `(cx-16, cy-57)`, `(cx+16, cy-57)`. Ekor segitiga `#1A7A4A@0.4`: `(cx, cy+20)`, `(cx-8, cy+5)`, `(cx+8, cy+5)`.
  3. Emoji `🕋` saiz font `S*0.079`, ditranslasi dari pusat dengan offset `(S*0.357*sin(rad), -S*0.357*cos(rad))` di mana `rad` = sudut animasi (tidak berputar, hanya bergerak).
- **Panel info** (kolum regang):
  - Lencana ketepatan (tengah):
    - ≥999: `pad(12,6)` grey.100 r=20 sempadan grey.300, `"Initializing sensor..."` 13 grey.700.
    - ≤10: `"✅  HIGH accuracy  ±{acc}°"`, latar `green.50`, teks `green.800`.
    - ≤45: `"⚠️  MEDIUM  ±{acc}° — wave in figure-8"`, `orange.50` / `orange.800`.
    - lain: `"❌  LOW  ±{acc}° — needs calibration"`, `red.50` / `red.800`.
    - (bukan 999) `pad(14,8)` r=20 sempadan `{teks}@0.35`, 13 w600. `{acc}` = dibundarkan tanpa perpuluhan. Perhatikan dua ruang selepas emoji.
  - gap 16. Baris dua kad (gap 12) isi separuh: `"Qibla Direction"` = `"{qibla, 1 perpuluhan}°"` ikon `explore`; `"Compass Heading"` = `"{heading, 1 perpuluhan}°"` ikon `navigation`. Kad: latar `#F6FBF3` r=12 bayang hitam@0.06 blur 8 offset (0,2) `pad 16`, kolum tengah: ikon 22 `#1A7A4A`; gap 6; nilai 18 bold `#1A7A4A`; label 11 grey.
  - gap 12. **Kad declination**: sama gaya, `pad(16,12)`, baris: ikon `compass_calibration_outlined` 24 `#1A7A4A`; gap 12; kolum [`"Magnetic Declination"` 11 grey; nilai 18 bold `#1A7A4A`; subtajuk 11 grey]; ruang fleksibel; ikon `info_outline` 18 grey dengan tooltip (tap untuk papar): `"The angle between true north and magnetic north at your location.\nPositive = magnetic north is east of true north.\nAndroid's sensor fusion already corrects for this."`. Nilai: belum dimuat `—` / `Loading...`; dimuat `"{+ jika ≥0}{dec, 2 perpuluhan}°"` / `East declination` (≥0) atau `West declination`.
  - gap 12. Kotak tip `pad 12` `#1A7A4A@0.08` r=12: ikon `info_outline` 18 `#1A7A4A`, gap 8, `"Wait for HIGH accuracy badge before comparing with other apps. Hold device flat, away from USB cables and metal objects."` 12 `#1A7A4A`.

### A7.11 CalendarScreen

- Latar `#F5F5F5`, tiada app bar, safe area. Bulan dipaparkan bermula dengan bulan Hijri hari ini.
- **Header** `#1A7A4A` sudut bawah r=24 `pad 20`: `"Hijri Calendar"` putih 22 bold; gap 4; `"Today: {hDay} {bulan} {hYear} AH"` white70 13.
  - Bulan (skrin ini): `Muharram`, `Safar`, `Rabi' Al-Awwal`, `Rabi' Al-Akhir`, `Jumada Al-Ula`, `Jumada Al-Akhirah`, `Rajab`, `Sha'ban`, `Ramadan`, `Shawwal`, `Dhul Qa'dah`, `Dhul Hijjah`.
- **Navigasi** `pad(16,12)` spaceBetween: IconButton `chevron_left` `#1A7A4A` (bulan sebelum; Muharram → Dhul Hijjah tahun-1); tengah kolum [nama bulan 20 bold; `"{tahun} AH"` grey 13]; IconButton `chevron_right` (bulan seterusnya).
- **Hari minggu** `pad(16,0)`: `Sun Mon Tue Wed Thu Fri Sat`, tengah, w600 12, grey kecuali `Fri` `#1A7A4A`.
- gap 8. **Grid** `pad(16,0)` (isi baki ruang, **tidak skrol**), 7 lajur, nisbah lebar:tinggi **0.85**. Sel kosong sebanyak `offset = weekday(1 hb Hijri dalam Gregorian)` dengan Ahad=0…Sabtu=6. Kemudian sel hari 1..`hariDalamBulan` (Umm al-Qura).
  - Sel: margin 2, r=10. Hari ini → latar `#1A7A4A`, bayang `#1A7A4A@0.3` blur 6 offset (0,2), teks putih / white70. Jumaat (Gregorian) → latar `#1A7A4A@0.12`, nombor Gregorian `#1A7A4A`. Lain → putih, nombor Gregorian grey.
  - Kandungan tengah: hari Hijri 16 bold (black87 / putih); hari Gregorian 10.
- **Legenda** `pad(16,0,16,12)` tengah: titik 12 `#1A7A4A` + `"Today"`; gap 20; titik `#1A7A4A@0.15` + `"Friday"` (12 grey, gap 6 antara titik & teks).

### A7.12 TasbeehScreen

- Tiada app bar; safe area; latar `#F6FBF3`.
- Muat `tasbeeh_count` (0) & `tasbeeh_target` (33).
- **Header** penuh lebar, gradient kiri-atas `#1A7A4A` → kanan-bawah `#0F5C38`, **tiada** sudut bulat, `pad 20`, baris: kolum [`"Tasbeeh"` putih 22 bold; gap 4; `"Digital Dhikr Counter"` white70 13]; menu popup ikon `tune` putih (tooltip `Set target`) — pilihan `33`, `66`, `99`, `100`; yang aktif ada ikon `check` 18 `#1A7A4A` (lain ruang 18), gap 8, nombor.
- Badan `pad(24,16)` kolum tengah:
  - Jika `cycles = count / target` (integer) > 0: pil `pad(14,6)` `#1A7A4A@0.1` r=20, `"Completed: {cycles} × {target}"` `#1A7A4A` w600 13.
  - ruang fleksibel
  - **Butang kiraan** 230×230, keseluruhannya boleh ditap: cincin kemajuan (stroke 10, trek grey.200, nilai `#1A7A4A`, nilai = `(count % target)/target`, bermula dari atas ikut arah jam); bulatan dalam 190×190 `#1A7A4A` bayang `#1A7A4A@0.35` blur 24 offset (0,8); tengah: `count` putih 60 bold (tinggi baris 1.1); `"/ {target}"` white70 16.
  - **Animasi tap**: skala 1.0 → 0.88 dalam 120 ms easeOut, kemudian balik ke 1.0 (120 ms).
  - gap 20. Frasa 22 w600 black87: jika target ∈ {33,66,99} → `["Subhanallah","Alhamdulillah","Allahu Akbar"][clamp(count/33, 0, 2)]`; target 100 → `ذِكر`.
  - gap 6. `"Tap the circle to count"` 13 grey.500.
  - ruang fleksibel
  - Jika target ∈ {33,66,99}: baris tengah 3 cip (padding mendatar 6 antara): label `Subhanallah`/`Alhamdulillah`/`Allahu Akbar` 11; tercapai bila `count ≥ (i+1)*33` → latar `#1A7A4A` teks putih; belum → latar `#1A7A4A@0.1` teks `#1A7A4A`; sempadan 0.8 `#1A7A4A`.
  - gap 20. TextButton ikon `refresh` 18 `"Reset"` merah → dialog `"Reset counter?"` / `"This will set the count back to 0."`, butang `"Cancel"` (tutup) dan `"Reset"` (merah; tutup + count=0 + simpan).
  - gap 8.
- **Tap**: haptic sederhana; animasi; `count++`; simpan serta-merta. Jika `count == target || count % target == 0` → haptic kuat tambahan.
- Tukar target → simpan; `count` **tidak** direset.

### A7.13 SettingsScreen

- Tiada app bar; safe area; latar `#F6FBF3`.
- **Header** `#1A7A4A` sudut bawah r=24 `pad 20`: `"Settings"` putih 22 bold; gap 4; `"Customise your QiblaApp"` white70 13.
- Senarai skrol `pad 16`:
  - Tajuk seksyen (`pad(L4,B8)`, HURUF BESAR, `#1A7A4A` 11 bold letterSpacing 1.2): `NOTIFICATIONS`.
  - Jubin suis (margin bawah 8, latar `#F6FBF3` r=12 bayang hitam@0.04 blur 6 offset (0,2), ListTile: ikon `#1A7A4A`, tajuk w500, subtajuk 12 grey, suis kanan dengan ibu jari aktif `#1A7A4A`):
    - `notifications_active` `"Adhan Alerts"` / `"Play Adhan at prayer times"` → `adhan_enabled`.
    - `vibration` `"Vibration"` / `"Vibrate at prayer times"` → `vibration_enabled`.
  - gap 16. `PRAYER CALCULATION`. Jubin dropdown (gaya kad sama, `pad(16,8)`, baris: ikon `#1A7A4A`; gap 14; kolum tajuk w500 + subtajuk 12 grey; dropdown tanpa garis bawah, teks `#1A7A4A` w600):
    - `calculate_outlined` `"Calculation Method"` / `"Used outside Malaysia"`; pilihan: `Singapore`, `Muslim World League`, `ISNA (North America)`, `Umm Al-Qura (Saudi)`, `Egyptian`, `Karachi` → `calc_method`.
    - `mosque_outlined` `"Madhab (Asr)"` / `"Affects Asr prayer time"`; `Shafi`, `Hanafi` → `madhab`.
  - gap 16. `API SERVER`. Kad (margin bawah 8, `pad 12`, gaya sama): baris ikon `cloud_outlined` `#1A7A4A`, gap 8, kolum [`"Halal Checker Server URL"` w500; `"Paste your ngrok URL here to use from any network"` 12 grey]; gap 10; TextField outline padat `pad(10,10)` papan kekunci URL, hint `"https://xxxx.ngrok-free.app"`, nilai awal = `api_url` atau kosong; ikon akhiran `save_outlined` `#1A7A4A` (tooltip `Save URL`): trim; kosong → buang key, jika tidak → simpan; tutup papan kekunci; SnackBar `"Server URL saved"` latar `#1A7A4A`. gap 6; `"Leave empty to use local WiFi (192.168.x.x)"` 11 grey.500.
  - gap 16. `ABOUT`. Jubin info (latar **putih**, gaya sama, ListTile tanpa trailing):
    - `info_outline` `Version` / `1.0.0`
    - `public` `Prayer Data (Malaysia)` / `api.waktusolat.app — JAKIM official times`
    - `calculate` `Prayer Data (Worldwide)` / `Calculated using Muslim World League formula`
    - `explore_outlined` `Qibla Bearing` / `Great Circle formula toward Mecca (21.4225°N, 39.8262°E)`
  - gap 16. Kotak `pad 14` `#1A7A4A@0.08` r=12: ikon `check_circle_outline` `#1A7A4A`; gap 10; `"In Malaysia: prayer times are fetched directly from JAKIM's official database via api.waktusolat.app. Your GPS zone is auto-detected."` 12 `#1A7A4A`.
- Semua perubahan disimpan **serta-merta**.

### A7.14 DroneScreen

Spesifikasi UI (sama untuk kedua-dua platform); integrasi SDK DJI di Bahagian B.

- Latar hitam. App bar hitam, ikon putih. Tajuk baris: titik 8×8 (`greenAccent` jika bersambung, merah jika tidak); gap 8; teks putih 15 = nama model jika bersambung, jika tidak `"Drone Scanner"`; jika merakam: gap 10 + pil `pad(7,2)` merah r=4 `"REC"` putih 11 bold.
  - Aksi: jika loading atau mengimbas → spinner putih 18×18 stroke 2 (`pad 16`); IconButton `refresh` white70 → semak status.
- **State**: `droneStatus` (String, lalai `"disconnected"`), `log`, `loading`, `sdkRegistered`, `isRecording`, `isScanning`, `scanResults` (list), `scanStatus` (`""`/`"halal"`/`"not_found"`/`"error"`), `detectedText`. `isConnected = droneStatus.startsWith("connected:")`.
- **Bila dibuka**: `sdkRegistered = isSDKRegistered()`; semak status; kemudian pemasa **setiap 5 s** semak status selagi belum bersambung. Henti pemasa bila skrin ditutup.
- **Semak status**: loading → `getDroneStatus()` → simpan. Bersambung → log `"{model} bersambung"`. Tidak → `reason` = semua selepas `:` pertama (di-trim); log = reason atau `"Drone tidak bersambung"`.
- **Daftar SDK**: loading, log `"Mendaftar SDK..."` → `registerSDK()` → berjaya: `sdkRegistered=true`, log `"SDK berjaya ({hasil})"`, semak status; gagal: `"Pendaftaran gagal: {e}"`.
- **Gambar**: log `"Mengambil gambar..."` → berjaya `"Gambar disimpan dalam SD card drone."`; gagal `"Guna butang RC untuk ambil gambar (SDK limitation)"`.
- **Video**: jika merakam → henti → `isRecording=false`, `"Video disimpan dalam SD card drone."`; jika tidak → mula → `true`, `"Sedang merekod..."`. Gagal → `"Ralat rekod: {e}"`.
- **Scan Halal**:
  1. `isScanning=true`, kosongkan hasil, `scanStatus=""`, `detectedText=""`, log `"Mengimbas logo/nama halal..."` → `"[1/3] Capture frame..."`.
  2. `captureFrame()` → JPEG bytes; log `"[2/3] Frame {kb}KB → server..."` (`kb` = bytes/1024, 1 perpuluhan).
  3. POST `/scan-premises` (A5.2 #5).
  4. `matches = data.matches ?? data.halal_records ?? []`; `visionTexts = data.vision_texts ?? [vision_result.brand, vision_result.product]` (yang String tidak kosong); `ocrTexts = data.ocr_texts ?? []`; `allTexts` = gabung, unik ikut susunan, ambil 5 pertama; `detectedText = join(" | ")`.
  5. `scanStatus = matches ? "halal" : "not_found"`; log `"[3/3] {n} premis dijumpai"` atau `"[3/3] Tiada rekod — dikesan: \"{detectedText}\""`.
  6. Exception → `scanStatus="error"`, log `"ERROR: {e}"`. Akhir: `isScanning=false`.
- **Susun atur** kolum:
  1. **Suapan kamera** nisbah **16:9** penuh lebar (lapisan):
     - Paparan video DJI (sentiasa wujud dalam hierarki supaya permukaan video sedia).
     - Jika tidak bersambung: lapisan hitam@0.87, tengah: ikon `videocam_off_rounded` 48 white24; gap 12; `"Sambungkan RC ke phone via USB-C"` white38 12; gap 16; jika SDK belum didaftar → OutlinedButton ikon `key_rounded` 16 blueAccent `"Daftar SDK"` blueAccent 13, sempadan blueAccent (disabled semasa loading).
     - Jika mengimbas: lapisan black54, tengah spinner `greenAccent`; gap 12; `"Mengimbas..."` putih.
     - Jika `scanStatus` tidak kosong & tidak mengimbas: pil atas-kanan (8,8) `pad(10,5)` r=20 — `"halal"` → latar `green.700` `"✓ HALAL"`; lain → `red.700` `"✗ TIADA REKOD"`; putih 11 bold.
  2. Kawasan skrol:
     - Kawalan latar hitam `pad(16,12,16,8)`: jika log ada → teks white54 11 tengah (`pad B8`); baris spaceEvenly 4 butang; gap 8.
     - Butang bulat: saiz 48 (besar 66); sempadan 2 (besar 3) warna butang; isi telus (besar `{warna}@0.15`); ikon 22 (besar 30); gap 4; label 10 (besar bold). Disabled → opacity 0.3.
       1. `key_rounded` — `"SDK ✓"` white30 jika didaftar (disabled), jika tidak `"Daftar"` blueAccent → daftar.
       2. **Besar** `document_scanner_rounded` `"Scan Halal"` greenAccent → aktif jika `sdkRegistered && !isScanning`.
       3. `camera_rounded` `"Gambar"` putih → aktif jika bersambung & !loading.
       4. `videocam_rounded` `"Video"` putih / semasa rakam `stop_rounded` `"Stop"` merah → aktif jika bersambung & !loading.
     - Panel hasil (jika ada hasil ATAU `not_found`): hitam penuh lebar `pad(16,4,16,24)`: baris ikon 16 (`cancel_rounded` merah / `verified_rounded` greenAccent), gap 6, `"Tiada rekod halal dalam pangkalan data JAKIM"` atau `"{n} rekod halal dijumpai"` 13 bold warna sama. Jika `detectedText` → gap 4 `"Dikesan: {detectedText}"` white38 10. Jika ada hasil → gap 10 + kad:
       - Kad: margin bawah 8, `pad 12`, latar `#0D1F14`, r=8, sempadan `green.900`. Baris: ikon `store_rounded` greenAccent 16; gap 8; `company ?? brand ?? "-"` putih 13 w600 (isi); jika expiry → pil `pad(6,2)` `green.900` r=4 teks greenAccent 9. Jika category → gap 4 teks white54 10. Jika address → gap 4 baris ikon `location_on_rounded` white30 11, gap 4, alamat white38 10 maks 2 baris elipsis.

---

## A8. Tingkah laku asal yang mesti dikekalkan (walaupun nampak pelik)

1. Dua warna hijau berbeza (`#1B7A4B` vs `#1A7A4A`) dan ScannerScreen guna `#2B6A46`.
2. Header Prayer/Qibla papar `", "` sebelum geocode siap.
3. Sebelum Subuh, PrayerScreen tandakan "Fajr (tomorrow)" sebagai seterusnya dan kiraan ~24 jam.
4. Home **tidak** jadual notifikasi; hanya PrayerScreen.
5. `vibration_enabled`, `calc_method`, `madhab` disimpan tetapi tidak digunakan.
6. ScanScreen papar 3 rekod pertama (boleh tap); ScannerScreen papar semua (tidak boleh tap).
7. Halal Checker tidak menyusun semula hasil server.
8. Ralat ScannerScreen mengandungi awalan `Exception: `; ScanScreen tidak.

## A9. Pembaikan yang DIBENARKAN (sedikit berbeza dari asal kerana asal rosak)

1. LogoScanScreen: guna `vision_result.all_text` jika `extracted_texts` tiada (A7.7).
2. Minta kebenaran notifikasi semasa app dimulakan (Android 13+ / iOS) — app asal tidak meminta, jadi notifikasi tidak muncul pada peranti baharu.

## A10. Senarai semak penerimaan

- [ ] 14 skrin/dialog wujud, semua teks sepadan tepat dengan dokumen ini.
- [ ] Warna hex sepadan (semak dengan picker pada tangkapan skrin app Flutter).
- [ ] Carian "milo" → senarai syarikat; tap → senarai produk; tap → butiran.
- [ ] Imbas produk (kamera & galeri) di kedua-dua skrin imbasan berfungsi dengan backend.
- [ ] Imbas logo memaparkan banner diiktiraf/tidak dan kad badan pensijilan.
- [ ] Waktu solat dari waktusolat (lencana 🏛 zon) dan offline (matikan internet → 🌍 Calculated, masa munasabah).
- [ ] Notifikasi solat muncul pada masa yang dijadualkan.
- [ ] Jarum kiblat menunjuk arah betul (bandingkan dengan app kiblat lain), animasi licin tanpa pusing 360°.
- [ ] Kalendar Hijri: hari ini & Jumaat ditanda betul; navigasi bulan berfungsi.
- [ ] Tasbeeh: kiraan kekal selepas app ditutup; haptic; reset dengan pengesahan.
- [ ] Settings: semua nilai kekal; URL API tersuai digunakan oleh carian/imbasan.
- [ ] Drone: status, daftar SDK, live feed, scan halal (dengan DJI Mini 2 sebenar).

---

# BAHAGIAN B — PELAKSANAAN ANDROID (Kotlin)

## B1. Seni bina

- **UI**: Jetpack Compose + Material 3, satu `MainActivity` (`ComponentActivity`), Navigation Compose.
- **State**: satu `ViewModel` per skrin (StateFlow) atau `remember`/`mutableStateOf` untuk skrin ringkas. Coroutines untuk semua I/O.
- **Tema**: `MaterialTheme` dengan `lightColorScheme(...)` diisi nilai **tepat** dari A2.1 (jangan guna dynamic color; jangan sokong dark theme).
- **Storan**: `SharedPreferences` nama `smart_prefs` dengan key dari A3 (sengaja ringkas dan segerak seperti `gPrefs` dalam Flutter).

```
app/src/main/java/com/halalcheck/halal_app/
├── MainActivity.kt            # setContent { SmartApp() }, minta POST_NOTIFICATIONS
├── SmartApplication.kt        # Application: MultiDex + DJI Helper.install + init notif channel
├── SmartApp.kt                # Theme + NavHost (route: home, search, companyProducts, detail, scan,
│                              #   scanner, logoScan, prayer, qibla, calendar, tasbeeh, settings, drone)
├── ui/theme/                  # Color.kt (A2.1 + A2.2), Type.kt, Theme.kt, MaterialIcon.kt (B5)
├── ui/components/             # AppBar.kt, HalalBadge.kt, CategoryChip.kt, ProductCard.kt, CompanyCard.kt …
├── ui/screens/                # HomeScreen.kt, SearchScreen.kt, CompanyProductsScreen.kt, DetailScreen.kt,
│                              # ScanScreen.kt, ScannerScreen.kt, LogoScanScreen.kt, PrayerScreen.kt,
│                              # QiblaScreen.kt, CalendarScreen.kt, TasbeehScreen.kt, SettingsScreen.kt,
│                              # DroneScreen.kt, LocationPickerDialog.kt
├── data/                      # Models.kt, ApiService.kt, Prefs.kt
├── service/                   # LocationService.kt, PrayerService.kt, QiblaService.kt (kompas + bearing),
│                              # NotificationService.kt, PrayerAlarmReceiver.kt, ImagePicking.kt
└── drone/                     # DjiManager.kt, DroneCameraView.kt
```

Data yang perlu dihantar antara skrin (CompanyResult, HalalProduct) — simpan dalam `ViewModel` dikongsi atau serialisasi JSON ke argumen navigasi. Jangan muat semula dari server.

## B2. Gradle & dependencies

**`gradle/libs.versions.toml`** (versi teruji serasi dengan DJI SDK v4; boleh naikkan versi patch):

```toml
[versions]
agp = "8.9.1"
kotlin = "2.1.0"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2025.01.00"
navigation = "2.8.5"
okhttp = "4.12.0"
serialization = "1.7.3"
coroutines = "1.9.0"
coil = "2.7.0"
playLocation = "21.3.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playLocation" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**`settings.gradle.kts`** — repositori DJI wajib:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://artifactory.djiits.com/artifactory/ams-android-sdk/") }
    }
}
```

**`app/build.gradle.kts`** (bahagian penting):
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.halalcheck.halal_app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.halalcheck.halal_app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += listOf(".tflite", ".flite", ".wav", ".jet", ".ogg", ".mp4", ".dji") }
    packaging {
        jniLibs {
            useLegacyPackaging = true   // sama seperti android:extractNativeLibs="true"
            keepDebugSymbols += setOf(
                "*/*/libdjivideo.so", "*/*/libSDKRelativeJNI.so", "*/*/libFlyForbid.so",
                "*/*/libduml_vision_bokeh.so", "*/*/libyuv2.so", "*/*/libGroudStation.so",
                "*/*/libFRCorkscrew.so", "*/*/libUpgradeVerify.so", "*/*/libFR.so",
                "*/*/libDJIFlySafeCore.so", "*/*/libdjifs_jni.so", "*/*/libsfjni.so",
                "*/*/libDJICommonJNI.so", "*/*/libDJICSDKCommon.so", "*/*/libDJIUpgradeCore.so",
                "*/*/libDJIUpgradeJNI.so", "*/*/libDJIWaypointV2Core.so",
                "*/*/libAMapSDK_MAP_v6_9_2.so", "*/*/libDJIMOP.so", "*/*/libDJISDKLOGJNI.so"
            )
        }
        resources {
            excludes += listOf(
                "META-INF/rxjava.properties", "META-INF/DEPENDENCIES", "META-INF/LICENSE",
                "META-INF/NOTICE", "META-INF/AL2.0", "META-INF/LGPL2.1",
                "assets/location_map_gps_locked.png", "assets/location_map_gps_3d.png"
            )
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.coil.compose)
    implementation(libs.play.services.location)
    debugImplementation(libs.androidx.ui.tooling)

    // DJI Mobile SDK v4.18 (Mini 2)
    implementation("com.dji:dji-sdk:4.18") { exclude(group = "com.dji", module = "library-anti-distortion") }
    compileOnly("com.dji:dji-sdk-provided:4.18")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
```

**`gradle.properties`**: `org.gradle.jvmargs=-Xmx8G -XX:MaxMetaspaceSize=4G`, `android.useAndroidX=true`, `android.enableJetifier=true` (DJI perlukan Jetifier).

**`proguard-rules.pro`**: salin keseluruhan bahagian DJI dari `android/app/proguard-rules.pro` repo Flutter (`-keep class dji.** { *; }`, `com.dji.**`, `com.cySdkyc.**`, `com.secneo.**`, `-dontwarn` yang sama) + `-keep class com.halalcheck.halal_app.** { *; }` + peraturan kotlinx.serialization lalai.

## B3. AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<!-- DJI -->
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"/>
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-feature android:name="android.hardware.usb.host" android:required="false"/>
<uses-feature android:name="android.hardware.usb.accessory" android:required="false"/>

<application
    android:name=".SmartApplication"
    android:label="SMART"
    android:icon="@mipmap/ic_launcher"
    android:usesCleartextTraffic="true"
    android:largeHeap="true"
    android:extractNativeLibs="true"
    android:theme="@style/Theme.SMART">
    <uses-library android:name="org.apache.http.legacy" android:required="false"/>
    <meta-data android:name="com.dji.sdk.API_KEY" android:value="SALIN_DARI_REPO_FLUTTER"/>
    <provider android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider" android:exported="false"
        android:grantUriPermissions="true">
        <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
    </provider>
    <receiver android:name=".service.PrayerAlarmReceiver" android:exported="false"/>
    <activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop"
        android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|screenLayout|density|uiMode"
        android:hardwareAccelerated="true" android:windowSoftInputMode="adjustResize">
        <intent-filter>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.LAUNCHER"/>
        </intent-filter>
        <intent-filter>
            <action android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED"/>
        </intent-filter>
        <meta-data android:name="android.hardware.usb.action.USB_ACCESSORY_ATTACHED"
            android:resource="@xml/accessory_filter"/>
    </activity>
</application>
```

**`res/xml/accessory_filter.xml`** (fail ini **tiada** dalam repo Flutter — cipta baharu; sahkan dengan sampel rasmi DJI MSDK v4):
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-accessory model="T600" manufacturer="DJI"/>
    <usb-accessory model="AG410" manufacturer="DJI"/>
    <usb-accessory model="com.dji.logiclink" manufacturer="DJI"/>
    <usb-accessory model="WM160" manufacturer="DJI"/>
</resources>
```

**`res/xml/file_paths.xml`**: `<paths><cache-path name="images" path="images/"/></paths>`.

## B4. Tema & warna

- `Color.kt`: isytihar semua warna A2.1 + A2.2 sebagai `val` (cth. `val BrandGreen = Color(0xFF1B7A4B)`, `val BrandGreenAlt = Color(0xFF1A7A4A)`, `val Green50 = Color(0xFFE8F5E9)` …).
- `lightColorScheme(primary = Color(0xFF2B6A46), onPrimary = White, primaryContainer = Color(0xFFAFF1C4), secondary = Color(0xFF4F6354), secondaryContainer = Color(0xFFD1E8D5), tertiary = Color(0xFF3B6470), surface = Color(0xFFF6FBF3), background = Color(0xFFF6FBF3), onSurface = Color(0xFF181D19), onSurfaceVariant = Color(0xFF414942), surfaceContainerLow = Color(0xFFF0F5EE), surfaceContainerHigh = Color(0xFFE5EAE2), surfaceContainerHighest = Color(0xFFDFE4DD), outline = Color(0xFF717971), outlineVariant = Color(0xFFC0C9C0), error = Color(0xFFBA1A1A), inverseSurface = Color(0xFF2C322D), inverseOnSurface = Color(0xFFEDF2EB), surfaceTint = Color(0xFF2B6A46))`.
- Status bar (`enableEdgeToEdge()`, kemudian tetapkan setiap skrin dengan `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars`):
  - Skrin **dengan app bar** (Search, CompanyProducts, Detail, Scan, Scanner, LogoScan, Drone): latar app bar memanjang ke belakang status bar; ikon status bar **putih**.
  - Skrin **tanpa app bar** (Home, Prayer, Qibla, Calendar, Tasbeeh, Settings): kandungan dalam `windowInsetsPadding(WindowInsets.safeDrawing)` — header hijau bermula **di bawah** status bar; kawasan status bar berwarna latar skrin (cerah); ikon status bar **gelap**.
- **App bar** (komponen sendiri, bukan `TopAppBar` default supaya ukuran tepat): tinggi 56dp + inset status bar, latar ikut skrin, ikon back `arrow_back` (Material, 24dp, padding 16dp kiri), tajuk mula pada x=72dp (atau tengah jika `centerTitle`), ikon aksi 48dp kawasan sentuh di kanan.
- Spinner: `CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 4.dp)`.
- Peta Flutter → Compose: `Card` → `Card(colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow), elevation = 1.dp, shape = RoundedCornerShape(12.dp))` + `Modifier.padding(4.dp)` margin; `InkWell` → `Modifier.clickable` (ripple); `Chip` → `Surface` dengan sempadan; `Wrap` → `FlowRow`; `Spacer()` dalam `Row` → `Modifier.weight(1f)`; `SnackBar` → `SnackbarHost` dalam `Scaffold`; `PopupMenuButton` → `DropdownMenu`; `AlertDialog` → `AlertDialog` M3; `Tooltip(triggerMode: tap)` → `TooltipBox` + `rememberTooltipState()` dibuka pada klik.

## B5. Ikon Material (glyph sama 100% dengan Flutter)

Flutter guna fon **Material Icons**. Untuk paparan identik, guna fon yang sama (Langkah 3) melalui *ligature*:

```kotlin
val MaterialIconsFilled   = FontFamily(Font(R.font.material_icons))
val MaterialIconsRound    = FontFamily(Font(R.font.material_icons_round))
val MaterialIconsOutlined = FontFamily(Font(R.font.material_icons_outlined))

/** name = nama Flutter, cth "verified_rounded", "settings_outlined", "camera_alt" */
@Composable
fun MIcon(name: String, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val (family, glyph) = when {
        name.endsWith("_rounded")  -> MaterialIconsRound to name.removeSuffix("_rounded")
        name.endsWith("_outlined") -> MaterialIconsOutlined to name.removeSuffix("_outlined")
        name.endsWith("_outline")  -> MaterialIconsFilled to name   // info_outline, error_outline, help_outline, check_circle_outline ialah ikon Filled
        else -> MaterialIconsFilled to name
    }
    Text(glyph, fontFamily = family, fontSize = with(LocalDensity.current) { size.toSp() },
         color = tint, lineHeight = with(LocalDensity.current) { size.toSp() },
         modifier = modifier.size(size), maxLines = 1)
}
```
(Pengecualian: `help_outline_rounded` → set Round, glyph `help_outline`.)

## B6. Rangkaian

- Satu `OkHttpClient` (`connectTimeout 30s`, `readTimeout 30s`); untuk panggilan imbasan guna `client.newBuilder().callTimeout(N, SECONDS)` dengan N dari A5.2 (120 / 45 / 60). Waktusolat: `callTimeout(8s)`.
- `Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }`. Medan String bukan-null dengan lalai `""` → `coerceInputValues` tukar `null` jadi lalai. `vision_result` → `JsonObject?`. `id` → `Int = 0`.
- Multipart: `MultipartBody.Builder().setType(FORM).addFormDataPart("file", filename, bytes.toRequestBody("image/jpeg".toMediaType()))`.
- Bina URL: `"${Prefs.apiBaseUrl}/search-grouped".toHttpUrl().newBuilder().addQueryParameter("q", q).addQueryParameter("limit", "100")` (pengekodan sama seperti `Uri.replace(queryParameters:)` Dart).
- Semua panggilan dalam `Dispatchers.IO`. Mesej ralat untuk skrin ikut A7 (cth. ScannerScreen: `"Exception: " + mesej`).

## B7. Perkhidmatan platform

### B7.1 Lokasi
- Minta `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` dengan `rememberLauncherForActivityResult(RequestMultiplePermissions)`. "Ditolak kekal" = ditolak dan `shouldShowRequestPermissionRationale == false` selepas permintaan.
- Perkhidmatan lokasi OFF: `LocationManagerCompat.isLocationEnabled(...)`.
- Lokasi: `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_LOW_POWER, cancellationToken).await()` dalam `withTimeout(15_000)`; gagal/null → `lastLocation.await()`; null → ralat A6.1.
- Geocoding: `android.location.Geocoder(context, Locale.getDefault())`. API 33+: guna versi callback (`getFromLocation(lat, lng, 1, listener)`, `getFromLocationName(query, 6, listener)`); bawah 33: versi segerak dalam `Dispatchers.IO`. Medan: `locality`, `subAdminArea`, `adminArea`, `countryCode`, `countryName`, `featureName` (= `name`).

### B7.2 Kompas — tiru plugin `flutter_compass` 0.8.1 Android **tepat**
- Sensor: `TYPE_ROTATION_VECTOR`; jika tiada → `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` dengan penapis low-pass `v = v + 0.45*(baru - v)` dan `SensorManager.getRotationMatrix`. Daftar dengan kadar 30 000 µs. Had kemas kini: abaikan jika < 32 ms dari kemas kini lepas.
- Matriks: `getRotationMatrixFromVector`. Remap ikut putaran paparan (`display.rotation`): ROTATION_0 → (AXIS_X, AXIS_Y); 90 → (AXIS_Y, AXIS_MINUS_X); 180 → (AXIS_MINUS_X, AXIS_MINUS_Y); 270 → (AXIS_MINUS_Y, AXIS_X). `getOrientation`. Kemudian jika `pitch < -π/4` remap semula: 0 → (X, Z); 90 → (Z, MINUS_X); 180 → (MINUS_X, MINUS_Z); 270 → (MINUS_Z, X). Jika `pitch > π/4`: 0 → (X, MINUS_Z); 90 → (MINUS_Z, MINUS_X); 180 → (MINUS_X, Z); 270 → (Z, X). Jika `|roll| > π/2`: 0 → (X, MINUS_Y); 90 → (MINUS_Y, MINUS_X); 180 → (MINUS_X, Y); 270 → (Y, X). `remapCoordinateSystem` dari **rotationMatrix asal** dengan paksi baharu → `getOrientation` → `heading = toDegrees(orientation[0])` (boleh negatif; formula A6.3 menormalkan).
- **Accuracy** dari `onAccuracyChanged`: `SENSOR_STATUS_ACCURACY_HIGH → 15`, `MEDIUM → 30`, `LOW → 45`, lain → tidak diketahui → **999**.
  - Akibatnya di Android lencana tidak pernah "HIGH" (15 > 10 → MEDIUM) — ini **sama** dengan app Flutter di Android; kekalkan.
- Heading ini relatif kepada utara **magnet** (tiada pembetulan declination) — sama seperti Flutter; kekalkan.
- Dedahkan sebagai `callbackFlow<Pair<Double, Double>>`; tutup listener dalam `awaitClose`.

### B7.3 Magnetic declination
`GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()`.

### B7.4 Notifikasi
- `SmartApplication.onCreate`: cipta `NotificationChannel("prayer_times", "Prayer Times", IMPORTANCE_HIGH)` dengan deskripsi `Notifications for each daily prayer time`.
- `MainActivity`: API 33+ minta `POST_NOTIFICATIONS` sekali semasa mula (A9 #2).
- Jadual: `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, millis, pendingIntent)`; jika `canScheduleExactAlarms()` false (API 31+) → `setAndAllowWhileIdle`. `PendingIntent` request code = ID (0–5), extra tajuk/badan, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. Batal = `alarmManager.cancel(samePendingIntent)` + `NotificationManagerCompat.cancel(id)`.
- `PrayerAlarmReceiver.onReceive` → `NotificationCompat.Builder(ctx, "prayer_times")` ikon kecil = ikon app monokrom, `PRIORITY_HIGH`, `setAutoCancel(true)`, intent tap → buka `MainActivity`.

### B7.5 Pemilih imej
- Kamera: `ActivityResultContracts.TakePicture()` ke fail cache melalui `FileProvider` (minta `CAMERA` dahulu).
- Galeri: `ActivityResultContracts.PickVisualMedia(ImageOnly)`.
- Selepas pilih: nyahkod dengan `ImageDecoder`/`BitmapFactory` (hormati orientasi EXIF), kecilkan ke lebar maks (A6.6), simpan JPEG kualiti ditetapkan ke fail cache baharu; fail itu digunakan untuk pratonton (Coil `AsyncImage`) dan muat naik. LogoScan: nama fail muat naik = nama fail cache itu.

### B7.6 Haptic
- Tasbeeh tap: `view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` (sama seperti `HapticFeedback.mediumImpact` Flutter di Android).
- Milestone: `view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)` (sama seperti `heavyImpact`).

### B7.7 Hijri
`android.icu.util.IslamicCalendar(ULocale.getDefault())` dengan `setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)`. Bulan ICU bermula 0 → tambah 1. Hari dalam bulan: `getActualMaximum(Calendar.DAY_OF_MONTH)`. Tukar 1 hb Hijri → Gregorian: set YEAR/MONTH/DAY → `.time` → `java.util.Calendar` untuk hari minggu.

### B7.8 Pelbagai
- Jam 1 s: `LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }`.
- Kiraan masa: guna `java.time` (desugaring dah diaktifkan).
- Monospace: `FontFamily.Monospace`.

## B8. Drone DJI (Android)

Logik sedia ada dalam repo Flutter (`android/app/src/main/kotlin/com/halalcheck/halal_app/`) **sudah dalam Kotlin** — pindahkan hampir terus:

| Fail Flutter | Fail native | Perubahan |
|---|---|---|
| `DJIApplication.kt` | `SmartApplication.kt` | `attachBaseContext`: `MultiDex.install(this)` + `com.cySdkyc.clx.Helper.install(this)`; simpan `isRegistered` dalam `companion object` |
| `MainActivity.kt` (MethodChannel `smart.app/dji`) | `drone/DjiManager.kt` | Tukar setiap kaedah channel kepada `suspend fun` (guna `suspendCancellableCoroutine`), pulangkan nilai sama (`"registered"`, `"connected:{model}"`, `"disconnected: {reason}"`, `"photo_captured"`, `"recording_started"`, `"recording_stopped"`, JPEG `ByteArray`) dan lempar exception dengan mesej sama |
| `DroneCameraView.kt` (PlatformView) | `drone/DroneCameraView.kt` | Buang `PlatformView`; dibalut dalam Compose dengan `AndroidView(factory = { DroneCameraView(it).textureView })`. Mesti **sentiasa** berada dalam komposisi DroneScreen |
| `DroneCameraViewFactory.kt` | (tidak perlu) | `DjiManager` simpan rujukan `currentView` |
| MethodChannel `smart.app/geomagnetic` | `QiblaService` | B7.3 |

Mesej ralat ke UI (DroneScreen memaparkan `"{prefix}: {e}"` dengan `e` = `Exception: ...`):
- `registerSDK` gagal → `Exception("DJI SDK registration gagal: {mesej}")`; mesej asal: `DJI SDK tidak dapat dimuatkan: …` atau `error.description` atau `Registration gagal`.
- `getDroneStatus` sebab: `SDK belum didaftar` / `RC tidak dikesan (semak USB permission)` / `Drone tidak dikesan (hidupkan drone)`; model = `product.model?.displayName ?: "Unknown Drone"`.
- `capturePhoto` gagal → `Exception("Gagal ambil gambar: {mesej}")`.
- `captureFrame`: PixelCopy seluruh `window` → potong ke kedudukan `TextureView` (`getLocationInWindow`) → JPEG 90. Gagal → fallback tangkap frame seterusnya (`getBitmap()` JPEG 85, timeout 5 s). Gagal → `Exception("Gagal capture frame: {mesej}")`; frame kosong → `Frame kosong`.
- `startRecording`/`stopRecording` gagal → `Exception("Gagal mula rekod: …")` / `Exception("Gagal berhenti rekod: …")`.

Panggil semua callback DJI kembali ke `Dispatchers.Main`.

## B9. Pemetaan ringkas Flutter → Compose

| Flutter | Compose |
|---|---|
| `Scaffold(appBar:)` | `Scaffold(topBar = { SmartAppBar(...) })` |
| `SafeArea` | `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` |
| `CustomScrollView` + `SliverGrid` (Home) | `LazyVerticalGrid(GridCells.Fixed(2))` dengan header sebagai `item(span = { GridItemSpan(2) })`, jubin `Modifier.aspectRatio(1.05f)` |
| `ListView.builder` | `LazyColumn` |
| `GridView` (Calendar, tidak skrol) | `LazyVerticalGrid(userScrollEnabled = false)` + `aspectRatio(0.85f)` |
| `CustomPainter` | `Canvas` / `drawBehind`; teks via `rememberTextMeasurer()` |
| `AnimationController` + `Tween` | `Animatable(0f).animateTo(target, tween(300, easing = FastOutSlowInEasing))` (easeOut ≈ `EaseOut`) |
| `AnimatedSwitcher` 400 ms | `AnimatedVisibility(enter = fadeIn(tween(400)), exit = fadeOut(tween(400)))` |
| `LinearGradient(topLeft→bottomRight)` | `Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite)` |
| `BoxShadow(color, blur, offset)` | `Modifier.shadow(elevation ≈ blur/2, shape, ambientColor = color, spotColor = color)` |
| `Timer.periodic` | `LaunchedEffect` + `while(isActive) { …; delay(ms) }` |
| `SharedPreferences` | `context.getSharedPreferences("smart_prefs", MODE_PRIVATE)` |

## B10. Fasa pembinaan (untuk AI)

| Fasa | Skop | Siap bila |
|---|---|---|
| 1 | Setup Gradle (B2), manifest (B3), tema/warna (B4), ikon (B5), `Prefs` + migrasi (A4), navigasi, **HomeScreen** penuh tanpa banner solat | App buka, grid 8 jubin, tap buka skrin kosong |
| 2 | Model + `ApiService` (A5, B6), **SearchScreen, CompanyProductsScreen, DetailScreen** | Carian "milo" berfungsi hujung ke hujung |
| 3 | Pemilih imej (B7.5), **ScanScreen, ScannerScreen, LogoScanScreen** | Ketiga-tiga imbasan berfungsi dengan server |
| 4 | Lokasi (B7.1), `PrayerService` (A6.2), notifikasi (B7.4), **PrayerScreen**, **LocationPicker**, banner solat Home | Waktu solat & kiraan detik betul; notifikasi muncul |
| 5 | Kompas (B7.2), declination (B7.3), **QiblaScreen** | Jarum kiblat betul, licin |
| 6 | Hijri (B7.7), **CalendarScreen, TasbeehScreen, SettingsScreen** | Semua tetapan kekal selepas restart |
| 7 | DJI (B8), **DroneScreen** | Live feed + Scan Halal dengan DJI Mini 2 |
| 8 | Semakan akhir A10, build release dengan ProGuard | APK release berjalan termasuk drone |
