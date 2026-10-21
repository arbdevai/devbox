package com.arbdev.devbox.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.data.ChrootProcessInfo
import com.arbdev.devbox.terminal.TerminalInputView
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.terminal.NativeTerminalSession
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.util.AppLogger
import com.arbdev.devbox.ui.theme.DevOnSurface
import com.arbdev.devbox.ui.theme.DevOnSurfaceVariant
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevSecondary
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalCyan
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalText

@Composable
fun TerminalScreen(
    initialCommand: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeSession = TerminalSessionManager.getActiveSession()
    val activeIndex = TerminalSessionManager.activeIndex.intValue

    // Persistensi ukuran font terminal via SharedPreferences (default 12sp, rentang 8sp - 28sp)
    val prefs = remember { context.getSharedPreferences("devbox_terminal_prefs", Context.MODE_PRIVATE) }
    var terminalFontSize by remember { mutableFloatStateOf(prefs.getFloat("terminal_font_size", 12f)) }

    // Helper untuk mengubah dan menyimpan ukuran font secara smooth (step 0.5f)
    val updateFontSize: (Float) -> Unit = { newSize ->
        val clamped = (Math.round(newSize * 2f) / 2f).coerceIn(8f, 28f)
        terminalFontSize = clamped
        prefs.edit().putFloat("terminal_font_size", clamped).apply()
        Toast.makeText(context, "Ukuran font: ${String.format(java.util.Locale.US, "%.1f", clamped)} sp", Toast.LENGTH_SHORT).show()
    }

    val hapticFeedback = LocalHapticFeedback.current
    var activeUrlPrompt by remember { mutableStateOf<String?>(null) }
    var urlListPrompt by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(activeSession) {
        activeSession.onClipboardCopy = { text ->
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, "Disalin via terminal (OSC 52)", Toast.LENGTH_SHORT).show()
        }
        activeSession.onBell = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Modifier states untuk virtual modifier keys
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }

    // Dialog proteksi teks clipboard sangat panjang (anti-loop CLI)
    var pendingLargePaste by remember { mutableStateOf<String?>(null) }

    // Dialog ubah nama sesi
    var sessionToRename by remember { mutableStateOf<NativeTerminalSession?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    // Manajer Sesi & Pemantau Background Sesi Usang (Orphan Process Manager)
    val coroutineScope = rememberCoroutineScope()
    var orphanProcesses by remember { mutableStateOf<List<ChrootProcessInfo>>(emptyList()) }
    var isCleaningOrphans by remember { mutableStateOf(false) }
    var showSessionManagerDialog by remember { mutableStateOf(false) }

    // Pemindaian proses orphan HANYA dilakukan saat dialog manajer sesi dibuka (nol pemanggilan su liar di background)
    LaunchedEffect(showSessionManagerDialog) {
        if (showSessionManagerDialog) {
            val activeIds = TerminalSessionManager.sessions.map { it.id }
            val activePids = TerminalSessionManager.sessions.mapNotNull { it.getProcessPid() }
            orphanProcesses = ChrootManager.getOrphanChrootProcesses(activeIds, activePids)
        }
    }

    // View input terminal berbasis BaseInputConnection (gaya Termux, tanpa Compose TextField delay)
    var terminalInputView by remember { mutableStateOf<TerminalInputView?>(null) }

    // Scroll state terisolasi per sesi (mencegah desinkronisasi viewport saat switch antar sesi)
    val verticalScrollState = remember(activeSession.id) { ScrollState(0) }
    val horizontalScrollState = remember(activeSession.id) { ScrollState(0) }

    // State auto-scroll cerdas: tetap mengikuti baris terbawah kecuali pengguna sengaja scroll ke atas
    var autoScrollEnabled by remember(activeSession.id) { mutableStateOf(true) }

    // Pantau jika pengguna menggeser scroll manual
    LaunchedEffect(verticalScrollState.isScrollInProgress) {
        if (verticalScrollState.isScrollInProgress) {
            val distFromBottom = verticalScrollState.maxValue - verticalScrollState.value
            // Jika user scroll ke atas (> 120px dari bawah), tahan auto-scroll agar nyaman membaca teks/log
            // Jika user scroll kembali mendekati bawah (<= 120px), aktifkan lagi auto-scroll
            autoScrollEnabled = distFromBottom <= 120
        }
    }

    // Helper navigasi tombol panah yang menghormati DECCKM (Application Cursor Mode pada TUI seperti agy)
    val sendArrowUp = {
        if (activeSession.buffer.isApplicationCursorKeys) activeSession.sendText("\u001BOA")
        else activeSession.sendText("\u001B[A")
    }
    val sendArrowDown = {
        if (activeSession.buffer.isApplicationCursorKeys) activeSession.sendText("\u001BOB")
        else activeSession.sendText("\u001B[B")
    }
    val sendArrowRight = {
        if (activeSession.buffer.isApplicationCursorKeys) activeSession.sendText("\u001BOC")
        else activeSession.sendText("\u001B[C")
    }
    val sendArrowLeft = {
        if (activeSession.buffer.isApplicationCursorKeys) activeSession.sendText("\u001BOD")
        else activeSession.sendText("\u001B[D")
    }

    LaunchedEffect(Unit) {
        KeepAliveService.startService(context)
        terminalInputView?.requestKeyboard()
    }

    LaunchedEffect(activeSession.isRunning, activeSession.currentDir, TerminalSessionManager.sessions.size) {
        KeepAliveService.updateNotification(context)
    }

    // Auto-scroll reaktif: mengikuti penambahan baris atau perubahan ukuran layar/keyboard (verticalScrollState.maxValue)
    LaunchedEffect(
        verticalScrollState.maxValue,
        activeSession.buffer.version.intValue,
        activeSession.buffer.isAltBuffer
    ) {
        if (activeSession.buffer.isAltBuffer) {
            if (verticalScrollState.value != 0) {
                verticalScrollState.scrollTo(0)
            }
        } else {
            if (autoScrollEnabled && verticalScrollState.maxValue > 0) {
                verticalScrollState.scrollTo(verticalScrollState.maxValue)
            }
        }
    }

    // Eksekusi initial command jika disediakan
    LaunchedEffect(initialCommand) {
        initialCommand?.let { cmd ->
            activeSession.runCommand(cmd)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .imePadding()
    ) {
        // Multi-Session Tab Row
        ScrollableTabRow(
            selectedTabIndex = activeIndex.coerceIn(0, (TerminalSessionManager.sessions.size - 1).coerceAtLeast(0)),
            containerColor = Color(0xFF0A0A0A),
            contentColor = DevPrimary,
            edgePadding = 8.dp,
            divider = {}
        ) {
            TerminalSessionManager.sessions.forEachIndexed { index, session ->
                Tab(
                    selected = activeIndex == index,
                    onClick = {
                        TerminalSessionManager.activeIndex.intValue = index
                    },
                    modifier = Modifier.pointerInput(session.id) {
                        detectTapGestures(
                            onTap = {
                                TerminalSessionManager.activeIndex.intValue = index
                            },
                            onLongPress = {
                                sessionToRename = session
                                renameInputText = session.name
                            }
                        )
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.name,
                                fontSize = 12.sp,
                                fontWeight = if (activeIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeIndex == index) DevPrimary else Color.Gray
                            )
                            if (session.isAgentRunning && session.activeAgentName != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                val infiniteTransition = rememberInfiniteTransition(label = "tabPulse_${session.id}")
                                val pulseAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.35f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(750, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulseAlpha"
                                )
                                Surface(
                                    color = Color(0xFF1B5E20).copy(alpha = pulseAlpha),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(Color(0xFF00E676), shape = CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = session.activeAgentName ?: "",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            if (TerminalSessionManager.sessions.size > 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { TerminalSessionManager.closeSession(index) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup Sesi",
                                        tint = if (activeIndex == index) DevPrimary else Color.DarkGray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Button to Add New Session
            IconButton(
                onClick = {
                    TerminalSessionManager.createNewSession()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Sesi Baru",
                    tint = DevPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Action Toolbar (Prompt status, Keyboard toggle, Zoom A-/A+, Copy Screen, Paste, Clear)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "coder@devbox",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ":${activeSession.getDisplayDir()}$",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalCyan
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (activeSession.isRunning) "• PTY" else "• SELESAI",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (activeSession.isRunning) DevPrimary else Color.Gray
                )
                if (activeSession.isAgentRunning && activeSession.activeAgentName != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF0D3315),
                        border = BorderStroke(1.dp, Color(0xFF00E676)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "barPulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "barPulseAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(0xFF00E676).copy(alpha = pulseAlpha), shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${activeSession.activeAgentName}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Manajer Sesi & Pembersih Background Orphan Sessions
                IconButton(
                    onClick = { showSessionManagerDialog = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Kelola Sesi",
                        tint = if (orphanProcesses.isNotEmpty()) Color(0xFFFF9800) else DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Toggle Soft Keyboard
                IconButton(
                    onClick = {
                        autoScrollEnabled = true
                        terminalInputView?.requestKeyboard()
                        keyboardController?.show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Buka Keyboard",
                        tint = DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Font Zoom Out (-) dengan step smooth 0.5f dan auto-save SharedPreferences
                IconButton(
                    onClick = {
                        updateFontSize(terminalFontSize - 0.5f)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        text = "A-",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText
                    )
                }

                // Font Zoom In (+) dengan step smooth 0.5f dan auto-save SharedPreferences
                IconButton(
                    onClick = {
                        updateFontSize(terminalFontSize + 0.5f)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        text = "A+",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalText
                    )
                }

                // Copy terminal screen transcript
                IconButton(
                    onClick = {
                        val text = activeSession.buffer.getTranscriptText()
                        if (text.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Layar terminal disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Tidak ada teks untuk disalin", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Layar",
                        tint = DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Ekstrak dan Buka Tautan Terminal (URL Scanner)
                IconButton(
                    onClick = {
                        val urls = activeSession.buffer.extractAllUrls()
                        when {
                            urls.isEmpty() -> {
                                Toast.makeText(context, "Tidak ada tautan (URL) di layar", Toast.LENGTH_SHORT).show()
                            }
                            urls.size == 1 -> {
                                activeUrlPrompt = urls.first()
                            }
                            else -> {
                                urlListPrompt = urls
                            }
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Pilih Tautan",
                        tint = DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Paste Clipboard directly into active PTY session
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            if (clip.length > 2000 || clip.count { it == '\n' } > 50) {
                                pendingLargePaste = clip
                            } else {
                                activeSession.pasteText(clip)
                            }
                        } else {
                            Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Tempel",
                        tint = DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Reset / Clear Screen (Ctrl+L)
                IconButton(
                    onClick = {
                        activeSession.sendBytes(byteArrayOf(12)) // Send Ctrl+L
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Layar",
                        tint = TerminalText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Banner Peringatan Sesi Latar Belakang Usang (Orphan Processes)
        AnimatedVisibility(
            visible = orphanProcesses.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val totalRamMb = orphanProcesses.sumOf { it.memoryKb } / 1024
            Surface(
                color = Color(0xFF231602),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${orphanProcesses.size} Sesi Usang Terdeteksi (~${totalRamMb} MB)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Sisa dari crash lama. Matikan agar RAM HP hemat.",
                                fontSize = 9.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isCleaningOrphans = true
                                val activeIds = TerminalSessionManager.sessions.map { it.id }
                                val activePids = TerminalSessionManager.sessions.mapNotNull { it.getProcessPid() }
                                val killed = ChrootManager.killOrphanChrootProcesses(activeIds, activePids)
                                orphanProcesses = emptyList()
                                isCleaningOrphans = false
                                Toast.makeText(context, "$killed sesi latar belakang berhasil dimatikan", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        enabled = !isCleaningOrphans,
                        modifier = Modifier.height(28.dp)
                    ) {
                        if (isCleaningOrphans) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Matikan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // Native Terminal Input View berbasis BaseInputConnection (Arsitektur Input Termux)
        AndroidView(
            factory = { ctx ->
                TerminalInputView(ctx).apply {
                    onCommitText = { text ->
                        if (isCtrlActive) {
                            val bytes = mutableListOf<Byte>()
                            for (ch in text) {
                                val code = when (ch.lowercaseChar()) {
                                    'c' -> 3
                                    'd' -> 4
                                    'z' -> 26
                                    'l' -> 12
                                    'a' -> 1
                                    'e' -> 5
                                    'u' -> 21
                                    'k' -> 11
                                    'w' -> 23
                                    in 'a'..'z' -> (ch.lowercaseChar() - 'a' + 1)
                                    else -> ch.code
                                }
                                bytes.add(code.toByte())
                            }
                            activeSession.sendBytes(bytes.toByteArray())
                            isCtrlActive = false
                        } else {
                            val sanitized = text.replace("\r\n", "\r").replace("\n", "\r")
                            activeSession.sendText(sanitized)
                        }
                    }
                    onBackspace = {
                        activeSession.sendBytes(byteArrayOf(127))
                    }
                    onSendKey = { keyCode ->
                        when (keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                sendArrowUp()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                sendArrowDown()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                sendArrowLeft()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                sendArrowRight()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_TAB -> {
                                if (isShiftActive) {
                                    activeSession.sendText("\u001B[Z")
                                    isShiftActive = false
                                } else {
                                    activeSession.sendBytes(byteArrayOf(9))
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_ESCAPE -> {
                                activeSession.sendBytes(byteArrayOf(27))
                                true
                            }
                            else -> false
                        }
                    }
                    terminalInputView = this
                }
            },
            update = { view ->
                terminalInputView = view
            },
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
        )

        // Native 2D VT100 Matrix Canvas / Terminal Text Area with Direct Glyph Drawing
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalBackground)
        ) {
            val density = LocalDensity.current
            LaunchedEffect(maxWidth, maxHeight, terminalFontSize) {
                with(density) {
                    val charWidth = (terminalFontSize * 0.6f).sp.toPx()
                    val charHeight = (terminalFontSize * 1.25f).sp.toPx()
                    if (charWidth > 0 && charHeight > 0) {
                        val cols = (maxWidth.toPx() / charWidth).toInt().coerceIn(20, 200)
                        val rows = (maxHeight.toPx() / charHeight).toInt().coerceIn(4, 100)
                        activeSession.updateWindowSize(cols, rows)
                    }
                }
            }

            Crossfade(
                targetState = activeSession,
                animationSpec = tween(120),
                label = "SessionCrossfade"
            ) { currentSession ->
                TerminalCanvas(
                    session = currentSession,
                    fontSizeSp = terminalFontSize,
                    verticalScrollState = verticalScrollState,
                    horizontalScrollState = horizontalScrollState,
                    isShiftActive = isShiftActive,
                    onFocusRequest = {
                        autoScrollEnabled = true
                        terminalInputView?.requestKeyboard()
                        keyboardController?.show()
                    },
                    onTapUrl = { url ->
                        activeUrlPrompt = url
                    },
                    onPasteLarge = { largeText ->
                        pendingLargePaste = largeText
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tombol Melayang Jump-to-Bottom (muncul saat auto-scroll dijeda karena scroll ke atas)
            if (!autoScrollEnabled && !activeSession.buffer.isAltBuffer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 16.dp, bottom = 16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    FloatingActionButton(
                        onClick = {
                            autoScrollEnabled = true
                            coroutineScope.launch {
                                verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                            }
                        },
                        containerColor = DevPrimary,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Scroll ke paling bawah",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Dialog Konfirmasi Tempel Teks Sangat Panjang (Mencegah Shell/CLI Looping akibat accidental log paste)
        pendingLargePaste?.let { largeText ->
            AlertDialog(
                onDismissRequest = { pendingLargePaste = null },
                title = { Text("Teks Sangat Panjang", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Clipboard berisi ${largeText.length} karakter (${largeText.lines().size} baris).\n\n" +
                        "Menempelkan teks log/kode raksasa ke prompt CLI dapat menyebabkan shell hang atau looping.\n\n" +
                        "Tetap tempelkan teks ini?",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val text = pendingLargePaste
                            pendingLargePaste = null
                            if (text != null) {
                                activeSession.pasteText(text)
                            }
                        }
                    ) {
                        Text("Tetap Tempel", color = DevPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLargePaste = null }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog Ubah Nama Sesi (Rename Session)
        sessionToRename?.let { sess ->
            AlertDialog(
                onDismissRequest = { sessionToRename = null },
                title = { Text("Ubah Nama Sesi", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Ketik nama kustom untuk sesi ini atau pilih 'Auto Detect' untuk mengikuti nama proses/folder secara otomatis.",
                            fontSize = 12.sp,
                            color = DevOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            label = { Text("Nama Sesi") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameInputText.isNotBlank()) {
                                sess.name = renameInputText.trim()
                                sess.isCustomName = true
                            }
                            sessionToRename = null
                        }
                    ) {
                        Text("Simpan", color = DevPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                sess.isCustomName = false
                                sess.name = NativeTerminalSession.cleanSessionTitle(sess.currentTitle)
                                sessionToRename = null
                            }
                        ) {
                            Text("Auto Detect", color = DevSecondary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { sessionToRename = null }) {
                            Text("Batal")
                        }
                    }
                }
            )
        }

        // Dialog Konfirmasi Buka Tautan (OSC 8 & Teks URL)
        activeUrlPrompt?.let { url ->
            AlertDialog(
                onDismissRequest = { activeUrlPrompt = null },
                title = { Text("Tautan Terminal", fontWeight = FontWeight.Bold) },
                text = { Text(url, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            activeUrlPrompt = null
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Gagal membuka link: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Buka di Browser", color = DevPrimary)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(url))
                            activeUrlPrompt = null
                            Toast.makeText(context, "URL disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Salin URL")
                    }
                }
            )
        }

        // Dialog Daftar Pilihan Tautan Terminal (Saat ditemukan lebih dari 1 URL di layar)
        urlListPrompt?.let { urls ->
            AlertDialog(
                onDismissRequest = { urlListPrompt = null },
                title = { Text("Pilih Tautan Terminal (${urls.size})", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        urls.forEach { url ->
                            Surface(
                                color = Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = url,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = DevPrimary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        maxLines = 2
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(url))
                                                Toast.makeText(context, "URL disalin", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Salin",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                urlListPrompt = null
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Gagal membuka link: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
                                        ) {
                                            Text("Buka", fontSize = 11.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { urlListPrompt = null }) {
                        Text("Tutup")
                    }
                }
            )
        }

        // Dialog Kelola Sesi DevBox & Pembersih Latar Belakang (Orphan Processes)
        if (showSessionManagerDialog) {
            AlertDialog(
                onDismissRequest = { showSessionManagerDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = DevPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manajer Sesi & Latar Belakang", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Seksi 1: Sesi Aktif
                        Text(
                            text = "SESI AKTIF (${TerminalSessionManager.sessions.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        TerminalSessionManager.sessions.forEachIndexed { idx, s ->
                            Surface(
                                color = if (idx == activeIndex) Color(0xFF1E281E) else Color(0xFF181818),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (idx == activeIndex) DevPrimary else Color.DarkGray),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        TerminalSessionManager.activeIndex.intValue = idx
                                        showSessionManagerDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${idx + 1}. ${s.name}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (idx == activeIndex) DevPrimary else Color.White
                                            )
                                            if (s.isAgentRunning && s.activeAgentName != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "• ${s.activeAgentName}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF00E676),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Dir: ${s.getDisplayDir()}",
                                            fontSize = 10.sp,
                                            color = DevOnSurfaceVariant
                                        )
                                    }
                                    if (TerminalSessionManager.sessions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                TerminalSessionManager.closeSession(idx)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Tutup Sesi",
                                                tint = Color.Red,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Seksi 2: Sesi Latar Belakang Lama (Orphan Processes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SESI USANG BACKGROUND (${orphanProcesses.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (orphanProcesses.isNotEmpty()) Color(0xFFFF9800) else Color.Gray
                            )
                            if (orphanProcesses.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isCleaningOrphans = true
                                            val activeIds = TerminalSessionManager.sessions.map { it.id }
                                            val activePids = TerminalSessionManager.sessions.mapNotNull { it.getProcessPid() }
                                            val killed = ChrootManager.killOrphanChrootProcesses(activeIds, activePids)
                                            orphanProcesses = emptyList()
                                            isCleaningOrphans = false
                                            Toast.makeText(context, "$killed sesi latar belakang berhasil dimatikan", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isCleaningOrphans,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Matikan Semua", fontSize = 11.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        if (orphanProcesses.isEmpty()) {
                            Text(
                                text = "✓ Tidak ada sesi usang di latar belakang (RAM bersih)",
                                fontSize = 11.sp,
                                color = Color(0xFF81C784)
                            )
                        } else {
                            orphanProcesses.forEach { orphan ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PID ${orphan.pid}: ${orphan.name}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFCC80)
                                    )
                                    Text(
                                        text = "${orphan.memoryKb / 1024} MB",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = DevOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            TerminalSessionManager.createNewSession()
                            showSessionManagerDialog = false
                        }
                    ) {
                        Text("+ Tambah Sesi", color = DevPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSessionManagerDialog = false }) {
                        Text("Tutup")
                    }
                }
            )
        }

        // Comprehensive Mobile Extra Keys Panel (2 Scrollable Rows for FlorisBoard / Mobile Terminal)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalBackground)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Essential Modifiers, Navigation & Line Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ESC
                TerminalModifierKey(label = "ESC") {
                    activeSession.sendBytes(byteArrayOf(27))
                }

                // TAB / SHIFT+TAB (Backtab untuk navigasi mundur di TUI agy/fzf/nano)
                TerminalModifierKey(label = if (isShiftActive) "⇧TAB" else "TAB", highlight = isShiftActive) {
                    if (isShiftActive) {
                        activeSession.sendText("\u001B[Z")
                        isShiftActive = false
                    } else {
                        activeSession.sendBytes(byteArrayOf(9))
                    }
                }

                // Sticky CTRL
                TerminalToggleKey(label = "CTRL", isActive = isCtrlActive) {
                    isCtrlActive = !isCtrlActive
                }

                // Quick Ctrl+C (Interrupt/Hentikan proses yang looping/hang)
                TerminalModifierKey(label = "Ctrl+C", isSignal = true) {
                    activeSession.cancelActiveProcess()
                }

                // Sticky ALT
                TerminalToggleKey(label = "ALT", isActive = isAltActive) {
                    isAltActive = !isAltActive
                }

                // Sticky SHIFT
                TerminalToggleKey(label = "SHIFT", isActive = isShiftActive) {
                    isShiftActive = !isShiftActive
                }

                // Dedicated ENTER
                TerminalModifierKey(label = "ENTER", highlight = true) {
                    activeSession.sendText("\r")
                }

                // Backspace (⌫)
                TerminalModifierKey(label = "⌫") {
                    activeSession.sendBytes(byteArrayOf(127))
                }

                // Delete forward (DEL)
                TerminalModifierKey(label = "DEL") {
                    activeSession.sendText("\u001B[3~")
                }

                // Arrow Left ◀
                TerminalModifierKey(label = "◀") {
                    sendArrowLeft()
                }

                // Arrow Down ▼ (Memilih menu agy)
                TerminalModifierKey(label = "▼") {
                    sendArrowDown()
                }

                // Arrow Up ▲ (Memilih menu agy)
                TerminalModifierKey(label = "▲") {
                    sendArrowUp()
                }

                // Arrow Right ▶
                TerminalModifierKey(label = "▶") {
                    sendArrowRight()
                }

                // HOME
                TerminalModifierKey(label = "HOME") {
                    activeSession.sendText("\u001B[H")
                }

                // END
                TerminalModifierKey(label = "END") {
                    activeSession.sendText("\u001B[F")
                }

                // PGUP
                TerminalModifierKey(label = "PGUP") {
                    activeSession.sendText("\u001B[5~")
                }

                // PGDN
                TerminalModifierKey(label = "PGDN") {
                    activeSession.sendText("\u001B[6~")
                }
            }

            // Row 2: Tombol Fungsi F1 - F12 & Simbol Shell Pemrograman
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tombol Fungsi F1 - F12 (Standar VT100/XTerm untuk mc, htop, nano, dsb.)
                val functionKeys = listOf(
                    "F1" to "\u001BOP",
                    "F2" to "\u001BOQ",
                    "F3" to "\u001BOR",
                    "F4" to "\u001BOS",
                    "F5" to "\u001B[15~",
                    "F6" to "\u001B[17~",
                    "F7" to "\u001B[18~",
                    "F8" to "\u001B[19~",
                    "F9" to "\u001B[20~",
                    "F10" to "\u001B[21~",
                    "F11" to "\u001B[23~",
                    "F12" to "\u001B[24~"
                )
                functionKeys.forEach { (label, seq) ->
                    TerminalModifierKey(label = label) {
                        activeSession.sendText(seq)
                    }
                }

                // Shell & Programming Symbols
                val symbols = listOf(
                    "~", "/", "-", "_", "|", "$", "=", ":", ";",
                    "&", "*", "\\", "\"", "'", "`", "<", ">", "!",
                    "?", "(", ")", "[", "]", "{", "}"
                )
                symbols.forEach { sym ->
                    TerminalModifierKey(label = sym) {
                        activeSession.sendText(sym)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalModifierKey(
    label: String,
    highlight: Boolean = false,
    isSignal: Boolean = false,
    onClick: () -> Unit
) {
    val containerBg = when {
        isSignal -> Color(0xFF3E1214)
        highlight -> DevPrimary.copy(alpha = 0.25f)
        else -> Color(0xFF1E1E1E)
    }

    val textColor = when {
        isSignal -> Color(0xFFFF7B72)
        highlight -> DevPrimary
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerBg)
            .clickable {
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}

@Composable
fun TerminalToggleKey(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val containerBg = if (isActive) DevPrimary else Color(0xFF1E1E1E)
    val textColor = if (isActive) Color.Black else Color.White

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerBg)
            .clickable {
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = textColor
        )
    }
}

/**
 * Titik koordinat matriks terminal untuk text selection native.
 */
data class GridPoint(val line: Int, val col: Int) : Comparable<GridPoint> {
    override fun compareTo(other: GridPoint): Int {
        if (line != other.line) return line.compareTo(other.line)
        return col.compareTo(other.col)
    }
}

/**
 * Direct Canvas Glyph & Monospace Matrix Terminal Renderer (Opsi 1).
 * Menggambar teks dan latar belakang langsung ke GPU Canvas (Skia drawText/drawRect),
 * menghasilkan 60-120 FPS bebas lag, nol alokasi SpanStyle pada CPU Helio G35.
 * Mendukung Native Android Floating ActionMode Text Selection (Copy, Paste, Select All, Share).
 */
@Composable
fun TerminalCanvas(
    session: com.arbdev.devbox.terminal.NativeTerminalSession,
    fontSizeSp: Float,
    verticalScrollState: ScrollState,
    horizontalScrollState: ScrollState,
    isShiftActive: Boolean = false,
    onFocusRequest: () -> Unit,
    onTapUrl: (String) -> Unit,
    onPasteLarge: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var currentActionMode by remember { mutableStateOf<ActionMode?>(null) }
    var selectionStart by remember { mutableStateOf<GridPoint?>(null) }
    var selectionEnd by remember { mutableStateOf<GridPoint?>(null) }
    var isSelectingText by remember { mutableStateOf(false) }

    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }

    val textPaint = remember(fontSizeSp, density) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = fontSizePx
        }
    }

    val charWidth = remember(textPaint) { textPaint.measureText("M").coerceAtLeast(1f) }
    val fontMetrics = remember(textPaint) { textPaint.fontMetrics }
    val charHeight = remember(fontMetrics) { ((fontMetrics.descent - fontMetrics.ascent) * 1.25f).coerceAtLeast(1f) }
    val baselineOffset = remember(fontMetrics, charHeight) {
        -fontMetrics.ascent + (charHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f
    }

    // Trigger recompose saat buffer terminal diperbarui
    val version = session.buffer.version.intValue
    val isAltBuffer = session.buffer.isAltBuffer
    val lineCount = remember(version, isAltBuffer) {
        if (isAltBuffer) session.buffer.maxRows
        else session.buffer.getLineCount().coerceAtLeast(1)
    }
    val totalWidthPx = (session.buffer.maxCols * charWidth).toInt().coerceAtLeast(1)
    val totalHeightPx = (lineCount * charHeight).toInt().coerceAtLeast(1)

    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val totalHeightDp = with(density) { totalHeightPx.toDp() }

    fun findWordBounds(lineIdx: Int, col: Int): Pair<Int, Int> {
        val line = session.buffer.getLineAt(lineIdx) ?: return col to col
        val maxC = session.buffer.maxCols
        if (col !in 0 until maxC) return col to col

        val targetCell = line[col]
        if (targetCell.char == ' ') {
            // Jika menekan spasi, cari kata terdekat di sebelah kiri atau kanan
            var left = col
            while (left > 0 && line[left - 1].char == ' ') left--
            if (left > 0) return findWordBounds(lineIdx, left - 1)
            var right = col
            while (right < maxC - 1 && line[right + 1].char == ' ') right++
            if (right < maxC - 1) return findWordBounds(lineIdx, right + 1)
            return col to col
        }

        var start = col
        while (start > 0 && line[start - 1].char != ' ') {
            start--
        }
        var end = col
        while (end < maxC - 1 && line[end + 1].char != ' ') {
            end++
        }
        return start to end
    }

    fun getSelectedText(p1: GridPoint, p2: GridPoint): String {
        val start = minOf(p1, p2)
        val end = maxOf(p1, p2)
        val sb = StringBuilder()
        for (l in start.line..end.line) {
            val line = session.buffer.getLineAt(l) ?: continue
            val colStart = if (l == start.line) start.col else 0
            val colEnd = if (l == end.line) end.col else session.buffer.maxCols - 1
            val lineText = StringBuilder()
            for (c in colStart..colEnd) {
                val cell = line[c]
                if (cell.isWideContinuation) continue
                lineText.append(cell.char)
                if (cell.extraChar != null) lineText.append(cell.extraChar)
            }
            sb.append(lineText.toString().trimEnd())
            if (l < end.line) sb.append('\n')
        }
        return sb.toString()
    }

    fun getSelectionBoundingRect(p1: GridPoint, p2: GridPoint): android.graphics.Rect {
        val start = minOf(p1, p2)
        val end = maxOf(p1, p2)
        val scrollX = horizontalScrollState.value
        val scrollY = if (!isAltBuffer) verticalScrollState.value else 0

        val left = ((start.col * charWidth - scrollX).toInt()).coerceAtLeast(0)
        val top = ((start.line * charHeight - scrollY).toInt()).coerceAtLeast(0)
        val right = (((end.col + 1) * charWidth - scrollX).toInt()).coerceAtLeast(left + 1)
        val bottom = (((end.line + 1) * charHeight - scrollY).toInt()).coerceAtLeast(top + 1)

        return android.graphics.Rect(left, top, right, bottom)
    }

    val actionModeCallback = remember(session, clipboardManager, context) {
        object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, 1, 1, android.R.string.copy).apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                menu.add(Menu.NONE, 2, 2, android.R.string.paste).apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                menu.add(Menu.NONE, 3, 3, android.R.string.selectAll).apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                menu.add(Menu.NONE, 5, 4, "Salin Baris").apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                menu.add(Menu.NONE, 4, 5, "Bagikan").apply {
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    1 -> { // Salin Teks Terpilih
                        val start = selectionStart
                        val end = selectionEnd
                        if (start != null && end != null) {
                            val text = getSelectedText(start, end)
                            if (text.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Teks disalin (${text.length} karakter)", Toast.LENGTH_SHORT).show()
                            }
                        }
                        mode.finish()
                        return true
                    }
                    2 -> { // Tempel
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            if (clip.length > 2000 || clip.count { it == '\n' } > 50) {
                                onPasteLarge(clip)
                            } else {
                                session.pasteText(clip)
                            }
                        } else {
                            Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                        }
                        mode.finish()
                        return true
                    }
                    3 -> { // Pilih Semua
                        val totalLines = if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()
                        val s = GridPoint(0, 0)
                        val e = GridPoint((totalLines - 1).coerceAtLeast(0), session.buffer.maxCols - 1)
                        selectionStart = s
                        selectionEnd = e
                        val text = getSelectedText(s, e)
                        if (text.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Semua teks disalin (${text.length} karakter)", Toast.LENGTH_SHORT).show()
                        }
                        mode.invalidateContentRect()
                        return true
                    }
                    5 -> { // Salin Baris yang Aktif
                        val lineIdx = selectionStart?.line ?: 0
                        val line = session.buffer.getLineAt(lineIdx)
                        val lineText = line?.filter { !it.isWideContinuation }?.map { it.char }?.joinToString("")?.trimEnd() ?: ""
                        if (lineText.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(lineText))
                            Toast.makeText(context, "Baris disalin", Toast.LENGTH_SHORT).show()
                        }
                        mode.finish()
                        return true
                    }
                    4 -> { // Bagikan
                        val start = selectionStart
                        val end = selectionEnd
                        if (start != null && end != null) {
                            val text = getSelectedText(start, end)
                            if (text.isNotEmpty()) {
                                try {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Bagikan Teks Terminal"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        mode.finish()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                selectionStart = null
                selectionEnd = null
                currentActionMode = null
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
                val start = selectionStart
                val end = selectionEnd
                if (start != null && end != null) {
                    outRect.set(getSelectionBoundingRect(start, end))
                } else {
                    super.onGetContentRect(mode, view, outRect)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (!isAltBuffer) Modifier.verticalScroll(verticalScrollState, enabled = !isSelectingText)
                else Modifier
            )
            .horizontalScroll(horizontalScrollState, enabled = !isSelectingText)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .pointerInput(session.buffer.isMouseTrackingEnabled, fontSizeSp, isAltBuffer, isShiftActive) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPressTimeout = if (isShiftActive) 0L else viewConfiguration.longPressTimeoutMillis
                    var isLongPress = false

                    if (isShiftActive) {
                        isLongPress = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFocusRequest()
                        currentActionMode?.finish()
                        currentActionMode = null
                        if (charWidth > 0 && charHeight > 0) {
                            val actualX = down.position.x + horizontalScrollState.value
                            val actualY = if (!isAltBuffer) down.position.y + verticalScrollState.value else down.position.y
                            val maxLine = (if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()) - 1
                            val col = (actualX / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                            val line = (actualY / charHeight).toInt().coerceIn(0, maxLine.coerceAtLeast(0))
                            isSelectingText = true
                            val (wStart, wEnd) = findWordBounds(line, col)
                            selectionStart = GridPoint(line, wStart)
                            selectionEnd = GridPoint(line, wEnd)
                        }
                    } else {
                        try {
                            withTimeout(longPressTimeout) {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    // Event TAP terdeteksi
                                    if (currentActionMode != null || selectionStart != null) {
                                        currentActionMode?.finish()
                                        currentActionMode = null
                                        selectionStart = null
                                        selectionEnd = null
                                        isSelectingText = false
                                    } else {
                                        onFocusRequest()
                                        if (charWidth > 0 && charHeight > 0) {
                                            val actualX = up.position.x + horizontalScrollState.value
                                            val actualY = if (!isAltBuffer) up.position.y + verticalScrollState.value else up.position.y
                                            val maxLine = (if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()) - 1
                                            val col = (actualX / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                                            val line = (actualY / charHeight).toInt().coerceIn(0, maxLine.coerceAtLeast(0))
                                            val historyCount = if (session.buffer.isAltBuffer) 0 else session.buffer.getScrollbackSnapshot().size
                                            val activeRow = line - historyCount

                                            val detectedUrl = session.buffer.getUrlAt(line, col)
                                            if (detectedUrl != null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onTapUrl(detectedUrl)
                                            } else if (session.buffer.isMouseTrackingEnabled) {
                                                if (activeRow in 0 until session.buffer.maxRows) {
                                                    session.sendMouseEvent(0, col, activeRow, true)
                                                    session.sendMouseEvent(0, col, activeRow, false)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: TimeoutCancellationException) {
                            // Timeout terlampaui -> Pengguna menahan jari (LONG PRESS / SELECTION)
                            isLongPress = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFocusRequest()
                            currentActionMode?.finish()
                            currentActionMode = null

                            if (charWidth > 0 && charHeight > 0) {
                                val actualX = down.position.x + horizontalScrollState.value
                                val actualY = if (!isAltBuffer) down.position.y + verticalScrollState.value else down.position.y
                                val maxLine = (if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()) - 1
                                val col = (actualX / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                                val line = (actualY / charHeight).toInt().coerceIn(0, maxLine.coerceAtLeast(0))

                                // Jika menekan pada atau dekat URL, langsung buka prompt tautan
                                val detectedUrl = session.buffer.getUrlAt(line, col)
                                if (detectedUrl != null) {
                                    onTapUrl(detectedUrl)
                                    isLongPress = false
                                } else {
                                    isSelectingText = true
                                    val (wStart, wEnd) = findWordBounds(line, col)
                                    selectionStart = GridPoint(line, wStart)
                                    selectionEnd = GridPoint(line, wEnd)
                                }
                            }
                        }
                    }

                    if (isLongPress && isSelectingText) {
                        // Loop drag untuk memperluas area seleksi secara bebas ke atas, bawah, kiri, kanan
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                if (charWidth > 0 && charHeight > 0) {
                                    val actualX = change.position.x + horizontalScrollState.value
                                    val actualY = if (!isAltBuffer) change.position.y + verticalScrollState.value else change.position.y
                                    val maxLine = (if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()) - 1
                                    val col = (actualX / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                                    val line = (actualY / charHeight).toInt().coerceIn(0, maxLine.coerceAtLeast(0))
                                    selectionEnd = GridPoint(line, col)
                                }
                            } else {
                                break
                            }
                        } while (event.changes.any { it.pressed })

                        isSelectingText = false
                        if (selectionStart != null && selectionEnd != null) {
                            currentActionMode = view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING)
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier.size(width = totalWidthDp, height = totalHeightDp)
        ) {
            val v = session.buffer.version.intValue // Daftarkan state di draw phase agar realtime redraw
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val maxCols = session.buffer.maxCols
            val cursorRow = session.buffer.cursorRow
            val cursorCol = session.buffer.cursorCol
            val isCursorVisible = session.buffer.isCursorVisible
            val historyCount = if (session.buffer.isAltBuffer) 0 else session.buffer.getScrollbackSnapshot().size

            val selStart = selectionStart
            val selEnd = selectionEnd
            val hasSelection = selStart != null && selEnd != null
            val selMin = if (hasSelection) minOf(selStart!!, selEnd!!) else null
            val selMax = if (hasSelection) maxOf(selStart!!, selEnd!!) else null

            // Viewport culling: Hanya render baris yang masuk dalam viewport layar
            val scrollY = if (isAltBuffer) 0f else verticalScrollState.value.toFloat()
            val viewportHeight = if (isAltBuffer || verticalScrollState.viewportSize == 0) {
                totalHeightPx.toFloat()
            } else {
                verticalScrollState.viewportSize.toFloat()
            }

            val firstVisibleLine = if (charHeight > 0) {
                ((scrollY - charHeight) / charHeight).toInt().coerceIn(0, lineCount - 1)
            } else 0
            val lastVisibleLine = if (charHeight > 0) {
                ((scrollY + viewportHeight + charHeight) / charHeight).toInt().coerceIn(0, lineCount - 1)
            } else lineCount - 1

            val textBatch = StringBuilder()

            for (lineIdx in firstVisibleLine..lastVisibleLine) {
                val line = session.buffer.getLineAt(lineIdx) ?: continue
                val yTop = lineIdx * charHeight
                val yText = yTop + baselineOffset

                // 1. Gambar latar belakang sel (Batched runs dengan warna sama)
                var bgCol = 0
                while (bgCol < maxCols) {
                    val startCol = bgCol
                    val cell = line[bgCol]
                    val isCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && bgCol == cursorCol)
                    val pt = GridPoint(lineIdx, bgCol)
                    val isSelected = hasSelection && selMin != null && selMax != null && pt >= selMin && pt <= selMax
                    val runBg = when {
                        isSelected -> Color(0x6638BDF8)
                        isCursor -> Color(0xFF61AFEF)
                        else -> cell.bgColor
                    }

                    val step = if (cell.isWide) 2 else 1
                    var runLen = step
                    bgCol += step

                    while (bgCol < maxCols) {
                        val nextCell = line[bgCol]
                        val nextIsCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && bgCol == cursorCol)
                        val nextPt = GridPoint(lineIdx, bgCol)
                        val nextIsSelected = hasSelection && selMin != null && selMax != null && nextPt >= selMin && nextPt <= selMax
                        val nextBg = when {
                            nextIsSelected -> Color(0x6638BDF8)
                            nextIsCursor -> Color(0xFF61AFEF)
                            else -> nextCell.bgColor
                        }
                        if (nextBg == runBg) {
                            val nextStep = if (nextCell.isWide) 2 else 1
                            runLen += nextStep
                            bgCol += nextStep
                        } else {
                            break
                        }
                    }

                    if (runBg != Color.Transparent) {
                        drawRect(
                            color = runBg,
                            topLeft = Offset(startCol * charWidth, yTop),
                            size = Size(runLen * charWidth, charHeight)
                        )
                    }
                }

                // 2. Gambar karakter teks (Batched text runs per style untuk 60 FPS di CPU low-end)
                var col = 0
                while (col < maxCols) {
                    val cell = line[col]
                    if (cell.isWideContinuation) {
                        col++
                        continue
                    }

                    val pt = GridPoint(lineIdx, col)
                    val isSelected = hasSelection && selMin != null && selMax != null && pt >= selMin && pt <= selMax
                    val isCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && col == cursorCol)
                    val fg = when {
                        isSelected -> Color.White
                        isCursor -> Color.Black
                        else -> cell.fgColor
                    }
                    val isBold = cell.isBold || isSelected
                    val isUnderline = cell.isUnderline || cell.hyperlinkUrl != null

                    // Karakter dengan extra combining mark
                    if (cell.extraChar != null) {
                        textPaint.color = fg.toArgb()
                        textPaint.isFakeBoldText = isBold
                        textPaint.isUnderlineText = isUnderline
                        nativeCanvas.drawText("${cell.char}${cell.extraChar}", col * charWidth, yText, textPaint)
                        col++
                        continue
                    }

                    // Karakter wide (CJK / wide symbol)
                    if (cell.isWide) {
                        textPaint.color = fg.toArgb()
                        textPaint.isFakeBoldText = isBold
                        textPaint.isUnderlineText = isUnderline
                        nativeCanvas.drawText(cell.char.toString(), col * charWidth, yText, textPaint)
                        col++
                        continue
                    }

                    // Kumpulkan rentetan karakter normal dengan style dan warna yang persis sama
                    val startCol = col
                    textBatch.setLength(0)

                    while (col < maxCols) {
                        val curCell = line[col]
                        if (curCell.isWideContinuation || curCell.isWide || curCell.extraChar != null) {
                            break
                        }
                        val curPt = GridPoint(lineIdx, col)
                        val curIsSelected = hasSelection && selMin != null && selMax != null && curPt >= selMin && curPt <= selMax
                        val curIsCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && col == cursorCol)
                        val curFg = when {
                            curIsSelected -> Color.White
                            curIsCursor -> Color.Black
                            else -> curCell.fgColor
                        }
                        val curBold = curCell.isBold || curIsSelected
                        val curUnderline = curCell.isUnderline || curCell.hyperlinkUrl != null

                        if (curFg == fg && curBold == isBold && curUnderline == isUnderline) {
                            textBatch.append(curCell.char)
                            col++
                        } else {
                            break
                        }
                    }

                    if (textBatch.isNotEmpty()) {
                        val runStr = textBatch.toString()
                        if (runStr.isNotBlank()) {
                            textPaint.color = fg.toArgb()
                            textPaint.isFakeBoldText = isBold
                            textPaint.isUnderlineText = isUnderline
                            nativeCanvas.drawText(runStr, startCol * charWidth, yText, textPaint)
                        }
                    }
                }

                // Kursor di luar batas kolom karakter
                if (isCursorVisible && (lineIdx - historyCount) == cursorRow && cursorCol >= maxCols) {
                    drawRect(
                        color = Color(0xFF61AFEF),
                        topLeft = Offset((maxCols - 1) * charWidth, yTop),
                        size = Size(charWidth, charHeight)
                    )
                }
            }

            // Visual Draggable Handles untuk Penanda Seleksi Teks (Termux Selection Pins)
            if (hasSelection && selMin != null && selMax != null) {
                val startX = selMin.col * charWidth
                val startY = selMin.line * charHeight + charHeight
                drawCircle(
                    color = Color(0xFF61AFEF),
                    radius = 4.dp.toPx(),
                    center = Offset(startX, startY)
                )

                val endX = (selMax.col + 1) * charWidth
                val endY = selMax.line * charHeight + charHeight
                drawCircle(
                    color = Color(0xFF61AFEF),
                    radius = 4.dp.toPx(),
                    center = Offset(endX, endY)
                )
            }
        }
    }
}
