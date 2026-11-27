import java.util.Properties

// DJI App Key dibaca dari local.properties (fail ini TIDAK masuk ke GitHub).
// Tambah satu baris dalam android_native/local.properties:
//   DJI_API_KEY=key_dari_developer.dji.com
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val djiApiKey: String = localProps.getProperty("DJI_API_KEY") ?: ""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
}

android {
    namespace = "com.smart.msrlab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smart.msrlab"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // DJI SDK melebihi had 64K method — wajib
        multiDexEnabled = true

        // Dimasukkan ke AndroidManifest sebagai ${DJI_API_KEY}
        manifestPlaceholders["DJI_API_KEY"] = djiApiKey

        // DJI SDK: restrict to ARM architectures supported by DJI
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // DJI SDK ada fail .jet/.ogg yang tak boleh di-compress
    androidResources {
        noCompress += listOf(".tflite", ".flite", ".wav", ".jet", ".ogg", ".mp4", ".dji")
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/rxjava.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "assets/location_map_gps_locked.png",
                "assets/location_map_gps_3d.png"
            )
        }
        jniLibs {
            // DJI SDK requires legacy packaging for JNI libs
            useLegacyPackaging = true
            // Fail .so DJI ada section ELF tidak standard — JANGAN strip, nanti crash
            keepDebugSymbols += setOf(
                "*/*/libdjivideo.so",
                "*/*/libSDKRelativeJNI.so",
                "*/*/libFlyForbid.so",
                "*/*/libduml_vision_bokeh.so",
                "*/*/libyuv2.so",
                "*/*/libGroudStation.so",
                "*/*/libFRCorkscrew.so",
                "*/*/libUpgradeVerify.so",
                "*/*/libFR.so",
                "*/*/libDJIFlySafeCore.so",
                "*/*/libdjifs_jni.so",
                "*/*/libsfjni.so",
                "*/*/libDJICommonJNI.so",
                "*/*/libDJICSDKCommon.so",
                "*/*/libDJIUpgradeCore.so",
                "*/*/libDJIUpgradeJNI.so",
                "*/*/libDJIWaypointV2Core.so",
                "*/*/libAMapSDK_MAP_v6_9_2.so",
                "*/*/libDJIMOP.so",
                "*/*/libDJISDKLOGJNI.so"
            )
        }
    }
}

dependencies {
    // ── AndroidX Core & Lifecycle ─────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ── Jetpack Compose + Material3 ───────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // ── Networking (Retrofit + OkHttp) ────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Image Loading (Coil) ──────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── Location (Play Services FusedLocation) ────────────────────────────────
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Persistence (DataStore Preferences) ───────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── CameraX (for photo/video capture outside drone) ────────────────────────
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    // ── DJI Mobile SDK v4.18 (sama seperti app Flutter) ───────────────────────
    implementation("com.dji:dji-sdk:4.18") {
        exclude(group = "com.dji", module = "library-anti-distortion")
    }
    // "provided" = DJI muat sendiri melalui Helper.install(), tak masuk dalam APK
    compileOnly("com.dji:dji-sdk-provided:4.18")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // diperlukan oleh layout DJI SDK

    // ── Notifications ─────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1") // NotificationCompat

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
