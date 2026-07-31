# SMART MSRLab Alpha — Halal Checker App

Aplikasi Flutter untuk semak produk & premis halal menggunakan AI Vision (Qwen3-VL) + database JAKIM. Termasuk ciri scan produk, scan logo, dan scan premis melalui drone DJI.

---

## Seni Bina Sistem

```
Fon (Flutter App)
      |
      | hantar gambar
      v
API Server (api.py + Docker)   <──>   PostgreSQL DB (348K rekod JAKIM)
      |
      | call AI
      v
Qwen3-VL-8B LLM (RunPod GPU / server dengan GPU)
```

| Komponen | Teknologi | Lokasi semasa |
|---|---|---|
| Mobile App | Flutter (Dart) | Build dari repo ini |
| Backend API | FastAPI (Python) + EasyOCR | Docker, server lama |
| Database | PostgreSQL | Docker, server lama |
| AI Vision | Qwen3-VL-8B | RunPod (berbayar, perlu GPU) |

---

## Struktur Repo

```
├── api.py                  # Backend API (FastAPI) — deploy ke server
├── requirements.txt        # Python dependencies
├── Dockerfile              # Docker image untuk api.py
├── docker-compose.yml      # Setup API + PostgreSQL sekali
├── lib/
│   ├── main.dart           # Entry point Flutter app
│   ├── screens/            # Semua skrin UI
│   │   ├── home_screen.dart
│   │   ├── scanner_screen.dart      # Scan produk (barcode + AI)
│   │   ├── logo_scan_screen.dart    # Scan logo/jenama
│   │   ├── drone_screen.dart        # Scan premis via drone DJI
│   │   ├── prayer_screen.dart
│   │   ├── qibla_screen.dart
│   │   └── ...
│   └── services/
│       ├── drone_service.dart       # DJI SDK bridge
│       └── ...
├── android/                # Android native code + DJI SDK integration
│   └── app/src/main/kotlin/com/halalcheck/halal_app/
│       ├── MainActivity.kt          # Method channels Flutter ↔ Android
│       ├── DroneCameraView.kt       # Live feed kamera drone
│       ├── DroneCameraViewFactory.kt
│       └── DJIApplication.kt       # DJI SDK init
├── ios/                    # iOS project (build dari Mac sahaja)
└── pubspec.yaml            # Flutter dependencies
```

---

## 1. Deploy Backend (Server)

### Keperluan
- Docker & Docker Compose
- Minimum 4GB RAM
- GPU tidak diperlukan untuk API (GPU untuk Qwen LLM sahaja)

### Langkah

```bash
# Clone repo
git clone https://github.com/manxz7/SMART-MSRLab-Alpha-1.git
cd SMART-MSRLab-Alpha-1

# Edit konfigurasi jika perlu (lihat bahagian Konfigurasi di bawah)
# Kemudian build dan jalankan
docker-compose up -d --build
```

API akan jalan di `http://localhost:8000`. Dokumentasi API: `http://localhost:8000/docs`

### Endpoint Utama

| Endpoint | Fungsi |
|---|---|
| `POST /scan-image` | Scan produk dari gambar |
| `POST /scan-logo` | Scan logo jenama |
| `POST /scan-premises` | Scan premis/restoran (untuk drone) |
| `GET /search?q=...` | Carian teks dalam database JAKIM |

---

## 2. Konfigurasi

Edit dalam `api.py` atau set sebagai environment variable dalam `docker-compose.yml`:

```python
# URL server Qwen LLM — WAJIB tukar jika pindah server
LM_STUDIO_URL = "https://..."   # line 37 dalam api.py

# Database — boleh override via environment variable
DB_HOST     = "..."
DB_PORT     = 5434
DB_NAME     = "halal_jakim"
DB_USER     = "postgres"
DB_PASSWORD = "..."
```

### Jika ada GPU di server baru

Boleh run Qwen3-VL-8B terus di server menggunakan LM Studio atau Ollama, kemudian tukar `LM_STUDIO_URL` ke alamat server tersebut. Tak perlu bayar RunPod lagi.

---

## 3. Build Android App

### Keperluan
- Flutter SDK (3.x ke atas)
- Android Studio
- Android SDK (API 26+)
- Java 17

### Langkah

```bash
# Install dependencies
flutter pub get

# Build APK (debug — untuk testing)
flutter build apk --debug

# Build APK (release — untuk distribute)
flutter build apk --release

# Install terus ke fon yang disambung USB
flutter install
```

APK release akan ada di: `build/app/outputs/flutter-apk/app-release.apk`

### Nota DJI SDK (Drone)

Ciri drone menggunakan DJI Mobile SDK v4. Key DJI sudah dikonfigurasi dalam `AndroidManifest.xml`. Jika perlu tukar key, cari baris ini dalam [android/app/src/main/AndroidManifest.xml](android/app/src/main/AndroidManifest.xml):

```xml
<meta-data android:name="com.dji.sdk.API_KEY" android:value="..." />
```

---

## 4. Build iOS App (Perlu Mac + Xcode)

### Keperluan
- MacBook dengan macOS
- Xcode 15+
- Apple Developer Account
- CocoaPods

### Langkah

```bash
# Install dependencies
flutter pub get
cd ios && pod install && cd ..

# Build dan run di simulator
flutter run

# Build untuk device sebenar (perlu sign dengan Apple certificate)
flutter build ios --release
```

Buka `ios/Runner.xcworkspace` dalam Xcode untuk manage certificates dan provisioning profiles.

---

## 5. Huawei (HMS)

Fon Huawei model lama (sebelum 2019) masih ada Google services — boleh guna APK Android biasa.

Fon Huawei model baru (P40, Mate 40 ke atas) tiada Google — jika nak support, perlu:
- Daftar akaun Huawei Developer
- Ganti Google dependencies dengan Huawei HMS equivalents
- Build APK berasingan untuk AppGallery

Fon Honor — masih ada Google services, guna APK Android biasa sahaja.

---

## 6. Ciri-Ciri App

| Ciri | Status |
|---|---|
| Scan produk (barcode) | Siap |
| Scan produk (AI Vision) | Siap |
| Scan logo jenama | Siap |
| Scan premis via drone DJI | Siap (beta) |
| Waktu solat | Siap |
| Qibla compass | Siap |
| Kalendar Hijri | Siap |
| Tasbih digital | Siap |

---

## 7. Kenalan

Untuk isu teknikal atau pertanyaan tentang kod, hubungi:
- Hilman (pembangun) — mohamadhilman1620@gmail.com
