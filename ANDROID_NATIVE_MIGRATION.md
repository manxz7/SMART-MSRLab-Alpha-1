# SMART MSRLab — Flutter → Android Native (Kotlin + Jetpack Compose) Migration Plan

**Versi:** 1.0  
**Tarikh:** Oktober 2026  
**Folder sasaran:** `android_native/`  
**Package:** `com.smart.msrlab`

---

## 1. Tujuan & Skop

Migrasi penuh Flutter app (Dart) kepada app Android native murni menggunakan **Kotlin + Jetpack Compose**, dengan 100% feature parity. Tiada Flutter runtime — semuanya berjalan sebagai APK Android native.

### Sebab migrasi
- Akses langsung kepada DJI Mobile SDK v4 (tanpa method channel bridge)
- Kawalan penuh sensor Android (kamera, kompas, lokasi)
- Saiz APK lebih kecil (tiada Flutter engine ~10MB)
- Performa lebih baik untuk animasi kompas real-time
- Debug lebih mudah dengan Android Studio debugger

---

## 2. Pemetaan Ciri (Flutter → Kotlin)

| Ciri | Flutter (asal) | Android Native (sasaran) |
|------|----------------|--------------------------|
| **Navigation** | `Navigator.push()` | `NavHost` + `NavController` (Compose Navigation) |
| **State Management** | `setState()` + `StatefulWidget` | `ViewModel` + `StateFlow` / `remember` |
| **HTTP / API** | `package:http` | `Retrofit2` + `OkHttp3` |
| **Image Pick** | `image_picker` | `ActivityResultContracts.GetContent` / `TakePicture` |
| **Compass** | `flutter_compass` | `SensorManager` (TYPE_ROTATION_VECTOR) |
| **GPS** | `geolocator` + `geocoding` | `FusedLocationProviderClient` (Play Services) |
| **Preferences** | `shared_preferences` | `DataStore Preferences` |
| **Notifications** | `flutter_local_notifications` | `NotificationCompat` + `AlarmManager` |
| **Hijri Calendar** | `package:hijri` | Pure Kotlin Hijri math (algoritma Kuwaiti) |
| **DJI Drone** | Method Channel → `MainActivity.kt` | DJI Mobile SDK v4 dipanggil terus dalam `DroneViewModel` |
| **Theme** | `MaterialApp(theme:)` | `MaterialTheme` Compose + `Color.kt` + `Theme.kt` |
| **Custom Paint** | `CustomPainter` (dart:ui Canvas) | `Canvas` dalam `DrawScope` Compose |
| **Haptic** | `HapticFeedback.mediumImpact()` | `Vibrator.vibrate()` / `VibrationEffect` |
| **Persistence (count)** | `SharedPreferences.setInt` | `DataStore<Preferences>` |
| **Fonts** | Default Material | Default Material3 |

---

## 3. Seni Bina App Native

```
android_native/
  app/
    src/main/
      AndroidManifest.xml          ← Semua permissions + DJI USB intent-filter
      java/com/smart/msrlab/
        SmartApplication.kt        ← Application class, DJI init
        MainActivity.kt            ← Single Activity, DJI channel, NavHost

        data/
          model/
            HalalProduct.kt        ← Data class + @SerializedName
            ScanResult.kt          ← ScanResult + LogoScanResult data classes
            CompanyResult.kt       ← CompanyResult data class
            PrayerModels.kt        ← PrayerTimesData data class
            DroneModels.kt         ← DroneStatus sealed class
          remote/
            ApiService.kt          ← Retrofit @GET / @POST / @Multipart endpoints
            RetrofitClient.kt      ← OkHttp + Retrofit singleton
          local/
            PreferencesManager.kt  ← DataStore Preferences singleton

        service/
          LocationService.kt       ← FusedLocation + Malaysian cities list
          PrayerService.kt         ← WaktuSolat API + solar math fallback
          CompassService.kt        ← SensorManager rotation vector → Qibla bearing
          NotificationService.kt   ← AlarmManager + NotificationCompat channels

        ui/
          theme/
            Color.kt               ← Islamic green palette
            Theme.kt               ← SmartAppTheme MaterialTheme
          screens/
            HomeScreen.kt          ← 8-tile feature grid, prayer banner, Hijri date
            ScannerScreen.kt       ← Camera/gallery → POST /scan-image → result
            LogoScanScreen.kt      ← Camera/gallery → POST /scan-logo → match cards
            DroneScreen.kt         ← DJI live SurfaceView + scan halal controls
            PrayerScreen.kt        ← 6 waktu solat, countdown, NOW/NEXT badges
            QiblaScreen.kt         ← Animated compass dial, Mecca arrow, accuracy
            TasbeehScreen.kt       ← Circular counter, progress arc, dhikr phrases
            CalendarScreen.kt      ← Hijri month grid, Gregorian dual-display
            SettingsScreen.kt      ← Adhan, madhab, calc method, API URL
```

---

## 4. Senarai Dependencies (`build.gradle.kts`)

### Versi terkini (Oktober 2026)

```kotlin
// ── AndroidX Core ──────────────────────────────────────────────
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
implementation("androidx.activity:activity-compose:1.9.3")

// ── Jetpack Compose + Material3 ────────────────────────────────
implementation(platform("androidx.compose:compose-bom:2024.11.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.navigation:navigation-compose:2.8.3")

// ── Networking (Retrofit + OkHttp) ─────────────────────────────
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-gson:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// ── Image Loading (Coil) ────────────────────────────────────────
implementation("io.coil-kt:coil-compose:2.7.0")

// ── Location (Play Services) ────────────────────────────────────
implementation("com.google.android.gms:play-services-location:21.3.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

// ── Persistence (DataStore) ─────────────────────────────────────
implementation("androidx.datastore:datastore-preferences:1.1.1")

// ── DJI Mobile SDK v4 ────────────────────────────────────────────
implementation("com.dji:dji-sdk-v4:4.16.4") {
    isTransitive = false
}
compileOnly("com.dji:dji-sdk-provided:4.16.4")

// ── CameraX (untuk DroneScreen fallback) ───────────────────────
implementation("androidx.camera:camera-core:1.4.0")
implementation("androidx.camera:camera-camera2:1.4.0")
implementation("androidx.camera:camera-lifecycle:1.4.0")
implementation("androidx.camera:camera-view:1.4.0")
```

### Perubahan dalam `settings.gradle.kts`

```kotlin
// Tambah Maven repositori DJI
maven { url = uri("https://artifact.djicdn.com/artifact/") }
```

---

## 5. Pemetaan API Endpoints

| Flutter `ApiService` | Kotlin `ApiService.kt` | Method |
|----------------------|------------------------|--------|
| `search(query)` | `searchProducts(q, limit)` | `GET /search` |
| `searchGrouped(query)` | `searchGrouped(q, limit)` | `GET /search-grouped` |
| `/company-products` | `getCompanyProducts(company, q)` | `GET /company-products` |
| `scanImage(file)` | `scanImage(file: MultipartBody.Part)` | `POST /scan-image` |
| `(logo_scan_screen)` | `scanLogo(file: MultipartBody.Part)` | `POST /scan-logo` |
| `(drone_screen)` | `scanPremises(file: MultipartBody.Part)` | `POST /scan-premises` |

---

## 6. Pemetaan State Management

Flutter menggunakan `setState()` dalam `StatefulWidget`. Untuk Android native, kita pakai pattern yang lebih clean:

| Flutter | Android Native |
|---------|----------------|
| `StatefulWidget` + `setState()` | `@Composable` + `remember { mutableStateOf() }` |
| Complex state (API calls) | `ViewModel` + `StateFlow` / `collectAsStateWithLifecycle()` |
| Global `gPrefs` | `PreferencesManager` singleton + `DataStore` |
| `initState()` | `LaunchedEffect(Unit)` |
| `dispose()` | `DisposableEffect` / `onCleared()` dalam ViewModel |

---

## 7. Pemetaan Services

### 7.1 LocationService

| Dart | Kotlin |
|------|--------|
| `Geolocator.isLocationServiceEnabled()` | `LocationManager.isProviderEnabled()` |
| `Geolocator.requestPermission()` | `ActivityResultContracts.RequestMultiplePermissions` |
| `Geolocator.getCurrentPosition()` | `fusedLocationClient.getCurrentLocation()` |
| `placemarkFromCoordinates()` | `Geocoder.getFromLocation()` |
| `LocationData.malaysianCities` map | `MalaysianCities.all` object dalam `LocationService.kt` |

### 7.2 PrayerService

- **API WaktuSolat**: `GET https://api.waktusolat.app/v2/solat/gps/{lat}/{lng}?year=Y&month=M`
- Parsing JSON sama persis dengan Dart — Unix timestamp → `Date` object
- Fallback: algoritma solar math (Muslim World League) ditukar semula ke Kotlin dari Dart
- Return `PrayerTimesData` data class

### 7.3 CompassService

| Dart (`flutter_compass`) | Kotlin (`SensorManager`) |
|--------------------------|--------------------------|
| `FlutterCompass.events` stream | `registerListener(TYPE_ROTATION_VECTOR)` |
| `CompassEvent.heading` | `SensorManager.getRotationMatrix()` → `getOrientation()` |
| `CompassEvent.accuracy` | `SensorEventListener.onAccuracyChanged()` |
| Nilai `accuracy` dalam degrees | Convert: `SENSOR_STATUS_*` → degrees uncertainty |

Qibla bearing dikira menggunakan formula Great Circle yang sama dengan Dart.

### 7.4 DJI Service (DroneScreen)

DJI SDK dipanggil terus dalam `DroneViewModel.kt` — tiada method channel diperlukan:

```kotlin
// Dart (Flutter)
await DroneService.registerSDK()          // → MethodChannel

// Kotlin (Native)
DJISDKManager.getInstance().registerApp(context, callback)  // Terus!
```

---

## 8. Pemetaan UI/Screen

### 8.1 HomeScreen

| Flutter | Compose |
|---------|---------|
| `CustomScrollView` + `SliverGrid` | `LazyColumn` + `LazyVerticalGrid` |
| `Container` dengan gradient | `Box` + `Brush.linearGradient()` |
| `_FeatureTile` widget | `FeatureTile` composable |
| `Timer.periodic` (clock) | `LaunchedEffect` + `delay(1000)` loop |
| Prayer banner auto-load | `LaunchedEffect` + ViewModel |

### 8.2 QiblaScreen

| Flutter | Compose |
|---------|---------|
| `CustomPainter` (`_CompassDialPainter`) | `Canvas` dalam `drawBehind` / `Modifier.drawWithContent` |
| `AnimationController` + `Tween` rotation | `Animatable<Float>` + `animateTo()` |
| `AnimatedBuilder` | `remember { Animatable() }` + `LaunchedEffect` |
| `Transform.rotate` | `graphicsLayer { rotationZ = angle }` |
| `_Figure8Icon` animation | Custom `Animatable` Canvas painter |

### 8.3 TasbeehScreen

| Flutter | Compose |
|---------|---------|
| `AnimationController` bounce | `Animatable<Float>` scale |
| `CircularProgressIndicator(value:)` | `CircularProgressIndicator` Compose |
| `GestureDetector.onTap` | `Modifier.clickable` |
| `HapticFeedback.mediumImpact()` | `Vibrator.vibrate(VibrationEffect.createOneShot)` |
| `gPrefs.setInt` | `dataStore.edit { it[key] = value }` |

### 8.4 CalendarScreen

Flutter menggunakan `package:hijri` yang ada helper `getDaysInMonth`, `hijriToGregorian`. Kita guna pure Kotlin Hijri math:

```kotlin
// Algoritma: Tabular Islamic Calendar (Kuwaiti variant)
fun hijriToGregorian(hYear: Int, hMonth: Int, hDay: Int): LocalDate
fun daysInHijriMonth(hYear: Int, hMonth: Int): Int
fun gregorianToHijri(date: LocalDate): Triple<Int, Int, Int>
```

---

## 9. Senarai Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- DJI SDK: USB untuk RC controller -->
<uses-permission android:name="android.permission.USB_PERMISSION" />

<!-- Features -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.sensor.compass" android:required="false" />
<uses-feature android:name="android.hardware.usb.host" />
```

---

## 10. Konfigurasi DJI SDK

### build.gradle.kts tambahan

```kotlin
android {
    // DJI SDK memerlukan ndk abiFilters
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Exclude conflict files
    packaging {
        resources {
            excludes += setOf(
                "META-INF/rxjava.properties",
                "META-INF/DEPENDENCIES",
                "/META-INF/{AL2.0,LGPL2.1}"
            )
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

### SmartApplication.kt

```kotlin
class SmartApplication : Application() {
    companion object {
        var isSDKRegistered = false
    }
    override fun onCreate() {
        super.onCreate()
        // DJI SDK requires manual init
        DJISDKManager.getInstance()
    }
}
```

### AndroidManifest.xml meta-data

```xml
<meta-data
    android:name="com.dji.sdk.API_KEY"
    android:value="[DJI_APP_KEY_DARI_FLUTTER_PROJECT]" />
```

---

## 11. Susunan Fail Akhir yang akan Dibuat/Diubah

```
android_native/
  build.gradle.kts                              ← UBAH (tambah DJI, CameraX deps)
  settings.gradle.kts                           ← UBAH (tambah DJI maven repo)
  app/
    build.gradle.kts                            ← UBAH (ndk filters, packaging)
    src/main/
      AndroidManifest.xml                       ← UBAH (tambah DJI, USB perms)
      java/com/smart/msrlab/
        SmartApplication.kt                     ← UBAH (DJI init)
        MainActivity.kt                         ← UBAH (DJI channel + NavHost)
        data/
          model/
            HalalProduct.kt                     ← TULIS SEMULA (lengkap)
            ScanResult.kt                       ← TULIS SEMULA (+ LogoScanResult)
            CompanyResult.kt                    ← BUAT BARU
            PrayerModels.kt                     ← BUAT BARU
            DroneModels.kt                      ← TULIS SEMULA
          remote/
            ApiService.kt                       ← TULIS SEMULA (semua endpoints)
            RetrofitClient.kt                   ← TULIS SEMULA (dynamic URL)
          local/
            PreferencesManager.kt               ← TULIS SEMULA (semua keys)
        service/
          LocationService.kt                    ← TULIS SEMULA (penuh)
          PrayerService.kt                      ← TULIS SEMULA (API + fallback)
          CompassService.kt                     ← TULIS SEMULA (SensorManager)
          NotificationService.kt               ← TULIS SEMULA (channels)
        ui/
          theme/
            Color.kt                            ← sedia (tiada perubahan besar)
            Theme.kt                            ← sedia (tiada perubahan besar)
          screens/
            HomeScreen.kt                       ← TULIS SEMULA (penuh, 8 tiles)
            ScannerScreen.kt                    ← TULIS SEMULA (penuh)
            LogoScanScreen.kt                   ← TULIS SEMULA (penuh)
            DroneScreen.kt                      ← TULIS SEMULA (DJI terus)
            PrayerScreen.kt                     ← TULIS SEMULA (penuh)
            QiblaScreen.kt                      ← TULIS SEMULA (Canvas anim)
            TasbeehScreen.kt                    ← TULIS SEMULA (penuh)
            CalendarScreen.kt                   ← TULIS SEMULA (Hijri math)
            SettingsScreen.kt                   ← TULIS SEMULA (penuh)
```

---

## 12. Urutan Pembangunan

1. **Gradle config** — pastikan build berjaya dahulu (dependencies resolve)
2. **Data models** — foundation untuk semua layer lain
3. **ApiService + RetrofitClient** — networking layer
4. **PreferencesManager** — settings persistence
5. **Services** (Location → Prayer → Compass → Notification) — dari paling mudah ke kompleks
6. **Screens** — dari paling mudah: Settings → Calendar → Tasbeeh → Prayer → Qibla → Scanner → LogoScan → Drone → Home
7. **Manifest + Application + MainActivity** — glue semuanya

---

## 13. Perbezaan Teknikal Penting

### 13.1 Image Picking
Flutter: `image_picker` plugin  
Native: `ActivityResultContracts.PickVisualMedia` (photo picker API 33+) atau `GetContent` (API 26+)

```kotlin
val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    // handle uri
}
// atau untuk kamera:
val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
    // handle
}
```

### 13.2 Multipart Upload
Flutter: `http.MultipartRequest`  
Native: Retrofit `@Multipart` + `@Part file: MultipartBody.Part`

### 13.3 Compass Animation
Flutter menggunakan `AnimationController` dengan `Tween<double>` dan `Transform.rotate`.  
Compose: kita guna `Animatable<Float>` dalam `LaunchedEffect` dengan `animateTo()` menggunakan `tween(easing = EaseOut)`.

### 13.4 Custom Drawing (Kompas dial)
Flutter: `CustomPainter.paint(Canvas, Size)`  
Compose: `Canvas { ... }` composable atau `Modifier.drawWithContent { ... }` / `drawBehind { ... }`

### 13.5 Hijri Calendar
Flutter: `package:hijri` (library)  
Native: Pure Kotlin implementation menggunakan algoritma tabular Islamic calendar:
- Epoch: 1 Muharram 1 AH = 16 Julai 622 CE (Julian)
- Cycle: 30-tahun, 11 tahun kabisat (2,5,7,10,13,15,18,21,24,26,29)

---

## 14. API Key & Konfigurasi

### DJI API Key
Salin dari `android/app/src/main/AndroidManifest.xml` baris:
```xml
<meta-data android:name="com.dji.sdk.API_KEY" android:value="..." />
```

### Default API URL
```kotlin
const val DEFAULT_API_URL = "https://halal-jakim-backend-vfhol5-85579f-159-65-136-40.sslip.io"
```

---

## 15. Nota Keserasian

| Platform | Min SDK | Catatan |
|----------|---------|---------|
| Android | 26 (8.0) | minSdk = 26, sama dengan Flutter project |
| Target SDK | 35 (Android 15) | compileSdk = 35 |
| Kotlin | 2.0.20 | Compose Compiler plugin versi sama |
| Java | 17 | sourceCompatibility + jvmTarget = 17 |
| DJI SDK | 4.16.4 | Sokong DJI Mini 2, Mini 3, Mavic 3 |
| Compose BOM | 2024.11.00 | Material3 1.3.x |

---

## 16. Testing Plan

Setelah semua fail ditulis:

1. `./gradlew assembleDebug` — pastikan compile berjaya
2. Test HomeScreen: semak 8 tiles navigate dengan betul
3. Test ScannerScreen: ambil gambar → upload → parse result
4. Test PrayerScreen: GPS load → waktu solat dipapar
5. Test QiblaScreen: kompas bergerak dengan animasi
6. Test TasbeehScreen: tap counter, persist selepas restart
7. Test CalendarScreen: navigasi bulan Hijri
8. Test SettingsScreen: simpan URL → verify terbaca
9. Test DroneScreen: DJI SDK register (perlukan hardware)
10. Test LogoScanScreen: ambil gambar → scan logo
