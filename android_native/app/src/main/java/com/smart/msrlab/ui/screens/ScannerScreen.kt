@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smart.msrlab.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.smart.msrlab.SmartApplication
import com.smart.msrlab.data.model.HalalProduct
import com.smart.msrlab.data.model.ScanResult
import com.smart.msrlab.data.remote.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private val ScannerGreen = Color(0xFF1B7A4B)

// ─────────────────────────────────────────────────────────────────────────────
// ScannerScreen — camera/gallery pick → POST /scan-image → show results
//
// Mirrors Flutter scanner_screen.dart 1:1
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = SmartApplication.prefs

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading   by remember { mutableStateOf(false) }
    var result      by remember { mutableStateOf<ScanResult?>(null) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    // Temp file URI for camera capture
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // ── Image pickers ─────────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            result      = null
            errorMsg    = null
            scope.launch { doScan(context, uri, prefs.getApiUrlBlocking(), onResult = { r -> result = r }, onError = { e -> errorMsg = e }, onLoadingChange = { isLoading = it })  }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            selectedUri = cameraUri
            result      = null
            errorMsg    = null
            scope.launch { doScan(context, cameraUri!!, prefs.getApiUrlBlocking(), onResult = { r -> result = r }, onError = { e -> errorMsg = e }, onLoadingChange = { isLoading = it }) }
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            errorMsg = "Gagal membuka kamera: ${e.message}"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            errorMsg = "Sila benarkan kebenaran kamera dalam tetapan peranti."
        }
    }

    fun pickCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Imbas Produk Halal", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScannerGreen),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Info banner
            Surface(
                color = ScannerGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ScannerGreen.copy(alpha = 0.3f)),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = ScannerGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Imbas label produk untuk semak status halal JAKIM",
                        fontSize = 12.sp, color = ScannerGreen)
                }
            }

            // Image preview box
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEEEEEE))
                    .clickable { galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null,
                            modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap untuk pilih dari galeri", color = Color.Gray)
                    }
                }
            }

            // Camera + Gallery buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { pickCamera() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScannerGreen),
                    border = BorderStroke(1.dp, ScannerGreen),
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kamera")
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ScannerGreen),
                    border = BorderStroke(1.dp, ScannerGreen),
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeri")
                }
            }

            // Scan button
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    scope.launch {
                        doScan(context, uri, prefs.getApiUrlBlocking(),
                            onResult = { r -> result = r },
                            onError  = { e -> errorMsg = e },
                            onLoadingChange = { isLoading = it },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = !isLoading && selectedUri != null,
                colors   = ButtonDefaults.buttonColors(containerColor = ScannerGreen),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Menganalisis...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.DocumentScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Imbas & Semak Halal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Error card
            errorMsg?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.Red, fontSize = 13.sp)
                    }
                }
            }

            // Scan result
            result?.let { r -> ScanResultCard(result = r) }
        }
    }
}

// ── Scan API call ─────────────────────────────────────────────────────────────
private suspend fun doScan(
    context: android.content.Context,
    uri: Uri,
    apiUrl: String,
    onResult: (ScanResult) -> Unit,
    onError: (String) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
) {
    onLoadingChange(true)
    try {
        val bytes = context.contentResolver.openInputStream(uri)!!.readBytes()
        val part  = MultipartBody.Part.createFormData(
            "file", "scan.jpg",
            bytes.toRequestBody("image/jpeg".toMediaType()),
        )
        val scanResult = RetrofitClient.create(apiUrl).scanImage(part)
        onResult(scanResult)
    } catch (e: Exception) {
        onError(e.message?.removePrefix("Exception: ") ?: "Scan gagal")
    } finally {
        onLoadingChange(false)
    }
}

// ── Scan result card ──────────────────────────────────────────────────────────
@Composable
private fun ScanResultCard(result: ScanResult) {
    val hasHalal = result.hasHalalRecords

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Detection method card (blue tint)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when { result.isVision -> Icons.Default.Visibility
                               result.isOcr   -> Icons.Default.TextFields
                               else            -> Icons.Default.ImageSearch },
                        null, tint = Color(0xFF1565C0), modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when { result.isVision -> "Produk Dikenal Pasti (Visual AI)"
                               result.isOcr   -> "Teks Dikesan (OCR)"
                               else            -> "Best visual match" },
                        fontWeight = FontWeight.Bold, color = Color(0xFF1565C0), fontSize = 14.sp,
                    )
                    if (!result.isOcr && !result.isVision && result.confidence != null) {
                        Spacer(Modifier.weight(1f))
                        val pct = result.confidencePct
                        Surface(
                            color = if (pct >= 70) Color(0xFFC8E6C9) else Color(0xFFFFE0B2),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("$pct% match",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 12.sp,
                                color = if (pct >= 70) Color(0xFF2E7D32) else Color(0xFFE65100))
                        }
                    }
                }

                if (result.isVision) {
                    val brand   = result.visionResult?.brand?.takeIf { it.isNotBlank() }
                    val product = result.visionResult?.product?.takeIf { it.isNotBlank() }
                    if (brand != null || product != null) {
                        brand?.let { Row { Text("Brand:  ", color = Color.Gray, fontSize = 13.sp); Text(it, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }
                        product?.let { Row { Text("Produk: ", color = Color.Gray, fontSize = 13.sp); Text(it, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }
                    } else {
                        Text("Tiada maklumat brand/produk dikesan", color = Color.Gray, fontSize = 13.sp)
                    }
                } else if (result.isOcr && result.ocrTexts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.ocrTexts.forEach { text ->
                            SuggestionChip(onClick = {}, label = { Text(text, fontSize = 12.sp) })
                        }
                    }
                } else if (result.matchedProduct != null) {
                    Text(result.matchedProduct, fontSize = 15.sp)
                    result.matchedCategory?.let { Text(it, color = Color.Gray, fontSize = 13.sp) }
                }

                // HALAL badge
                Surface(
                    color = if (hasHalal) ScannerGreen else Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (hasHalal) "HALAL JAKIM" else "? Tiada Sijil",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasHalal) Color.White else Color.DarkGray,
                    )
                }
            }
        }

        // JAKIM records list
        if (hasHalal) {
            Text("Sijil Halal JAKIM (${result.halalRecords.size} rekod)",
                fontWeight = FontWeight.Bold, color = ScannerGreen,
                modifier = Modifier.padding(start = 4.dp))
            result.halalRecords.forEach { product ->
                HalalRecordTile(product = product)
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null,
                        tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tiada rekod halal JAKIM ditemui untuk produk ini.\n" +
                        "Ini tidak bermakna produk tidak halal — mungkin tiada dalam pangkalan data.",
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HalalRecordTile(product: HalalProduct) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Surface(color = ScannerGreen.copy(alpha = 0.1f), shape = CircleShape) {
                    Icon(Icons.Default.Verified, null,
                        tint = ScannerGreen, modifier = Modifier.padding(6.dp).size(20.dp))
                }
            },
            headlineContent = {
                Text(product.company, fontWeight = FontWeight.SemiBold)
            },
            supportingContent = {
                Column {
                    if (product.brand.isNotBlank()) Text("Brand: ${product.brand}")
                    if (product.productName.isNotBlank()) Text("Produk: ${product.productName}")
                    Text("Kategori: ${product.category}")
                    Text("Kod: ${product.compCode}", fontSize = 11.sp)
                    Text(
                        "Luput: ${product.expiry}",
                        fontSize = 11.sp,
                        color = if (product.isExpired()) Color.Red else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
        )
    }
}
