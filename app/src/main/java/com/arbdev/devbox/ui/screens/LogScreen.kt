package com.arbdev.devbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.ui.theme.DevBackground
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevSecondary
import com.arbdev.devbox.ui.theme.DevSuccess
import com.arbdev.devbox.ui.theme.DevWarning
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.util.AppLogger
import com.arbdev.devbox.util.LogLevel
import kotlinx.coroutines.launch

@Composable
fun LogScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var isRunningDiag by remember { mutableStateOf(false) }

    val rawLogs = AppLogger.logs
    val filteredLogs = remember(rawLogs.size, selectedLevel) {
        if (selectedLevel == null) rawLogs else rawLogs.filter { it.level == selectedLevel }
    }

    // Auto scroll ke paling bawah saat ada log baru
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevBackground)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Log Sistem & Diagnostik",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${rawLogs.size} baris log tercatat",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Copy All Button
                IconButton(
                    onClick = {
                        val allText = AppLogger.getAllAsText()
                        if (allText.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(allText))
                            Toast.makeText(context, "Semua log berhasil disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Log masih kosong", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Semua Log",
                        tint = DevPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Clear Button
                IconButton(
                    onClick = {
                        AppLogger.clear()
                        Toast.makeText(context, "Log berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Hapus Log",
                        tint = DevError,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Quick Diagnostics Action Bar & Level Filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Diagnostik Cepat Button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isRunningDiag = true
                        ChrootManager.runEnvironmentDiagnostics()
                        isRunningDiag = false
                    }
                },
                enabled = !isRunningDiag,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = DevPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunningDiag) "Mendiagnostik..." else "Cek Lingkungan (Diagnostik)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DevPrimary
                )
            }

            // Filter Chips
            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text("Semua (${rawLogs.size})", fontSize = 11.sp) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF141414),
                    selectedContainerColor = DevPrimary,
                    selectedLabelColor = Color.Black
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.ERROR,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                label = { Text("Error (${rawLogs.count { it.level == LogLevel.ERROR }})", fontSize = 11.sp) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF141414),
                    selectedContainerColor = DevError,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.COMMAND,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.COMMAND) null else LogLevel.COMMAND },
                label = { Text("Command", fontSize = 11.sp) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF141414),
                    selectedContainerColor = DevWarning,
                    selectedLabelColor = Color.Black
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.OUTPUT,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.OUTPUT) null else LogLevel.OUTPUT },
                label = { Text("Output", fontSize = 11.sp) },
                shape = RoundedCornerShape(6.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF141414),
                    selectedContainerColor = DevSuccess,
                    selectedLabelColor = Color.Black
                )
            )
        }

        // Log Entries List with Native Selection Container
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalBackground)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada log tercatat.\nTekan tombol 'Cek Lingkungan' untuk mulai diagnostik.",
                        color = Color.DarkGray,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs) { entry ->
                        val badgeColor = when (entry.level) {
                            LogLevel.INFO -> DevSecondary
                            LogLevel.WARN -> DevWarning
                            LogLevel.ERROR -> DevError
                            LogLevel.COMMAND -> Color(0xFFE3B341)
                            LogLevel.OUTPUT -> DevSuccess
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "[${entry.level}]",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = badgeColor
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.tag,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.LightGray
                                        )
                                    }
                                    Text(
                                        text = entry.timestamp,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.DarkGray
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.message,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (entry.level == LogLevel.ERROR) DevError else Color(0xFFC9D1D9)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
