package com.arbdev.devbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.arbdev.devbox.ui.theme.DevBorder
import com.arbdev.devbox.ui.theme.DevCanvasDeep
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevOnPrimary
import com.arbdev.devbox.ui.theme.DevOnSurface
import com.arbdev.devbox.ui.theme.DevOnSurfaceVariant
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevPrimaryContainer
import com.arbdev.devbox.ui.theme.DevSecondary
import com.arbdev.devbox.ui.theme.DevSuccess
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import com.arbdev.devbox.ui.theme.DevWarning
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
        // 2026 Google M3 Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Logs & Diagnostics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DevOnSurface
                )
                Text(
                    text = "${rawLogs.size} events recorded",
                    fontSize = 11.sp,
                    color = DevOnSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        val allText = AppLogger.getAllAsText()
                        if (allText.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(allText))
                            Toast.makeText(context, "All logs copied to clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Log buffer is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    color = DevSurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy All Logs",
                            tint = DevPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        AppLogger.clear()
                        Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                    },
                    color = DevSurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = DevError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = DevBorder)

        // Diagnostics & Filter Pill Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = {
                    if (!isRunningDiag) {
                        scope.launch {
                            isRunningDiag = true
                            ChrootManager.runEnvironmentDiagnostics()
                            isRunningDiag = false
                        }
                    }
                },
                color = DevPrimaryContainer,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = DevPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunningDiag) "Inspecting..." else "Run Diagnostics",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DevPrimary
                    )
                }
            }

            FilterChip(
                selected = selectedLevel == null,
                onClick = { selectedLevel = null },
                label = { Text("All (${rawLogs.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DevSurfaceVariant,
                    labelColor = DevOnSurfaceVariant,
                    selectedContainerColor = DevPrimary,
                    selectedLabelColor = DevOnPrimary
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.ERROR,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                label = { Text("Errors (${rawLogs.count { it.level == LogLevel.ERROR }})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DevSurfaceVariant,
                    labelColor = DevOnSurfaceVariant,
                    selectedContainerColor = DevError,
                    selectedLabelColor = DevOnPrimary
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.COMMAND,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.COMMAND) null else LogLevel.COMMAND },
                label = { Text("Commands", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DevSurfaceVariant,
                    labelColor = DevOnSurfaceVariant,
                    selectedContainerColor = DevWarning,
                    selectedLabelColor = Color.Black
                )
            )

            FilterChip(
                selected = selectedLevel == LogLevel.OUTPUT,
                onClick = { selectedLevel = if (selectedLevel == LogLevel.OUTPUT) null else LogLevel.OUTPUT },
                label = { Text("Output", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = DevSurfaceVariant,
                    labelColor = DevOnSurfaceVariant,
                    selectedContainerColor = DevPrimary,
                    selectedLabelColor = DevOnPrimary
                )
            )
        }

        // Log Entries List
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DevBackground)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No log events recorded.\nTap 'Run Diagnostics' to inspect container health.",
                        color = DevOnSurfaceVariant,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs) { entry ->
                        val badgeColor = when (entry.level) {
                            LogLevel.INFO -> DevPrimary
                            LogLevel.WARN -> DevWarning
                            LogLevel.ERROR -> DevError
                            LogLevel.COMMAND -> DevSecondary
                            LogLevel.OUTPUT -> DevSuccess
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DevCanvasDeep),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DevBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "[${entry.level}]",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = badgeColor
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.tag,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = DevOnSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = entry.timestamp,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = DevOnSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.message,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (entry.level == LogLevel.ERROR) DevError else DevOnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
