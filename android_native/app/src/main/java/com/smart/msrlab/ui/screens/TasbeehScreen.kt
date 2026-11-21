package com.smart.msrlab.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smart.msrlab.SmartApplication
import kotlinx.coroutines.launch

private val TasbeehGreen     = Color(0xFF1A7A4A)
private val TasbeehGreenDark = Color(0xFF0F5C38)
private val PHRASES = arrayOf("Subhanallah", "Alhamdulillah", "Allahu Akbar")
private val TARGETS = listOf(33, 66, 99, 100)

// ─────────────────────────────────────────────────────────────────────────────
// TasbeehScreen — digital dhikr counter
//
// Mirrors Flutter tasbeeh_screen.dart 1:1:
//   • Tap to count + haptic feedback
//   • Preset targets 33 / 66 / 99 / 100
//   • Circular progress arc
//   • Bounce animation on tap
//   • Persist count + target via DataStore
//   • Phrase milestone chips
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbeehScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = SmartApplication.prefs

    var count   by remember { mutableIntStateOf(0) }
    var target  by remember { mutableIntStateOf(33) }
    var showReset   by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }

    // Load persisted values
    LaunchedEffect(Unit) {
        count  = prefs.getTasbeehCountBlocking()
        target = prefs.getTasbeehTargetBlocking()
    }

    // Bounce scale animation
    val scale = remember { Animatable(1f) }

    fun vibrate(heavy: Boolean) {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val duration = if (heavy) 100L else 40L
            vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    fun onTap() {
        vibrate(false)
        count++
        scope.launch { prefs.setTasbeehCount(count) }
        // Milestone vibration
        if (count > 0 && count % target == 0) vibrate(true)
        // Bounce
        scope.launch {
            scale.animateTo(0.88f, animationSpec = tween(120, easing = EaseOut))
            scale.animateTo(1f,    animationSpec = tween(100, easing = EaseIn))
        }
    }

    fun onReset() {
        count = 0
        scope.launch { prefs.setTasbeehCount(0) }
    }

    fun setTarget(t: Int) {
        target = t
        scope.launch { prefs.setTasbeehTarget(t) }
    }

    // Computed
    val progress      = if (target == 0) 0f else (count % target).toFloat() / target.toFloat()
    val cycles        = count / target
    val currentPhrase = when {
        target == 33 || target == 66 || target == 99 -> PHRASES[(count / 33).coerceIn(0, 2)]
        else -> "ذِكر"
    }

    // Reset confirmation dialog
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset counter?") },
            text  = { Text("This will set the count back to 0.") },
            confirmButton = {
                TextButton(
                    onClick = { showReset = false; onReset() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(TasbeehGreen, TasbeehGreenDark)))
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
                            Text("Tasbeeh", color = Color.White,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Digital Dhikr Counter",
                                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                    // Target selector
                    Box {
                        IconButton(onClick = { showTargetMenu = true }) {
                            Icon(Icons.Default.Tune, null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showTargetMenu,
                            onDismissRequest = { showTargetMenu = false },
                        ) {
                            TARGETS.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (t == target) {
                                                Icon(Icons.Default.Check, null,
                                                    tint = TasbeehGreen, modifier = Modifier.size(18.dp))
                                            } else {
                                                Spacer(Modifier.size(18.dp))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text("$t")
                                        }
                                    },
                                    onClick = { setTarget(t); showTargetMenu = false },
                                )
                            }
                        }
                    }
                }
            }

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Cycles badge
                if (cycles > 0) {
                    Surface(
                        color = TasbeehGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            "Completed: $cycles × $target",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = TasbeehGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.weight(1f))

                // ── Big circular counter ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTap() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Progress ring (drawn under the circle)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        // Background ring
                        drawArc(
                            color = Color.Gray.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke,
                        )
                        // Progress arc
                        drawArc(
                            color = TasbeehGreen,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = stroke,
                        )
                    }

                    // Inner filled circle (with scale bounce)
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .graphicsLayerScale(scale.value)
                            .background(TasbeehGreen, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$count",
                                color = Color.White,
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 66.sp,
                            )
                            Text("/ $target", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Current phrase
                Text(
                    currentPhrase,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black.copy(alpha = 0.87f),
                )
                Spacer(Modifier.height(6.dp))
                Text("Tap the circle to count", fontSize = 13.sp, color = Color.Gray)

                Spacer(Modifier.weight(1f))

                // Phrase milestone chips (for 33/66/99 targets)
                if (target == 33 || target == 66 || target == 99) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        PHRASES.forEachIndexed { i, phrase ->
                            val milestone = (i + 1) * 33
                            val reached   = count >= milestone
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(phrase, fontSize = 11.sp,
                                        color = if (reached) Color.White else TasbeehGreen)
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (reached) TasbeehGreen else TasbeehGreen.copy(alpha = 0.1f),
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = TasbeehGreen,
                                    borderWidth = 0.8.dp,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // Reset button
                TextButton(
                    onClick = { showReset = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// Helper to apply scale via graphicsLayer without extra nesting
@Composable
private fun Modifier.graphicsLayerScale(scale: Float) =
    this.graphicsLayer(scaleX = scale, scaleY = scale)
