pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // DJI Mobile SDK v4 (sama seperti app Flutter)
        maven { url = uri("https://artifactory.djiits.com/artifactory/ams-android-sdk/") }
    }
}

rootProject.name = "SMART SandboxPlayground Native"
include(":app")