package com.smart.msrlab.service

import com.smart.msrlab.data.model.PrayerTimesData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
import java.util.Date
import kotlin.math.*

/**
 * PrayerService — fetch or calculate prayer times
 *
 * Strategy mirrors Flutter prayer_service.dart exactly:
 *   1. Try api.waktusolat.app GPS endpoint (Malaysia + worldwide GPS detection)
 *   2. Fallback: calculate offline using solar math (Muslim World League method)
 */
object PrayerService {

    private const val WAKTU_SOLAT_API = "https://api.waktusolat.app"
    private val D2R = Math.PI / 180.0

    // ── OPTION A: WaktuSolat API ────────────────────────────────────────────────
    // Endpoint: GET /v2/solat/gps/{lat}/{lng}?year=YYYY&month=M
    // API auto-detects JAKIM zone from GPS coordinates.
    // Times are returned as Unix timestamps (seconds).
    private suspend fun fetchFromApi(lat: Double, lng: Double): PrayerTimesData? {
        return withTimeoutOrNull(8_000L) {
            try {
                withContext(Dispatchers.IO) {
                    val now   = Calendar.getInstance()
                    val year  = now.get(Calendar.YEAR)
                    val month = now.get(Calendar.MONTH) + 1
                    val day   = now.get(Calendar.DAY_OF_MONTH)

                    val urlStr = "$WAKTU_SOLAT_API/v2/solat/gps/$lat/$lng?year=$year&month=$month"
                    val json   = URL(urlStr).readText()
                    val root   = JSONObject(json)
                    val prayers = root.optJSONArray("prayers") ?: return@withContext null
                    val zone    = root.optString("zone", null).takeIf { it.isNotBlank() }

                    // Find today's entry
                    var todayEntry: JSONObject? = null
                    for (i in 0 until prayers.length()) {
                        val p = prayers.getJSONObject(i)
                        if (p.optInt("day") == day) { todayEntry = p; break }
                    }
                    if (todayEntry == null) return@withContext null

                    fun fromUnix(ts: Long) = Date(ts * 1000L)

                    PrayerTimesData(
                        fajr    = fromUnix(todayEntry.getLong("fajr")),
                        sunrise = fromUnix(todayEntry.getLong("syuruk")), // Malay: syuruk = sunrise
                        dhuhr   = fromUnix(todayEntry.getLong("dhuhr")),
                        asr     = fromUnix(todayEntry.getLong("asr")),
                        maghrib = fromUnix(todayEntry.getLong("maghrib")),
                        isha    = fromUnix(todayEntry.getLong("isha")),
                        source  = "WaktuSolat",
                        zone    = zone,
                        hijri   = todayEntry.optString("hijri", null).takeIf { it?.isNotBlank() == true },
                    )
                }
            } catch (_: Exception) { null }
        }
    }

    // ── OPTION B: Calculate offline (Muslim World League method) ────────────────
    // Direct Kotlin port of Flutter _calculateOffline().
    // Identical algorithm — same Julian Day, declination, solar noon, hour angle formulas.
    private fun calculateOffline(lat: Double, lng: Double): PrayerTimesData {
        val now   = Calendar.getInstance()
        val year  = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day   = now.get(Calendar.DAY_OF_MONTH)

        // Julian Day Number (JDN)
        var y = year; var m = month
        if (m <= 2) { y--; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        val jd = (365.25 * (y + 4716)).toLong() +
                 (30.6001 * (m + 1)).toLong() +
                 day + b - 1524.5

        val dd = jd - 2451545.0 // days from J2000.0

        // Sun's mean anomaly + mean longitude (degrees)
        val g = (357.529 + 0.98560028 * dd) % 360
        val q = (280.459 + 0.98564736 * dd) % 360
        // Ecliptic longitude
        val l = (q + 1.915 * sin(g * D2R) + 0.020 * sin(2 * g * D2R)) % 360
        // Obliquity of ecliptic
        val e    = 23.439 - 0.00000036 * dd
        // Right ascension and declination
        val ra   = atan2(cos(e * D2R) * sin(l * D2R), cos(l * D2R)) / D2R
        val decl = asin(sin(e * D2R) * sin(l * D2R)) // radians
        // Equation of time (degrees)
        val eqT = q - ra
        // Solar noon in UTC hours
        val solarNoon = 12 - lng / 15 - eqT / 15

        val latR = lat * D2R

        fun hourAngle(angleDeg: Double): Double {
            val cosHA = (sin(angleDeg * D2R) - sin(latR) * sin(decl)) /
                        (cos(latR) * cos(decl))
            return acos(cosHA.coerceIn(-1.0, 1.0)) / D2R / 15 // hours
        }

        val ha       = hourAngle(-0.833)
        val sunriseH = solarNoon - ha
        val sunsetH  = solarNoon + ha

        // Asr time — Shafi method: shadow = 1× object height
        val noonAlt  = 90.0 - abs(lat) - abs(decl / D2R)
        val asrAngle = atan(1.0 / (1.0 + tan(abs(noonAlt) * D2R)))
        val cosAsrHA = (sin(asrAngle) - sin(latR) * sin(decl)) / (cos(latR) * cos(decl))
        val asrHa    = acos(cosAsrHA.coerceIn(-1.0, 1.0)) / D2R / 15
        val asrH     = solarNoon + asrHa

        // Fajr/Isha — Muslim World League depression angles
        val fajrH = solarNoon - hourAngle(-18.0)
        val ishaH = solarNoon + hourAngle(-17.0)

        // Convert UTC decimal hours → local Date
        val offsetH = now.timeZone.getOffset(now.timeInMillis) / 3_600_000.0

        fun toDate(utcH: Double): Date {
            val local = utcH + offsetH
            val h     = ((local.toInt() % 24) + 24) % 24
            val min   = ((local - kotlin.math.floor(local)) * 60).roundToInt().coerceIn(0, 59)
            return Calendar.getInstance().apply {
                set(Calendar.YEAR,         year)
                set(Calendar.MONTH,        month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY,  h)
                set(Calendar.MINUTE,       min)
                set(Calendar.SECOND,       0)
                set(Calendar.MILLISECOND,  0)
            }.time
        }

        return PrayerTimesData(
            fajr    = toDate(fajrH),
            sunrise = toDate(sunriseH),
            dhuhr   = toDate(solarNoon),
            asr     = toDate(asrH),
            maghrib = toDate(sunsetH),
            isha    = toDate(ishaH),
            source  = "Calculated",
        )
    }

    // ── Main entry point ───────────────────────────────────────────────────────
    // Always try API first (it accepts GPS globally, auto-detects zone).
    // Falls back to offline solar math if API unavailable.
    suspend fun getPrayerTimes(lat: Double, lng: Double): PrayerTimesData {
        return fetchFromApi(lat, lng) ?: calculateOffline(lat, lng)
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
