package com.smart.msrlab.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.smart.msrlab.data.model.LocationData
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * LocationService — GPS permission check + get current location
 *
 * Mirrors Flutter location_service.dart:
 *   LocationService.getLocation() → LocationData
 *   LocationService.geocodeInBackground() → onResult(updated)
 *   LocationService.malaysianCities → Map<String, LocationData>
 */
object LocationService {

    /** List of Malaysian cities for manual location picker — exact same as Flutter */
    val malaysianCities: Map<String, LocationData> = linkedMapOf(
        "Kuala Lumpur / Putrajaya" to LocationData(3.1412,  101.6865, "Kuala Lumpur",      "Wilayah Persekutuan", "MY"),
        "Selangor (Shah Alam)"     to LocationData(3.0738,  101.5183, "Shah Alam",          "Selangor",            "MY"),
        "Johor Bahru"              to LocationData(1.4927,  103.7414, "Johor Bahru",        "Johor",               "MY"),
        "Penang (Georgetown)"      to LocationData(5.4164,  100.3327, "Georgetown",         "Pulau Pinang",        "MY"),
        "Ipoh (Perak)"             to LocationData(4.5975,  101.0901, "Ipoh",               "Perak",               "MY"),
        "Kota Bharu (Kelantan)"    to LocationData(6.1254,  102.2386, "Kota Bharu",         "Kelantan",            "MY"),
        "Kuching (Sarawak)"        to LocationData(1.5533,  110.3592, "Kuching",            "Sarawak",             "MY"),
        "Kota Kinabalu (Sabah)"    to LocationData(5.9804,  116.0735, "Kota Kinabalu",      "Sabah",               "MY"),
        "Alor Setar (Kedah)"       to LocationData(6.1248,  100.3678, "Alor Setar",         "Kedah",               "MY"),
        "Seremban (N. Sembilan)"   to LocationData(2.7297,  101.9381, "Seremban",           "Negeri Sembilan",     "MY"),
        "Melaka"                   to LocationData(2.1896,  102.2501, "Melaka",             "Melaka",              "MY"),
        "Kuantan (Pahang)"         to LocationData(3.8077,  103.3260, "Kuantan",            "Pahang",              "MY"),
        "Kuala Terengganu"         to LocationData(5.3302,  103.1408, "Kuala Terengganu",   "Terengganu",          "MY"),
        "Kangar (Perlis)"          to LocationData(6.4414,  100.1987, "Kangar",             "Perlis",              "MY"),
    )

    /** Returns true if FINE or COARSE location permission is granted */
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Get current location using FusedLocationProviderClient.
     * Tries lastLocation first (instant), then falls back to getCurrentLocation (15s timeout).
     * Throws Exception if permission denied or GPS times out.
     *
     * Mirrors Flutter:
     *   Geolocator.getCurrentPosition(timeLimit: 15s) with lastKnown fallback
     */
    @Throws(Exception::class)
    suspend fun getLocation(context: Context): LocationData {
        if (!hasLocationPermission(context)) {
            throw Exception("Location permission denied.")
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 1. Try last known position (fast — no wait needed)
        try {
            @Suppress("MissingPermission")
            val lastKnown = fusedClient.lastLocation.await()
            if (lastKnown != null) {
                return LocationData(
                    latitude  = lastKnown.latitude,
                    longitude = lastKnown.longitude,
                )
            }
        } catch (_: Exception) {}

        // 2. Request fresh location with balanced power (faster indoors)
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(30_000L) // accept 30s-old fix
                .setDurationMillis(15_000L)     // give up after 15s
                .build()
            @Suppress("MissingPermission")
            val location = fusedClient.getCurrentLocation(request, null).await()
                ?: throw Exception("GPS timed out. Are you indoors? Try \"Choose location manually\" below.")
            return LocationData(latitude = location.latitude, longitude = location.longitude)
        } catch (e: Exception) {
            throw Exception("GPS timed out. Are you indoors? Try \"Choose location manually\" below.")
        }
    }

    /**
     * Reverse-geocode coordinates → city, region, country.
     * Returns original location if geocoding fails.
     *
     * Mirrors Flutter:
     *   LocationService.geocodeInBackground(location, onResult)
     */
    suspend fun geocode(context: Context, location: LocationData): LocationData {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                location.copy(
                    city    = addr.locality ?: addr.subAdminArea ?: "",
                    region  = addr.adminArea ?: "",
                    country = addr.countryCode ?: "",
                )
            } else location
        } catch (_: Exception) {
            location
        }
    }
}
