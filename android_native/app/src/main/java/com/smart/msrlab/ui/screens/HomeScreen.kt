package com.smart.msrlab.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart.msrlab.SmartApplication
import com.smart.msrlab.data.model.PrayerTimesData
import com.smart.msrlab.service.LocationService
import com.smart.msrlab.service.PrayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val IslamicGreen     = Color(0xFF1B7A4B)
private val IslamicGreenDark = Color(0xFF0D5C30)

// ─────────────────────────────────────────────────────────────────────────────
// HomeScreen — central hub of SMART SandboxPlayground
//
// Mirrors Flutter home_screen.dart exactly:
//   • Islamic greeting by time of day
//   • Gregorian + Hijri date in header
//   • Prayer countdown banner (loaded from GPS in background)
//   • 8-tile feature grid
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(onNavigateTo: (String) -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Clock state ──────────────────────────────────────────────────────────
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000L)
        }
    }

    // ── Prayer times state ───────────────────────────────────────────────────
    var prayerTimes   by remember { mutableStateOf<PrayerTimesData?>(null) }
    var prayerLoading by remember { mutableStateOf(true) }
    var nextPrayerName by remember { mutableStateOf("") }
    var nextPrayerTime by remember { mutableStateOf("") }
    var timeUntilNext  by remember { mutableStateOf(0L) } // millis

    // ── Location permission launcher ─────────────────────────────────────────
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch {
                try {
                    val loc   = LocationService.getLocation(context)
                    val times = PrayerService.getPrayerTimes(loc.latitude, loc.longitude)
                    prayerTimes   = times
                    prayerLoading = false
                } catch (_: Exception) {
                    prayerLoading = false
                }
            }
        } else {
            prayerLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (LocationService.hasLocationPermission(context)) {
            try {
                val loc   = LocationService.getLocation(context)
                val times = PrayerService.getPrayerTimes(loc.latitude, loc.longitude)
                prayerTimes   = times
                prayerLoading = false
            } catch (_: Exception) {
                prayerLoading = false
            }
        } else {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
    }

    // Update next prayer countdown every second
    LaunchedEffect(now, prayerTimes) {
        prayerTimes?.let { pt ->
            val prayers = listOf(
                "Fajr"    to pt.fajr,
                "Sunrise" to pt.sunrise,
                "Dhuhr"   to pt.dhuhr,
                "Asr"     to pt.asr,
                "Maghrib" to pt.maghrib,
                "Isha"    to pt.isha,
            )
            var found = false
            for ((name, time) in prayers) {
                if (time.after(now)) {
                    nextPrayerName = name
                    nextPrayerTime = formatTime12(time)
                    timeUntilNext  = time.time - now.time
                    found = true; break
                }
            }
            if (!found) {
                // Past Isha — next is tomorrow's Fajr
                nextPrayerName = "Fajr (tomorrow)"
                val tomorrowFajr = Date(pt.fajr.time + 86_400_000L)
                nextPrayerTime = formatTime12(tomorrowFajr)
                timeUntilNext  = tomorrowFajr.time - now.time
            }
        }
    }

    // ── Derived UI values ────────────────────────────────────────────────────
    val cal      = Calendar.getInstance().apply { time = now }
    val greeting = when (cal.get(Calendar.HOUR_OF_DAY)) {
        in 0..11  -> "Selamat Pagi"
        in 12..14 -> "Selamat Tengahari"
        in 15..17 -> "Selamat Petang"
        else      -> "Selamat Malam"
    }
    val gregorianStr = formatGregorian(now)
    val (hYear, hMonth, hDay) = gregorianToHijri(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
    val hijriStr = "$hDay ${hijriMonthName(hMonth)} $hYear AH"

    // ── Layout ───────────────────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F4F0))
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(IslamicGreenDark, IslamicGreen)),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "SMART",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                        )
                        Text(
                            "SandboxPlayground",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                    IconButton(onClick = { onNavigateTo("settings") }) {
                        Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text("$greeting!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Assalamu Alaikum Warahmatullahi Wabarakatuh",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(16.dp))

                // Date card
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CalendarToday, null,
                            tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(gregorianStr, color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(hijriStr, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ── Prayer countdown banner ───────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = IslamicGreen.copy(alpha = 0.1f),
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Default.Mosque, null,
                        tint = IslamicGreen,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))

                if (prayerLoading) {
                    Text("Loading prayer times…", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                } else if (nextPrayerName.isEmpty()) {
                    Text("Tap Prayer Times for details", color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Next: $nextPrayerName  •  $nextPrayerTime",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1B4332),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "in ${formatCountdown(timeUntilNext)}",
                            color = IslamicGreen,
                            fontSize = 12.sp,
                        )
                    }
                }

                TextButton(onClick = { onNavigateTo("prayer") }) {
                    Text("View all", fontSize = 12.sp, color = IslamicGreen)
                }
            }
        }

        // ── Features section ──────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Text(
            "Features",
            modifier = Modifier.padding(horizontal = 20.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D4A35),
        )
        Spacer(Modifier.height(8.dp))

        // 2-column grid — non-scrollable (parent Column scrolls)
        val features = featureList()
        val rows = features.chunked(2)
        Column(Modifier.padding(horizontal = 16.dp)) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { feature ->
                        FeatureTile(
                            info = feature,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateTo(feature.route) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Feature tile composable ───────────────────────────────────────────────────
@Composable
private fun FeatureTile(info: FeatureInfo, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            Surface(
                color = info.color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(
                    info.icon, null,
                    tint = info.color,
                    modifier = Modifier.padding(10.dp).size(26.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                info.label,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1B2A1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                info.subtitle,
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Feature list ──────────────────────────────────────────────────────────────
private data class FeatureInfo(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val subtitle: String,
    val route: String,
)

private fun featureList() = listOf(
    FeatureInfo("Prayer Times",     Icons.Default.AccessTime,        Color(0xFF1B7A4B), "Waktu solat harian",           "prayer"),
    FeatureInfo("Qibla Compass",    Icons.Default.Explore,           Color(0xFF2E7D9A), "Arah kiblat",                  "qibla"),
    FeatureInfo("Halal Checker",    Icons.Default.Verified,          Color(0xFF2D6A4F), "Cari produk halal JAKIM",      "halal_search"),
    FeatureInfo("Scan Product",     Icons.Default.DocumentScanner,   Color(0xFF6B4C9A), "Scan label produk",            "scanner"),
    FeatureInfo("Tasbeeh",          Icons.Default.RadioButtonChecked, Color(0xFFA05C2A), "Zikir & tasbih digital",      "tasbeeh"),
    FeatureInfo("Hijri Calendar",   Icons.Default.CalendarMonth,     Color(0xFF7B6D2F), "Takwim Islam",                 "calendar"),
    FeatureInfo("Drone Scanner",    Icons.Default.Flight,            Color(0xFF1565C0), "Scan halal label via DJI Mini 2", "drone"),
    FeatureInfo("Scan Logo Halal",  Icons.Default.Verified,          Color(0xFF2E7D4F), "Kenal pasti badan pensijilan halal", "logo_scan"),
)

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun formatTime12(date: Date): String {
    val cal    = Calendar.getInstance().apply { time = date }
    val hour   = cal.get(Calendar.HOUR_OF_DAY)
    val h      = if (hour % 12 == 0) 12 else hour % 12
    val m      = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val suffix = if (hour < 12) "am" else "pm"
    return "$h:$m $suffix"
}

private fun formatGregorian(date: Date): String {
    val sdf = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH)
    return sdf.format(date)
}

private fun formatCountdown(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}

internal fun hijriMonthName(month: Int): String {
    val names = arrayOf(
        "", "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    )
    return names.getOrElse(month) { "?" }
}

// ── Pure Kotlin Hijri math (Tabular Islamic Calendar — Kuwaiti algorithm) ─────
// Identical to Flutter package:hijri logic
internal fun gregorianToHijri(gYear: Int, gMonth: Int, gDay: Int): Triple<Int, Int, Int> {
    // Convert Gregorian → Julian Day Number
    var y = gYear; var m = gMonth
    if (m <= 2) { y--; m += 12 }
    val a  = y / 100
    val b  = 2 - a + a / 4
    val jd = (365.25 * (y + 4716)).toLong() + (30.6001 * (m + 1)).toLong() + gDay + b - 1524.5

    // Convert JDN → Hijri
    val l = jd.toLong() - 1948440L + 10632L
    val n = (l - 1) / 10631L
    val l2 = l - 10631L * n + 354L
    val j  = ((10985L - l2) / 5316L) * ((50L * l2) / 17719L) +
             (l2 / 5670L) * ((43L * l2) / 15238L)
    val l3 = l2 - ((30L - j) / 15L) * ((17719L * j) / 50L) -
             (j / 16L) * ((15238L * j) / 43L) + 29L
    val i  = (20L * l3 - 29L) / 610L
    val hDay   = (l3 - (610L * i + 29L) / 20L).toInt()
    val hMonth = if (i < 11L) (i + 1).toInt() else (i - 10).toInt()
    val hYear  = (n * 30L + j + (i / 11L) - 30L).toInt()
    return Triple(hYear, hMonth, hDay)
}

internal fun daysInHijriMonth(hYear: Int, hMonth: Int): Int {
    if (hMonth == 12 && isHijriLeapYear(hYear)) return 30
    return if (hMonth % 2 == 1) 30 else 29
}

internal fun isHijriLeapYear(hYear: Int): Boolean {
    val leapYears = setOf(2, 5, 7, 10, 13, 15, 18, 21, 24, 26, 29)
    return (hYear % 30) in leapYears
}

// Convert Hijri date → Gregorian (for day-of-week calculation)
internal fun hijriToGregorian(hYear: Int, hMonth: Int, hDay: Int): Triple<Int, Int, Int> {
    val n = hDay + ((hMonth - 1) * 29.5001).toLong() +
            (hYear - 1) * 354L +
            ((11L * hYear + 3L) / 30L) + 1948440L - 385L
    // n is Julian Day Number
    val l = n + 68569L
    val nn = (4L * l) / 146097L
    val l2 = l - (146097L * nn + 3L) / 4L
    val i  = (4000L * (l2 + 1L)) / 1461001L
    val l3 = l2 - (1461L * i) / 4L + 31L
    val j  = (80L * l3) / 2447L
    val gDay   = (l3 - (2447L * j) / 80L).toInt()
    val l4 = j / 11L
    val gMonth = (j + 2L - 12L * l4).toInt()
    val gYear  = (100L * (nn - 49L) + i + l4).toInt()
    return Triple(gYear, gMonth, gDay)
}
