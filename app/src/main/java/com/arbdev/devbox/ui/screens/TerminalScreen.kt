package com.arbdev.devbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

    val logLines = remember {
        mutableStateListOf(
            TerminalLine("DevBox Terminal [Ubuntu 24.04 Chroot Engine]", LineType.SYSTEM),
            TerminalLine("Ketik langsung pada baris prompt coder@devbox:~$ di bawah.", LineType.SYSTEM),
            TerminalLine("Gunakan tombol Paste untuk menempelkan perintah, atau Copy untuk menyalin.", LineType.SYSTEM),
            TerminalLine("------------------------------------------------------------", LineType.SYSTEM)
        )
    }

    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var inputText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || isRunning) return

        history.add(trimmed)
        historyIndex = history.size
        logLines.add(TerminalLine("coder@devbox:~$ $trimmed", LineType.INPUT))
        inputText = ""

        scope.launch {
            isRunning = true

            if (trimmed == "clear") {
                logLines.clear()
                isRunning = false
                return@launch
            }

            // Ensure mounts are available before execution
            ChrootManager.mountAll()

            // Run with explicit environment and PATH
            val executionCmd = if (trimmed.startsWith("ub ") || trimmed == "ub") {
                "su -c '/data/data/com.termux/files/usr/bin/ub $trimmed'"
            } else {
                val escapedCmd = trimmed.replace("'", "'\\''")
                val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                "su -c \"chroot ${ChrootManager.DEFAULT_ROOTFS} /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash -lc 'cd /home/coder && $escapedCmd'\""
            }

            RootExecutor.executeStream(executionCmd) { outputLine ->
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
            listState.animateScrollToItem(logLines.size)
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
                    text = ":~#",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalCyan
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Paste Button (Pastes clipboard directly to prompt)
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            inputText += clip
                            Toast.makeText(context, "Ditempel: $clip", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                        }
                        focusRequester.requestFocus()
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
                        Toast.makeText(context, "Semua teks terminal disalin", Toast.LENGTH_SHORT).show()
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

        // Terminal Output Area with Direct Inline Input
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Past Logs
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

                // Native Inline Input Row right on the prompt!
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "coder@devbox:~$ ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TerminalGreen
                        )
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
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
                                onSend = { runCommand(inputText) }
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (inputText.isEmpty() && !isRunning) {
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
                }
            }
        }

        // Quick Key & Navigation Bar (Docked right above keyboard)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // History Up Button
            FilterChip(
                selected = false,
                onClick = {
                    if (history.isNotEmpty()) {
                        historyIndex = (historyIndex - 1).coerceAtLeast(0)
                        inputText = history[historyIndex]
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Riwayat Atas",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                },
                label = { Text("Up", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF141414))
            )

            // History Down Button
            FilterChip(
                selected = false,
                onClick = {
                    if (history.isNotEmpty()) {
                        historyIndex = (historyIndex + 1).coerceAtMost(history.size)
                        inputText = if (historyIndex < history.size) history[historyIndex] else ""
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Riwayat Bawah",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                },
                label = { Text("Down", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF141414))
            )

            // Quick Chips
            TerminalKeyChip(label = "ls -la") { runCommand("ls -la") }
            TerminalKeyChip(label = "pwd") { runCommand("pwd") }
            TerminalKeyChip(label = "clear") { runCommand("clear") }
            TerminalKeyChip(label = "htop") { runCommand("htop") }
            TerminalKeyChip(label = "df -h") { runCommand("df -h") }
            TerminalKeyChip(label = "ub status") { runCommand("ub status") }
            TerminalKeyChip(label = "Ctrl+C") {
                logLines.add(TerminalLine("coder@devbox:~$ ^C", LineType.SYSTEM))
                inputText = ""
            }
        }
    }
}

@Composable
fun TerminalKeyChip(
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        },
        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF141414))
    )
}
