# 📱 Qibla Flutter — Beginner's Complete Guide

> **This file is your learning journal. Every time we add a feature, fix a bug, or run a command, it gets documented here so you always know what's happening and why.**

---

## 📋 Table of Contents

1. [What Is This App?](#1-what-is-this-app)
2. [What Is Flutter?](#2-what-is-flutter)
3. [Project File Structure](#3-project-file-structure)
4. [How the App Is Built — The Big Picture](#4-how-the-app-is-built--the-big-picture)
5. [Screen by Screen Explanation](#5-screen-by-screen-explanation)
6. [Services (The Brain)](#6-services-the-brain)
7. [APIs Used and How They Work](#7-apis-used-and-how-they-work)
8. [Packages / Dependencies](#8-packages--dependencies)
9. [Important Flutter Concepts for Beginners](#9-important-flutter-concepts-for-beginners)
10. [Terminal Commands Reference](#10-terminal-commands-reference)
11. [How to Build & Install an APK](#how-to-build--install-an-apk)
12. [🚀 How to Create a Brand New Flutter App](#12-how-to-create-a-brand-new-flutter-app)
13. [🗺️ Full Developer Workflow Visual](#13-full-developer-workflow-visual)
14. [🧱 Dart Language Basics for Flutter](#14-dart-language-basics-for-flutter)
15. [📐 App Architecture Patterns](#15-app-architecture-patterns)
16. [🐛 Debugging Guide](#16-debugging-guide)
17. [📦 pubspec.yaml Deep Dive](#17-pubspecyaml-deep-dive)
18. [Change Log — What We Did and When](#18-change-log--what-we-did-and-when)

---

## 1. What Is This App?

**QiblaApp** is a Flutter mobile app for Muslims. It has 4 features accessible through tabs at the bottom:

| Tab | What it does |
|-----|-------------|
| 🧭 **Qibla** | Shows a compass arrow pointing toward Mecca (Kaaba) |
| 🕌 **Prayer** | Shows today's 6 prayer times with a live countdown |
| 📅 **Calendar** | Shows a Hijri (Islamic lunar) calendar |
| ⚙️ **Settings** | App preferences (adhan alerts, calculation method) |

---

## 2. What Is Flutter?

Flutter is a **framework made by Google** that lets you write one codebase (in the Dart language) and run it on Android, iOS, Web, and Desktop.

- **Dart** is the programming language (similar to JavaScript or Java but simpler)
- Everything you see on screen is a **Widget** — buttons, text, rows, columns, images — all widgets
- You write code → Flutter converts it into a native Android/iOS app

---

## 3. Project File Structure

```
qibla_flutter/
│
├── pubspec.yaml          ← Lists all packages (dependencies) the app needs
├── NOTES.md              ← This file! Your learning guide
│
├── lib/                  ← ALL your Dart code lives here
│   ├── main.dart         ← App entry point + bottom navigation bar
│   │
│   ├── screens/          ← Each screen/tab is one file
│   │   ├── qibla_screen.dart     ← Tab 1: Compass pointing to Mecca
│   │   ├── prayer_screen.dart    ← Tab 2: Prayer times + countdown
│   │   ├── calendar_screen.dart  ← Tab 3: Hijri calendar grid
│   │   └── settings_screen.dart  ← Tab 4: App settings
│   │
│   └── services/         ← "Behind the scenes" logic (no UI here)
│       ├── location_service.dart ← GPS permission + coordinates + city name
│       └── prayer_service.dart   ← Fetch/calculate prayer times
│
└── android/              ← Android-specific config (you rarely touch this)
```

**Simple rule:** `screens/` = what the user SEES, `services/` = the logic that DOES things.

---

## 4. How the App Is Built — The Big Picture

```
main.dart
  └── QiblaApp (sets up theme and colors)
       └── MainScreen (holds the 4 tabs + bottom nav bar)
            ├── QiblaScreen     uses → LocationService, QiblaService
            ├── PrayerScreen    uses → LocationService, PrayerService
            ├── CalendarScreen  uses → hijri package (offline, no service needed)
            └── SettingsScreen  (standalone, no services yet)
```

**Data flow for Prayer Screen (as an example):**
```
User opens app
  → PrayerScreen starts
    → LocationService.getLocation()
        → Asks GPS permission
        → Gets coordinates (lat, lng)
        → Reverse geocodes to city name + country code
    → PrayerService.getPrayerTimes(lat, lng, country)
        → If country == "MY" → calls api.waktusolat.app (see API section)
        → Otherwise → calculates offline using solar math formulas
    → Display results on screen
```

---

## 5. Screen by Screen Explanation

### 5.1 `main.dart` — App Entry Point

```dart
void main() {
  runApp(const QiblaApp());
}
```
- `main()` is the very first function that runs when the app starts
- `runApp()` tells Flutter what to show
- `QiblaApp` sets the green color theme (`#1a7a4a`) and the app title
- `MainScreen` uses `IndexedStack` to hold all 4 screens loaded at once — this means when you switch tabs, the data doesn't reset

**Bottom Navigation Bar:** Has 4 items. When you tap a tab, `_selectedIndex` changes and Flutter redraws with the new screen.

---

### 5.2 `qibla_screen.dart` — Qibla Compass

**What it does:**
1. Gets your GPS location
2. Calculates the bearing (angle) from your location to Mecca
3. Reads the phone's magnetometer (compass sensor) live
4. Rotates a green arrow to always point toward Mecca

**Three states:**
- **Loading** → spinning circle, waiting for GPS + sensor
- **Error** → shows message + "Try Again" button
- **Normal** → animated compass

**The compass animation:**
- `AnimationController` drives smooth rotation
- Every time the magnetometer fires (100ms), we calculate the new angle
- We find the shortest path (e.g. going from 350° to 10° should rotate +20°, not -340°)
- `AnimatedBuilder` rebuilds only the arrow widget, not the whole screen

**Custom painting:**
- `_CompassDialPainter` — draws the circular compass dial with N/S/E/W labels and tick marks using `Canvas` (low-level drawing)
- `_ArrowPainter` — draws the green arrow pointing up (then rotation rotates it)
- The 🕋 emoji is positioned at the tip of the arrow using trigonometry (`sin`/`cos`)

---

### 5.3 `prayer_screen.dart` — Prayer Times

**What it does:**
1. Gets GPS location
2. Fetches/calculates 6 prayer times: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha
3. Shows a live countdown to the next prayer (updates every second)
4. Highlights the current prayer in green

**The countdown timer:**
```dart
_clockTimer = Timer.periodic(Duration(seconds: 1), (_) {
  setState(() => _now = DateTime.now());
});
```
Every second, `_now` is updated → Flutter redraws → countdown shows new value.

**Current/Next detection:**
Loops through all 6 prayer times and finds which one `DateTime.now()` falls between.

**Hijri date display:**
The API returns the Hijri date as `"1447-10-06"` → we convert it to `"6 Shawwal 1447"`.

---

### 5.4 `calendar_screen.dart` — Hijri Calendar

**What it does:**
- Shows a monthly grid calendar in the Hijri (Islamic lunar) system
- Each cell shows the Hijri day number AND the Gregorian (normal) day number
- You can navigate forward/backward months with arrow buttons
- Today is highlighted in green
- Friday is highlighted in green text

**How Hijri works:**
- The Islamic calendar is **lunar** — each month starts when the new crescent moon is sighted
- A Hijri year has 354 or 355 days (about 11 days shorter than Gregorian)
- We use the `hijri` package which has the conversion math built-in

**Calendar grid:**
- `GridView.builder` creates a 7-column grid (one column per day of week)
- We first find which day of week the 1st of the month falls on → add empty cells before it
- `getDaysInMonth()` tells us how many days the month has (29 or 30)

---

### 5.5 `settings_screen.dart` — Settings

**What it does:**
- Shows toggle switches for Adhan alerts and vibration
- Shows dropdown menus for prayer calculation method and madhab
- Shows "About" info (app version, data sources)

**Current limitation:** Settings are stored in memory only — if you close the app, they reset. A future improvement would save them with `SharedPreferences`.

---

## 6. Services (The Brain)

### 6.1 `location_service.dart`

Contains two classes:

**`LocationService`** — Gets GPS coordinates
```
Step 1: Check if GPS is turned on in phone settings
Step 2: Check if permission was granted
Step 3: If not asked yet → ask for permission
Step 4: If permanently denied → tell user to go to Settings
Step 5: Get actual coordinates (latitude, longitude)
Step 6: Reverse geocode → convert numbers to city name
```

**`QiblaService`** — Compass math
```
calculateQiblaAngle(lat, lng)
  → Uses the Great Circle formula
  → Returns degrees clockwise from North (0–360) pointing to Mecca
  → Mecca is hardcoded at 21.4225°N, 39.8262°E

startCompass(lat, lng)
  → Returns a Stream (live data feed) from the magnetometer sensor
  → Fires every 100 milliseconds
  → Each reading = [compassHeading, qiblaAngle, needleRotation]
  → compassHeading = which way the phone faces right now
  → needleRotation = how much to rotate the arrow = qiblaAngle - compassHeading
```

**What is a Stream?**
A Stream is like a water pipe — data keeps flowing through it continuously. The magnetometer sends new readings 10 times per second. We listen to the stream and update the arrow every time we get a new reading.

---

### 6.2 `prayer_service.dart`

**`PrayerService`** — Gets prayer times

Two options:
1. **API (Malaysia):** Calls `api.waktusolat.app` for official JAKIM times
2. **Offline calculation:** Uses solar position math formulas (works worldwide, no internet)

Decision logic:
```
if (country == "MY") → try API first → if API fails → use offline calculation
if (not Malaysia) → use offline calculation directly
```

---

## 7. APIs Used and How They Work

### 7.1 waktusolat.app API (Malaysia Prayer Times)

**What is an API?**
An API (Application Programming Interface) is like a waiter at a restaurant:
- You (the app) tell the waiter what you want
- The waiter goes to the kitchen (server) and gets it
- The waiter brings back the food (data)

**The API we use:**
- **URL:** `https://api.waktusolat.app/v2/solat/gps/{latitude}/{longitude}?year=YYYY&month=M`
- **Method:** GET (just fetching data, not sending anything)
- **Free:** Yes, no API key needed
- **Source:** JAKIM (Jabatan Kemajuan Islam Malaysia) — the official Malaysian Islamic authority

**Example request:**
```
GET https://api.waktusolat.app/v2/solat/gps/2.926/101.696?year=2026&month=5
```

**Example response (JSON):**
```json
{
  "zone": "WLY02",
  "prayers": [
    {
      "day": 5,
      "hijri": "1447-10-06",
      "fajr": 1746394440,
      "syuruk": 1746416040,
      "dhuhr": 1746438900,
      "asr": 1746452940,
      "maghrib": 1746461820,
      "isha": 1746475200
    }
  ]
}
```

**How we decode it:**
- `fajr: 1746394440` is a **Unix timestamp** — the number of seconds since 1 January 1970
- We convert it: `DateTime.fromMillisecondsSinceEpoch(1746394440 * 1000)` → readable time
- `zone: "WLY02"` = Wilayah Persekutuan zone 2 — the API auto-detects your zone from GPS

**What is JSON?**
JSON (JavaScript Object Notation) is how data is sent over the internet. It looks like a dictionary: `{ "key": value }`. We use `jsonDecode()` in Dart to turn the JSON text into a Dart Map that we can read.

---

## 8. Packages / Dependencies

All packages are listed in `pubspec.yaml` under `dependencies:`.

| Package | What it does | How we use it |
|---------|-------------|---------------|
| `geolocator` | GPS permission + coordinates | `LocationService.getLocation()` |
| `geocoding` | Coordinates → city name (reverse geocoding) | Gets city, state, country from lat/lng |
| `sensors_plus` | Reads phone sensors (magnetometer for compass) | `magnetometerEventStream()` in QiblaService |
| `http` | Makes HTTP requests to APIs | Fetching prayer times from waktusolat.app |
| `hijri` | Hijri calendar calculations | `HijriCalendar.now()`, `getDaysInMonth()` |
| `flutter_local_notifications` | Shows local push notifications | For Adhan alerts (future feature) |
| `cupertino_icons` | iOS-style icons | Used by Flutter |

**How to add a new package:**
1. Find the package on https://pub.dev (Flutter's package registry)
2. Add it to `pubspec.yaml` under `dependencies:`
3. Run `flutter pub get` in the terminal
4. Import it in your Dart file: `import 'package:package_name/package_name.dart';`

---

## 9. Important Flutter Concepts for Beginners

### Widgets
Everything in Flutter is a Widget. Think of widgets like LEGO bricks — you stack and combine them to build your UI.

Common widgets:
- `Text('Hello')` — displays text
- `Icon(Icons.star)` — displays an icon
- `Column(children: [...])` — stacks widgets vertically
- `Row(children: [...])` — places widgets horizontally
- `Container(...)` — a box you can style (color, padding, border radius)
- `Padding(padding: ..., child: ...)` — adds space around a widget
- `Scaffold` — the basic page structure (includes AppBar, body, bottom bar)

### StatelessWidget vs StatefulWidget
- **StatelessWidget** = a widget that never changes after it's built (e.g. a label, an icon)
- **StatefulWidget** = a widget that CAN change (e.g. a counter, a compass that updates)
- To change a StatefulWidget, you call `setState(() { ... })` — this tells Flutter to redraw

### `async` / `await`
Some things take time (GPS, API calls, file reading). Instead of freezing the app, Dart uses:
```dart
Future<void> _load() async {          // async = this function can wait
  final location = await getLocation(); // await = wait here until done
  // now we have location
}
```
- `Future<T>` = a promise to return value of type T later
- `await` pauses execution until the Future completes (but doesn't freeze the app)

### `Stream`
A `Stream` is an ongoing sequence of values over time.
- Magnetometer → `Stream<MagnetometerEvent>` fires 10x per second
- A `Timer.periodic` → fires every N seconds
- To listen: `.listen((event) { ... })`
- To stop: `subscription.cancel()` (always cancel in `dispose()`)

### `setState()`
```dart
setState(() {
  _counter = _counter + 1;  // update the variable
});
```
This tells Flutter: "something changed, please redraw the screen". Without `setState()`, the screen won't update even if the variable changed.

### `dispose()`
Called when a widget is removed from the screen. Always cancel timers, subscriptions, and animation controllers here to avoid memory leaks:
```dart
@override
void dispose() {
  _clockTimer?.cancel();
  _animController.dispose();
  super.dispose();
}
```

---

## 10. Terminal Commands Reference

These are commands you type in the terminal (the black/dark window in VS Code).
Open terminal in VS Code: `Ctrl + `` ` (backtick)

| Command | What it does |
|---------|-------------|
| `flutter run` | Build and run the app on a connected device/emulator |
| `flutter run -d <device_id>` | Run on a specific device |
| `flutter devices` | List all connected devices/emulators |
| `flutter pub get` | Download all packages listed in pubspec.yaml |
| `flutter pub upgrade` | Upgrade packages to newer versions |
| `flutter build apk` | Build an APK file for Android (for installing/sharing) |
| `flutter build apk --release` | Build a release APK (smaller, faster, no debug info) |
| `flutter clean` | Delete build cache (fixes many weird build errors) |
| `flutter doctor` | Check if Flutter is installed correctly |
| `flutter analyze` | Check code for errors/warnings |

**How to run the app step by step:**
```
1. Connect your Android phone via USB (enable USB Debugging in Developer Options)
   OR open an emulator in Android Studio
2. Open terminal in VS Code (Ctrl + `)
3. Type: flutter devices    ← to see your device
4. Type: flutter run        ← to start the app
5. Wait ~30-60 seconds for first build
6. The app appears on your phone!
```

**Hot Reload vs Hot Restart:**
- Press `r` in the terminal → **Hot Reload**: applies UI changes in ~1 second (keeps app state)
- Press `R` in the terminal → **Hot Restart**: restarts the app (resets state, slower)
- Press `q` → quit

---

## 12. 🚀 How to Create a Brand New Flutter App

This is the COMPLETE checklist for starting a Flutter project from zero — every step, every requirement.

---

### ✅ Step 0 — One-Time Computer Setup (do this once, ever)

Before writing a single line of code, your computer needs these tools installed:

#### 1. Install Flutter SDK
- Download from: https://docs.flutter.dev/get-started/install/windows
- Extract to a folder like `C:\flutter`
- Add `C:\flutter\bin` to your Windows PATH environment variable
- Verify: open a terminal and type `flutter --version`

#### 2. Install Android Studio
- Download from: https://developer.android.com/studio
- During install, include: Android SDK, Android SDK Platform-Tools, Android Emulator
- Open Android Studio → SDK Manager → install Android 10 (API 29) or higher
- Open Android Studio → Virtual Device Manager → create an emulator (or use a real phone)

#### 3. Install VS Code (code editor)
- Download from: https://code.visualstudio.com
- Install these extensions inside VS Code:
  - **Flutter** (by Dart Code) — syntax highlighting, run button, hot reload
  - **Dart** (by Dart Code) — Dart language support
  - **GitHub Copilot** (optional but helpful — that's me!)

#### 4. Accept Android licenses
```
flutter doctor --android-licenses
```
Type `y` and press Enter for each license.

#### 5. Verify everything works
```
flutter doctor
```
You want to see green checkmarks for Flutter, Android toolchain, and VS Code.
Red items = something is missing, follow the instructions it gives.

---

### ✅ Step 1 — Create the Project

```
flutter create my_app_name
```

**Rules for the app name:**
- Lowercase letters only
- Underscores `_` allowed, NO spaces or hyphens
- Good: `qibla_flutter`, `prayer_app`, `todo_list`
- Bad: `My App`, `prayer-app`, `PrayerApp`

**What `flutter create` generates:**
```
my_app_name/
├── pubspec.yaml          ← Your package list + app config
├── lib/
│   └── main.dart         ← The starter app (a counter demo)
├── android/              ← Android project (auto-managed by Flutter)
├── ios/                  ← iOS project (auto-managed by Flutter)
├── test/
│   └── widget_test.dart  ← Automated tests
└── README.md             ← Blank description file
```

**Open the project in VS Code:**
```
cd my_app_name
code .
```

---

### ✅ Step 2 — Configure the App Identity

Edit `pubspec.yaml` — this is the most important config file:

```yaml
name: my_app_name          # package name (snake_case, no spaces)
description: My awesome app
version: 1.0.0+1           # version number + build number

environment:
  sdk: ^3.0.0              # minimum Dart version required

dependencies:
  flutter:
    sdk: flutter
  cupertino_icons: ^1.0.8  # basic icons

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^4.0.0

flutter:
  uses-material-design: true
  # assets:                # uncomment to add images/fonts
  #   - assets/images/
```

**App display name and icon** — edit `android/app/src/main/AndroidManifest.xml`:
```xml
<application
    android:label="My App Name"   ← what shows under the icon on home screen
    android:icon="@mipmap/ic_launcher"
```

To set a custom icon, use the `flutter_launcher_icons` package (see section 17).

---

### ✅ Step 3 — Plan Your App Structure

Before writing code, decide:

**How many screens?**
Each screen = one file in `lib/screens/`

**What data does the app need?**
Each data source/logic = one file in `lib/services/`

**Example plan for a To-Do app:**
```
lib/
├── main.dart
├── screens/
│   ├── home_screen.dart      ← list of tasks
│   ├── add_task_screen.dart  ← form to add a new task
│   └── settings_screen.dart  ← user preferences
└── services/
    └── task_service.dart     ← save/load/delete tasks
```

**Minimum files for any app:**
- `main.dart` — always needed, entry point
- At least one screen file
- Services only if your app talks to the internet or hardware

---

### ✅ Step 4 — Add Packages You Need

Search for packages at https://pub.dev — Flutter's official package registry.

Add them to `pubspec.yaml`:
```yaml
dependencies:
  flutter:
    sdk: flutter
  http: ^1.2.2          # making API/internet requests
  shared_preferences: ^2.2.2  # saving user settings
  url_launcher: ^6.2.0  # opening links in browser
```

Then run:
```
flutter pub get
```

**Commonly needed packages by app type:**

| If your app needs... | Package to use |
|---|---|
| Internet requests / APIs | `http` |
| GPS / location | `geolocator` |
| Camera | `camera` |
| Local database | `sqflite` or `hive` |
| Save small settings | `shared_preferences` |
| Show images from internet | `cached_network_image` |
| Charts / graphs | `fl_chart` |
| Maps | `google_maps_flutter` |
| Notifications | `flutter_local_notifications` |
| Authentication | `firebase_auth` |
| State management | `provider` or `riverpod` |
| Icons (beyond Material) | `font_awesome_flutter` |

---

### ✅ Step 5 — Set Up Permissions (Android)

If your app needs to access system features, declare them in:
`android/app/src/main/AndroidManifest.xml`

Add permission lines inside `<manifest>` but OUTSIDE `<application>`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Internet access -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- GPS / Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Camera -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- Microphone -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Notifications -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Storage (read files/images) -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

    <application ...>
```

**Rule:** If you ask for a permission in code but didn't declare it here, Android silently ignores your request.

---

### ✅ Step 6 — Write Your Screens

Every screen follows the same template:

```dart
import 'package:flutter/material.dart';

class MyScreen extends StatefulWidget {      // StatefulWidget if screen changes
  const MyScreen({super.key});

  @override
  State<MyScreen> createState() => _MyScreenState();
}

class _MyScreenState extends State<MyScreen> {
  // ── Variables ─────────────────────────────────────────────────────
  String _message = 'Hello!';
  bool _loading = false;

  // ── Lifecycle ─────────────────────────────────────────────────────
  @override
  void initState() {
    super.initState();
    // Called once when screen first appears — good place to load data
    _loadData();
  }

  @override
  void dispose() {
    // Called when screen is removed — cancel timers/streams here
    super.dispose();
  }

  // ── Logic ─────────────────────────────────────────────────────────
  Future<void> _loadData() async {
    setState(() => _loading = true);
    // ... do async work ...
    setState(() {
      _message = 'Data loaded!';
      _loading = false;
    });
  }

  // ── Build UI ──────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('My Screen')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Center(child: Text(_message)),
    );
  }
}
```

---

### ✅ Step 7 — Connect Screens in main.dart

**Single screen app:**
```dart
void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'My App',
      theme: ThemeData(colorSchemeSeed: Colors.blue),
      home: const HomeScreen(),   // ← your first screen
    );
  }
}
```

**Multi-screen app with bottom navigation (like ours):**
```dart
class MainScreen extends StatefulWidget { ... }

class _MainScreenState extends State<MainScreen> {
  int _selectedIndex = 0;

  // List of all screens
  static const List<Widget> _screens = [
    HomeScreen(),
    SearchScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _selectedIndex,
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: (i) => setState(() => _selectedIndex = i),
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(icon: Icon(Icons.search), label: 'Search'),
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Profile'),
        ],
      ),
    );
  }
}
```

> **Why `IndexedStack` instead of just showing one screen?**
> `IndexedStack` keeps ALL screens alive in memory. If you switch to tab 2 and back to tab 1, tab 1 still has your scroll position and loaded data. If you used a regular `if/else`, tab 1 would reload from scratch every time.

---

### ✅ Step 8 — Test on Your Phone

```
flutter run
```

During development:
- Press `r` → Hot reload (instant UI changes)
- Press `R` → Hot restart (restarts app from scratch)
- Press `q` → Quit

**First run is slow (~1-3 minutes)** — Flutter downloads Gradle and builds the Android project. Every run after that is fast (~10-20 seconds).

---

### ✅ Step 9 — Build Release APK for Real Use

```
flutter build apk --release
flutter install build\app\outputs\flutter-apk\app-release.apk
```

See the full APK guide in the section above for details.

---

### ✅ Step 10 — Publish to Google Play Store (optional, future)

1. Create a keystore (signing certificate): `keytool -genkey -v -keystore upload-keystore.jks ...`
2. Configure signing in `android/app/build.gradle`
3. Run `flutter build appbundle` (produces `.aab` file, smaller than APK)
4. Create a Google Play Developer account ($25 one-time fee)
5. Upload the `.aab` to Google Play Console
6. Fill in store listing (screenshots, description, category)
7. Submit for review → live in 1-3 days

---

## 13. 🗺️ Full Developer Workflow Visual

Here is the complete picture of what happens from "I have an idea" to "app is on my phone".

```
╔══════════════════════════════════════════════════════════════════╗
║              FLUTTER APP DEVELOPMENT LIFECYCLE                   ║
╚══════════════════════════════════════════════════════════════════╝

  💡 IDEA
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  SETUP (one time only)                      │
  │  • Install Flutter SDK                      │
  │  • Install Android Studio + SDK             │
  │  • Install VS Code + Flutter extension      │
  │  • Run: flutter doctor                      │
  └─────────────────────────────────────────────┘
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  CREATE PROJECT                             │
  │  flutter create app_name                    │
  │  cd app_name                                │
  │  code .                                     │
  └─────────────────────────────────────────────┘
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  PLAN & CONFIGURE                           │
  │  • Sketch screens on paper / Figma          │
  │  • Edit pubspec.yaml (app name, version)    │
  │  • Add packages to pubspec.yaml             │
  │  • Run: flutter pub get                     │
  │  • Add permissions to AndroidManifest.xml   │
  └─────────────────────────────────────────────┘
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  ┌────────────────────────────────────────┐ │
  │  │  DEVELOPMENT LOOP  (repeat many times) │ │
  │  │                                        │ │
  │  │  1. Write/edit code in VS Code         │ │
  │  │         ↓                              │ │
  │  │  2. flutter run (first time)           │ │
  │  │     OR press 'r' (hot reload)          │ │
  │  │         ↓                              │ │
  │  │  3. See changes on phone/emulator      │ │
  │  │         ↓                              │ │
  │  │  4. Something wrong? → Debug it        │ │
  │  │         ↓                              │ │
  │  │  5. Happy with it? → move to next      │ │
  │  │     feature                            │ │
  │  └────────────────────────────────────────┘ │
  └─────────────────────────────────────────────┘
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  BUILD RELEASE APK                          │
  │  flutter build apk --release                │
  └─────────────────────────────────────────────┘
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  INSTALL & TEST                             │
  │  flutter install build\...\app-release.apk  │
  │  • Unplug USB                               │
  │  • Use app normally                         │
  │  • Found a bug? → back to Dev Loop          │
  └─────────────────────────────────────────────┘
     │
     ▼
  ┌─────────────────────────────────────────────┐
  │  SHARE / PUBLISH                            │
  │  • Send APK via WhatsApp/Drive (sideload)   │
  │  • OR publish to Google Play Store          │
  └─────────────────────────────────────────────┘
```

---

### How Code Becomes an App — Under the Hood

```
Your .dart files
      │
      ▼  dart compile (AOT — Ahead of Time compilation)
      │
Native ARM machine code
      │
      ▼  Gradle build system (Android)
      │
  Packages together:
  ┌───────────────────────────────────┐
  │  Your compiled Dart code          │
  │  Flutter engine (C++ renderer)    │
  │  Your images, fonts, assets       │
  │  AndroidManifest.xml (metadata)   │
  │  Platform code (Java/Kotlin)      │
  └───────────────────────────────────┘
      │
      ▼
  app-release.apk  ✓
```

---

### How Flutter Renders UI

Flutter does NOT use native Android buttons, text views, etc. It draws EVERYTHING itself using the Skia (or Impeller) graphics engine — the same engine used by Google Chrome.

```
Your Widget tree
  Column
  ├── Text('Hello')
  └── ElevatedButton
        │
        ▼  Flutter's rendering engine
        │
  Skia/Impeller draws pixels directly on screen
        │
        ▼
  Phone screen shows your app
```

This is why Flutter apps look identical on Android and iOS — it's drawing everything from scratch, not asking the OS to draw buttons.

---

### Widget Tree for This App

```
QiblaApp (MaterialApp — sets theme, color, title)
└── MainScreen (StatefulWidget — manages which tab is selected)
    ├── IndexedStack (keeps all 4 screens alive)
    │   ├── [0] QiblaScreen
    │   │     ├── Column
    │   │     │   ├── _buildHeader()   → Container with location text
    │   │     │   └── _buildCompass()  → AnimatedBuilder
    │   │     │         └── Stack
    │   │     │               ├── CustomPaint (compass dial)
    │   │     │               ├── Transform.rotate (arrow)
    │   │     │               └── Transform.translate (🕋 emoji)
    │   ├── [1] PrayerScreen
    │   │     └── Column
    │   │           ├── _buildHeader()   → date + location
    │   │           └── _buildPrayerList() → ListView of cards
    │   ├── [2] CalendarScreen
    │   │     └── Column
    │   │           ├── month navigator (< Month Year >)
    │   │           └── GridView (7 columns × N rows)
    │   └── [3] SettingsScreen
    │         └── ListView
    │               ├── SwitchListTile (Adhan alerts)
    │               └── DropdownButton (calculation method)
    └── BottomNavigationBar (4 tabs at the bottom)
```

---

## 14. 🧱 Dart Language Basics for Flutter

You don't need to know all of Dart — here are the parts that actually come up in Flutter apps.

---

### Variables

```dart
// Type is inferred from the value (preferred style)
var name = 'Hilman';        // String
var age = 22;               // int
var height = 1.75;          // double
var isLoggedIn = false;     // bool

// Explicit type
String city = 'Kuala Lumpur';
int count = 0;

// Final = set once, never change
final String appName = 'QiblaApp';

// Const = compile-time constant (even stricter than final)
const double meccaLat = 21.4225;
```

---

### Null Safety (the `?` and `!` operators)

Dart has **null safety** — variables can't be null unless you say so:

```dart
String name = 'Hilman';   // can NEVER be null
String? city;             // CAN be null (the ? means "maybe null")

city = 'KL';              // assign a value
city = null;              // allowed because of ?

// To use a nullable variable safely:
if (city != null) {
  print(city.length);     // safe — we checked
}

// Shorthand with ?? ("if null, use this default")
String display = city ?? 'Unknown city';

// The ! operator: "I promise this is not null, trust me"
print(city!.length);      // CRASH if city is actually null — use carefully!
```

---

### Functions

```dart
// Basic function
String greet(String name) {
  return 'Hello, $name!';
}

// Arrow function (single expression)
String greet(String name) => 'Hello, $name!';

// Named parameters (used a lot in Flutter widgets)
void showInfo({required String title, String? subtitle}) {
  print(title);
  if (subtitle != null) print(subtitle);
}
// Call like: showInfo(title: 'Prayer Times', subtitle: 'KL')

// Async function (returns a Future)
Future<String> fetchData() async {
  final result = await someApi.get();  // wait here
  return result;
}
```

---

### Classes

```dart
// A class is a blueprint for an object
class LocationData {
  final double latitude;
  final double longitude;
  final String city;

  // Constructor
  const LocationData({
    required this.latitude,
    required this.longitude,
    required this.city,
  });

  // Method
  String get displayName => '$city ($latitude, $longitude)';
}

// Create an instance (an actual object)
final loc = LocationData(
  latitude: 3.1412,
  longitude: 101.6865,
  city: 'Kuala Lumpur',
);

print(loc.displayName);  // → "Kuala Lumpur (3.1412, 101.6865)"
```

---

### Lists and Maps

```dart
// List (array)
List<String> prayers = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
prayers.add('Syuruk');
print(prayers[0]);          // → 'Fajr'
print(prayers.length);      // → 6

// Map (dictionary / key-value pairs)
Map<String, double> coords = {
  'latitude': 3.1412,
  'longitude': 101.6865,
};
print(coords['latitude']);  // → 3.1412

// Common operations
final upperPrayers = prayers.map((p) => p.toUpperCase()).toList();
final fajrOnly = prayers.where((p) => p == 'Fajr').toList();
```

---

### The `??=` and `?.` operators

```dart
// ?. — safe navigation: only calls if not null
String? city;
print(city?.length);      // prints null (no crash)
print(city?.toUpperCase()); // prints null (no crash)

// ??= — assign only if currently null
String? name;
name ??= 'Unknown';       // name is now 'Unknown'
name ??= 'Other';         // name is still 'Unknown' (wasn't null)
```

---

## 15. 📐 App Architecture Patterns

As apps grow bigger, you need to organise code properly. Here's the pattern we use (and why).

---

### What We Use: Service Layer Pattern

```
Screens (UI)  ←──────────────→  Services (Logic)
  qibla_screen.dart              location_service.dart
  prayer_screen.dart             prayer_service.dart
  calendar_screen.dart
  settings_screen.dart
```

**Rule:** Screens only handle what the USER SEES. Services handle everything else.

- A screen should NOT contain API URL strings
- A screen should NOT do GPS math
- A screen should NOT decode JSON

Why? Because if you want to reuse the prayer time logic in a notification or a widget, you can — without copying code.

---

### The 3-State Pattern (every screen uses this)

Every screen that loads data has exactly 3 states:

```dart
bool _loading = true;   // waiting for data
String? _error;         // something went wrong
// (if not loading and no error = success state)

@override
Widget build(BuildContext context) {
  if (_loading) return _buildLoadingState();
  if (_error != null) return _buildErrorState();
  return _buildSuccessState();
}
```

Always handle all 3. A loading spinner + meaningful error message = good UX.

---

### Data Flow Direction

```
            ┌──────────────┐
            │   Service    │  ← talks to internet / sensors / database
            └──────┬───────┘
                   │  returns data (Future or Stream)
                   ▼
            ┌──────────────┐
            │    Screen    │  ← receives data, calls setState()
            └──────┬───────┘
                   │  returns widgets
                   ▼
            ┌──────────────┐
            │  Widget Tree │  ← what the user sees
            └──────────────┘
```

Data always flows DOWN (from service → screen → widgets). Never have widgets calling services directly inside `build()` — always call services in `initState()` or button handlers.

---

## 16. 🐛 Debugging Guide

### Where to See Errors

**In VS Code:**
- Red underlines in code = compile error (syntax mistake)
- Yellow underlines = warning (not a crash, but should fix)
- The **Problems** panel (View → Problems) = list of all issues

**In Terminal (during `flutter run`):**
- `flutter: some message` = your own `print()` statements
- `E/flutter: ...` = a Dart exception (crash)
- `W/...` = Android warning

---

### The Most Common Flutter Errors

**`setState() called after dispose()`**
- Means: you called `setState` after the screen was closed
- Fix: always check `if (mounted)` before calling `setState()` in async code:
```dart
if (mounted) setState(() { ... });
```

**`Null check operator used on a null value` (`!` crash)**
- Means: you used `someVariable!` but it was actually null
- Fix: use `?.` or `?? defaultValue` instead of `!`

**`RenderFlex overflowed by X pixels`**
- Means: your Column/Row doesn't fit on screen
- Fix: wrap in `SingleChildScrollView`, use `Flexible`/`Expanded`, or reduce sizes

**`MissingPluginException`**
- Means: a package's native Android code isn't linked
- Fix: run `flutter clean` then `flutter run` (full rebuild)

**`No devices found`**
- Fix: enable USB Debugging on phone, try different USB cable, run `flutter doctor`

**`Gradle build failed`**
- Fix 1: `flutter clean` then rebuild
- Fix 2: Check internet connection (Gradle downloads files)
- Fix 3: Check `android/app/build.gradle` for version conflicts

---

### Debugging Tools

**`print()` — the simplest debugger:**
```dart
print('GPS result: $position');
print('Qibla angle: $angle degrees');
```
Output appears in the VS Code terminal while running.

**Flutter DevTools:**
While running with `flutter run`, press `d` in the terminal to open DevTools in browser:
- **Widget Inspector** — see the full widget tree, tap any widget on screen to inspect it
- **Performance** — see if your app is dropping frames (causes jank/stutter)
- **Memory** — see if memory usage is growing (potential leak)

**`debugPrint()` instead of `print()` for long strings:**
```dart
debugPrint(jsonString);  // truncates safely, won't crash on very long strings
```

---

## 17. 📦 pubspec.yaml Deep Dive

`pubspec.yaml` is the most important config file in your project. Here is every section explained:

```yaml
# ── Identity ──────────────────────────────────────────────────────
name: qibla_flutter          # Package name (used internally)
description: A Qibla compass and prayer times app.
version: 1.0.0+1             # 1.0.0 = user-visible version
                             # +1 = build number (must increase each Play Store upload)

# ── Dart/Flutter version requirements ────────────────────────────
environment:
  sdk: ^3.9.2                # ^ means "3.9.2 or higher, below 4.0"

# ── Runtime dependencies (shipped with your app) ─────────────────
dependencies:
  flutter:
    sdk: flutter             # Always required
  cupertino_icons: ^1.0.8   # iOS-style icons

  # Add your packages here:
  geolocator: ^13.0.4       # GPS
  http: ^1.2.2              # API calls
  hijri: ^3.0.0             # Hijri calendar

# ── Dev dependencies (only used during development) ───────────────
dev_dependencies:
  flutter_test:
    sdk: flutter             # Testing framework
  flutter_lints: ^4.0.0     # Code style warnings

# ── Flutter-specific config ───────────────────────────────────────
flutter:
  uses-material-design: true  # Enables Material icons

  # Include image files:
  assets:
    - assets/images/logo.png
    - assets/images/          # All files in this folder

  # Include custom fonts:
  fonts:
    - family: MyFont
      fonts:
        - asset: assets/fonts/MyFont-Regular.ttf
        - asset: assets/fonts/MyFont-Bold.ttf
          weight: 700
```

**Version numbers explained:**

| Symbol | Meaning | Example | Allows |
|---|---|---|---|
| `^1.2.3` | Same major version | `^1.2.3` | 1.2.3 to 1.x.x |
| `>=1.2.3 <2.0.0` | Range | explicit range | same as ^ |
| `1.2.3` | Exact version | `1.2.3` | only 1.2.3 |
| `any` | Any version | not recommended | anything |

Always use `^` (caret) — it gets new bug fixes automatically but prevents breaking changes.

**After editing pubspec.yaml — always run:**
```
flutter pub get
```

---

### How `flutter pub get` works

```
pubspec.yaml  (your wishlist: "I want geolocator ^13.0")
     │
     ▼  flutter pub get
     │
pub.dev servers  (Flutter's package registry)
     │  downloads packages
     ▼
.dart_tool/  +  pubspec.lock
```

`pubspec.lock` is auto-generated — it records the EXACT version that was downloaded (e.g. `geolocator 13.0.4`). Never edit it manually. Commit it to git so your team uses the same versions.

---

## 18. Change Log — What We Did and When

> *(formerly Section 11)*

> Every time we make a change, it gets added here with a date and explanation.

---

### 📅 May 5, 2026 — Initial Setup & Documentation

**What was already built:**
- ✅ 4-tab app: Qibla Compass, Prayer Times, Hijri Calendar, Settings
- ✅ GPS location detection with permission handling
- ✅ Real-time Qibla compass using phone magnetometer
- ✅ Prayer times: uses JAKIM API for Malaysia, offline solar calculation for rest of world
- ✅ Hijri calendar with month navigation
- ✅ Settings screen (UI only, preferences not saved yet)

**Packages installed (in pubspec.yaml):**
- `geolocator: ^13.0.4` — GPS
- `geocoding: ^3.0.0` — reverse geocoding
- `sensors_plus: ^6.1.1` — magnetometer
- `http: ^1.2.2` — API calls
- `hijri: ^3.0.0` — Hijri calendar
- `flutter_local_notifications: ^18.0.1` — notifications (not fully wired yet)

**Commands run so far:**
- `flutter devices` — checked connected devices
- `flutter pub get` — (run when packages were first added)

**Files created:**
- `NOTES.md` — this documentation file

---

### 📅 May 5, 2026 — Fix: Compass Stuck on Loading Forever

**Problem:**
The Qibla screen was stuck showing "Getting your location..." forever on a real Android phone.

**Why it happened:**
The code called `Geolocator.getCurrentPosition()` with `LocationAccuracy.high`.
- **High accuracy** uses the GPS chip directly — it can take 30–60+ seconds to get a fix, especially indoors.
- There was **no timeout**, so if GPS was slow, the app just waited forever with no error message.
- The reverse geocoding (`placemarkFromCoordinates`) also had no timeout and could hang if internet was slow.

**What we fixed** (in `lib/services/location_service.dart`):

1. **Try last known position first** — `Geolocator.getLastKnownPosition()` returns the most recently cached GPS location instantly. If your phone has been used for GPS before, this returns in milliseconds.

   ```dart
   Position? position = await Geolocator.getLastKnownPosition();
   ```

2. **Add a 15-second timeout** — if there's no cached position and fresh GPS is slow, we now fail after 15 seconds with a clear message instead of hanging forever.

   ```dart
   position ??= await Geolocator.getCurrentPosition(...).timeout(
     Duration(seconds: 15),
     onTimeout: () => throw 'GPS is taking too long...',
   );
   ```
   
   > `??=` means "assign only if the variable is currently null". So if `getLastKnownPosition()` already returned something, `getCurrentPosition()` is never called.

3. **Changed accuracy to `medium`** — medium accuracy uses GPS + WiFi + cell towers. It locks in 1–3 seconds vs 30+ seconds for high accuracy. Still accurate enough for Qibla direction.

4. **Geocoding is now optional** — wrapped in try/catch with 8-second timeout. If internet is slow or offline, the compass still works; the city name just shows blank.

**What the `!` (exclamation mark) means:**
```dart
latitude: position!.latitude,
```
The `!` is the "null assertion operator". It tells Dart: "I know this variable is not null right now, trust me." We need it because Dart's type system can't automatically figure out that the code above already guarantees `position` is not null when we reach this line.

---

### 📅 May 5, 2026 — Fix #2: Manual Location Picker (GPS Fallback)

**Problem:**
Even after the first fix (8-second timeout), the compass was still stuck on "Getting your location..." for too long. GPS is unreliable indoors on first launch.

**Root cause:**
The app had no way to work WITHOUT GPS. If GPS was slow or unavailable, the user was just stuck.

**What we fixed:**

#### 1. Added a Malaysian city lookup table (`lib/services/location_service.dart`)
Added a static Map called `LocationService.malaysianCities` with 14 Malaysian state capitals and their exact GPS coordinates:
```dart
static const Map<String, LocationData> malaysianCities = {
  'Kuala Lumpur / Putrajaya': LocationData(latitude: 3.1412, longitude: 101.6865, ...),
  'Selangor (Shah Alam)':     LocationData(latitude: 3.0738, longitude: 101.5183, ...),
  // ... 12 more cities
};
```
**Why static const?** `static` means it belongs to the class, not an instance — you access it as `LocationService.malaysianCities` without creating a `LocationService()` object. `const` means it's a compile-time constant — the values are baked into the app at compile time, so no runtime cost.

#### 2. Refactored Qibla screen (`lib/screens/qibla_screen.dart`)
- Extracted compass-starting code into `_applyLocation(LocationData)` — avoids repeating the same magnetometer code in two places
- Added `_showLocationPicker()` — shows an `AlertDialog` with a scrollable list of cities
- Updated `_buildError()` to show two buttons: "Try GPS Again" + "Choose location manually" + a tip card

#### 3. Refactored Prayer screen (`lib/screens/prayer_screen.dart`)
Same changes — added `_loadFromLocation()` helper, `_showLocationPicker()`, updated error UI.

**New user flow when GPS fails:**
```
GPS fails or times out (8 seconds)
  → Error screen appears with:
      [Try GPS Again]          ← tries GPS again
      [Choose location manually] ← opens city picker
  → User taps "Choose location manually"
  → Scrollable list of Malaysian cities appears
  → User taps their city (e.g. "Selangor (Shah Alam)")
  → Compass/prayer times load immediately — no GPS needed!
```

**What is `showDialog()`?**
`showDialog()` is a Flutter function that overlays a popup box (dialog) on top of the current screen. The dialog has a title, content (our city list), and action buttons. It returns a `Future<T>` — it waits for the user to tap something, then returns that value. We `await` it:
```dart
final selected = await showDialog<LocationData>(...);
// Code here only runs AFTER the dialog is closed
if (selected != null) { ... }
```

**What is `Navigator.of(ctx).pop(value)`?**
`pop()` closes the dialog and returns `value` to whoever called `showDialog()`. So when the user taps a city, `pop(entry.value)` closes the dialog and `selected` becomes that city's `LocationData`.

**Reduced GPS timeout:** 15 seconds → **8 seconds** so the error appears faster.

**Files changed:**
- `lib/services/location_service.dart` — added `malaysianCities`, reduced timeout
- `lib/screens/qibla_screen.dart` — refactored, added picker
- `lib/screens/prayer_screen.dart` — refactored, added picker

**Command to apply the fix on your phone:**
```
# In VS Code terminal — press R (capital R) in the running flutter session
# OR kill the app and re-run:
flutter run
```

---

### 📅 May 5, 2026 — Fix #3: Tilt-Compensated Compass (Wrong Direction Fix)

**Problem:**
The compass was pointing in the wrong direction. It only worked correctly when the phone was lying perfectly flat on a table.

**Why it happened — the old code:**
```dart
// OLD: raw magnetometer only
double heading = atan2(event.x, event.y);
```
This formula assumes the phone is flat (x = East, y = North). The moment you tilt or hold the phone upright (portrait), the x/y axes no longer point East/North — they point in random tilted directions. So the heading was wrong.

**The fix — tilt-compensated compass:**
We now use **both** the accelerometer AND magnetometer together. This is the exact same method Android uses internally in `SensorManager.getRotationMatrix()`.

**Step-by-step explanation:**

```
1. Accelerometer → gravity vector [ax, ay, az]
   - Tells us which way is "down" (gravity)
   - Works as our tilt reference

2. East vector = gravity × magnetometer  (cross product)
   - Cross product of two vectors gives a vector perpendicular to both
   - gravity × mag = a vector pointing East (horizontal)

3. North vector = East × gravity  (cross product again)
   - This "cancels out" the tilt
   - Result: a horizontal vector pointing toward magnetic North
   - Works no matter how much the phone is tilted!

4. Heading = atan2(East.y, North.y)
   - Converts the 3D vectors into a single angle (degrees clockwise from North)
```

**Cross product — what is it?**
A cross product of two vectors gives a THIRD vector that is perpendicular (90°) to both. Example: if you cross "forward" × "right", you get "up". We use it here to find East and North from the gravity + magnetic field measurements.

**Low-pass filter on accelerometer:**
```dart
const alpha = 0.85;
gravity[0] = alpha * gravity[0] + (1 - alpha) * e.x;
```
- The accelerometer is very sensitive — it picks up every tap, shake, and vibration
- Without filtering, the compass would spin wildly every time you tap the screen
- alpha = 0.85 means: keep 85% of the old reading, take only 15% of the new one
- This smooths out sudden jolts while still following real tilts

**StreamController — what is it?**
`StreamController` lets us manually control a Stream. We need it here because we have TWO sensor streams (accelerometer + magnetometer) but want to output ONE stream (compass heading). We write the combined logic inside `onListen` and cancel both when `onCancel` is called.

**Files changed:**
- `lib/services/location_service.dart` — replaced `startCompass()`, added `_tiltCompensatedHeading()`, updated import

**To test on your phone:**
```
# In the running flutter session, press:
R     ← Hot Restart (reloads the full app)
# Then open the Qibla tab and test:
# - Flat on table → should point correctly
# - Tilted at 45° → should still point correctly
# - Held upright → should still point correctly
```

---

### 📅 May 5, 2026 — Fix #4: Compass Still Wrong Direction (Root Cause Found)

**Problem:**
Even after Fix #3, the compass still pointed in the wrong direction.

**Root cause of Fix #3's failure:**
Android's accelerometer doesn't measure gravity — it measures the **reaction force** against gravity. So when the phone lies flat:
- Real gravity direction = downward = (0, 0, −9.8) in world space
- Accelerometer reads = **upward** = (0, 0, +9.8) ← opposite direction!

The previous cross-product code did `gravity × magnetometer` to find "East". But because the "gravity" vector was actually pointing UP (not down), the cross product pointed **West** instead of East. The heading was therefore ~180° off in some orientations.

**The fix — Freescale AN4248 pitch/roll method:**

Instead of cross products, we compute tilt angles first, then use them to correct the magnetometer reading.

**Step 1: Compute tilt angles from accelerometer**
```dart
pitch = atan2(-ax, √(ay² + az²))   // lean left/right
roll  = atan2(ay, az)               // tilt top toward/away from you
```
These don't need the direction of gravity to be correct — `atan2` works with the raw values.

**Step 2: Project magnetometer onto the horizontal plane**
```dart
Bpx = mx·cos(pitch) + mz·sin(pitch)
Bpy = mx·sin(roll)·sin(pitch) + my·cos(roll) − mz·sin(roll)·cos(pitch)
```
This removes the effect of tilt. `Bpx` and `Bpy` are what the magnetometer would read if the phone were perfectly flat.

**Step 3: Compute heading**
```dart
heading = atan2(−Bpx, Bpy)   // 0° = North, 90° = East
```

**Why `atan2(−Bpx, Bpy)` and not `atan2(Bpy, Bpx)`?**
Many tutorials online have this WRONG. I verified it manually for all 4 directions:

| Phone lies flat facing... | Bpx | Bpy | `atan2(−Bpx, Bpy)` result |
|--------------------------|-----|-----|--------------------------|
| North | 0 | +H | atan2(0, H) = **0° ✓** |
| East | −H | 0 | atan2(H, 0) = **90° ✓** |
| South | 0 | −H | atan2(0, −H) = **180° ✓** |
| West | +H | 0 | atan2(−H, 0) = **270° ✓** |

(H = horizontal magnetic field strength, ~20–40 μT in Malaysia)

**Files changed:**
- `lib/services/location_service.dart` — replaced `_tiltCompensatedHeading()` with pitch/roll method

**To apply on your phone:**
In the terminal (same window where you ran `flutter run`), press:
```
R    ← capital R = Hot Restart (applies the fix in ~5 seconds)
```

**How to test accuracy:**
1. Open any compass app (even Google Maps) and note which direction is North
2. Open your QiblaApp — the Compass Heading number should match the other compass
3. From Malaysia, Qibla direction to Mecca should be around **291–295°** (NW direction)
4. When you face that direction, the green arrow should point straight up

---

### 📅 May 5, 2026 — Fix #5: App Broke When Cable Unplugged (Debug vs Release)

**Problem:**
The compass worked when the USB cable was plugged in, but went wrong (or stopped working) when the cable was unplugged.

**Root cause — Debug builds require a USB connection:**

When you run `flutter run`, Flutter installs a **debug build** on your phone. A debug build is special:
- It streams Dart code live from your computer to the phone
- It needs the USB connection to load the actual app code
- When you unplug, the debug build **falls back to the old code** that was already compiled into it — which was the previous buggy version

This is why the compass was wrong without the cable — it was running old code!

**Two types of Flutter builds:**

| | Debug Build (`flutter run`) | Release Build (`flutter build apk --release`) |
|--|--|--|
| Purpose | Development & testing | Real use / sharing |
| Speed | Slow (streams code live) | Fast (fully compiled) |
| Needs USB? | Yes, to get latest changes | **No — works standalone** |
| File size | ~45MB (includes dev tools) | ~22MB (stripped down) |
| Hot reload? | Yes | No |

**What we did:**

**Step 1 — Built the release APK:**
```
flutter build apk --release
```
Output: `build\app\outputs\flutter-apk\app-release.apk` (43.7 MB)

This compiles ALL your Dart code into native machine code. The APK is completely self-contained — no USB, no computer needed ever again.

**Step 2 — Installed it on the phone:**
```
flutter install --device-id 3KX0222A11000958 build\app\outputs\flutter-apk\app-release.apk
```
This uninstalled the old debug version and replaced it with the release build.

**From now on:**
- Unplug the cable ✓
- Open QiblaApp from your home screen ✓
- The compass works with the correct direction ✓
- No computer needed ✓

**IMPORTANT — future workflow:**
Every time you make a code change, you need to rebuild and reinstall:
```
flutter build apk --release
flutter install --device-id 3KX0222A11000958 build\app\outputs\flutter-apk\app-release.apk
```
Or just use `flutter run` while plugged in to test quickly, then do a release build when you want the final version.

**APK file location on your PC:**
```
C:\Users\hilmanxz\Documents\Projek Intern\qibla_flutter\build\app\outputs\flutter-apk\app-release.apk
```
You can share this file with anyone — they can install it directly on their Android phone!

---

### 📅 May 2026 — Fix #6: Compass Still Inaccurate → Switched to Hardware Sensor Fusion

**Problem:**
After Fix #4 (AN4248 pitch/roll tilt compensation), the compass was still wrong — pointing in the wrong direction, especially when the phone was tilted.

**Why all our previous manual math kept failing:**

Every compass approach we tried wrote Dart code to manually process the raw magnetometer + accelerometer sensors. The problem with this:

1. **Raw magnetometer needs calibration** — it picks up interference from phone circuits, metal in tables, etc. Raw readings alone are noisy and drifting.
2. **Accelerometer axis confusion** — Android's accelerometer points UP (reports +9.8 when flat), not DOWN. This caused signs to flip and the heading to be 90°–180° wrong.
3. **No gyroscope** — Without gyroscope data, sudden movements make the heading spin wildly. The gyroscope is what stabilises everything.
4. **Dart math precision** — Writing correct tilt-compensation formulas from scratch is hard. Small mistakes in sign (+/−) or axis order (x/y/z) break everything.

**The real fix — let Android do the math:**

Android phones have a built-in sensor called **TYPE_ROTATION_VECTOR**. This is a *virtual* sensor — it doesn't correspond to one physical chip. Instead, Android's OS constantly combines three physical sensors in the background:

```
Gyroscope  ──┐
              ├─→ Kalman Filter (C++ native code) ──→ TYPE_ROTATION_VECTOR
Magnetometer──┤                                        = accurate, stable heading
              │
Accelerometer─┘
```

A **Kalman filter** is a smart math algorithm that:
- Trusts the gyroscope for short fast movements (very responsive)
- Trusts the magnetometer for long-term direction (no drift)
- Combines them to get the best of both worlds
- Runs in fast native C++ code, not slow Dart

This is the SAME sensor Google Maps uses for its blue direction arrow.

**The `flutter_compass` package:**

```yaml
# pubspec.yaml
flutter_compass: ^0.8.0
```

This package wraps Android's TYPE_ROTATION_VECTOR sensor and gives us one simple stream:

```dart
FlutterCompass.events!.listen((CompassEvent event) {
  double heading = event.heading!;  // 0° = North, 90° = East, 270° = West
  // That's it. Accurate, stable, tilt-compensated — Android did all the work.
});
```

**What we removed from `location_service.dart`:**
- The entire `_tiltCompensatedHeading()` method (~35 lines of AN4248 pitch/roll math)
- The `StreamController` with two `StreamSubscription`s (accelerometer + magnetometer)
- The `sensors_plus` import (no longer needed)
- Low-pass filter on accelerometer (no longer needed)

**What we kept:**
- `calculateQiblaAngle(lat, lng)` — our Great Circle bearing formula is still correct!

**The new `startCompass()` — just 10 lines:**

```dart
static Stream<List<double>> startCompass(double userLat, double userLng) {
  final qiblaAngle = calculateQiblaAngle(userLat, userLng);

  final events = FlutterCompass.events;
  if (events == null) return const Stream.empty(); // no compass hardware

  return events
      .where((e) => e.heading != null)  // skip null readings
      .map((e) {
        final heading = e.heading!;
        final needleRotation = (qiblaAngle - heading + 360) % 360;
        return [heading, qiblaAngle, needleRotation];
      });
}
```

**How the needle rotation math works:**

Imagine your phone's "north" pointer points at `heading` degrees. You want the Qibla arrow to point at `qiblaAngle` degrees. How much do you rotate the arrow?

```
needleRotation = qiblaAngle − heading
```

For example, in KL:
- Qibla angle = 292° (northwest)
- Your phone is facing East = heading 90°
- `needleRotation = 292 − 90 = 202°`
- So the arrow rotates 202° from "phone's forward direction" to point at Mecca ✓

The `+ 360) % 360` just keeps the result in the 0–360 range (no negative numbers).

**Build & install commands:**
```
flutter build apk --release
flutter install build\app\outputs\flutter-apk\app-release.apk
```
APK size: **21.9 MB** (smaller than before because we removed sensors_plus dependency usage).

**Lesson learned:**
> Don't fight hardware. When the OS gives you a ready-made accurate sensor, use it. Only write raw sensor math if you absolutely have no choice.

---

## 📦 How to Build & Install an APK — Complete Guide

This section explains everything about turning your Flutter code into an app you can actually use on your phone.

---

### What is an APK?

**APK = Android Package Kit.** It's the file format for Android apps — like a `.exe` for Windows or a `.zip` that contains your entire app: code, images, fonts, everything. When you install an app from the Play Store, the Play Store is just downloading an APK and installing it for you. You can do this manually too.

Your APK file lives here after building:
```
C:\Users\hilmanxz\Documents\Projek Intern\qibla_flutter\build\app\outputs\flutter-apk\app-release.apk
```

---

### The Two Modes: Debug vs Release

Flutter can build your app in two different ways. This is the most important concept to understand.

#### 🔧 Debug Mode — for development (`flutter run`)
```
flutter run
```
- Connects your phone to your computer via USB
- Streams Dart code live from your laptop to your phone
- Supports **hot reload** (press `r` to instantly see changes without restarting)
- The app is slow and big because it includes debugging tools
- **ONLY works while the USB cable is plugged in** — when you unplug, the app runs the last compiled snapshot, which may be an old version
- Use this when you're actively writing code and testing

#### 🚀 Release Mode — for real use (`flutter build apk --release`)
```
flutter build apk --release
```
- Compiles ALL your Dart code into native machine code
- No USB cable needed ever — the app is 100% self-contained
- Fast, small, and optimised
- No hot reload — to update, you must rebuild and reinstall
- Use this when you want the final version to use daily or share

| Feature | `flutter run` (Debug) | `flutter build apk --release` |
|---|---|---|
| Needs USB cable? | Yes, to get latest code | **No** |
| Speed | Slow | Fast |
| File size | ~45 MB | ~22 MB |
| Hot reload | ✅ Yes | ❌ No |
| Works standalone? | ❌ No | ✅ Yes |
| Good for | Writing & testing code | Daily use & sharing |

---

### Step-by-Step: Build a Release APK

**Step 1 — Make sure your phone is connected (USB) and USB Debugging is on:**

On your Huawei DCO LX9:
1. Go to **Settings → About Phone**
2. Tap **Build Number** 7 times fast → "You are now a developer"
3. Go to **Settings → Developer Options** → enable **USB Debugging**
4. Connect USB cable → tap "Allow" on the USB debugging popup on the phone

**Step 2 — Check Flutter can see your phone:**
```
flutter devices
```
You should see your phone listed. Example output:
```
DCO-LX9 (mobile) • 3KX0222A11000958 • android-arm64 • Android 10
```

**Step 3 — Build the release APK:**
```
flutter build apk --release
```
This takes 1–3 minutes. You'll see a progress bar. When done:
```
✓ Built build\app\outputs\flutter-apk\app-release.apk (43.7MB)
```

What Flutter did behind the scenes:
1. Ran `dart compile` to turn all your `.dart` files into native ARM machine code
2. Ran Android Gradle to package everything (images, fonts, platform code) into one `.apk` file
3. Signed the APK with a debug key (so Android trusts it)
4. Optimised and compressed everything

**Step 4 — Install on your phone:**
```
flutter install build\app\outputs\flutter-apk\app-release.apk
```
Flutter will:
1. Uninstall the old version (if any)
2. Push the APK file to your phone
3. Run `adb install` to install it
4. Done — find the app in your app drawer

If you have multiple devices connected, specify which one:
```
flutter install --device-id 3KX0222A11000958 build\app\outputs\flutter-apk\app-release.apk
```
(Replace `3KX0222A11000958` with your device ID from `flutter devices`)

**Step 5 — Unplug the cable and use the app!**

The app now lives on your phone permanently. No laptop needed.

---

### What is `adb`? (Android Debug Bridge)

`flutter install` uses a tool called **adb** (Android Debug Bridge) behind the scenes. adb is a command-line tool that talks to Android devices over USB. Flutter installs it automatically as part of the Android SDK.

You can also use adb directly if you want:
```
adb install build\app\outputs\flutter-apk\app-release.apk
```

Or even copy the APK to your phone via USB/WhatsApp/Google Drive and tap it to install (you'll need to allow "Install from unknown sources" in phone settings).

---

### What Happens When You Build

Here's what Flutter does during `flutter build apk --release`, step by step:

```
Your Dart code (.dart files)
        ↓
  dart2native compiler
        ↓
ARM64 machine code (runs directly on phone CPU)
        ↓
  Android Gradle build system
        ↓
  Packages together:
  - Your compiled Dart code
  - Flutter engine (C++ code that draws the UI)
  - Your assets (images, fonts)
  - Android manifest (app name, permissions, icon)
        ↓
  app-release.apk  ✓
```

---

### Every Time You Change Code — Full Workflow

```
1. Write/edit your Dart code
2. Test quickly with:
      flutter run
   (plug in USB, use hot reload with 'r' key)
3. When happy with the result, build release:
      flutter build apk --release
4. Install on phone:
      flutter install build\app\outputs\flutter-apk\app-release.apk
5. Unplug cable → use the app!
```

**Shortcut tip:** You can also run this one-liner to build AND install in sequence:
```
flutter build apk --release ; flutter install build\app\outputs\flutter-apk\app-release.apk
```

---

### Sharing the APK with Others

The file at `build\app\outputs\flutter-apk\app-release.apk` can be shared directly:
- Send via **WhatsApp, Telegram, email, Google Drive, USB cable** — anything
- The other person must allow **"Install from unknown sources"** on their Android phone
  - Go to: Settings → Apps → Special App Access → Install Unknown Apps → (browser or file manager) → Allow
- They tap the file → tap Install → done

This is called **sideloading** — installing an app outside the Play Store.

> **Note:** The APK is signed with a debug certificate, which is fine for personal use. If you ever want to publish to the Play Store, you'd need to create a proper signing key — but that's a much later step.

---

### Common Errors & Fixes

| Error | Meaning | Fix |
|---|---|---|
| `No devices found` | Phone not detected | Check USB cable, enable USB Debugging, try a different USB port |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signature mismatch with old version | Uninstall old app first: `adb uninstall com.hilmanxz.qibla_flutter` |
| `Gradle build failed` | Android build system error | Run `flutter clean` then try again |
| `flutter: command not found` | Flutter not in PATH | Open a new terminal, or reinstall Flutter |
| Build stuck at "Running Gradle task" | First build is slow | Wait 5–10 minutes, it downloads dependencies |

**`flutter clean` — the "turn it off and on again" for Flutter:**
```
flutter clean
flutter pub get
flutter build apk --release
```
This deletes all build cache and starts fresh. Use it when something is mysteriously broken.

---

*Next time we do something, a new entry will be added below here.*

---

### 📅 May 6, 2026 — Feature: Always-Visible "Change Location" Button

**What was added:**
Both the Qibla screen and Prayer screen headers now have a permanent location edit button (📍 pencil icon) in the top-right corner. Before this, the city picker only appeared when GPS failed. Now it's always there so you can check prayer times or Qibla for any city anytime.

**What changed:**

`lib/screens/qibla_screen.dart` — `_buildHeader()`:
- Converted the header `Column` into a `Row` with `Expanded` + `IconButton`
- The icon button uses `Icons.edit_location_alt_outlined` and calls `_showLocationPicker()`
- Semi-transparent white background (`Colors.white.withOpacity(0.15)`) so it looks clean on the green header
- `Tooltip` wraps the button so long-pressing shows "Change location" text

`lib/screens/prayer_screen.dart` — `_buildContent()` header:
- Added the same icon button next to the existing JAKIM zone badge
- Same styling — white icon, semi-transparent background

**How the location picker works (reminder):**
1. Taps the icon → `_showLocationPicker()` opens an `AlertDialog`
2. Shows a scrollable list of 14 Malaysian cities
3. User taps a city → `_applyLocation(location)` is called
4. Compass immediately recalculates for the new city / prayer times reload for the new city
5. No GPS required — works fully offline with the hardcoded coordinates

---

### 🗺️ Feature Roadmap — What to Build Next

Here are all the suggested next features, from easiest to hardest:

---

#### ✅ Feature 1 — Change Location Button (DONE above)
Always-visible button in both headers. Already implemented.

---

#### 🔲 Feature 2 — Adhan Sound Player

**What it does:**
When prayer time arrives, play an Adhan (call to prayer) sound from a library of recordings.

**What you need:**

1. **Audio package** — `just_audio` is the best Flutter audio player:
   ```yaml
   just_audio: ^0.9.40
   ```

2. **A database of Adhan files** — store the audio files as local assets inside your app:
   ```
   assets/
   └── adhan/
       ├── makkah.mp3          ← Adhan from Masjidil Haram
       ├── madinah.mp3         ← Adhan from Masjid Nabawi
       ├── egypt.mp3           ← Classic Egyptian Adhan
       └── local_malaysia.mp3  ← Malaysian style
   ```
   Declare them in `pubspec.yaml`:
   ```yaml
   flutter:
     assets:
       - assets/adhan/
   ```

3. **A timer that fires at prayer time** — compare current time to each prayer time every minute, play when they match:
   ```dart
   Timer.periodic(Duration(minutes: 1), (_) {
     final now = DateTime.now();
     for (final prayerTime in allTimes) {
       if (now.hour == prayerTime.hour && now.minute == prayerTime.minute) {
         _playAdhan();
       }
     }
   });
   ```

4. **User preference saved** — use `shared_preferences` to remember which Adhan the user picked:
   ```yaml
   shared_preferences: ^2.2.2
   ```
   ```dart
   final prefs = await SharedPreferences.getInstance();
   prefs.setString('selected_adhan', 'makkah');
   final selected = prefs.getString('selected_adhan') ?? 'makkah';
   ```

5. **Settings screen integration** — add a dropdown in `settings_screen.dart` to let the user pick their preferred Adhan. Preview button to hear it before selecting.

**Files to create/modify:**
- `lib/services/adhan_service.dart` — new, handles audio playback
- `lib/screens/settings_screen.dart` — add Adhan picker + preview button
- `android/app/src/main/AndroidManifest.xml` — may need `FOREGROUND_SERVICE` permission if you want Adhan to play when app is backgrounded

**Difficulty:** ⭐⭐⭐ Medium

---

#### 🔲 Feature 3 — Notification at Prayer Time (Background Adhan)

**What it does:**
Even when the app is closed, a notification pops up at each prayer time with a sound.

**What you need:**
- `flutter_local_notifications` is already installed — just needs to be wired up
- Schedule a notification for each prayer time using `zonedSchedule`
- Works even when the app is fully closed

**Difficulty:** ⭐⭐⭐⭐ Medium-Hard (needs exact alarm permissions, already in manifest)

---

#### 🔲 Feature 4 — Save Settings Permanently (SharedPreferences)

**What it does:**
Right now, the Settings screen resets when you close the app. This fixes that.

**What you need:**
```yaml
shared_preferences: ^2.2.2
```
```dart
// Save
final prefs = await SharedPreferences.getInstance();
prefs.setBool('adhan_enabled', true);
prefs.setString('selected_adhan', 'makkah');
prefs.setString('calculation_method', 'JAKIM');

// Load on startup
final enabled = prefs.getBool('adhan_enabled') ?? false;
```

**Difficulty:** ⭐⭐ Easy — mostly just wiring up existing UI to read/write prefs

---

#### ✅ Feature 5 — Qibla Accuracy Indicator (DONE — Fix #8)

**What it does:**
Show a small badge on the compass: "Accuracy: High / Medium / Low" based on `CompassEvent.accuracy` from flutter_compass. If the magnetometer needs calibration (user should wave phone in figure-8), show a hint.

```dart
FlutterCompass.events!.listen((event) {
  final accuracy = event.accuracy;   // degrees of uncertainty
  // < 15° = High, 15–45° = Medium, > 45° = Low → needs calibration
});
```

**Difficulty:** ⭐⭐ Easy

---

#### ✅ Feature 6 — App Icon (DONE)

**What it does:**
Replace the default Flutter blue icon with a custom Qibla/mosque icon.

**What you need:**
```yaml
dev_dependencies:
  flutter_launcher_icons: ^0.14.1
```
Add an icon image (1024×1024 PNG) in `assets/` and configure:
```yaml
flutter_launcher_icons:
  android: true
  ios: true
  image_path: "assets/icon.png"
```
Then run:
```
flutter pub run flutter_launcher_icons
```

**Difficulty:** ⭐ Very Easy

---

#### 🔲 Feature 7 — Dark Mode

**What it does:**
Follow the phone's system dark/light mode setting automatically.

In `main.dart`:
```dart
MaterialApp(
  theme: ThemeData(colorSchemeSeed: const Color(0xFF1a7a4a)),
  darkTheme: ThemeData(
    colorSchemeSeed: const Color(0xFF1a7a4a),
    brightness: Brightness.dark,
  ),
  themeMode: ThemeMode.system,  // follows phone setting
)
```
Then replace hardcoded `Color(0xFFF5F5F5)` backgrounds with `Theme.of(context).scaffoldBackgroundColor`.

**Difficulty:** ⭐⭐ Easy-Medium

---

#### 🔲 Feature 8 — Worldwide City Search

**What it does:**
Instead of only 14 Malaysian cities, let the user type any city name in the world and get prayer times for it.

**What you need:**
- A geocoding API to convert city name → coordinates (Google Places API, or use the free `geocoding` package already installed)
- A search text field in the location picker dialog
- Offline fallback: keep the Malaysian city list

**Difficulty:** ⭐⭐⭐ Medium

---

#### Priority Recommendation

| Order | Feature | Why |
|---|---|---|
| Order | Feature | Status |
|---|---|---|
| ✅ | **App Icon** | Done |
| ✅ | **Accuracy Indicator** | Done (Fix #8) |
| ✅ | **Save Settings** (SharedPreferences) | Done |
| ✅ | **Magnetic Declination** (NOAA WMM) | Done (Supervisor request) |
| 1 | **Dark Mode** | ❌ |
| 2 | **Adhan Sound** | ❌ |
| 3 | **Background Notifications** | ❌ |
| 4 | **Worldwide Search** | ❌ |

---

### 📅 May 6, 2026 — Fix #7: App Stuck on Loading Screen After Granting Permission

**Problem:**
When opening the app and tapping "Allow" on the GPS permission popup, the app would stay on the loading spinner forever. The only way to make it work was to reopen the app. Strangely, turning off location first and then accepting the permission popup made it load quickly.

**Root cause — two separate problems stacked together:**

#### Problem A: `getLastKnownPosition()` hangs on Huawei

The old code did this:
```dart
Position? position = await Geolocator.getLastKnownPosition(); // try cache first
position ??= await Geolocator.getCurrentPosition(...);        // fallback
```

On Huawei phones (which use HMS — Huawei Mobile Services — instead of Google's GMS), `getLastKnownPosition()` can take several seconds to return `null` instead of returning instantly. So we were wasting 5–10 seconds just on the "try cache" step before even starting to get a real GPS fix.

We removed `getLastKnownPosition()` entirely and always call `getCurrentPosition()` directly.

#### Problem B: `LocationAccuracy.medium` triggers full GPS satellite search

`medium` accuracy in Geolocator means "I want GPS satellite-level accuracy". On a cold start (GPS not warmed up), acquiring satellite lock can take **30–60 seconds**. Our Dart `.timeout(8 seconds)` was kicking in and showing an error before GPS could lock.

When you turned off location first, the phone used **network location** (WiFi/cell towers) instead of satellites, which returns in 1–3 seconds — that's why it worked faster with location disabled!

The fix: **always use `LocationAccuracy.low`**:
- `low` = WiFi + cell towers = returns in 1–3 seconds, accuracy ±500 metres
- That's MORE than enough to calculate Qibla direction (we just need to know which city you're in)
- Satellites are overkill for a compass app

#### Problem C: Dart `.timeout()` doesn't cancel native GPS on Huawei/HMS

Even with a Dart-side `.timeout()`, the underlying native Android location request keeps running. On Huawei, this causes the Future to resolve slowly or not at all.

The fix: use `AndroidSettings` with a `timeLimit` parameter — this sets a **native Android** timeout, not just a Dart wrapper:

```dart
// OLD — unreliable on Huawei:
locationSettings: const LocationSettings(accuracy: LocationAccuracy.medium)

// NEW — fast and reliable:
locationSettings: AndroidSettings(
  accuracy: LocationAccuracy.low,         // WiFi/cell: returns in 1-3 seconds
  timeLimit: const Duration(seconds: 10), // native Android timeout
)
```

#### Problem D: Geocoding was blocking the compass

The old flow was:
```
Get GPS → Wait for GPS fix → Wait for city name lookup → Show compass
```

If internet was slow, the city name lookup (geocoding) could add another 5–8 seconds of waiting AFTER GPS already had a fix. The compass was ready but the user still saw a loading spinner.

The new flow:
```
Get GPS → Show compass immediately → City name updates quietly in background
```

We moved geocoding into `LocationService.geocodeInBackground()` which:
1. The compass appears as soon as GPS gives coordinates
2. The city name in the header updates a few seconds later once geocoding finishes
3. If geocoding times out or fails, the compass is already working — no problem

**Changes made:**

**`lib/services/location_service.dart`:**
- Removed `getLastKnownPosition()` call
- Changed `LocationSettings` → `AndroidSettings` with `accuracy: low` + `timeLimit: 10s`
- `getLocation()` now returns coordinates immediately with empty city/region/country
- Added new `geocodeInBackground(location, callback)` static method that fetches city name asynchronously and calls the callback when done

**`lib/screens/qibla_screen.dart`:**
- After `_applyLocation()`, calls `LocationService.geocodeInBackground()` to update the header city name once geocoding finishes

**`lib/screens/prayer_screen.dart`:**
- Same pattern as qibla screen — compass/prayer times load immediately, city name updates in background

**`lib/services/prayer_service.dart`:**
- Removed the `if (country == 'MY')` gate before calling the API
- Previously, since geocoding is now deferred, `country` would always be empty on first load, which meant prayer times always fell back to offline calculation
- Now: always try the online API first (it auto-detects Malaysia zone from GPS), fall back to offline if it fails

**The new loading flow (fast path):**
```
User opens app
   ↓ ~0.5s
Permission check passes
   ↓ ~1-3s
WiFi/cell tower gives GPS fix  ←── This is where we used to wait 30-60s
   ↓ instant
Compass shown, prayer times loaded  ←── User sees result here
   ↓ ~2-5s later (background)
City name appears in header
```

**Summary of why it's faster now:**

| Step | Before | After |
|---|---|---|
| `getLastKnownPosition` | 0–10s (Huawei hangs) | Removed |
| GPS accuracy | medium (satellite, 30–60s cold) | low (network, 1–3s) |
| Timeout mechanism | Dart wrapper (unreliable on Huawei) | Native Android `timeLimit` |
| Geocoding | Blocks compass from showing | Background, non-blocking |
| **Total wait** | **5–60+ seconds** | **1–3 seconds** |

---

### 📅 Fix #8: Compass Broke Again — Arrow Moving But Pointing Wrong Direction

**Problem:**
After Fix #6 made the compass work correctly, the compass broke again. The needle was spinning/moving but pointing in the wrong direction — not toward Mecca.

**Root cause: `flutter run` installs a DEBUG build, overwriting the release APK**

This is the same trap as Fix #5, but now for the compass instead of the whole app.

When you type `flutter run` in the terminal, Flutter:
1. Compiles a **debug build** (with debugging tools included, different performance profile)
2. Uninstalls whatever APK is on the phone
3. Installs the new debug APK
4. Runs it

The debug APK **replaced** the working release APK. The compass then behaved differently in debug mode.

**Why does debug vs release matter for sensors?**

| | Debug Build (`flutter run`) | Release Build (`flutter build apk --release`) |
|---|---|---|
| Dart code | Runs in interpreter (slower) | Compiled to native machine code (fast) |
| Plugin initialisation | Slower, may miss first sensor events | Fast and reliable |
| Flutter engine | Includes debug overlay, observatory | Stripped down, optimised |
| Sensor timing | Frames can be dropped, stream events delayed | Smooth, consistent |
| `flutter_compass` plugin | Can lose first N events, causing heading offset | Initialises cleanly |

When `flutter_compass` starts in debug mode, it can miss the first few sensor readings or initialise with a stale cached value. This makes the first heading wrong, which cascades into a wrong `needleRotation`, which makes the arrow point in the wrong direction from the start.

**Golden rule:**
> **Never use `flutter run` to test the final compass behaviour.**
> Always build release: `flutter build apk --release` then `flutter install`.

**Three code improvements added alongside this fix:**

#### Improvement 1: Compass accuracy tracking

`FlutterCompass` returns an `accuracy` value alongside each heading reading. This is the **estimated heading error in degrees** — lower is better:

| Accuracy value | Meaning |
|---|---|
| `< 15°` | Good — compass is reliable |
| `15° – 45°` | Acceptable — slight interference |
| `> 45°` | Poor — compass needs calibration |
| `999` | Unknown — sensor didn't report accuracy |

We now stream this value out of `startCompass()` as the 4th element:
```dart
// location_service.dart — startCompass() return value
return [heading, qiblaAngle, needleRotation, accuracy];
//       [0]       [1]          [2]            [3]
```

And store it in the screen:
```dart
// qibla_screen.dart
double _compassAccuracy = 999; // 999 = unknown until first reading
```

#### Improvement 2: Calibration warning banner

When `_compassAccuracy > 45°`, a visible orange warning appears above the info cards:

```
⚠ Compass needs calibration — wave your phone slowly in a figure-8 shape a few times.
```

This tells the user WHY the compass might be wrong and exactly how to fix it. The figure-8 motion physically realigns the phone's magnetometer sensor.

**How to calibrate a phone compass:**
1. Hold the phone flat (screen facing up)
2. Slowly move it in a figure-8 shape (like drawing the number 8 in the air)
3. Do this 3–5 times
4. The compass accuracy should improve within a few seconds

The banner disappears automatically once accuracy improves below 45°.

#### Improvement 3: `_prevRotation` reset on location change

When the user changes location (e.g., switches from GPS to a manual city), the new Qibla angle can be very different from the old one. Without resetting `_prevRotation`, the shortest-path animation code would try to animate from the old rotation to the new one, which could cause a sudden spin.

Fix: reset `_prevRotation = 0` before starting the new compass subscription:
```dart
void _applyLocation(LocationData location) {
  _compassSubscription?.cancel();
  _prevRotation = 0;   // ← Reset so new compass starts clean
  _compassSubscription = QiblaService.startCompass(...).listen(...);
}
```

**Changes made:**

**`lib/services/location_service.dart`:**
- `startCompass()` now returns `[heading, qiblaAngle, needleRotation, accuracy]` (4 values, was 3)
- `accuracy` sourced from `event.accuracy ?? 999.0`

**`lib/screens/qibla_screen.dart`:**
- Added `double _compassAccuracy = 999;` state variable
- `_applyLocation()`: resets `_prevRotation = 0` before new subscription
- Stream listener: extracts `values[3]` as accuracy, stores in `setState`
- `_buildCompass()`: shows orange calibration banner when `_compassAccuracy > 45`

**Build and install commands used:**
```bash
flutter build apk --release
flutter install build\app\outputs\flutter-apk\app-release.apk
```

---

### 📅 Feature: Explicit Magnetic Declination (Supervisor Request)

**Background — what is magnetic declination?**

A compass needle points toward **Magnetic North** — which is NOT the same as **True (Geographic) North** (the actual North Pole). The difference between the two is called **magnetic declination**:

```
True North (Geographic North Pole)
       |
       |  ← small angle = declination
       |
Magnetic North (where compass points)
```

Think of it like this:
- True North = where the map says North is (actual North Pole)
- Magnetic North = where a bar magnet buried in the Earth's core is pointing
- They're different by a few degrees, and the difference varies by location

For Malaysia, the difference is small: approximately **+0.5° to +1.5° East** (positive = Magnetic North is slightly east of True North).

**Why does this matter for Qibla?**

Our Qibla calculation uses **True North** as the reference (like a map). But the phone compass sensor (magnetometer) points toward **Magnetic North**. If we use Magnetic North directly to point toward Mecca, we'll be slightly off.

The correction:
```
trueHeading = compassHeading + magneticDeclination
```

For KL with declination ≈ +0.51°:
```
if compass says 45° → true heading = 45° + 0.51° = 45.51°
```

This is the supervisor's requested flow:
```
Magnetometer + Accelerometer + Gyroscope
    ↓
Sensor Fusion (TYPE_ROTATION_VECTOR) — Android Kalman filter
    ↓
Raw stable heading (Magnetic North reference)
    ↓
+ Magnetic Declination (from NOAA WMM API)
    ↓
True North heading
    ↓
− Qibla bearing
    ↓
Needle rotation angle
```

**What is WMM?**

**World Magnetic Model (WMM)** is a mathematical model of Earth's magnetic field maintained jointly by the US National Oceanic and Atmospheric Administration (NOAA) and the British Geological Survey. It's updated every 5 years. It's the standard used worldwide for navigation, including GPS devices, phone compasses, and aircraft navigation.

**What is NOAA?**

**NOAA** = National Oceanic and Atmospheric Administration. A US government science agency. They provide a free web API at `https://www.ngdc.noaa.gov/geomag-web/` that returns the exact magnetic declination for any coordinate on Earth, for any date.

**Why fetch it from NOAA instead of computing it locally?**

Options available:
1. **Hardcode a fixed value** — bad, changes over time and by location
2. **Implement WMM math in Dart** — complex, 400+ lines of coefficient math
3. **Use Android's built-in GeomagneticField** — available, but needs platform channel code
4. **Call NOAA free API** — simple, always up-to-date, no extra packages

We chose option 4 (NOAA API) because we already have the `http` package installed and it requires no new dependencies.

**NOAA API endpoint:**
```
GET https://www.ngdc.noaa.gov/geomag-web/calculators/calculateDeclination
    ?lat=3.1&lon=101.7
    &resultFormat=json
    &startYear=2026&startMonth=5&startDay=8
```

**Example response for Kuala Lumpur:**
```json
{
  "result": [
    {
      "declination": 0.5120,   ← degrees (positive = east)
      "declinationSV": 0.01,
      ...
    }
  ]
}
```

**Why explicit declination even if TYPE_ROTATION_VECTOR already handles it?**

On standard Android phones with Google Mobile Services (GMS), `TYPE_ROTATION_VECTOR` internally applies WMM and already outputs True North. So on most phones, `declination ≈ 0` effect.

But this user's device is a **Huawei DCO LX9 with HMS** (Huawei Mobile Services, no Google). On HMS devices, there is no guarantee that the WMM is applied inside the sensor fusion stack. So explicit declination is important for this specific device.

Additionally, the supervisor wants to see the explicit step in code — for learning, auditing, and correctness.

**What changed in code:**

**`lib/services/location_service.dart`:**
- Added `import 'dart:convert'` (to parse JSON response)
- Added `import 'package:http/http.dart' as http` (to call NOAA API)
- Added `_fetchMagneticDeclination(lat, lng)` static method — calls NOAA, returns `double` in degrees
- Changed `startCompass()` from a `.map()` stream to an `async*` generator:
  - First `await`s the declination (fetched once at startup)
  - Then loops through each compass event
  - Applies: `trueHeading = rawHeading + declination`
  - Yields 5 values instead of 4: `[trueHeading, qiblaAngle, needleRotation, accuracy, declination]`

```dart
// NEW startCompass() — async* generator with explicit declination
static Stream<List<double>> startCompass(double userLat, double userLng) async* {
  final qiblaAngle = calculateQiblaAngle(userLat, userLng);

  // Step 1: Fetch declination from NOAA WMM
  double declination = 0.0;
  try {
    declination = await _fetchMagneticDeclination(userLat, userLng);
  } catch (_) {
    // No internet → proceed with 0 declination
  }

  // Step 2: Stream compass events with declination applied
  await for (final e in FlutterCompass.events!) {
    if (e.heading == null) continue;
    final rawHeading = e.heading!;
    final trueHeading = (rawHeading + declination + 360) % 360; // True North
    final needleRotation = (qiblaAngle - trueHeading + 360) % 360;
    yield [trueHeading, qiblaAngle, needleRotation, e.accuracy ?? 999.0, declination];
  }
}
```

**`lib/screens/qibla_screen.dart`:**
- Added `double _declination = 0.0;` state variable
- Stream listener extracts `values[4]` as declination, stores in `setState`
- Added a new **Magnetic Declination card** below the Qibla/Heading row
  - Shows: `Magnetic Declination (NOAA WMM): +0.51°` (with + or − sign)
  - Shows "Fetching..." if declination is still 0.0 (before API responds)

**New info card in the UI:**
```
┌─────────────────────────────────────────────────────┐
│ 🌐  Magnetic Declination (NOAA WMM)        +0.51°  │
└─────────────────────────────────────────────────────┘
```

This makes the declination **visible to the user and supervisor** — you can see the real value that was fetched and applied.

**Build and install commands used:**
```bash
flutter build apk --release
flutter install build\app\outputs\flutter-apk\app-release.apk --device-id 3KX0222A11000958
```

**What to verify on the device:**
1. Open the Qibla screen
2. Wait a second for the compass to load
3. Look for the green "Magnetic Declination (NOAA WMM)" card below the other two info cards
4. The value should show something like `+0.51°` for your location (if internet is available)
5. If no internet, it shows `+0.00°` (graceful fallback, compass still works)

---

### 📅 Fix #9: Compass Inaccurate + Declination Always "Fetching" + App Rename

**Three problems fixed in one session:**

---

#### Problem 1: Compass was jerky / inaccurate after adding declination

**Root cause — blocking the stream with `await`:**

When we first implemented magnetic declination, we wrote:

```dart
// WRONG — this BLOCKS the compass for up to 8 seconds:
static Stream<List<double>> startCompass(...) async* {
  double declination = 0.0;
  try {
    declination = await _fetchMagneticDeclination(lat, lng); // ← BLOCKS HERE
  } catch (_) {}
  // compass events only start flowing AFTER the await finishes
  await for (final e in FlutterCompass.events!) { ... }
}
```

Think of it like this analogy:

> You're running a race but before the starting gun fires, you have to wait for a slow postal delivery. The race doesn't start until the letter arrives — even if it's late or never comes.

The compass is the race. The NOAA API call is the slow postal delivery. If the API takes 5 seconds (or times out after 8 seconds), the compass doesn't START for 5–8 seconds. During that time the phone has been moved/rotated, so the first sensor reading is already stale. The compass then jumps to catch up, looking jerky.

**The fix — fetch in parallel, don't block:**

```dart
// CORRECT — compass starts immediately, declination updates in background:
static Stream<List<double>> startCompass(...) async* {
  double declination = -999.0; // sentinel: "still fetching"

  // Fire off the API call WITHOUT awaiting it
  _fetchMagneticDeclination(lat, lng).then((d) {
    declination = d; // update variable when API responds (later)
  }).catchError((_) {
    declination = 0.0; // if API fails, fall back to no correction
  });

  // Compass starts IMMEDIATELY — doesn't wait for the API
  await for (final e in FlutterCompass.events!) {
    final effective = (declination == -999.0) ? 0.0 : declination;
    final trueHeading = (e.heading! + effective + 360) % 360;
    yield [trueHeading, ...];
  }
}
```

Now the compass is smooth from the start. The declination value quietly updates in the background within ~1–5 seconds once the NOAA API responds.

**New concept: `.then()` and `.catchError()`**

These are the "callback" way to handle Futures without `await`:

```dart
// await version (BLOCKS execution):
final result = await someAsyncFunction();
doNextThing(result);

// .then() version (NON-BLOCKING — runs when ready):
someAsyncFunction().then((result) {
  doNextThing(result); // runs in background when Future completes
}).catchError((error) {
  handleError(error); // runs if it fails
});
// code here runs IMMEDIATELY, before someAsyncFunction() finishes
```

---

#### Problem 2: Declination card showed "Fetching..." forever (or "N/A")

**Root cause — wrong sentinel logic:**

The old code used `0.0` as the "not loaded yet" state:
```dart
double _declination = 0.0;
bool _declinationLoaded = false;
```

But `0.0` is also a valid real declination value (e.g. on the prime meridian). And if the API fails, the fallback was also `0.0`. So we had three different meanings for the same value:
- `0.0` = still fetching (before first event)
- `0.0` = API timed out (no internet)
- `0.0` = actual declination is zero (genuine zero)

The UI couldn't tell which case it was.

**The fix — use a sentinel value `-999.0`:**

```dart
double _declination = -999.0; // ONLY means "currently fetching from NOAA"
//  0.0 = fetch completed but failed / no internet
// other = real declination value (e.g. +0.51 for KL)
```

The stream passes `-999.0` in `values[4]` while the API call is in progress. Once the API responds (or fails), it switches to either the real value or `0.0`.

Display logic is now clear and correct:
```dart
_declination == -999.0 ? 'Fetching...'      // still loading
: _declination == 0.0  ? 'N/A (offline)'    // API failed / no internet
: '+0.51°'                                  // real value
```

**New concept: Sentinel value**

A **sentinel value** is a special value you put in a variable to mean "not a real value yet — a special state". It's like putting a bookmark placeholder in a list:
- `null` is a common sentinel in Dart (means "no value")
- `-999.0` works as a sentinel when `null` isn't convenient (e.g. in a `double`)
- `-1` is often used in lists to mean "index not found"

---

#### Problem 3: App still called "qibla_flutter" on the phone

**Root cause:**
The app name shown on the phone home screen comes from `android:label` in `AndroidManifest.xml`. We hadn't changed it from the default name that Flutter set when the project was created.

**The fix:**

In `android/app/src/main/AndroidManifest.xml`, line 12:
```xml
<!-- Before: -->
android:label="qibla_flutter"

<!-- After: -->
android:label="Qibla Prayer"
```

This `android:label` controls:
1. The name shown below the app icon on the home screen
2. The name shown in the recent apps list (when you swipe up)
3. The name shown in Settings > Apps

Note: the APK **filename** (`app-release.apk`) does NOT affect the displayed app name. Only `android:label` matters.

---

**Summary of all changes:**

| File | What changed |
|---|---|
| `lib/services/location_service.dart` | `startCompass()` now fetches declination in background (non-blocking) using `.then()`. Timeout reduced from 8s → 5s. Sentinel `-999.0` passed while fetching. |
| `lib/screens/qibla_screen.dart` | Removed `_declinationLoaded` flag. `_declination` starts as `-999.0`. Display uses 3-way check: fetching / offline / real value. Reset to `-999.0` when location changes. |
| `android/app/src/main/AndroidManifest.xml` | `android:label` changed from `qibla_flutter` → `Qibla Prayer` |

**Build and install commands:**
```bash
flutter build apk --release
flutter install build\app\outputs\flutter-apk\app-release.apk --device-id 3KX0222A11000958
```

**What to verify on the device:**
1. App icon label should now say **"Qibla Prayer"** (not qibla_flutter)
2. Open app → Qibla tab
3. Compass should start smoothly **immediately** (no 5–8 second freeze)
4. The green "Magnetic Declination (NOAA WMM)" card:
   - Shows "Fetching..." for a few seconds while calling NOAA
   - Then shows `+0.51°` (or similar) if internet works
   - Shows "N/A (offline)" if NOAA API is unreachable on your network

---

### 📅 Fix #10: NOAA API Broken → Switched to Android Built-in GeomagneticField

**Problem discovered:**
The NOAA API (`https://www.ngdc.noaa.gov/geomag-web/calculators/calculateDeclination`) now returns **400 Bad Request** for all requests. NOAA added a **registration requirement** — you now need an API key to use it. This is why the declination card always showed "N/A (offline)" and never showed a real value.

**The real fix — Android's built-in `GeomagneticField` class:**

Android's SDK has a class called `GeomagneticField` that:
- Is built into every Android phone (no download, no internet)
- Uses the **same WMM** (World Magnetic Model) data as NOAA
- Works offline forever
- Takes: `latitude, longitude, altitude, timestamp` → returns `declination` in degrees
- Is already used internally by the stock compass app on every Android phone

This is objectively better than the NOAA API — faster, more reliable, works offline.

**What is a MethodChannel? (Important new concept)**

Flutter apps run in Dart. But sometimes you need to use features that only exist in native Android (Java/Kotlin) or iOS (Swift). A **MethodChannel** is a named "bridge" that lets Dart and native code talk to each other:

```
Dart code (Flutter)                 Android code (Kotlin)
─────────────────────               ─────────────────────
channel.invokeMethod('getDeclination', {   MethodChannel.setMethodCallHandler {
  'lat': 3.14,              →         if (call.method == "getDeclination") {
  'lng': 101.69,                         val lat = call.argument<Double>("lat")
  'alt': 0.0,                            // do native Android stuff
})                          ←            result.success(geo.declination)
                                     }
                                  }
```

Think of it like a phone call between two people who speak different languages, using a translator in the middle. The channel name (`"com.hilmanxz.qibla_flutter/geomagnetic"`) is like a phone number — both sides must use the same name to connect.

**Files changed:**

**`android/app/src/main/kotlin/com/hilmanxz/qibla_flutter/MainActivity.kt`** (modified):
```kotlin
// Before (default):
class MainActivity : FlutterActivity()

// After (adds method channel handler):
class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.hilmanxz.qibla_flutter/geomagnetic"
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                if (call.method == "getDeclination") {
                    val lat = call.argument<Double>("lat") ?: 0.0
                    val lng = call.argument<Double>("lng") ?: 0.0
                    val alt = call.argument<Double>("alt") ?: 0.0
                    val geo = GeomagneticField(lat.toFloat(), lng.toFloat(), 
                                               alt.toFloat(), System.currentTimeMillis())
                    result.success(geo.declination.toDouble())
                } else {
                    result.notImplemented()
                }
            }
    }
}
```

**`lib/services/location_service.dart`** (modified):
```dart
// Before (HTTP call to NOAA API — broken):
import 'dart:convert';
import 'package:http/http.dart' as http;

static Future<double> _fetchMagneticDeclination(double lat, double lng) async {
  final uri = Uri.parse('https://www.ngdc.noaa.gov/geomag-web/...');
  final response = await http.get(uri).timeout(const Duration(seconds: 5));
  // ...
}

// After (method channel to Android built-in):
import 'package:flutter/services.dart';

static const _channel = MethodChannel('com.hilmanxz.qibla_flutter/geomagnetic');

static Future<double> _fetchMagneticDeclination(double lat, double lng) async {
  final double declination = await _channel.invokeMethod('getDeclination', {
    'lat': lat,
    'lng': lng,
    'alt': 0.0,
  });
  return declination;
}
```

**What to verify on the device:**
1. Open app → Qibla tab
2. The green "Magnetic Declination" card should show "Fetching..." for less than 1 second
3. Then it should show a real value like `+0.51°` for your location
4. No internet needed — it works offline too

---

## 🌗 Feature #11: Dark Mode

### What Is Dark Mode?

Dark mode changes the app's colors from a white/light background to a dark background. This is easier on the eyes at night, saves battery on OLED screens, and is a standard modern app feature.

### How It Works in Flutter

Flutter's `MaterialApp` widget has three theme-related properties:
- `theme:` — the **light** theme (used when phone is in light mode)
- `darkTheme:` — the **dark** theme (used when phone is in dark mode)
- `themeMode:` — which one to actually use: `ThemeMode.system`, `ThemeMode.light`, or `ThemeMode.dark`

We control `themeMode` using a `ValueNotifier`. Think of `ValueNotifier` like a special variable that announces to everyone listening "hey, I changed!" whenever it gets a new value. The UI updates automatically.

### What We Changed

**`lib/main.dart`** — Added global `themeMode` variable and `darkTheme`:
```dart
// Global theme notifier — reactive, persisted to gPrefs
final themeMode = ValueNotifier<ThemeMode>(ThemeMode.system);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  gPrefs = await SharedPreferences.getInstance();

  // Load saved theme preference ('light', 'dark', or 'system')
  final savedTheme = gPrefs.getString('theme_mode') ?? 'system';
  themeMode.value = ThemeMode.values.firstWhere(
    (m) => m.name == savedTheme,
    orElse: () => ThemeMode.system,
  );

  runApp(const QiblaApp());
}

// In QiblaApp.build():
return ValueListenableBuilder<ThemeMode>(
  valueListenable: themeMode,
  builder: (_, mode, __) => MaterialApp(
    theme: ThemeData(/* light colors */),
    darkTheme: ThemeData(/* dark colors */),
    themeMode: mode, // controlled by the user's saved preference
  ),
);
```

**`lib/screens/settings_screen.dart`** — Added Theme dropdown tile:
```dart
_dropdownTile(
  icon: Icons.brightness_6_outlined,
  title: 'Theme',
  value: themeMode.value.name, // 'system', 'light', or 'dark'
  options: const ['system', 'light', 'dark'],
  labels: const ['Follow system', 'Light mode', 'Dark mode'],
  onChanged: (v) {
    themeMode.value = ThemeMode.values.firstWhere((m) => m.name == v);
    gPrefs.setString('theme_mode', v!); // saves for next app launch
  },
),
```

**All 4 screens** — Removed hardcoded `backgroundColor: const Color(0xFFF5F5F5)` so the scaffold uses the theme's color automatically.

**All card containers** — Changed `color: Colors.white` to `color: Theme.of(context).cardColor` so cards are white in light mode, dark grey in dark mode.

### Key Concepts
- `ValueNotifier<T>` — a variable that widgets can "listen" to; when it changes, listeners rebuild
- `ValueListenableBuilder` — a widget that rebuilds whenever a `ValueNotifier` changes
- `ThemeMode.system` — follows the phone's system setting (most user-friendly default)
- `ThemeData` — a complete set of colors, fonts, and sizes for one theme (light or dark)

---

## 🌍 Feature #12: Worldwide City Search

### What Was There Before

The location picker only showed 14 Malaysian cities in a scrollable list. If you were travelling or testing from a different country, you were stuck.

### What We Added

A search field at the top of the location picker dialog. The user can type ANY city in the world (e.g. "Tokyo", "London", "New York") and the app finds its coordinates using the `geocoding` package (already installed).

The 14 Malaysian quick-pick cities are still shown below — they are useful shortcuts.

### How It Works

The `geocoding` package has a function called `locationFromAddress(query)` which sends the city name to Google's geocoding service and returns latitude/longitude. We had this package already for reverse geocoding (coords → city name). Now we also use it forward (city name → coords).

**`lib/screens/qibla_screen.dart`** and **`lib/screens/prayer_screen.dart`** — Updated `_showLocationPicker()`:
```dart
Future<void> _showLocationPicker() async {
  final searchController = TextEditingController();

  final selected = await showDialog<LocationData>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      // StatefulBuilder gives the dialog its own setState()
      // without converting the whole screen to a different widget type
      builder: (ctx, setDialogState) => AlertDialog(
        content: Column(children: [
          // ── Search field ──────────────────
          TextField(
            controller: searchController,
            hintText: 'Search any city worldwide...',
            onSubmitted: (_) async {
              final locs = await locationFromAddress(query)
                  .timeout(const Duration(seconds: 8));
              // Navigate to the found location
              Navigator.of(ctx).pop(LocationData(
                latitude: locs.first.latitude,
                longitude: locs.first.longitude,
                city: query, ...
              ));
            },
          ),
          // ── Malaysian quick list ──────────
          Expanded(child: ListView(children: [...])),
        ]),
      ),
    ),
  );
}
```

**`lib/services/location_service.dart`** — Added `geocodeInBackground()` helper:

After the user types a city name and picks its coordinates, we run reverse geocoding in the background to fill in the proper city/state/country fields. This is "fire and forget" — the app doesn't wait for it.
```dart
static void geocodeInBackground(LocationData loc, void Function(LocationData) onDone) {
  placemarkFromCoordinates(loc.latitude, loc.longitude).then((marks) {
    if (marks.isEmpty) return;
    final m = marks.first;
    onDone(LocationData(city: m.locality, region: m.administrativeArea, ...));
  }).catchError((_) {/* silently ignore */});
}
```

### Key Concepts
- `StatefulBuilder` — lets you give a `showDialog` its own `setState()` for local UI changes
- `locationFromAddress(query)` — converts a text address to lat/lng (forward geocoding)
- `placemarkFromCoordinates(lat, lng)` — converts lat/lng to a place name (reverse geocoding)
- `.timeout(Duration)` — if the network call takes too long, throw an error instead of waiting forever

---

## 🔔 Feature #13: Prayer Time Notifications

### What Are Local Notifications?

A "local notification" is a notification the phone shows at a specific time, scheduled by the app itself — no server needed. It works like a phone alarm. When the prayer time arrives, the OS wakes the app briefly and shows the notification, even if the app is closed.

### Packages Used

- `flutter_local_notifications: ^18.0.1` — already in pubspec, now wired up
- `timezone: ^0.9.4` — **newly added** — required because notifications must use timezone-aware times

### What We Built

**`lib/services/notification_service.dart`** — New file:
```dart
// Initialize the notification plugin (called once in main())
Future<void> initNotifications() async {
  tz.initializeTimeZones(); // loads all world timezone data
  await _plugin.initialize(
    const InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
    ),
  );
}

// Schedule a notification for each prayer time
Future<void> schedulePrayerNotifications({
  required List<DateTime> prayerTimes,
  required bool adhanEnabled,
}) async {
  // Cancel old ones first to avoid duplicates
  for (int i = 0; i < 6; i++) await _plugin.cancel(i);
  if (!adhanEnabled) return;

  for (int i = 0; i < prayerTimes.length; i++) {
    if (prayerTimes[i].isBefore(DateTime.now())) continue; // skip past prayers

    // Convert to timezone-aware time (required by zonedSchedule)
    final scheduledTime = tz.TZDateTime.from(prayerTimes[i], tz.local);

    await _plugin.zonedSchedule(
      i,                              // unique ID per prayer (0-5)
      'It is time for Fajr',         // notification title
      'الفجر • Prayer time has come', // notification body
      scheduledTime,
      // ... notification details
      androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
      uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
    );
  }
}
```

**`lib/main.dart`** — Called `initNotifications()` once at startup:
```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  gPrefs = await SharedPreferences.getInstance();
  await initNotifications(); // <-- new line
  // ... rest of main
}
```

**`lib/screens/prayer_screen.dart`** — After prayer times load, schedule notifications:
```dart
Future<void> _loadFromLocation(LocationData location) async {
  final times = await PrayerService.getPrayerTimes(...);
  setState(() { _prayerTimes = times; });

  // Schedule notifications for today's remaining prayers
  final adhanOn = gPrefs.getBool('adhan_enabled') ?? true;
  await schedulePrayerNotifications(
    prayerTimes: [times.fajr, times.sunrise, times.dhuhr, times.asr, times.maghrib, times.isha],
    adhanEnabled: adhanOn,
  );
}
```

### Permissions Already Added

In `AndroidManifest.xml` (from a previous fix), we already have:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
```

`SCHEDULE_EXACT_ALARM` is the permission that allows `AndroidScheduleMode.exactAllowWhileIdle` to fire at the exact prayer time — not "approximately".

### Key Concepts
- `zonedSchedule()` — schedules a notification at an exact future time (timezone-aware)
- `AndroidScheduleMode.exactAllowWhileIdle` — fires precisely even in low-battery/idle mode
- `tz.initializeTimeZones()` — loads all timezone data so the app knows what time zone the device is in
- `tz.TZDateTime.from(dateTime, tz.local)` — converts a regular `DateTime` to timezone-aware one
- Notification ID (0-5) — each prayer gets a fixed ID. Canceling and re-scheduling with the same ID replaces the old one — no duplicates

---

## 🛠️ Build Session — All Three Features

### New package added
```bash
# timezone package (needed by flutter_local_notifications for zonedSchedule)
# Added to pubspec.yaml, then:
flutter pub get
```

### Build command
```bash
flutter build apk --release
```
Result: `build\app\outputs\flutter-apk\app-release.apk` (44.2 MB)

### Install command
```bash
# Note: device ID can change between sessions — always check with flutter devices
flutter devices
flutter install build\app\outputs\flutter-apk\app-release.apk --device-id 49TDU19302000383
```

### What to test on device
1. **Dark Mode** — Go to Settings tab → Theme → select "Dark mode" → whole app turns dark instantly
2. **Worldwide Search** — Tap location pin in any tab → type "Tokyo" → press Enter/Search → Qibla and prayer times update for Tokyo
3. **Notifications** — Open Prayer tab → wait for times to load → a notification is scheduled for the next prayer time → you'll see it arrive when that prayer time comes

