package com.arbdev.devbox.ui.screens

import android.graphics.Typeface
import android.widget.ScrollView
import android.widget.TextView
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalCyan
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalText
import com.arbdev.devbox.util.AnsiParser
import com.arbdev.devbox.util.LineType
import com.arbdev.devbox.util.TerminalLine

@Composable
fun TerminalScreen(
    initialCommand: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val activeSession = TerminalSessionManager.getActiveSession()
    val activeIndex = TerminalSessionManager.activeIndex.intValue

    var inputState by remember { mutableStateOf(TextFieldValue("")) }

    // Modifier states for keyboard
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }

    var textViewRef by remember { mutableStateOf<TextView?>(null) }
    var scrollViewRef by remember { mutableStateOf<ScrollView?>(null) }

    fun insertTextAtCursor(insert: String) {
        val toInsert = if (isShiftActive) insert.uppercase() else insert
        if (isShiftActive) isShiftActive = false

        val cur = inputState.text
        val pos = inputState.selection.start.coerceIn(0, cur.length)
        val before = cur.substring(0, pos)
        val after = cur.substring(pos)
        inputState = inputState.copy(
            text = "$before$toInsert$after",
            selection = TextRange(pos + toInsert.length)
        )
        focusRequester.requestFocus()
    }

    fun submitCommand() {
        val text = inputState.text
        if (text.isBlank()) return
        if (activeSession.isRunning) {
            activeSession.sendInputToProcess(text)
        } else {
            activeSession.runCommand(text, scope)
        }
        inputState = TextFieldValue("")
        focusRequester.requestFocus()
    }

    LaunchedEffect(initialCommand) {
        initialCommand?.let { cmd ->
            activeSession.runCommand(cmd, scope)
        }
    }

    // Auto-scroll when new logs arrive in active session
    LaunchedEffect(activeSession.logLines.size) {
        scrollViewRef?.post {
            scrollViewRef?.fullScroll(ScrollView.FOCUS_DOWN)
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

        // Action Toolbar (Prompt, Paste, Copy All, Clear)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "coder@devbox",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ":${activeSession.getDisplayDir()}$",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeSession.isRunning) "• PROSES..." else "• SIAP",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (activeSession.isRunning) DevPrimary else Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Paste Button
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            insertTextAtCursor(clip)
                            Toast.makeText(context, "Ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Tempel",
                        tint = DevPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy All Button
                IconButton(
                    onClick = {
                        val allText = activeSession.logLines.joinToString("\n") { it.text }
                        clipboardManager.setText(AnnotatedString(allText))
                        Toast.makeText(context, "Semua teks terminal disalin", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Semua Log",
                        tint = TerminalText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear Terminal
                IconButton(
                    onClick = {
                        activeSession.logLines.clear()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Bersihkan Layar",
                        tint = TerminalText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Native Interactive Terminal Log Area using TextView with setTextIsSelectable(true)
        AndroidView(
            factory = { ctx ->
                ScrollView(ctx).apply {
                    isFillViewport = true
                    val tv = TextView(ctx).apply {
                        setTextIsSelectable(true)
                        typeface = Typeface.MONOSPACE
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#C9D1D9"))
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setPadding(20, 14, 20, 14)
                    }
                    addView(tv)
                    textViewRef = tv
                    scrollViewRef = this
                }
            },
            update = { _ ->
                textViewRef?.let { tv ->
                    val ssb = AnsiParser.buildSpannable(activeSession.logLines)
                    tv.text = ssb
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Native Inline Prompt Input Line (Zero Delay, 100% Android Keyboard Compatibility)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF070707))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Top breadcrumb row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "┌─[",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = "coder@devbox",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalCyan
                )
                Text(
                    text = "]─[",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = activeSession.getDisplayDir(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DevPrimary
                )
                Text(
                    text = "]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                if (activeSession.isRunning) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• [RUNNING - stdin aktif]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DevPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "└─❯ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeSession.isRunning) DevPrimary else TerminalGreen
                )

                BasicTextField(
                    value = inputState,
                    onValueChange = { inputState = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.White
                    ),
                    cursorBrush = SolidColor(DevPrimary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Ascii
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { submitCommand() }
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (inputState.text.isEmpty()) {
                                Text(
                                    text = if (activeSession.isRunning) "kirim respon stdin (y/n/teks)..." else "ketik perintah...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (inputState.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { submitCommand() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim",
                            tint = DevPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Termux & Agent CLI Extra Keys Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D0D))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ESC
            TerminalModifierKey(label = "ESC") {
                if (inputState.text.isNotEmpty()) {
                    inputState = TextFieldValue("")
                }
                focusRequester.requestFocus()
            }

            // TAB
            TerminalModifierKey(label = "TAB") {
                insertTextAtCursor("    ")
            }

            // CTRL Toggle
            TerminalToggleKey(label = "CTRL", isActive = isCtrlActive) {
                isCtrlActive = !isCtrlActive
            }

            // ALT Toggle
            TerminalToggleKey(label = "ALT", isActive = isAltActive) {
                isAltActive = !isAltActive
            }

            // SHIFT Toggle
            TerminalToggleKey(label = "SHIFT", isActive = isShiftActive) {
                isShiftActive = !isShiftActive
            }

            // Dedicated Ctrl+C Button
            TerminalModifierKey(label = "Ctrl+C") {
                if (activeSession.isRunning) {
                    activeSession.cancelActiveProcess()
                    Toast.makeText(context, "Proses dibatalkan (SIGINT)", Toast.LENGTH_SHORT).show()
                } else {
                    activeSession.logLines.add(TerminalLine("^C", LineType.SYSTEM))
                    inputState = TextFieldValue("")
                }
                focusRequester.requestFocus()
            }

            // Arrow Left ◀
            TerminalModifierKey(label = "◀") {
                val pos = inputState.selection.start
                if (pos > 0) {
                    inputState = inputState.copy(selection = TextRange(pos - 1))
                }
                focusRequester.requestFocus()
            }

            // Arrow Right ▶
            TerminalModifierKey(label = "▶") {
                val pos = inputState.selection.end
                if (pos < inputState.text.length) {
                    inputState = inputState.copy(selection = TextRange(pos + 1))
                }
                focusRequester.requestFocus()
            }

            // Arrow Up ▲ (Command History Previous)
            TerminalModifierKey(label = "▲") {
                if (activeSession.history.isNotEmpty()) {
                    activeSession.historyIndex = (activeSession.historyIndex - 1).coerceAtLeast(0)
                    val cmd = activeSession.history[activeSession.historyIndex]
                    inputState = TextFieldValue(text = cmd, selection = TextRange(cmd.length))
                }
                focusRequester.requestFocus()
            }

            // Arrow Down ▼ (Command History Next)
            TerminalModifierKey(label = "▼") {
                if (activeSession.history.isNotEmpty()) {
                    activeSession.historyIndex = (activeSession.historyIndex + 1).coerceAtMost(activeSession.history.size)
                    val cmd = if (activeSession.historyIndex < activeSession.history.size) {
                        activeSession.history[activeSession.historyIndex]
                    } else {
                        ""
                    }
                    inputState = TextFieldValue(text = cmd, selection = TextRange(cmd.length))
                }
                focusRequester.requestFocus()
            }

            // Symbols
            TerminalModifierKey(label = "~") { insertTextAtCursor("~") }
            TerminalModifierKey(label = "/") { insertTextAtCursor("/") }
            TerminalModifierKey(label = "-") { insertTextAtCursor("-") }
            TerminalModifierKey(label = "_") { insertTextAtCursor("_") }
            TerminalModifierKey(label = "|") { insertTextAtCursor("|") }
            TerminalModifierKey(label = "=") { insertTextAtCursor("=") }
            TerminalModifierKey(label = ":") { insertTextAtCursor(":") }
            TerminalModifierKey(label = "$") { insertTextAtCursor("$") }
        }
    }
}

@Composable
fun TerminalModifierKey(
    label: String,
    onClick: () -> Unit
) {
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
                color = Color.White
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xFF1C1C1C)
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
