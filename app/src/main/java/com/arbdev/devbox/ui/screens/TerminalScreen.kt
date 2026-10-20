package com.arbdev.devbox.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.terminal.TerminalInputView
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.util.AppLogger
import com.arbdev.devbox.ui.theme.DevPrimary
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

    // Ukuran font terminal dengan dukungan pinch-to-zoom (rentang 8sp - 28sp)
    var terminalFontSize by remember { mutableFloatStateOf(12f) }
    val hapticFeedback = LocalHapticFeedback.current
    var activeUrlPrompt by remember { mutableStateOf<String?>(null) }

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

    // View input terminal berbasis BaseInputConnection (gaya Termux, tanpa Compose TextField delay)
    var terminalInputView by remember { mutableStateOf<TerminalInputView?>(null) }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

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

    // Auto-scroll cerdas: kunci posisi 0 jika AltBuffer (TUI), dan scroll ke bawah pada normal buffer hanya jika dekat posisi terbawah
    LaunchedEffect(activeSession.buffer.version.intValue, activeSession.buffer.isAltBuffer) {
        if (activeSession.buffer.isAltBuffer) {
            if (verticalScrollState.value != 0) {
                verticalScrollState.scrollTo(0)
            }
        } else {
            val isNearBottom = verticalScrollState.value >= (verticalScrollState.maxValue - 120).coerceAtLeast(0)
            if (isNearBottom && verticalScrollState.maxValue > 0) {
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

    // Gestur Pinch-to-Zoom (Cubit layar untuk zoom in / zoom out font)
    val zoomModifier = Modifier.pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            if (zoom != 1f) {
                terminalFontSize = (terminalFontSize * zoom).coerceIn(8f, 28f)
            }
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
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.name,
                                fontSize = 12.sp,
                                fontWeight = if (activeIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeIndex == index) DevPrimary else Color.Gray
                            )
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
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle Soft Keyboard
                IconButton(
                    onClick = {
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

                // Font Zoom Out (-)
                IconButton(
                    onClick = {
                        if (terminalFontSize > 8f) {
                            terminalFontSize = (terminalFontSize - 1f).coerceAtLeast(8f)
                        }
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

                // Font Zoom In (+)
                IconButton(
                    onClick = {
                        if (terminalFontSize < 28f) {
                            terminalFontSize = (terminalFontSize + 1f).coerceAtMost(28f)
                        }
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

                // Paste Clipboard directly into active PTY session (mendukung bracketed paste)
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            activeSession.pasteText(clip)
                            Toast.makeText(context, "Ditempel ke terminal", Toast.LENGTH_SHORT).show()
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
                                activeSession.sendBytes(byteArrayOf(9))
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
                .then(zoomModifier)
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

            TerminalCanvas(
                session = activeSession,
                fontSizeSp = terminalFontSize,
                verticalScrollState = verticalScrollState,
                horizontalScrollState = horizontalScrollState,
                onFocusRequest = {
                    terminalInputView?.requestKeyboard()
                    keyboardController?.show()
                },
                onTapUrl = { url ->
                    activeUrlPrompt = url
                },
                modifier = Modifier.fillMaxSize()
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

        // Comprehensive Mobile Extra Keys Panel (2 Scrollable Rows for FlorisBoard / Mobile Terminal)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D0D))
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

                // TAB
                TerminalModifierKey(label = "TAB") {
                    activeSession.sendBytes(byteArrayOf(9))
                }

                // Sticky CTRL
                TerminalToggleKey(label = "CTRL", isActive = isCtrlActive) {
                    isCtrlActive = !isCtrlActive
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

            // Row 2: POSIX Signals, Shortcuts & Programming Symbols
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ctrl+C (Dedicated SIGINT Kill)
                TerminalModifierKey(label = "Ctrl+C", isSignal = true) {
                    activeSession.cancelActiveProcess()
                    Toast.makeText(context, "SIGINT (Ctrl+C) terkirim", Toast.LENGTH_SHORT).show()
                }

                // Ctrl+Z (SIGTSTP Suspend)
                TerminalModifierKey(label = "Ctrl+Z") {
                    activeSession.sendBytes(byteArrayOf(26))
                }

                // Ctrl+D (EOF / Logout)
                TerminalModifierKey(label = "Ctrl+D") {
                    activeSession.sendBytes(byteArrayOf(4))
                }

                // Ctrl+L (Clear Screen)
                TerminalModifierKey(label = "Ctrl+L") {
                    activeSession.sendBytes(byteArrayOf(12))
                }

                // Ctrl+A (Jump to beginning of line)
                TerminalModifierKey(label = "Ctrl+A") {
                    activeSession.sendBytes(byteArrayOf(1))
                }

                // Ctrl+E (Jump to end of line)
                TerminalModifierKey(label = "Ctrl+E") {
                    activeSession.sendBytes(byteArrayOf(5))
                }

                // Ctrl+R (Reverse history search)
                TerminalModifierKey(label = "Ctrl+R") {
                    activeSession.sendBytes(byteArrayOf(18))
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
 * Direct Canvas Glyph & Monospace Matrix Terminal Renderer (Opsi 1).
 * Menggambar teks dan latar belakang langsung ke GPU Canvas (Skia drawText/drawRect),
 * menghasilkan 60-120 FPS bebas lag, nol alokasi SpanStyle pada CPU Helio G35.
 */
@Composable
fun TerminalCanvas(
    session: com.arbdev.devbox.terminal.NativeTerminalSession,
    fontSizeSp: Float,
    verticalScrollState: ScrollState,
    horizontalScrollState: ScrollState,
    onFocusRequest: () -> Unit,
    onTapUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
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
        else session.buffer.getLineCount().coerceAtLeast(session.buffer.maxRows)
    }
    val totalWidthPx = (session.buffer.maxCols * charWidth).toInt().coerceAtLeast(1)
    val totalHeightPx = (lineCount * charHeight).toInt().coerceAtLeast(1)

    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val totalHeightDp = with(density) { totalHeightPx.toDp() }

    Box(
        modifier = modifier
            .then(
                if (!isAltBuffer) Modifier.verticalScroll(verticalScrollState)
                else Modifier
            )
            .horizontalScroll(horizontalScrollState)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .pointerInput(session.buffer.isMouseTrackingEnabled, fontSizeSp, isAltBuffer) {
                detectTapGestures { offset ->
                    onFocusRequest()

                    if (charWidth > 0 && charHeight > 0) {
                        val actualX = offset.x + horizontalScrollState.value
                        val actualY = if (!isAltBuffer) offset.y + verticalScrollState.value else offset.y
                        val col = (actualX / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                        val line = (actualY / charHeight).toInt().coerceIn(0, (session.buffer.getLineCount() - 1).coerceAtLeast(0))
                        val historyCount = if (session.buffer.isAltBuffer) 0 else session.buffer.getScrollbackSnapshot().size
                        val activeRow = line - historyCount

                        if (session.buffer.isMouseTrackingEnabled) {
                            if (activeRow in 0 until session.buffer.maxRows) {
                                session.sendMouseEvent(0, col, activeRow, true)
                                session.sendMouseEvent(0, col, activeRow, false)
                            }
                        } else {
                            val detectedUrl = session.buffer.getUrlAt(line, col)
                            if (detectedUrl != null) {
                                onTapUrl(detectedUrl)
                            }
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

            AppLogger.i("TerminalCanvas", "Canvas redraw v$v: kursor=($cursorRow,$cursorCol), visible=${firstVisibleLine}..${lastVisibleLine}")

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
                    val runBg = if (isCursor) Color(0xFF61AFEF) else cell.bgColor

                    val step = if (cell.isWide) 2 else 1
                    var runLen = step
                    bgCol += step

                    while (bgCol < maxCols) {
                        val nextCell = line[bgCol]
                        val nextIsCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && bgCol == cursorCol)
                        val nextBg = if (nextIsCursor) Color(0xFF61AFEF) else nextCell.bgColor
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

                    val isCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && col == cursorCol)
                    val fg = if (isCursor) Color.Black else cell.fgColor
                    val isBold = cell.isBold
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
                        val curIsCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && col == cursorCol)
                        val curFg = if (curIsCursor) Color.Black else curCell.fgColor
                        val curBold = curCell.isBold
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
        }
    }
}
