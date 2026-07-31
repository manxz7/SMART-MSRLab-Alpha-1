plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.halalcheck.halal_app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "com.halalcheck.halal_app"
        minSdk = flutter.minSdkVersion          // DJI SDK requires minimum API 23 (Android 6.0)
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        multiDexEnabled = true  // DJI SDK melebihi 64K method limit — wajib enable
        ndk {
            // Sama seperti DJI sample code rasmi
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // DJI SDK ada fail .jet/.ogg yang tak boleh di-compress
    // AGP 8.x guna androidResources, bukan aaptOptions
    androidResources {
        noCompress += listOf(".tflite", ".flite", ".wav", ".jet", ".ogg", ".mp4", ".dji")
    }

    packaging {
        jniLibs {
            // DJI SDK .so files mempunyai section ELF yang tidak standard
            // MESTI jangan di-strip atau akan crash masa runtime
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
        resources {
            excludes += listOf(
                "META-INF/rxjava.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "assets/location_map_gps_locked.png",
                "assets/location_map_gps_3d.png"
            )
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // DJI Mobile SDK v4.18 — versi yang digunakan dalam sample code rasmi DJI
    // exclude library-anti-distortion untuk kurangkan saiz APK
    implementation("com.dji:dji-sdk:4.18") {
        exclude(group = "com.dji", module = "library-anti-distortion")
    }
    // "provided" = DJI load sendiri via Helper.install(), tak masuk dalam APK
    compileOnly("com.dji:dji-sdk-provided:4.18")

    // Required by DJI SDK
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}

flutter {
    source = "../.."
}
