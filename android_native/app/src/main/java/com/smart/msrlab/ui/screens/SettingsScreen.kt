package com.smart.msrlab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart.msrlab.SmartApplication
import kotlinx.coroutines.launch

private val SettingsGreen = Color(0xFF1A7A4A)

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen — app preferences
//
// Mirrors Flutter settings_screen.dart 1:1
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = SmartApplication.prefs
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Read initial values from DataStore
    val adhanEnabled   by prefs.adhanFlow.collectAsState(initial = true)
    val vibrationEnabled by prefs.vibrationFlow.collectAsState(initial = true)
    val calcMethod     by prefs.calcMethodFlow.collectAsState(initial = "Singapore")
    val madhab         by prefs.madhabFlow.collectAsState(initial = "Shafi")
    val savedApiUrl    by prefs.apiUrlFlow.collectAsState(initial = "")

    var apiUrlText by remember(savedApiUrl) { mutableStateOf(savedApiUrl) }
    var showSnackbar by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            snackbarHostState.showSnackbar("Server URL saved")
            showSnackbar = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SettingsGreen),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
        ) {
            // Header
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SettingsGreen)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            ) {
                Text("Settings", color = Color.White,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Customise your SMART App",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }

            // Settings list
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── NOTIFICATIONS ────────────────────────────────────────────
                SectionHeader("NOTIFICATIONS")
                SwitchSettingTile(
                    icon = Icons.Default.NotificationsActive,
                    title = "Adhan Alerts",
                    subtitle = "Play Adhan at prayer times",
                    checked = adhanEnabled,
                    onCheckedChange = { scope.launch { prefs.setAdhan(it) } },
                )
                SwitchSettingTile(
                    icon = Icons.Default.Vibration,
                    title = "Vibration",
                    subtitle = "Vibrate at prayer times",
                    checked = vibrationEnabled,
                    onCheckedChange = { scope.launch { prefs.setVibration(it) } },
                )

                Spacer(Modifier.height(8.dp))

                // ── PRAYER CALCULATION ────────────────────────────────────────
                SectionHeader("PRAYER CALCULATION")
                DropdownSettingTile(
                    icon = Icons.Default.Calculate,
                    title = "Calculation Method",
                    subtitle = "Used outside Malaysia",
                    selected = calcMethod,
                    options = listOf(
                        "Singapore",
                        "Muslim World League",
                        "ISNA (North America)",
                        "Umm Al-Qura (Saudi)",
                        "Egyptian",
                        "Karachi",
                    ),
                    onSelect = { scope.launch { prefs.setCalcMethod(it) } },
                )
                DropdownSettingTile(
                    icon = Icons.Default.Mosque,
                    title = "Madhab (Asr)",
                    subtitle = "Affects Asr prayer time",
                    selected = madhab,
                    options = listOf("Shafi", "Hanafi"),
                    onSelect = { scope.launch { prefs.setMadhab(it) } },
                )

                Spacer(Modifier.height(8.dp))

                // ── API SERVER ────────────────────────────────────────────────
                SectionHeader("API SERVER")
                Card(
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Cloud, null, tint = SettingsGreen)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Halal Checker Server URL", fontWeight = FontWeight.Medium)
                                Text("Paste your server URL here to use from any network",
                                    fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        OutlinedTextField(
                            value = apiUrlText,
                            onValueChange = { apiUrlText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://xxxx.sslip.io", color = Color.Gray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                scope.launch {
                                    prefs.setApiUrl(apiUrlText)
                                    showSnackbar = true
                                }
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    scope.launch {
                                        prefs.setApiUrl(apiUrlText)
                                        showSnackbar = true
                                    }
                                }) {
                                    Icon(Icons.Default.Save, null, tint = SettingsGreen)
                                }
                            },
                        )
                        Text("Leave empty to use default server",
                            fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── ABOUT ─────────────────────────────────────────────────────
                SectionHeader("ABOUT")
                InfoTile(Icons.Default.Info, "Version", "1.0.0")
                InfoTile(Icons.Default.Public, "Prayer Data (Malaysia)",
                    "api.waktusolat.app — JAKIM official times")
                InfoTile(Icons.Default.Calculate, "Prayer Data (Worldwide)",
                    "Calculated using Muslim World League formula")
                InfoTile(Icons.Default.Explore, "Qibla Bearing",
                    "Great Circle formula toward Mecca (21.4225°N, 39.8262°E)")

                Spacer(Modifier.height(8.dp))

                // Malaysia info card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SettingsGreen.copy(alpha = 0.08f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = SettingsGreen)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "In Malaysia: prayer times are fetched directly from " +
                            "JAKIM's official database via api.waktusolat.app. " +
                            "Your GPS zone is auto-detected.",
                            fontSize = 12.sp, color = SettingsGreen,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        color = SettingsGreen,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun SwitchSettingTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        ListItem(
            leadingContent = { Icon(icon, null, tint = SettingsGreen) },
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(subtitle, fontSize = 12.sp, color = Color.Gray) },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = SettingsGreen),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSettingTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        ListItem(
            leadingContent = { Icon(icon, null, tint = SettingsGreen) },
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(subtitle, fontSize = 12.sp, color = Color.Gray) },
            trailingContent = {
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(selected, color = SettingsGreen, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ArrowDropDown, null, tint = SettingsGreen)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { onSelect(option); expanded = false },
                                leadingIcon = if (option == selected) {
                                    { Icon(Icons.Default.Check, null, tint = SettingsGreen, modifier = Modifier.size(18.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun InfoTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        ListItem(
            leadingContent = { Icon(icon, null, tint = SettingsGreen) },
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = { Text(subtitle, fontSize = 12.sp, color = Color.Gray) },
        )
    }
}
