# ─────────────────────────────────────────────────────────────────────────────
# ProGuard / R8 rules for SMART Halal App
# ─────────────────────────────────────────────────────────────────────────────

# Flutter references Google Play Core (split APK delivery) internally tapi
# kita tak guna feature tu — ignore warning supaya R8 tak fail
-dontwarn com.google.android.play.core.**

# Keep our app classes — R8 tidak boleh rename atau buang
-keep class com.halalcheck.halal_app.** { *; }

# Keep Flutter embedding
-keep class io.flutter.** { *; }
-keep class io.flutter.plugin.common.MethodChannel { *; }
-keep class io.flutter.plugin.common.MethodChannel$MethodCallHandler { *; }
-keep class io.flutter.plugin.common.MethodCall { *; }
-keep class io.flutter.plugin.common.EventChannel { *; }
-keep class io.flutter.plugin.common.EventChannel$StreamHandler { *; }
-keep class io.flutter.plugin.common.EventChannel$EventSink { *; }
-keep class io.flutter.plugin.platform.** { *; }

# Keep Android sensor classes (untuk Qibla compass)
-keep class android.hardware.GeomagneticField { *; }
-keep class android.hardware.Sensor { *; }
-keep class android.hardware.SensorEvent { *; }
-keep class android.hardware.SensorEventListener { *; }
-keep class android.hardware.SensorManager { *; }

# Keep sensors_plus plugin
-keep class dev.fluttercommunity.plus.sensors.** { *; }

# ── DJI Mobile SDK v4 ────────────────────────────────────────────────────────
# DJI SDK heavily uses reflection — R8 MESTI jangan rename atau buang mana-mana class DJI
-keep class dji.** { *; }
-keep class com.dji.** { *; }
-keep class com.cySdkyc.** { *; }
-keep class com.secneo.** { *; }
-keepclassmembers class dji.** { *; }
-keepclassmembers class com.dji.** { *; }
-dontwarn dji.**
-dontwarn com.dji.**
-dontwarn com.cySdkyc.**
-dontwarn com.secneo.**

# DJI codec — diperlukan untuk live video feed
-keep class dji.sdk.codec.** { *; }
-keep class dji.sdk.camera.VideoFeeder { *; }
-keep class dji.sdk.camera.VideoFeeder$VideoDataListener { *; }
-keep class dji.sdk.camera.VideoFeeder$PhysicalSource { *; }

# DJI SDK manager
-keep class dji.sdk.sdkmanager.DJISDKManager { *; }
-keep class dji.sdk.sdkmanager.DJISDKManager$SDKManagerCallback { *; }
-keep class dji.sdk.base.BaseProduct { *; }
-keep class dji.sdk.products.Aircraft { *; }

# ─────────────────────────────────────────────────────────────────────────────
