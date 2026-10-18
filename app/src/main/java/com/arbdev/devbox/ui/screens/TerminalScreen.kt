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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.data.RootExecutor
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalCyan
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalRed
import com.arbdev.devbox.ui.theme.TerminalText
import com.arbdev.devbox.ui.theme.TerminalYellow
import kotlinx.coroutines.launch

data class TerminalLine(
    val text: String,
    val type: LineType = LineType.OUTPUT
)

enum class LineType {
    INPUT,
    OUTPUT,
    ERROR,
    SYSTEM
}

@Composable
fun TerminalScreen(
    initialCommand: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var currentDir by remember { mutableStateOf("/home/coder") }

    val displayDir = when {
        currentDir == "/home/coder" -> "~"
        currentDir.startsWith("/home/coder/") -> "~" + currentDir.removePrefix("/home/coder")
        else -> currentDir
    }
    val currentPrompt = "coder@devbox:$displayDir$ "

    val logLines = remember {
        mutableStateListOf(
            TerminalLine("DevBox Terminal [Ubuntu 24.04 Chroot Engine]", LineType.SYSTEM),
            TerminalLine("Tekan lama pada teks log untuk memunculkan menu copy khas Android.", LineType.SYSTEM),
            TerminalLine("Extra keys lengkap (CTRL, TAB, ESC, ALT, arah panah) tersedia di bawah.", LineType.SYSTEM),
            TerminalLine("------------------------------------------------------------", LineType.SYSTEM)
        )
    }

    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var inputState by remember { mutableStateOf(TextFieldValue("")) }
    var isRunning by remember { mutableStateOf(false) }

    // Modifier keys state
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    fun insertTextAtCursor(insert: String) {
        val cur = inputState.text
        val pos = inputState.selection.start
        val before = cur.substring(0, pos)
        val after = cur.substring(pos)
        inputState = inputState.copy(
            text = "$before$insert$after",
            selection = TextRange(pos + insert.length)
        )
        focusRequester.requestFocus()
    }

    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || isRunning) return

        history.add(trimmed)
        historyIndex = history.size
        logLines.add(TerminalLine("$currentPrompt$trimmed", LineType.INPUT))
        inputState = TextFieldValue("")

        scope.launch {
            isRunning = true

            if (trimmed == "clear") {
                logLines.clear()
                isRunning = false
                return@launch
            }

            // Ensure mounts are available
            ChrootManager.mountAll()

            // Construct command with currentDir and retrieve updated pwd
            val escapedCmd = trimmed.replace("'", "'\\''")
            val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

            val executionCmd = if (trimmed.startsWith("ub ") || trimmed == "ub") {
                "su -c '/data/data/com.termux/files/usr/bin/ub $trimmed'"
            } else {
                "su -c \"chroot ${ChrootManager.DEFAULT_ROOTFS} /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash -c 'cd \\\"$currentDir\\\" && ($escapedCmd); __p__=\\\$(pwd); printf \\\"\\n__DEVBOX_PWD__:%s\\n\\\" \\\"\\\$__p__\\\"'\""
            }

            RootExecutor.executeStream(executionCmd) { outputLine ->
                if (outputLine.startsWith("__DEVBOX_PWD__:")) {
                    val newPath = outputLine.removePrefix("__DEVBOX_PWD__:").trim()
                    if (newPath.isNotEmpty() && newPath.startsWith("/")) {
                        currentDir = newPath
                    }
                    return@executeStream
                }

                val lineType = when {
                    outputLine.contains("[ERROR]", ignoreCase = true) || outputLine.contains("Permission denied", ignoreCase = true) -> LineType.ERROR
                    outputLine.contains("[WARN]", ignoreCase = true) -> LineType.SYSTEM
                    else -> LineType.OUTPUT
                }
                logLines.add(TerminalLine(outputLine, lineType))
            }

            isRunning = false
            listState.animateScrollToItem(logLines.size)
        }
    }

    LaunchedEffect(initialCommand) {
        initialCommand?.let {
            runCommand(it)
        }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .imePadding()
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "coder@devbox",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalGreen
                )
                Text(
                    text = ":$displayDir#",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalCyan
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Paste Button (Pastes clipboard directly into prompt cursor)
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            insertTextAtCursor(clip)
                            Toast.makeText(context, "Ditempel dari clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Tempel Teks",
                        tint = DevPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Copy All Button
                IconButton(
                    onClick = {
                        val allText = logLines.joinToString("\n") { it.text }
                        clipboardManager.setText(AnnotatedString(allText))
                        Toast.makeText(context, "Semua log berhasil disalin", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Semua Log",
                        tint = TerminalText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Clear Button
                IconButton(
                    onClick = { logLines.clear() }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Bersihkan Layar",
                        tint = TerminalText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Terminal Log History with NATIVE Long-Press Selection (No clickable interference)
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(logLines) { line ->
                    val color = when (line.type) {
                        LineType.INPUT -> TerminalGreen
                        LineType.OUTPUT -> TerminalText
                        LineType.ERROR -> TerminalRed
                        LineType.SYSTEM -> TerminalYellow
                    }
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = color
                    )
                }
            }
        }

        // Native Inline Prompt Input (Connected directly to currentDir)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF070707))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentPrompt,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalGreen
            )
            BasicTextField(
                value = inputState,
                onValueChange = { inputState = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                ),
                cursorBrush = SolidColor(DevPrimary),
                singleLine = true,
                enabled = !isRunning,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(
                    onSend = { runCommand(inputState.text) }
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (inputState.text.isEmpty() && !isRunning) {
                            Text(
                                text = "ketik perintah & tekan enter...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Termux-Style Complete Extra Keys Bar (CTRL, TAB, ESC, ALT, Arrows, Symbols)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ESC Key
            TerminalModifierKey(label = "ESC") {
                if (inputState.text.isNotEmpty()) {
                    inputState = TextFieldValue("")
                } else {
                    logLines.add(TerminalLine("$currentPrompt^[", LineType.SYSTEM))
                }
                focusRequester.requestFocus()
            }

            // TAB Key
            TerminalModifierKey(label = "TAB") {
                insertTextAtCursor("    ")
            }

            // CTRL Key Toggle
            TerminalToggleKey(label = "CTRL", isActive = isCtrlActive) {
                isCtrlActive = !isCtrlActive
            }

            // ALT Key Toggle
            TerminalToggleKey(label = "ALT", isActive = isAltActive) {
                isAltActive = !isAltActive
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

            // Arrow Up ▲ (History Previous)
            TerminalModifierKey(label = "▲") {
                if (history.isNotEmpty()) {
                    historyIndex = (historyIndex - 1).coerceAtLeast(0)
                    val cmd = history[historyIndex]
                    inputState = TextFieldValue(text = cmd, selection = TextRange(cmd.length))
                }
                focusRequester.requestFocus()
            }

            // Arrow Down ▼ (History Next)
            TerminalModifierKey(label = "▼") {
                if (history.isNotEmpty()) {
                    historyIndex = (historyIndex + 1).coerceAtMost(history.size)
                    val cmd = if (historyIndex < history.size) history[historyIndex] else ""
                    inputState = TextFieldValue(text = cmd, selection = TextRange(cmd.length))
                }
                focusRequester.requestFocus()
            }

            // Symbol Keys
            TerminalModifierKey(label = "~") { insertTextAtCursor("~") }
            TerminalModifierKey(label = "/") { insertTextAtCursor("/") }
            TerminalModifierKey(label = "-") { insertTextAtCursor("-") }
            TerminalModifierKey(label = "|") { insertTextAtCursor("|") }

            // Ctrl+C Quick Action
            TerminalModifierKey(label = "Ctrl+C") {
                logLines.add(TerminalLine("$currentPrompt^C", LineType.SYSTEM))
                inputState = TextFieldValue("")
                focusRequester.requestFocus()
            }
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
