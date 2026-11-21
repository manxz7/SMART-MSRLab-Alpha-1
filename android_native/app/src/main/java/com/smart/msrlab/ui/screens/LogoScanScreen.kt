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
import com.smart.msrlab.data.model.LogoMatch
import com.smart.msrlab.data.model.LogoScanResult
import com.smart.msrlab.data.remote.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private val LogoGreen = Color(0xFF1B7A4B)

// ─────────────────────────────────────────────────────────────────────────────
// LogoScanScreen — scan halal certification logo → identify certifying body
//
// Mirrors Flutter logo_scan_screen.dart:
//   1. User picks image → auto-scan
//   2. POST /scan-logo → Qwen Vision identifies abbreviation + country
//   3. Cross-check with JAKIM's recognised certifier list
//   4. Show status banner + match cards
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = SmartApplication.prefs

    var selectedUri  by remember { mutableStateOf<Uri?>(null) }
    var isLoading    by remember { mutableStateOf(false) }
    var result       by remember { mutableStateOf<LogoScanResult?>(null) }
    var errorMsg     by remember { mutableStateOf<String?>(null) }
    var cameraUri    by remember { mutableStateOf<Uri?>(null) }

    suspend fun doScan(uri: Uri) {
        isLoading = true
        errorMsg  = null
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.readBytes()
            val part  = MultipartBody.Part.createFormData(
                "file", "logo.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType()),
            )
            result = RetrofitClient.create(prefs.getApiUrlBlocking()).scanLogo(part)
        } catch (e: Exception) {
            errorMsg = "Gagal scan: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri; result = null
            scope.launch { doScan(uri) }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            selectedUri = cameraUri; result = null
            scope.launch { doScan(cameraUri!!) }
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "logo_${System.currentTimeMillis()}.jpg")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Logo Halal", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LogoGreen),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(containerColor = LogoGreen.copy(alpha = 0.07f)),
                border = BorderStroke(1.dp, LogoGreen.copy(alpha = 0.2f)),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = LogoGreen.copy(alpha = 0.15f), shape = CircleShape) {
                        Icon(Icons.Default.Verified, null,
                            tint = LogoGreen, modifier = Modifier.padding(10.dp).size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Scan Logo Halal",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1B4332))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ambil gambar logo halal pada produk untuk semak sama ada badan pensijilan diiktiraf JAKIM.",
                            fontSize = 12.sp, color = Color(0xFF4A7A5A), lineHeight = 18.sp,
                        )
                    }
                }
            }

            // Image preview
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable { pickCamera() },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, null,
                            modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Text("Ketuk untuk ambil gambar",
                            color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            // Camera + Gallery buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f).clickable { pickCamera() },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CameraAlt, null,
                            tint = LogoGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Kamera", fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = Color(0xFF1B4332))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).clickable {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(
                        Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null,
                            tint = LogoGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery", fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = Color(0xFF1B4332))
                    }
                }
            }

            // Loading
            if (isLoading) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = LogoGreen)
                    Text("Qwen Vision sedang menganalisis logo...",
                        color = Color(0xFF4A7A5A), fontSize = 13.sp)
                }
            }

            // Error
            errorMsg?.let { msg ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    border = BorderStroke(1.dp, Color(0xFFEF9A9A))) {
                    Text(msg, modifier = Modifier.padding(14.dp),
                        color = Color(0xFFC62828), fontSize = 13.sp)
                }
            }

            // Results
            result?.let { r ->
                val isOk = r.isRecognisedByJakim == true

                // Status banner
                val bannerColor = if (isOk) LogoGreen else Color(0xFFC62828)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = bannerColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.3f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            null, tint = bannerColor, modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (isOk) "Diiktiraf oleh JAKIM"
                            else       "Tidak Dijumpai dalam Senarai JAKIM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = bannerColor,
                        )
                    }
                }

                // Vision result card
                if (r.extractedTexts.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TextFields, null,
                                    tint = Color(0xFF5C35A0), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Teks Dikesan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp, color = Color(0xFF5C35A0))
                            }
                            HorizontalDivider()
                            Text("Teks dalam logo: ${r.extractedTexts.joinToString(", ")}",
                                fontSize = 12.sp)
                        }
                    }
                }

                // Match cards
                if (r.matches.isNotEmpty()) {
                    r.matches.forEach { match -> LogoMatchCard(match) }
                } else {
                    // No match card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        border = BorderStroke(1.dp, Color(0xFFFFCC02)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Help, null,
                                    tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Badan tidak dijumpai",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = Color(0xFF795548))
                            }
                            Text(
                                "Logo ini mungkin:\n" +
                                "• Bukan logo halal\n" +
                                "• Dari badan yang belum diiktiraf JAKIM\n" +
                                "• Gambar kurang jelas — cuba ambil gambar yang lebih dekat dan terang",
                                fontSize = 12.sp,
                                color = Color(0xFF795548),
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoMatchCard(match: LogoMatch) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, LogoGreen.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (match.abbreviation.isNotBlank()) {
                    Surface(color = LogoGreen, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            match.abbreviation,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (match.country.isNotBlank()) {
                    Text(match.country,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, color = Color(0xFF4A7A5A))
                }
            }
            Text(match.organizationName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = Color(0xFF1B2A1F), lineHeight = 20.sp)
            if (match.email.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, null,
                        tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(match.email, fontSize = 12.sp, color = Color(0xFF1B2A1F))
                }
            }
            if (match.website.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null,
                        tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(match.website, fontSize = 12.sp, color = Color(0xFF1B2A1F))
                }
            }
            if (match.phone.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null,
                        tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(match.phone, fontSize = 12.sp)
                }
            }
        }
    }
}
