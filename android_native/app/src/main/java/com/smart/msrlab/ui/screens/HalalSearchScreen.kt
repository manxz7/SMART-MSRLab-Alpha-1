@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smart.msrlab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart.msrlab.SmartApplication
import com.smart.msrlab.data.model.CompanyResult
import com.smart.msrlab.data.model.HalalProduct
import com.smart.msrlab.data.remote.RetrofitClient
import kotlinx.coroutines.launch

private val HalalSearchGreen = Color(0xFF1B7A4B)

// ─────────────────────────────────────────────────────────────────────────────
// HalalSearchScreen — search products by keyword, grouped by company
//
// Mirrors Flutter SearchScreen + CompanyCard + CompanyProductsScreen + ProductCard
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalalSearchScreen(onBack: () -> Unit) {
    val prefs = SmartApplication.prefs
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var query       by remember { mutableStateOf("") }
    var results     by remember { mutableStateOf<List<CompanyResult>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    // Navigation to company products
    var selectedCompany by remember { mutableStateOf<CompanyResult?>(null) }

    selectedCompany?.let { company ->
        CompanyProductsScreen(
            company = company,
            query   = query,
            prefs   = prefs,
            onBack  = { selectedCompany = null },
        )
        return
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            isLoading = true; errorMsg = null; hasSearched = true
            try {
                val response = RetrofitClient.create(prefs.getApiUrlBlocking())
                    .searchGrouped(q)
                results = response.companies
            } catch (e: Exception) {
                errorMsg = "Could not connect to server.\nMake sure the API is running."
                results  = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Halal Checker", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HalalSearchGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar (green bg)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HalalSearchGreen)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search product or company...",
                            color = Color.White.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    )
                    Button(
                        onClick = { doSearch() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White,
                            contentColor = HalalSearchGreen),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text("Search", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Results area
            Box(Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = HalalSearchGreen)
                        }
                    }
                    errorMsg != null -> {
                        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.WifiOff, null,
                                    tint = Color.Gray, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(errorMsg!!, color = Color.Gray, fontSize = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                    hasSearched && results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null,
                                    tint = Color.Gray, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("No halal-certified records found.",
                                    color = Color.Gray, fontSize = 16.sp)
                            }
                        }
                    }
                    !hasSearched -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Verified, null,
                                    tint = Color.Green.copy(alpha = 0.3f),
                                    modifier = Modifier.size(80.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Search for a product or company\nto check halal status",
                                    color = Color.Gray, fontSize = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                Text("348,844 certified records from JAKIM",
                                    color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                    else -> {
                        Column {
                            Text(
                                "${results.size} syarikat ditemui",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = Color.Gray, fontSize = 13.sp,
                            )
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(results) { company ->
                                    CompanyCard(
                                        company = company,
                                        onClick  = { selectedCompany = company },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyCard(company: CompanyResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(company.company, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784))) {
                    Text("HALAL ✓",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Inventory2, null,
                    tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${company.productCount} produk bersijil",
                    color = Color.Gray, fontSize = 13.sp)
                if (company.isPopular) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCA28))) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null,
                                tint = Color(0xFFF9A825), modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Popular", fontSize = 10.sp, color = Color(0xFFF9A825),
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    company.categories.take(3).forEach { cat ->
                        Surface(
                            color = HalalSearchGreen.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(cat,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = HalalSearchGreen, fontSize = 11.sp)
                        }
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
    }
}

// ── Company Products Screen ───────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyProductsScreen(
    company: CompanyResult,
    query: String,
    prefs: com.smart.msrlab.data.local.PreferencesManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var products  by remember { mutableStateOf<List<HalalProduct>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }
    var selectedProduct by remember { mutableStateOf<HalalProduct?>(null) }

    LaunchedEffect(company.company) {
        try {
            val resp = RetrofitClient.create(prefs.getApiUrlBlocking())
                .getCompanyProducts(company.company, query)
            products  = resp.products
            isLoading = false
        } catch (e: Exception) {
            errorMsg  = "Ralat server: ${e.message}"
            isLoading = false
        }
    }

    // Product detail dialog
    selectedProduct?.let { p ->
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text("Halal Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Syarikat", p.company)
                    if (p.brand.isNotBlank()) DetailRow("Brand", p.brand)
                    if (p.productName.isNotBlank()) DetailRow("Produk", p.productName)
                    DetailRow("Kategori", p.category)
                    DetailRow("Kod Sijil", p.compCode)
                    DetailRow("Tarikh Luput", p.expiry.ifBlank { "Not specified" })
                    if (p.address.isNotBlank()) DetailRow("Alamat", p.address)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProduct = null }) { Text("Tutup") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(company.company, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HalalSearchGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HalalSearchGreen.copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Verified, null,
                    tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("${company.productCount} produk halal bersijil JAKIM",
                    fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HalalSearchGreen)
                }
                errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg!!, color = Color.Red)
                }
                products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tiada produk ditemui.", color = Color.Gray)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(products) { product ->
                        ProductCard(product = product, onClick = { selectedProduct = product })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: HalalProduct, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(product.company, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(20.dp)) {
                    Text("HALAL ✓",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (product.productName.isNotBlank())
                Text(product.productName, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (product.brand.isNotBlank())
                Text(product.brand, color = Color.Gray, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = HalalSearchGreen.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp)) {
                    Text(product.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = HalalSearchGreen, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.CalendarToday, null,
                    tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (product.expiry.isNotBlank()) "Exp: ${product.expiry}" else "No expiry",
                    color = Color.Gray, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, fontSize = 15.sp)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}
