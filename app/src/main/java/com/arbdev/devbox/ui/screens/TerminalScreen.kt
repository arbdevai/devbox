package com.arbdev.devbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    val logLines = remember {
        mutableStateListOf(
            TerminalLine("DevBox Terminal [Ubuntu 24.04 Chroot Engine]", LineType.SYSTEM),
            TerminalLine("Teks output dapat dipilih dan disalin. Gunakan ikon copy untuk salin semua.", LineType.SYSTEM),
            TerminalLine("------------------------------------------------------------", LineType.SYSTEM)
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    fun runCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || isRunning) return

        logLines.add(TerminalLine("coder@devbox:~$ $trimmed", LineType.INPUT))
        inputText = ""
        focusManager.clearFocus()

        scope.launch {
            isRunning = true

            if (trimmed == "clear") {
                logLines.clear()
                isRunning = false
                return@launch
            }

            // Ensure mounts are available before command execution
            ChrootManager.mountAll()

            // Explicitly set PATH and environment so commands like ls, ps, htop work
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
            listState.animateScrollToItem(logLines.size - 1)
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
        // Terminal Header Bar (Vantablack OLED)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
            Row {
                IconButton(
                    onClick = {
                        val allText = logLines.joinToString("\n") { it.text }
                        clipboardManager.setText(AnnotatedString(allText))
                        Toast.makeText(context, "Semua teks terminal berhasil disalin", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Semua Log",
                        tint = TerminalText
                    )
                }
                IconButton(
                    onClick = { logLines.clear() }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Bersihkan Layar",
                        tint = TerminalText
                    )
                }
            }
        }

        // Terminal Output Area with SelectionContainer for text selection & copying
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

        // Quick Key Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TerminalKeyChip(label = "ls -la") { runCommand("ls -la") }
            TerminalKeyChip(label = "pwd") { runCommand("pwd") }
            TerminalKeyChip(label = "whoami") { runCommand("whoami") }
            TerminalKeyChip(label = "htop") { runCommand("htop") }
            TerminalKeyChip(label = "df -h") { runCommand("df -h") }
            TerminalKeyChip(label = "ps aux") { runCommand("ps aux") }
            TerminalKeyChip(label = "clear") { runCommand("clear") }
            TerminalKeyChip(label = "ub status") { runCommand("ub status") }
        }

        // Command Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (isRunning) "Menjalankan..." else "Ketik perintah...",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                enabled = !isRunning,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DevPrimary,
                    unfocusedBorderColor = Color(0xFF222222),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF050505),
                    unfocusedContainerColor = Color(0xFF050505)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { runCommand(inputText) })
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { runCommand(inputText) },
                enabled = !isRunning && inputText.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim",
                    tint = Color.Black
                )
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
