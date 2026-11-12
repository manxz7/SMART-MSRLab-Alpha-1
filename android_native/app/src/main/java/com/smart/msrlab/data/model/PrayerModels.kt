package com.smart.msrlab.data.model

/**
 * PrayerTimesData — 6 waktu solat untuk hari ini + metadata
 *
 * Sepadan dengan Flutter prayer_service.dart:
 *   class PrayerTimesData { fajr, sunrise, dhuhr, asr, maghrib, isha, source, zone, hijri }
 */
data class PrayerTimesData(
    val fajr: java.util.Date,
    val sunrise: java.util.Date,
    val dhuhr: java.util.Date,
    val asr: java.util.Date,
    val maghrib: java.util.Date,
    val isha: java.util.Date,
    /** "WaktuSolat" (dari API JAKIM) atau "Calculated" (solar math fallback) */
    val source: String,
    /** Zone JAKIM contoh "WLY02" — hanya ada kalau source = WaktuSolat */
    val zone: String? = null,
    /** Tarikh Hijri dalam format "1447-10-06" — dari API WaktuSolat */
    val hijri: String? = null,
) {
    /** Senarai semua waktu solat dalam susunan (untuk iteration) */
    val asList: List<Pair<String, java.util.Date>> get() = listOf(
        "Fajr"    to fajr,
        "Sunrise" to sunrise,
        "Dhuhr"   to dhuhr,
        "Asr"     to asr,
        "Maghrib" to maghrib,
        "Isha"    to isha,
    )
}

/**
 * LocationData — koordinat GPS + nama bandar untuk prayer/qibla screens
 *
 * Sepadan dengan Flutter location_service.dart:
 *   class LocationData { latitude, longitude, city, region, country }
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val city: String = "",
    val region: String = "",
    val country: String = "",
) {
    /** Nama papar: "Shah Alam, Selangor" atau kosong kalau belum geocode */
    val displayName: String get() = when {
        city.isNotBlank() && region.isNotBlank() -> "$city, $region"
        city.isNotBlank() -> city
        region.isNotBlank() -> region
        else -> ""
    }

    val isMalaysia: Boolean get() = country.equals("MY", ignoreCase = true)
}
