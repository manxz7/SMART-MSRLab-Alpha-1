package com.smart.msrlab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val CalGreen = Color(0xFF1A7A4A)

// ─────────────────────────────────────────────────────────────────────────────
// CalendarScreen — Hijri Islamic calendar grid
//
// Mirrors Flutter calendar_screen.dart 1:1.
// Uses pure Kotlin Hijri math (no library) from HomeScreen.kt helpers.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val cal = Calendar.getInstance()
    val (todayHY, todayHM, todayHD) = gregorianToHijri(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )

    var viewYear  by remember { mutableIntStateOf(todayHY) }
    var viewMonth by remember { mutableIntStateOf(todayHM) }

    fun prevMonth() {
        viewMonth--
        if (viewMonth < 1) { viewMonth = 12; viewYear-- }
    }
    fun nextMonth() {
        viewMonth++
        if (viewMonth > 12) { viewMonth = 1; viewYear++ }
    }

    val daysInMonth = daysInHijriMonth(viewYear, viewMonth)
    // Find weekday of 1st: convert Hijri 1st → Gregorian → weekday (0=Sun)
    val (g1y, g1m, g1d) = hijriToGregorian(viewYear, viewMonth, 1)
    val startOffset: Int = run {
        val c = Calendar.getInstance()
        c.set(g1y, g1m - 1, g1d)
        c.get(Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY = 1 → 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hijri Calendar", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CalGreen),
            )
        },
        containerColor = Color(0xFFF5F5F5),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Today header
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(CalGreen)
                    .padding(horizontal = 20.dp, vertical = 0.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    "Today: $todayHD ${hijriMonthName(todayHM)} $todayHY AH",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(0.dp))

            // Month navigation
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { prevMonth() }) {
                    Icon(Icons.Default.ChevronLeft, null, tint = CalGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hijriMonthName(viewMonth),
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("$viewYear AH", color = Color.Gray, fontSize = 13.sp)
                }
                IconButton(onClick = { nextMonth() }) {
                    Icon(Icons.Default.ChevronRight, null, tint = CalGreen)
                }
            }

            // Day-of-week header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            day,
                            color = if (day == "Fri") CalGreen else Color.Gray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Calendar grid
            val totalCells = startOffset + daysInMonth
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                userScrollEnabled = false,
            ) {
                // Empty cells before 1st
                items(startOffset) { Box(Modifier.aspectRatio(0.85f)) }

                // Day cells
                items(daysInMonth) { dayIndex ->
                    val hDay = dayIndex + 1
                    val isToday = (hDay == todayHD && viewMonth == todayHM && viewYear == todayHY)

                    // Get Gregorian day for display + Friday check
                    val (_, _, gDay) = hijriToGregorian(viewYear, viewMonth, hDay)
                    val (gy2, gm2, gd2) = hijriToGregorian(viewYear, viewMonth, hDay)
                    val dayOfWeek = run {
                        val c = Calendar.getInstance()
                        c.set(gy2, gm2 - 1, gd2)
                        c.get(Calendar.DAY_OF_WEEK) // 1=Sun, 6=Fri
                    }
                    val isFriday = dayOfWeek == Calendar.FRIDAY

                    DayCell(
                        hijriDay = hDay,
                        gregDay  = gDay,
                        isToday  = isToday,
                        isFriday = isFriday,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Legend
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(CalGreen, "Today")
                Spacer(Modifier.width(20.dp))
                LegendDot(CalGreen.copy(alpha = 0.15f), "Friday")
            }
        }
    }
}

@Composable
private fun DayCell(hijriDay: Int, gregDay: Int, isToday: Boolean, isFriday: Boolean) {
    Box(
        modifier = Modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .background(
                color = when {
                    isToday  -> CalGreen
                    isFriday -> CalGreen.copy(alpha = 0.12f)
                    else     -> Color.White
                },
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$hijriDay",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isToday) Color.White else Color.Black.copy(alpha = 0.87f),
            )
            Text(
                "$gregDay",
                fontSize = 10.sp,
                color = when {
                    isToday  -> Color.White.copy(alpha = 0.7f)
                    isFriday -> CalGreen
                    else     -> Color.Gray
                },
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}
