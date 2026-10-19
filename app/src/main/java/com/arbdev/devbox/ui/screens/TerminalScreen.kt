package com.arbdev.devbox.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalCyan
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalText
import com.arbdev.devbox.util.AppLogger
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TerminalScreen(
    initialCommand: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val activeSession = TerminalSessionManager.getActiveSession()
    val activeIndex = TerminalSessionManager.activeIndex.intValue

    val terminalViewHolder = remember { arrayOfNulls<TerminalView>(1) }
    var terminalFontSize by remember { mutableIntStateOf(12) }

    // Modifier states for virtual keyboard integration
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        KeepAliveService.startService(context)
    }

    LaunchedEffect(activeSession.isRunning, activeSession.currentDir, TerminalSessionManager.sessions.size) {
        KeepAliveService.updateNotification(context)
    }

    // Execute initial command if provided
    LaunchedEffect(initialCommand) {
        initialCommand?.let { cmd ->
            activeSession.runCommand(cmd)
        }
    }

    LaunchedEffect(activeSession) {
        activeSession.onClipboardCopy = { text ->
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, "Teks disalin ke clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
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

        // Action Toolbar (Prompt, Keyboard toggle, Font size, Paste, Clear)
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
                // Toggle Soft Keyboard (FlorisBoard / Gboard)
                IconButton(
                    onClick = {
                        terminalViewHolder[0]?.let { tv ->
                            tv.requestFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                        }
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
                        if (terminalFontSize > 8) {
                            terminalFontSize -= 1
                            terminalViewHolder[0]?.setTextSize(terminalFontSize)
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
                        if (terminalFontSize < 24) {
                            terminalFontSize += 1
                            terminalViewHolder[0]?.setTextSize(terminalFontSize)
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
                        val text = activeSession.termSession?.emulator?.screen?.transcriptText
                        if (!text.isNullOrEmpty()) {
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

                // Paste Clipboard directly into active PTY session
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            activeSession.sendText(clip)
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

        // Native 2D VT100 Matrix Canvas TerminalView (Termux Core Engine)
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    setTextSize(terminalFontSize)
                    setTypeface(Typeface.MONOSPACE)
                    setTerminalViewClient(object : TerminalViewClient {
                        override fun onScale(scale: Float): Float {
                            return scale
                        }

                        override fun onSingleTapUp(e: MotionEvent) {
                            this@apply.requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                        }

                        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

                        // Crucial for FlorisBoard & Gboard: keep false so IME dispatches text normally without double echo or lag
                        override fun shouldEnforceCharBasedInput(): Boolean = false

                        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

                        override fun isTerminalViewSelected(): Boolean = true

                        override fun copyModeChanged(copyMode: Boolean) {}

                        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

                        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

                        override fun onLongPress(event: MotionEvent): Boolean = false

                        override fun readControlKey(): Boolean = isCtrlActive

                        override fun readAltKey(): Boolean = isAltActive

                        override fun readShiftKey(): Boolean = isShiftActive

                        override fun readFnKey(): Boolean = false

                        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
                            if (isCtrlActive || isAltActive || isShiftActive) {
                                isCtrlActive = false
                                isAltActive = false
                                isShiftActive = false
                            }
                            return false
                        }

                        override fun onEmulatorSet() {}

                        override fun logError(tag: String, message: String) {
                            AppLogger.e(tag, message)
                        }

                        override fun logWarn(tag: String, message: String) {
                            AppLogger.w(tag, message)
                        }

                        override fun logInfo(tag: String, message: String) {
                            AppLogger.i(tag, message)
                        }

                        override fun logDebug(tag: String, message: String) {
                            AppLogger.i(tag, "[DEBUG] $message")
                        }

                        override fun logVerbose(tag: String, message: String) {}

                        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                            AppLogger.e(tag, "$message: ${e.message}")
                        }

                        override fun logStackTrace(tag: String, e: Exception) {
                            AppLogger.e(tag, "Stack trace: ${e.message}")
                        }
                    })

                    terminalViewHolder[0] = this
                    activeSession.termSession?.let { attachSession(it) }
                }
            },
            update = { tv ->
                activeSession.termSession?.let { session ->
                    if (tv.currentSession != session) {
                        tv.attachSession(session)
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

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
                    activeSession.sendText("\u001B[D")
                }

                // Arrow Down ▼
                TerminalModifierKey(label = "▼") {
                    activeSession.sendText("\u001B[B")
                }

                // Arrow Up ▲
                TerminalModifierKey(label = "▲") {
                    activeSession.sendText("\u001B[A")
                }

                // Arrow Right ▶
                TerminalModifierKey(label = "▶") {
                    activeSession.sendText("\u001B[C")
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
        else -> Color(0xFF1C1C1C)
    }

    val textColor = when {
        isSignal -> Color(0xFFFF7B72)
        highlight -> DevPrimary
        else -> Color.White
    }

    FilterChip(
        selected = false,
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerBg
        )
    )
}

@Composable
fun TerminalToggleKey(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isActive) Color.Black else Color.White
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xFF1C1C1C),
            selectedContainerColor = DevPrimary
        )
    )
}
