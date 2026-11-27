package com.smart.msrlab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.smart.msrlab.SmartApplication
import com.smart.msrlab.data.model.HalalProduct
import com.smart.msrlab.data.remote.RetrofitClient
import com.smart.msrlab.drone.DroneVideoView
import dji.sdk.sdkmanager.DJISDKManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private val DroneGreenAccent = Color(0xFF00E676)
private val DroneDark        = Color(0xFF0D1F14)

// ─────────────────────────────────────────────────────────────────────────────
// DroneScreen — DJI live camera feed + halal scanning
//
// Mirrors Flutter drone_screen.dart exactly.
// DJI SDK called directly (no method channel needed in native).
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroneScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = SmartApplication.prefs

    var droneStatus   by remember { mutableStateOf("disconnected") }
    var logText       by remember { mutableStateOf("") }
    var isLoading     by remember { mutableStateOf(false) }
    var sdkRegistered by remember { mutableStateOf(SmartApplication.isDjiRegistered) }
    var isRecording   by remember { mutableStateOf(false) }
    var isScanning    by remember { mutableStateOf(false) }
    var scanResults   by remember { mutableStateOf<List<HalalProduct>>(emptyList()) }
    var scanStatus    by remember { mutableStateOf("") } // "halal" | "not_found" | "error" | ""
    var detectedText  by remember { mutableStateOf("") }

    // Paparan live video drone (TextureView + DJICodecManager)
    var videoView by remember { mutableStateOf<DroneVideoView?>(null) }
    DisposableEffect(Unit) { onDispose { videoView?.stopVideoFeed() } }

    val isConnected = droneStatus.startsWith("connected:")

    fun checkStatus() {
        scope.launch {
            isLoading = true
            try {
                val product = withContext(Dispatchers.IO) {
                    try { DJISDKManager.getInstance().product } catch (_: Exception) { null }
                }
                droneStatus = if (product != null && product.isConnected) {
                    val name = product.model?.displayName ?: "Unknown Drone"
                    logText  = "$name bersambung"
                    "connected:$name"
                } else {
                    val reason = when {
                        !SmartApplication.isDjiRegistered -> "SDK belum didaftar"
                        product == null  -> "RC tidak dikesan (semak USB)"
                        else             -> "Drone tidak dikesan (hidupkan drone)"
                    }
                    logText = reason
                    "disconnected: $reason"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun registerSDK() {
        scope.launch {
            isLoading = true
            logText   = "Mendaftar SDK..."
            try {
                (context as? android.app.Activity)?.let { activity ->
                    (activity as? com.smart.msrlab.MainActivity)?.registerDjiSdk { success, msg ->
                        sdkRegistered = success
                        logText = if (success) "SDK berjaya ($msg)" else "Gagal: $msg"
                        if (success) scope.launch { checkStatus() }
                    }
                } ?: run {
                    logText = "Perlukan MainActivity untuk daftar SDK"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun capturePhoto() {
        scope.launch {
            isLoading = true
            logText   = "Mengambil gambar..."
            try {
                val aircraft = withContext(Dispatchers.IO) {
                    try { DJISDKManager.getInstance().product as? dji.sdk.products.Aircraft }
                    catch (_: Exception) { null }
                }
                val camera = aircraft?.camera
                if (camera == null) {
                    logText = "Kamera tidak dapat diakses"; return@launch
                }
                camera.startShootPhoto { err ->
                    logText = if (err == null) "Gambar disimpan dalam SD card drone."
                              else "Guna butang RC untuk ambil gambar (SDK limitation)"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleRecording() {
        scope.launch {
            isLoading = true
            try {
                val aircraft = withContext(Dispatchers.IO) {
                    try { DJISDKManager.getInstance().product as? dji.sdk.products.Aircraft }
                    catch (_: Exception) { null }
                }
                val camera = aircraft?.camera ?: run {
                    logText = "Kamera tidak dapat diakses"; return@launch
                }
                if (isRecording) {
                    camera.stopRecordVideo { err ->
                        if (err == null) {
                            isRecording = false
                            logText     = "Video disimpan dalam SD card drone."
                        } else { logText = "Ralat berhenti rekod: ${err.description}" }
                    }
                } else {
                    camera.startRecordVideo { err ->
                        if (err == null) {
                            isRecording = true
                            logText     = "Sedang merekod..."
                        } else { logText = "Ralat mula rekod: ${err.description}" }
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun scanHalal() {
        scope.launch {
            isScanning   = true
            scanResults  = emptyList()
            scanStatus   = ""
            detectedText = ""
            logText      = "Mengimbas logo/nama halal..."

            try {
                // Tangkap frame sebenar dari video drone (PixelCopy → getBitmap → YUV)
                logText = "[1/3] Capture frame..."
                val activity = context as? android.app.Activity
                val view = videoView
                val frameBytes: ByteArray =
                    if (activity == null || view == null) ByteArray(0)
                    else suspendCancellableCoroutine { cont ->
                        view.captureFrame(activity) { bytes ->
                            if (cont.isActive) cont.resume(bytes ?: ByteArray(0))
                        }
                    }
                if (frameBytes.isEmpty()) {
                    logText = "Gagal capture frame — pastikan drone bersambung"
                    scanStatus = "error"; return@launch
                }

                logText = "[2/3] Frame ${frameBytes.size / 1024}KB → server..."
                val part = MultipartBody.Part.createFormData(
                    "file", "drone_frame.jpg",
                    frameBytes.toRequestBody("image/jpeg".toMediaType()),
                )
                val apiResult = RetrofitClient.create(prefs.getApiUrlBlocking()).scanPremises(part)

                val matches = apiResult.allMatches
                val texts   = apiResult.allDetectedTexts

                scanResults  = matches
                detectedText = texts.joinToString(" | ")
                scanStatus   = if (matches.isNotEmpty()) "halal" else "not_found"
                logText = if (matches.isNotEmpty())
                    "[3/3] ${matches.size} premis dijumpai"
                else
                    "[3/3] Tiada rekod — dikesan: \"${detectedText}\""
            } catch (e: Exception) {
                scanStatus = "error"
                logText    = "ERROR: ${e.message}"
            } finally {
                isScanning = false
            }
        }
    }

    // Semak status bila skrin dibuka, kemudian setiap 3 saat selepas SDK didaftar
    // (supaya video terus keluar bila RC/drone disambung, tanpa tekan refresh)
    LaunchedEffect(Unit) {
        checkStatus()
        while (true) {
            kotlinx.coroutines.delay(3000)
            if (!SmartApplication.isDjiRegistered || isLoading || isScanning) continue
            val product = withContext(Dispatchers.IO) {
                try { DJISDKManager.getInstance().product } catch (_: Exception) { null }
            }
            val nowConnected = product != null && product.isConnected
            if (nowConnected != isConnected) checkStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (isConnected) Color.Green else Color.Red,
                                    CircleShape,
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isConnected) droneStatus.removePrefix("connected:")
                            else "Drone Scanner",
                            color = Color.White, fontSize = 15.sp,
                        )
                        if (isRecording) {
                            Spacer(Modifier.width(10.dp))
                            Surface(color = Color.Red, shape = RoundedCornerShape(4.dp)) {
                                Text("REC", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                actions = {
                    if (isLoading || isScanning) {
                        CircularProgressIndicator(color = Color.White,
                            modifier = Modifier.size(18.dp).padding(end = 16.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = { checkStatus() }) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Live camera feed 16:9 ─────────────────────────────────────────
            AspectRatio16x9 {
                Box(Modifier.fillMaxSize()) {
                    // DJI live camera (TextureView — sama seperti app Flutter)
                    AndroidView(
                        factory = { ctx -> DroneVideoView(ctx).also { videoView = it } },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Not connected overlay
                    if (!isConnected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.87f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.VideocamOff, null,
                                    tint = Color.White.copy(alpha = 0.24f),
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Sambungkan RC ke phone via USB-C",
                                    color = Color.White.copy(alpha = 0.38f), fontSize = 12.sp)
                                if (!sdkRegistered) {
                                    Spacer(Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = { registerSDK() },
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, Color(0xFF42A5F5)),
                                    ) {
                                        Icon(Icons.Default.Key, null,
                                            tint = Color(0xFF42A5F5), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Daftar SDK",
                                            color = Color(0xFF42A5F5), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Scanning overlay
                    if (isScanning) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.54f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = DroneGreenAccent)
                                Spacer(Modifier.height(12.dp))
                                Text("Mengimbas...", color = Color.White)
                            }
                        }
                    }

                    // Scan result badge
                    if (scanStatus.isNotEmpty() && !isScanning) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            color = if (scanStatus == "halal") Color(0xFF2E7D32) else Color(0xFFC62828),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(
                                if (scanStatus == "halal") "✓ HALAL" else "✗ TIADA REKOD",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // ── Controls + results (scrollable) ──────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Controls
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (logText.isNotBlank()) {
                        Text(logText, color = Color.White.copy(alpha = 0.54f),
                            fontSize = 11.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DroneControlBtn(
                            icon = Icons.Default.Key,
                            label = if (sdkRegistered) "SDK ✓" else "Daftar",
                            color = if (sdkRegistered) Color.White.copy(alpha = 0.3f) else Color(0xFF42A5F5),
                            enabled = !sdkRegistered,
                            onClick = { registerSDK() },
                        )
                        DroneControlBtn(
                            icon = Icons.Default.DocumentScanner,
                            label = "Scan Halal",
                            color = DroneGreenAccent,
                            enabled = sdkRegistered && !isScanning,
                            large = true,
                            onClick = { scanHalal() },
                        )
                        DroneControlBtn(
                            icon = Icons.Default.Camera,
                            label = "Gambar",
                            color = Color.White,
                            enabled = isConnected && !isLoading,
                            onClick = { capturePhoto() },
                        )
                        DroneControlBtn(
                            icon = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                            label = if (isRecording) "Stop" else "Video",
                            color = if (isRecording) Color.Red else Color.White,
                            enabled = isConnected && !isLoading,
                            onClick = { toggleRecording() },
                        )
                    }
                }

                // Results panel
                if (scanResults.isNotEmpty() || scanStatus == "not_found") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (scanResults.isEmpty()) Icons.Default.Cancel else Icons.Default.Verified,
                                null,
                                tint = if (scanResults.isEmpty()) Color.Red else DroneGreenAccent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (scanResults.isEmpty()) "Tiada rekod halal dalam pangkalan data JAKIM"
                                else "${scanResults.size} rekod halal dijumpai",
                                color = if (scanResults.isEmpty()) Color.Red else DroneGreenAccent,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        if (detectedText.isNotBlank()) {
                            Text("Dikesan: $detectedText",
                                color = Color.White.copy(alpha = 0.38f), fontSize = 10.sp)
                        }
                        scanResults.forEach { r -> DroneResultCard(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AspectRatio16x9(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val h = maxWidth * 9f / 16f
        Box(Modifier.fillMaxWidth().height(h), content = content)
    }
}

@Composable
private fun DroneControlBtn(
    icon: ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    val size = if (large) 66.dp else 48.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (enabled) Modifier else Modifier.then(Modifier))
    ) {
        val alpha = if (enabled) 1f else 0.3f
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size),
            border = androidx.compose.foundation.BorderStroke(
                if (large) 3.dp else 2.dp,
                color.copy(alpha = alpha),
            ),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                contentColor = color.copy(alpha = alpha),
                containerColor = if (large) color.copy(alpha = 0.15f * alpha) else Color.Transparent,
            ),
        ) {
            Icon(icon, null, modifier = Modifier.size(if (large) 30.dp else 22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = color.copy(alpha = alpha),
            fontSize = 10.sp,
            fontWeight = if (large) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DroneResultCard(product: HalalProduct) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DroneDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B5E20)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Store, null,
                    tint = DroneGreenAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(product.company, color = Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (product.expiry.isNotBlank()) {
                    Surface(color = Color(0xFF1B5E20), shape = RoundedCornerShape(4.dp)) {
                        Text(product.expiry,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = DroneGreenAccent, fontSize = 9.sp)
                    }
                }
            }
            if (product.category.isNotBlank()) {
                Text(product.category, color = Color.White.copy(alpha = 0.54f), fontSize = 10.sp)
            }
            if (product.address.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.LocationOn, null,
                        tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(product.address,
                        color = Color.White.copy(alpha = 0.38f), fontSize = 10.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
