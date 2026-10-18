package com.arbdev.devbox.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalText

class DevBoxBridge(
    private val onInput: (String) -> Unit,
    private val onResize: (Int, Int) -> Unit,
    private val onReady: (Int, Int) -> Unit,
    private val onSelection: (String) -> Unit
) {
    @JavascriptInterface
    fun sendInput(data: String) {
        onInput(data)
    }

    @JavascriptInterface
    fun onTerminalResize(cols: Int, rows: Int) {
        onResize(cols, rows)
    }

    @JavascriptInterface
    fun onTerminalReady(cols: Int, rows: Int) {
        onReady(cols, rows)
    }

    @JavascriptInterface
    fun onSelectionChanged(text: String) {
        onSelection(text)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    initialCommand: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentSelection by remember { mutableStateOf("") }

    // Modifier keys state
    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }

    val activeSession = TerminalSessionManager.getActiveSession()
    val activeIndex = TerminalSessionManager.activeIndex.intValue

    fun sendToTerminal(text: String) {
        var processed = text
        if (isCtrlActive && text.isNotEmpty()) {
            val char = text[0]
            val ctrlCode = if (char.isLetter()) {
                (char.uppercaseChar().code - 64).toChar().toString()
            } else {
                text
            }
            processed = ctrlCode
            isCtrlActive = false
        } else if (isAltActive && text.isNotEmpty()) {
            processed = "\u001b$text"
            isAltActive = false
        } else if (isShiftActive && text.length == 1) {
            processed = text.uppercase()
            isShiftActive = false
        }

        activeSession.writeInput(processed)
    }

    fun dispatchOutputToXterm(b64Data: String) {
        mainHandler.post {
            webViewRef?.evaluateJavascript("if (window.writeToTerminal) { window.writeToTerminal('$b64Data'); }", null)
        }
    }

    DisposableEffect(activeSession) {
        activeSession.setListener { b64 ->
            dispatchOutputToXterm(b64)
        }
        onDispose {
            activeSession.setListener(null)
        }
    }

    LaunchedEffect(initialCommand) {
        initialCommand?.let { cmd ->
            activeSession.writeInput("$cmd\n")
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
                        webViewRef?.evaluateJavascript("if (window.clearTerminal) { window.clearTerminal(); }", null)
                        val s = TerminalSessionManager.getActiveSession()
                        s.setListener { b64 -> dispatchOutputToXterm(b64) }
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.name,
                                fontSize = 12.sp,
                                fontWeight = if (activeIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeIndex == index) DevPrimary else TerminalText
                            )
                            if (TerminalSessionManager.sessions.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { TerminalSessionManager.closeSession(index) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup Sesi",
                                        tint = Color.Gray,
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
                    webViewRef?.evaluateJavascript("if (window.clearTerminal) { window.clearTerminal(); }", null)
                    val s = TerminalSessionManager.getActiveSession()
                    s.setListener { b64 -> dispatchOutputToXterm(b64) }
                },
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Sesi Baru",
                    tint = DevPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Action Toolbar (Paste, Copy, Clear, Resize/Refit)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "xterm.js",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TerminalGreen
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeSession.isAlive) "• AKTIF" else "• SELESAI",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (activeSession.isAlive) DevPrimary else Color.Red
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Paste Button
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            activeSession.writeInput(clip)
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

                // Copy Selected Text Button
                IconButton(
                    onClick = {
                        if (currentSelection.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(currentSelection))
                            Toast.makeText(context, "Teks terpilih berhasil disalin", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Pilih teks di terminal terlebih dahulu", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin Teks",
                        tint = TerminalText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Refit / Resize Terminal
                IconButton(
                    onClick = {
                        webViewRef?.evaluateJavascript("if (window.triggerFit) { window.triggerFit(); }", null)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sesuaikan Ukuran Layar",
                        tint = TerminalText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear Terminal
                IconButton(
                    onClick = {
                        webViewRef?.evaluateJavascript("if (window.clearTerminal) { window.clearTerminal(); }", null)
                        activeSession.writeInput("\u000c")
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

        // Interactive xterm.js WebView Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = false
                            displayZoomControls = false
                        }

                        val bridge = DevBoxBridge(
                            onInput = { data ->
                                activeSession.writeInput(data)
                            },
                            onResize = { cols, rows ->
                                activeSession.resize(cols, rows)
                            },
                            onReady = { cols, rows ->
                                activeSession.resize(cols, rows)
                                activeSession.setListener { b64 -> dispatchOutputToXterm(b64) }
                            },
                            onSelection = { sel ->
                                currentSelection = sel
                            }
                        )

                        addJavascriptInterface(bridge, "DevBoxBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("if (window.triggerFit) { window.triggerFit(); }", null)
                            }
                        }

                        loadUrl("file:///android_asset/xterm/terminal.html")
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
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
            TerminalKeyButton(label = "ESC") { sendToTerminal("\u001b") }

            // TAB
            TerminalKeyButton(label = "TAB") { sendToTerminal("\t") }

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

            // Arrow Keys
            TerminalKeyButton(label = "◀") { sendToTerminal("\u001b[D") }
            TerminalKeyButton(label = "▲") { sendToTerminal("\u001b[A") }
            TerminalKeyButton(label = "▼") { sendToTerminal("\u001b[B") }
            TerminalKeyButton(label = "▶") { sendToTerminal("\u001b[C") }

            // Common Shortcuts & Control Keys
            TerminalKeyButton(label = "Ctrl+C") { activeSession.writeInput("\u0003") }
            TerminalKeyButton(label = "Ctrl+D") { activeSession.writeInput("\u0004") }
            TerminalKeyButton(label = "Ctrl+Z") { activeSession.writeInput("\u001a") }
            TerminalKeyButton(label = "Ctrl+L") { activeSession.writeInput("\u000c") }

            // Quick Symbols
            TerminalKeyButton(label = "~") { sendToTerminal("~") }
            TerminalKeyButton(label = "/") { sendToTerminal("/") }
            TerminalKeyButton(label = "-") { sendToTerminal("-") }
            TerminalKeyButton(label = "_") { sendToTerminal("_") }
            TerminalKeyButton(label = "|") { sendToTerminal("|") }
            TerminalKeyButton(label = "=") { sendToTerminal("=") }
            TerminalKeyButton(label = ":") { sendToTerminal(":") }
            TerminalKeyButton(label = "$") { sendToTerminal("$") }
            TerminalKeyButton(label = ";") { sendToTerminal(";") }
            TerminalKeyButton(label = "&") { sendToTerminal("&") }
            TerminalKeyButton(label = "\"") { sendToTerminal("\"") }
            TerminalKeyButton(label = "'") { sendToTerminal("'") }
            TerminalKeyButton(label = "`") { sendToTerminal("`") }

            // Soft Keyboard Trigger
            IconButton(
                onClick = {
                    webViewRef?.requestFocus()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Buka Keyboard",
                    tint = DevPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TerminalKeyButton(
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
