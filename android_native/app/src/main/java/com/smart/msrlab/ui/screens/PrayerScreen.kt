package com.smart.msrlab.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart.msrlab.SmartApplication
import com.smart.msrlab.data.model.LocationData
import com.smart.msrlab.data.model.PrayerTimesData
import com.smart.msrlab.service.LocationService
import com.smart.msrlab.service.NotificationService
import com.smart.msrlab.service.PrayerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val PrayerGreen = Color(0xFF1A7A4A)

data class PrayerEntry(val name: String, val time: Date, val icon: ImageVector)

// ─────────────────────────────────────────────────────────────────────────────
// PrayerScreen — shows all 6 prayer times with countdown to next prayer
//
// Mirrors Flutter prayer_screen.dart 1:1
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = SmartApplication.prefs

    var location    by remember { mutableStateOf<LocationData?>(null) }
    var prayerTimes by remember { mutableStateOf<PrayerTimesData?>(null) }
    var isLoading   by remember { mutableStateOf(true) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var now         by remember { mutableStateOf(Date()) }
    var showLocationPicker by remember { mutableStateOf(false) }

    // Clock — tick every second
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1_000L) }
    }

    suspend fun loadFromLocation(loc: LocationData) {
        try {
            val times = PrayerService.getPrayerTimes(loc.latitude, loc.longitude)
            location    = loc
            prayerTimes = times
            isLoading   = false
            errorMsg    = null
            val adhanOn = prefs.getAdhanBlocking()
            NotificationService.schedulePrayerNotifications(
                context, times.asList.map { it.second }, adhanOn)
            // Geocode in background
            val geocoded = LocationService.geocode(context, loc)
            if (geocoded != loc) location = geocoded
        } catch (e: Exception) {
            errorMsg  = e.message ?: "Gagal memuatkan waktu solat"
            isLoading = false
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch {
                try { loadFromLocation(LocationService.getLocation(context)) }
                catch (e: Exception) { errorMsg = e.message; isLoading = false }
            }
        } else {
            errorMsg  = "Location permission denied. Choose a city manually."
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (LocationService.hasLocationPermission(context)) {
            try { loadFromLocation(LocationService.getLocation(context)) }
            catch (e: Exception) { errorMsg = e.message; isLoading = false }
        } else {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // ── Location picker dialog ────────────────────────────────────────────────
    if (showLocationPicker) {
        AlertDialog(
            onDismissRequest = { showLocationPicker = false },
            title = { Text("Pilih Bandar", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(LocationService.malaysianCities.entries.toList()) { (key, loc) ->
                        ListItem(
                            headlineContent = { Text(key) },
                            modifier = Modifier.clickable {
                                showLocationPicker = false
                                isLoading = true; errorMsg = null
                                scope.launch { loadFromLocation(loc) }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationPicker = false }) { Text("Batal") }
            },
        )
    }

    // ── Build prayer entries ──────────────────────────────────────────────────
    val entries = prayerTimes?.let { pt ->
        listOf(
            PrayerEntry("Fajr",    pt.fajr,    Icons.Default.Brightness3),
            PrayerEntry("Sunrise", pt.sunrise, Icons.Default.WbSunny),
            PrayerEntry("Dhuhr",   pt.dhuhr,   Icons.Default.WbSunny),
            PrayerEntry("Asr",     pt.asr,     Icons.Default.Cloud),
            PrayerEntry("Maghrib", pt.maghrib, Icons.Default.WbTwilight),
            PrayerEntry("Isha",    pt.isha,    Icons.Default.NightsStay),
        )
    }

    // Determine current + next prayer
    val (currentIdx, nextIdx) = remember(entries, now) {
        if (entries == null) return@remember Pair(-1, 0)
        val times = entries.map { it.time }
        var cur = -1; var nxt = 0
        for (i in 0 until times.size - 1) {
            if (now.after(times[i]) && now.before(times[i + 1])) {
                cur = i; nxt = i + 1; break
            }
        }
        if (now.after(times.last())) { cur = times.size - 1; nxt = 0 }
        Pair(cur, nxt)
    }

    val nextTime: Date? = prayerTimes?.let { pt ->
        if (nextIdx == 0) Date(pt.fajr.time + 86_400_000L) else entries?.get(nextIdx)?.time
    }

    // ── Hijri date from API or computed ──────────────────────────────────────
    val hijriDisplay = remember(prayerTimes, now) {
        prayerTimes?.hijri?.let { hijri ->
            val parts = hijri.split("-")
            if (parts.size == 3) {
                val monthNames = arrayOf("","Muharram","Safar","Rabi Al-Awwal","Rabi Al-Akhir",
                    "Jumada Al-Ula","Jumada Al-Akhirah","Rajab","Sha'ban",
                    "Ramadan","Shawwal","Dhul Qa'dah","Dhul Hijjah")
                val m = parts[1].toIntOrNull() ?: 0
                "${parts[2].toIntOrNull() ?: ""} ${monthNames.getOrElse(m) { parts[1] }} ${parts[0]}"
            } else hijri
        } ?: run {
            val c = Calendar.getInstance()
            val (hy, hm, hd) = gregorianToHijri(c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
            "$hd ${hijriMonthName(hm)} $hy AH"
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(PrayerGreen, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                        Column {
                            Text("Prayer Times", color = Color.White,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(location?.displayName?.takeIf { it.isNotBlank() } ?: "Detecting...",
                                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        prayerTimes?.let { pt ->
                            Surface(color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)) {
                                Text(
                                    if (pt.source == "WaktuSolat") "🏛 ${pt.zone ?: "MY"}" else "🌍 Calculated",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = Color.White, fontSize = 11.sp,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { showLocationPicker = true }) {
                            Icon(Icons.Default.EditLocation, null, tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(formatFullDate(now), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Text(hijriDisplay, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PrayerGreen)
                            Spacer(Modifier.height(16.dp))
                            Text("Loading prayer times...", color = Color.Gray)
                        }
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.ErrorOutline, null,
                                tint = Color.Red, modifier = Modifier.size(64.dp))
                            Text(errorMsg!!, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Button(onClick = {
                                isLoading = true; errorMsg = null
                                scope.launch {
                                    try { loadFromLocation(LocationService.getLocation(context)) }
                                    catch (e: Exception) { errorMsg = e.message; isLoading = false }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = PrayerGreen)) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Try GPS Again")
                            }
                            OutlinedButton(
                                onClick = { showLocationPicker = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrayerGreen),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrayerGreen),
                            ) {
                                Icon(Icons.Default.List, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Choose location manually")
                            }
                        }
                    }
                }
                else -> {
                    // Next prayer countdown card
                    if (entries != null && nextTime != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(4.dp),
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    color = PrayerGreen.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Default.AccessTime, null,
                                        tint = PrayerGreen,
                                        modifier = Modifier.padding(10.dp).size(24.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Next Prayer", color = Color.Gray, fontSize = 12.sp)
                                    Text(
                                        if (nextIdx == 0) "Fajr (tomorrow)" else entries[nextIdx].name,
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    formatCountdown(nextTime.time - now.time),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrayerGreen,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    // Prayer list
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        entries?.let { ents ->
                            items(ents.indices.toList()) { i ->
                                val isCurrent = (i == currentIdx)
                                val isNext    = (i == nextIdx)
                                PrayerCard(
                                    entry = ents[i],
                                    isCurrent = isCurrent,
                                    isNext    = isNext,
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerCard(entry: PrayerEntry, isCurrent: Boolean, isNext: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isCurrent) PrayerGreen else Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp,
        border = if (isNext && !isCurrent)
            androidx.compose.foundation.BorderStroke(1.5.dp, PrayerGreen) else null,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(entry.icon, null,
                tint = if (isCurrent) Color.White else PrayerGreen,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(entry.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) Color.White else Color.Black.copy(alpha = 0.87f))
            Spacer(Modifier.weight(1f))
            if (isCurrent) {
                Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(10.dp)) {
                    Text("NOW",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            } else if (isNext) {
                Surface(color = PrayerGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
                    Text("NEXT",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = PrayerGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                formatTime12h(entry.time),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) Color.White else Color.Black.copy(alpha = 0.87f),
            )
        }
    }
}

private fun formatTime12h(date: Date): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.ENGLISH)
    return sdf.format(date)
}

private fun formatFullDate(date: Date): String {
    val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
    return sdf.format(date)
}

private fun formatCountdown(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
