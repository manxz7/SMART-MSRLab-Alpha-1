package com.smart.msrlab.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart.msrlab.data.model.LocationData
import com.smart.msrlab.service.CompassReading
import com.smart.msrlab.service.CompassService
import com.smart.msrlab.service.LocationService
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.*

private val QiblaGreen = Color(0xFF1A7A4A)

// ─────────────────────────────────────────────────────────────────────────────
// QiblaScreen — animated Qibla compass
//
// Mirrors Flutter qibla_screen.dart 1:1:
//   • Animated compass dial + arrow (Canvas)
//   • Smooth rotation with shortest-path logic
//   • Accuracy badge
//   • Magnetic declination card
//   • Location picker dialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var location         by remember { mutableStateOf<LocationData?>(null) }
    var reading          by remember { mutableStateOf<CompassReading?>(null) }
    var declination      by remember { mutableStateOf<Float?>(null) }
    var isLoading        by remember { mutableStateOf(true) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }

    // Animated rotation state
    var prevRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation = remember { Animatable(0f) }

    // Start compass for a given location
    fun startCompass(loc: LocationData) {
        scope.launch {
            // Fetch declination in background
            declination = CompassService.getMagneticDeclination(loc.latitude, loc.longitude)

            CompassService.startCompass(context, loc.latitude, loc.longitude)
                .catch { e -> errorMsg = e.message }
                .collect { r ->
                    reading = r
                    // Smooth rotation — shortest-path delta
                    val prevNorm = prevRotation % 360f
                    var delta = r.needleRotation.toFloat() - prevNorm
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    val target = prevRotation + delta
                    animatedRotation.animateTo(
                        target,
                        animationSpec = tween(durationMillis = 300, easing = EaseOut),
                    )
                    prevRotation = target
                }
        }
    }

    fun loadLocation() {
        isLoading = true; errorMsg = null
        scope.launch {
            try {
                val loc = LocationService.getLocation(context)
                location = loc
                isLoading = false
                startCompass(loc)
                // Geocode in background
                val geocoded = LocationService.geocode(context, loc)
                if (geocoded != loc) location = geocoded
            } catch (e: Exception) {
                errorMsg  = e.message
                isLoading = false
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) loadLocation()
        else { errorMsg = "Location permission denied."; isLoading = false }
    }

    LaunchedEffect(Unit) {
        if (LocationService.hasLocationPermission(context)) loadLocation()
        else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // Location picker dialog
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
                                location = loc
                                isLoading = false
                                startCompass(loc)
                                scope.launch {
                                    val geocoded = LocationService.geocode(context, loc)
                                    if (geocoded != loc) location = geocoded
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLocationPicker = false }) { Text("Batal") } },
        )
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(QiblaGreen, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                        }
                        Column {
                            Text("Qibla Compass", color = Color.White,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null,
                                    tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    location?.displayName?.takeIf { it.isNotBlank() } ?: "Detecting location...",
                                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLocationPicker = true }) {
                        Icon(Icons.Default.EditLocation, null, tint = Color.White)
                    }
                }
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = QiblaGreen)
                            Spacer(Modifier.height(16.dp))
                            Text("Getting your location...", color = Color.Gray)
                        }
                    }
                }
                errorMsg != null -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.LocationOff, null,
                                tint = Color.Red, modifier = Modifier.size(64.dp))
                            Text(errorMsg!!, color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Button(onClick = { loadLocation() },
                                colors = ButtonDefaults.buttonColors(containerColor = QiblaGreen)) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Try GPS Again")
                            }
                            OutlinedButton(
                                onClick = { showLocationPicker = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = QiblaGreen),
                                border = androidx.compose.foundation.BorderStroke(1.dp, QiblaGreen),
                            ) {
                                Icon(Icons.Default.List, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Choose location manually")
                            }
                            Card(colors = CardDefaults.cardColors(
                                containerColor = QiblaGreen.copy(alpha = 0.07f))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.TipsAndUpdates, null,
                                        tint = QiblaGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "GPS is slow indoors. Use \"Choose location manually\" " +
                                        "— the compass still works perfectly!",
                                        fontSize = 12.sp, color = QiblaGreen,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    val r = reading
                    val qibla = r?.qiblaAngle ?: location?.let {
                        CompassService.calculateQiblaAngle(it.latitude, it.longitude)
                    } ?: 0.0

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Calibration banner
                        val acc = r?.accuracyDegrees ?: 999.0
                        if (acc > 45.0) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A))) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = Color.Red)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Compass needs calibration",
                                            fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 13.sp)
                                        Text("Wave your phone in a figure-8 pattern.",
                                            color = Color.Red, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Compass dial
                        val textMeasurer = rememberTextMeasurer()
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(280.dp)) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = size.width / 2f - 10f

                                // Outer ring
                                drawCircle(color = Color.White, radius = radius, center = Offset(cx, cy))
                                drawCircle(color = QiblaGreen.copy(alpha = 0.3f), radius = radius,
                                    center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                                // Tick marks
                                for (i in 0 until 36) {
                                    val angle = Math.toRadians(i * 10.0)
                                    val isMajor = i % 9 == 0
                                    val inner = radius - (if (isMajor) 18f else 10f)
                                    val outer = radius - 4f
                                    val x1 = cx + inner * sin(angle).toFloat()
                                    val y1 = cy - inner * cos(angle).toFloat()
                                    val x2 = cx + outer * sin(angle).toFloat()
                                    val y2 = cy - outer * cos(angle).toFloat()
                                    drawLine(Color.Gray.copy(alpha = 0.4f),
                                        Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.5f)
                                }

                                // N/S/E/W labels
                                val labels = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
                                val labelRadius = radius - 30f
                                labels.forEach { (label, deg) ->
                                    val angle = Math.toRadians(deg)
                                    val x = cx + labelRadius * sin(angle).toFloat()
                                    val y = cy - labelRadius * cos(angle).toFloat()
                                    val textResult = textMeasurer.measure(
                                        label,
                                        TextStyle(
                                            color = if (label == "N") QiblaGreen else Color.Gray,
                                            fontSize = if (label == "N") 16.sp else 13.sp,
                                            fontWeight = if (label == "N") FontWeight.Bold else FontWeight.Normal,
                                        )
                                    )
                                    drawText(textResult,
                                        topLeft = Offset(x - textResult.size.width / 2f,
                                            y - textResult.size.height / 2f))
                                }

                                // Center dot
                                drawCircle(color = QiblaGreen, radius = 6f, center = Offset(cx, cy))
                            }

                            // Arrow — rotated by animated angle
                            val rotDeg = animatedRotation.value
                            Canvas(
                                Modifier
                                    .size(280.dp)
                                    .graphicsLayer { rotationZ = rotDeg }
                            ) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val arrowLen = 85f
                                val arrowW   = 10f

                                // Arrow body
                                drawRect(
                                    color = QiblaGreen,
                                    topLeft = Offset(cx - arrowW / 2f, cy - arrowLen + 10f),
                                    size = androidx.compose.ui.geometry.Size(arrowW, arrowLen - 20f),
                                )

                                // Arrow head (triangle)
                                val head = Path().apply {
                                    moveTo(cx, cy - arrowLen)
                                    lineTo(cx - 16f, cy - arrowLen + 28f)
                                    lineTo(cx + 16f, cy - arrowLen + 28f)
                                    close()
                                }
                                drawPath(head, QiblaGreen)

                                // Arrow tail
                                val tail = Path().apply {
                                    moveTo(cx, cy + 20f)
                                    lineTo(cx - 8f, cy + 5f)
                                    lineTo(cx + 8f, cy + 5f)
                                    close()
                                }
                                drawPath(tail, QiblaGreen.copy(alpha = 0.4f))
                            }

                            // Ka'aba emoji at arrow tip
                            val rotRad = Math.toRadians(animatedRotation.value.toDouble())
                            val offset = 280.dp * 0.357f
                            Box(
                                Modifier.offset(
                                    x = (offset * sin(rotRad).toFloat()),
                                    y = -(offset * cos(rotRad).toFloat()),
                                )
                            ) { Text("🕋", fontSize = 22.sp) }
                        }

                        // Accuracy badge
                        when {
                            acc >= 999.0 -> {
                                Surface(color = Color.Gray.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))) {
                                    Text("Initializing sensor...",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                            acc <= 10.0 -> {
                                Surface(color = Color(0xFFC8E6C9), shape = RoundedCornerShape(20.dp)) {
                                    Text("✅  HIGH accuracy  ±${acc.toInt()}°",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        fontSize = 13.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            acc <= 45.0 -> {
                                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(20.dp)) {
                                    Text("⚠️  MEDIUM  ±${acc.toInt()}° — wave in figure-8",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        fontSize = 13.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                                }
                            }
                            else -> {
                                Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(20.dp)) {
                                    Text("❌  LOW  ±${acc.toInt()}° — needs calibration",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        fontSize = 13.sp, color = Color.Red, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Qibla + Heading info cards
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoCard("Qibla Direction", "${qibla.let { "%.1f".format(it) }}°",
                                Icons.Default.Explore, Modifier.weight(1f))
                            InfoCard("Compass Heading",
                                "${r?.heading?.let { "%.1f".format(it) } ?: "—"}°",
                                Icons.Default.Navigation, Modifier.weight(1f))
                        }

                        // Magnetic declination card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Explore, null,
                                    tint = QiblaGreen, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Magnetic Declination",
                                        fontSize = 11.sp, color = Color.Gray)
                                    val dec = declination
                                    Text(
                                        if (dec == null) "—" else "${if (dec >= 0) "+" else ""}${"%.2f".format(dec)}°",
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = QiblaGreen,
                                    )
                                    Text(
                                        if (declination == null) "Loading..."
                                        else if ((declination ?: 0f) >= 0) "East declination"
                                        else "West declination",
                                        fontSize = 11.sp, color = Color.Gray,
                                    )
                                }
                            }
                        }

                        // Tip card
                        Card(colors = CardDefaults.cardColors(
                            containerColor = QiblaGreen.copy(alpha = 0.08f))) {
                            Row(Modifier.padding(12.dp)) {
                                Icon(Icons.Default.Info, null,
                                    tint = QiblaGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Wait for HIGH accuracy badge before comparing with other apps. " +
                                    "Hold device flat, away from USB cables and metal objects.",
                                    fontSize = 12.sp, color = QiblaGreen,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = QiblaGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = QiblaGreen)
            Text(label, fontSize = 11.sp, color = Color.Gray)
        }
    }
}
