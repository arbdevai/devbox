package com.arbdev.devbox.ui.screens

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Base64
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
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.data.RootExecutor
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalCyan
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalText
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
            TerminalLine("Tekan lama pada teks mana saja untuk seleksi & copy khas Android.", LineType.SYSTEM),
            TerminalLine("Extra keys: CTRL, TAB, ESC, SHIFT, ALT, panah kiri/kanan/atas/bawah.", LineType.SYSTEM),
            TerminalLine("------------------------------------------------------------", LineType.SYSTEM)
        )
    }

    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var inputState by remember { mutableStateOf(TextFieldValue("")) }
    var isRunning by remember { mutableStateOf(false) }

    // Modifier states
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }

    var textViewRef by remember { mutableStateOf<TextView?>(null) }
    var scrollViewRef by remember { mutableStateOf<ScrollView?>(null) }

    fun insertTextAtCursor(insert: String) {
        val toInsert = if (isShiftActive) insert.uppercase() else insert
        val cur = inputState.text
        val pos = inputState.selection.start
        val before = cur.substring(0, pos)
        val after = cur.substring(pos)
        inputState = inputState.copy(
            text = "$before$toInsert$after",
            selection = TextRange(pos + toInsert.length)
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

            // Ensure mounts are available before execution
            ChrootManager.mountAll()

            // Safe Base64-encoded execution script to preserve working directory (cd) without shell escaping bugs
            val scriptContent = """
cd "$currentDir" || exit 1
$trimmed
__code=$?
printf "\n___DEVBOX_PWD:%s\n" "${'$'}(pwd)"
exit ${'$'}__code
""".trimIndent()

            val b64Script = Base64.encodeToString(scriptContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val envPrefix = "env -i HOME=/home/coder USER=coder LOGNAME=coder SHELL=/bin/bash TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

            val executionCmd = if (trimmed.startsWith("ub ") || trimmed == "ub") {
                "su -c '/data/data/com.termux/files/usr/bin/ub $trimmed'"
            } else {
                "su -c \"echo '$b64Script' | base64 -d | chroot ${ChrootManager.DEFAULT_ROOTFS} /usr/bin/$envPrefix /usr/sbin/runuser -u coder -- /bin/bash\""
            }

            RootExecutor.executeStream(executionCmd) { outputLine ->
                if (outputLine.startsWith("___DEVBOX_PWD:")) {
                    val newPath = outputLine.substringAfter("___DEVBOX_PWD:").trim()
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

            // Auto-scroll to bottom
            scrollViewRef?.post {
                scrollViewRef?.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    LaunchedEffect(initialCommand) {
        initialCommand?.let {
            runCommand(it)
        }
    }

    LaunchedEffect(logLines.size) {
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

        // Terminal Log Area using Native Android TextView with REAL setTextIsSelectable(true)
        // This guarantees native Android long-press selection handles and floating Copy/Share/Select All action mode
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
                        setPadding(24, 16, 24, 16)
                    }
                    addView(tv)
                    textViewRef = tv
                    scrollViewRef = this
                }
            },
            update = { _ ->
                textViewRef?.let { tv ->
                    val ssb = SpannableStringBuilder()
                    logLines.forEach { line ->
                        val start = ssb.length
                        ssb.append(line.text).append("\n")
                        val end = ssb.length
                        val color = when (line.type) {
                            LineType.INPUT -> android.graphics.Color.parseColor("#7EE787")
                            LineType.OUTPUT -> android.graphics.Color.parseColor("#C9D1D9")
                            LineType.ERROR -> android.graphics.Color.parseColor("#FFA198")
                            LineType.SYSTEM -> android.graphics.Color.parseColor("#D29922")
                        }
                        ssb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    tv.text = ssb
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Native Inline Prompt Input Line
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

            if (inputState.text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { runCommand(inputState.text) },
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

        // Termux Extra Keys Bar (CTRL, TAB, ESC, SHIFT, ALT, Arrows, Symbols)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D0D))
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

            // SHIFT Key Toggle (Requested by user)
            TerminalToggleKey(label = "SHIFT", isActive = isShiftActive) {
                isShiftActive = !isShiftActive
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

            // Symbols
            TerminalModifierKey(label = "~") { insertTextAtCursor("~") }
            TerminalModifierKey(label = "/") { insertTextAtCursor("/") }
            TerminalModifierKey(label = "-") { insertTextAtCursor("-") }
            TerminalModifierKey(label = "_") { insertTextAtCursor("_") }
            TerminalModifierKey(label = "|") { insertTextAtCursor("|") }
            TerminalModifierKey(label = "=") { insertTextAtCursor("=") }
            TerminalModifierKey(label = ":") { insertTextAtCursor(":") }
            TerminalModifierKey(label = "$") { insertTextAtCursor("$") }

            // Ctrl+C
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
