≈# Panduan Build iOS (MacBook + Xcode)

Panduan langkah demi langkah untuk jalankan app SMART MSRLab Alpha di iPhone.

---

## Perlu tukar ke Kotlin dulu? **Tidak.**

App ini ditulis dalam **Flutter (Dart)**. Satu kod dalam `lib/` boleh dibina untuk Android **dan** iOS. Kita tak perlu tulis semula app dalam Kotlin atau Swift.

| Bahagian | Bahasa | Berfungsi di iOS? |
|---|---|---|
| Semua skrin & logik (`lib/`) | Dart | Ya |
| Kompas kiblat — magnetic declination | Kotlin (`smart.app/geomagnetic`) | Tidak, tapi Dart dah ada fallback `0.0` (lihat nota bawah) |
| Drone DJI | Kotlin + DJI Android SDK | **Tidak** — perlu dimatikan di iOS |

Android Studio cuma diperlukan untuk build Android. Untuk iOS, guna **Xcode** (dan VS Code / terminal untuk kod Flutter).

---

## Status MacBook (disemak 15 Okt 2026)

| Alat | Status |
|---|---|
| Flutter 3.47.7 | ✅ Ada (`/opt/homebrew/bin/flutter`) |
| Xcode 27.0 | ✅ Ada |
| Homebrew | ✅ Ada |
| **CocoaPods** | ❌ **Belum dipasang** |
| Folder `ios/` dalam repo | ❌ **Belum dijana** |

---

## Langkah 1 — Pasang CocoaPods & sediakan Xcode

```bash
brew install cocoapods
pod --version

# Setup Xcode (sekali sahaja)
sudo xcodebuild -license accept
xcodebuild -runFirstLaunch
xcodebuild -downloadPlatform iOS     # muat turun iOS Simulator runtime (besar, ~8GB)

# Semak semuanya
flutter doctor -v
```

Pastikan bahagian **Xcode** dalam `flutter doctor` bertanda ✓.

---

## Langkah 2 — Jana folder `ios/`

Dari root repo:

```bash
cd ~/Documents/SMART-MSRLab-Alpha-1
flutter create --platforms=ios --org com.halalcheck .
```

Arahan ini **hanya menambah** folder `ios/`. Kod dalam `lib/` dan `android/` tidak disentuh.

---

## Langkah 3 — Tetapan iOS

### 3a. Versi iOS minimum — `ios/Podfile`

Buang `#` dan set:

```ruby
platform :ios, '13.0'
```

### 3b. Kebenaran (permissions) — `ios/Runner/Info.plist`

Tambah sebelum `</dict>` terakhir. **Tanpa ini, app akan crash** bila buka kamera atau minta lokasi:

```xml
<key>NSCameraUsageDescription</key>
<string>Kamera diperlukan untuk scan produk dan logo halal.</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>Akses galeri diperlukan untuk pilih gambar produk.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>Lokasi diperlukan untuk waktu solat dan arah kiblat.</string>
```

### 3c. Notifikasi — `ios/Runner/AppDelegate.swift`

Tambah dalam `application(_:didFinishLaunchingWithOptions:)`, sebelum `GeneratedPluginRegistrant.register`:

```swift
if #available(iOS 10.0, *) {
  UNUserNotificationCenter.current().delegate = self as? UNUserNotificationCenterDelegate
}
```

---

## Langkah 4 — Ubah kod Dart (wajib)

### 4a. Notifikasi — `lib/services/notification_service.dart`

Sekarang hanya ada tetapan Android. Di iOS, `initialize()` akan **crash semasa app start** kalau tetapan iOS tiada.

```dart
const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
const iosSettings = DarwinInitializationSettings(
  requestAlertPermission: true,
  requestBadgePermission: true,
  requestSoundPermission: true,
);

await _plugin.initialize(
  const InitializationSettings(android: androidSettings, iOS: iosSettings),
);
```

Untuk `zonedSchedule`, tambah juga `iOS: DarwinNotificationDetails()` dalam `NotificationDetails(...)`.

### 4b. Drone — `lib/screens/home_screen.dart` & `drone_screen.dart`

`drone_screen.dart` guna `AndroidView`, dan channel `smart.app/dji` tidak wujud di iOS. Membuka skrin itu di iOS akan menyebabkan ralat. Paling mudah, sembunyikan menu Drone di iOS:

```dart
import 'dart:io' show Platform;

// Dalam _featureTiles(), senarai `features` — tambah item Drone hanya untuk Android:
if (Platform.isAndroid)
  _FeatureInfo(
    label: 'Drone Scanner',
    ...
    screen: const DroneScreen(),
  ),
```

> Nota: `DroneService` hanya tangkap `PlatformException`. Di iOS ralatnya ialah `MissingPluginException`, yang **tidak** ditangkap. Sebab itu skrin ini mesti disembunyikan, bukan dibiarkan.

### 4c. Kompas kiblat — tiada perubahan diperlukan

`getMagneticDeclination()` dah ada `catch (_) { return 0.0; }`. Di Malaysia, magnetic declination hanya sekitar 0°–1°, jadi kesannya pada arah kiblat sangat kecil. Boleh dibaiki kemudian dengan Swift (`CLHeading.trueHeading`) jika perlu.

---

## Langkah 5 — Pasang dependencies & run

```bash
flutter pub get
cd ios && pod install && cd ..

# Senarai simulator / iPhone yang ada
flutter devices

# Run di Simulator
open -a Simulator
flutter run
```

---

## Langkah 6 — Run di iPhone sebenar

1. Sambung iPhone dengan kabel. Di iPhone: **Settings → Privacy & Security → Developer Mode → On** (akan restart).
2. Buka projek dalam Xcode — **mesti fail `.xcworkspace`**, bukan `.xcodeproj`:
   ```bash
   open ios/Runner.xcworkspace
   ```
3. Dalam Xcode: pilih **Runner** (kiri) → tab **Signing & Capabilities**:
   - Tick **Automatically manage signing**
   - **Team** → *Add an Account…* → log masuk Apple ID
   - **Bundle Identifier** → mesti unik, cth. `com.halalcheck.halalApp`
4. Kembali ke terminal: `flutter run` (pilih iPhone).
5. Kali pertama, di iPhone: **Settings → General → VPN & Device Management** → percayai (trust) akaun developer.

| Akaun Apple | Boleh buat |
|---|---|
| Apple ID percuma | Install ke iPhone sendiri; app tamat tempoh selepas **7 hari** (perlu run semula) |
| Apple Developer Program (USD 99/tahun) | TestFlight, App Store, tiada had 7 hari |

---

## Langkah 7 — Build untuk edaran (perlu akaun berbayar)

```bash
flutter build ipa --release
```

Hasil di `build/ios/ipa/`. Upload ke App Store Connect guna app **Transporter** (Mac App Store), kemudian edarkan melalui **TestFlight**.

---

## Masalah biasa

| Ralat | Penyelesaian |
|---|---|
| `CocoaPods not installed` | `brew install cocoapods` |
| `pod install` gagal / versi konflik | `cd ios && pod repo update && pod install` |
| `No profiles for '...' were found` | Set **Team** dalam Signing & Capabilities |
| App crash bila buka kamera / lokasi | Kunci `NS...UsageDescription` tiada dalam `Info.plist` |
| App crash semasa start | Langkah 4a (tetapan notifikasi iOS) belum dibuat |
| `Untrusted Developer` di iPhone | Settings → General → VPN & Device Management → Trust |
| Build lama / pelik selepas tukar plugin | `flutter clean && flutter pub get && cd ios && pod install` |

---

## Drone di iOS (fasa kemudian — pilihan)

DJI ada **iOS Mobile SDK v4** yang menyokong Mini 2, tapi semua kod dalam `MainActivity.kt`, `DroneCameraView.kt` dan `DJIApplication.kt` perlu **ditulis semula dalam Swift**: daftar SDK, live feed sebagai `UiKitView`, `captureFrame`, rakaman. Ia juga perlukan App Key DJI baru yang didaftarkan untuk Bundle ID iOS. Anggaran kerja: beberapa hari. Disarankan siapkan app iOS tanpa drone dahulu.

---

## Senarai semak ringkas

- [ ] `brew install cocoapods`
- [ ] `xcodebuild -downloadPlatform iOS`
- [ ] `flutter doctor` — Xcode ✓
- [ ] `flutter create --platforms=ios --org com.halalcheck .`
- [ ] `Podfile` → `platform :ios, '13.0'`
- [ ] `Info.plist` → 3 kunci kebenaran
- [ ] `AppDelegate.swift` → delegate notifikasi
- [ ] `notification_service.dart` → `DarwinInitializationSettings`
- [ ] Sembunyikan menu Drone di iOS
- [ ] `pod install` → `flutter run` di Simulator
- [ ] Signing di Xcode → run di iPhone sebenar
