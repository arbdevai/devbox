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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.arbdev.devbox.terminal.TerminalTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.data.ChrootProcessInfo
import com.arbdev.devbox.data.RootExecutor
import com.arbdev.devbox.terminal.TerminalInputView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.terminal.NativeTerminalSession
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.util.AppLogger
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
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import com.arbdev.devbox.ui.theme.DevWarning
import com.arbdev.devbox.ui.theme.TerminalBackground
import com.arbdev.devbox.ui.theme.TerminalCyan
import com.arbdev.devbox.ui.theme.TerminalGreen
import com.arbdev.devbox.ui.theme.TerminalText

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    initialCommand: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeSession = TerminalSessionManager.getActiveSession()
    val activeIndex = TerminalSessionManager.activeIndex.intValue

    // Persistensi ukuran font terminal via SharedPreferences (default 12sp, rentang 2.5sp - 28sp)
    val prefs = remember { context.getSharedPreferences("devbox_terminal_prefs", Context.MODE_PRIVATE) }
    var terminalFontSize by remember { mutableFloatStateOf(prefs.getFloat("terminal_font_size", 12f)) }
    var fontToast by remember { mutableStateOf<android.widget.Toast?>(null) }

    // Helper untuk mengubah dan menyimpan ukuran font secara smooth (step 0.5f, min 2.5f)
    val updateFontSize: (Float) -> Unit = { newSize ->
        val clamped = (Math.round(newSize * 2f) / 2f).coerceIn(2.5f, 28f)
        if (clamped != terminalFontSize) {
            terminalFontSize = clamped
            prefs.edit().putFloat("terminal_font_size", clamped).apply()
            fontToast?.cancel()
            fontToast = android.widget.Toast.makeText(context, "Ukuran font: ${String.format(java.util.Locale.US, "%.1f", clamped)} sp", android.widget.Toast.LENGTH_SHORT).apply { show() }
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    var activeUrlPrompt by remember { mutableStateOf<String?>(null) }
    var urlListPrompt by remember { mutableStateOf<List<String>?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TerminalSessionManager.initTheme(context)
    }

    LaunchedEffect(activeSession) {
        activeSession.onClipboardCopy = { text ->
            clipboardManager.setText(AnnotatedString(text))
            TerminalSessionManager.addToClipboardHistory(text)
            Toast.makeText(context, "Disalin via terminal (OSC 52)", Toast.LENGTH_SHORT).show()
        }
        activeSession.onClipboardRequest = {
            clipboardManager.getText()?.text ?: ""
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

    // Menu Overflow Titik Tiga (Action Toolbar Ringkas)
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Dialog Antigravity CLI Agent Hub
    var showAgentHubDialog by remember { mutableStateOf(false) }
    var agentLastResponse by remember { mutableStateOf<String?>(null) }
    var agentLastUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingAgentInfo by remember { mutableStateOf(false) }
    var agentQuickPrompt by remember { mutableStateOf("") }

    val loadAgentInfo: () -> Unit = {
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            isLoadingAgentInfo = true
            val rootfs = ChrootManager.activeRootfs
            val cmd = """
                BRAIN_DIR='$rootfs/home/coder/.gemini/antigravity-cli/brain'
                [ -d "${'$'}BRAIN_DIR" ] || BRAIN_DIR='$rootfs/root/.gemini/antigravity-cli/brain'
                LATEST_LOG=${'$'}(find "${'$'}BRAIN_DIR" -name "transcript.jsonl" -type f 2>/dev/null | xargs ls -t 2>/dev/null | head -n1)
                if [ -f "${'$'}LATEST_LOG" ]; then
                    grep '"type":"PLANNER_RESPONSE"' "${'$'}LATEST_LOG" | tail -n1
                else
                    echo ""
                fi
            """.trimIndent()
            val res = RootExecutor.execute(cmd, silent = true)
            val jsonLine = res.getOrNull()?.stdout?.trim().orEmpty()
            if (jsonLine.isNotEmpty()) {
                val contentRegex = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                val match = contentRegex.find(jsonLine)
                var rawContent = match?.groupValues?.get(1) ?: jsonLine
                rawContent = rawContent.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                agentLastResponse = rawContent

                val urlRegex = Regex("https?://[a-zA-Z0-9.-]+(?:/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?")
                agentLastUrls = urlRegex.findAll(rawContent).map { it.value }.distinct().toList()
            } else {
                agentLastResponse = "Belum ada riwayat respon dari Antigravity CLI di direktori brain."
                agentLastUrls = emptyList()
            }
            isLoadingAgentInfo = false
        }
    }

    // View input terminal berbasis BaseInputConnection (gaya Termux, tanpa Compose TextField delay)
    var terminalInputView by remember { mutableStateOf<TerminalInputView?>(null) }
    val isImeVisible = WindowInsets.isImeVisible

    // Scroll state terisolasi per sesi (mencegah desinkronisasi viewport saat switch antar sesi)
    val verticalScrollState = remember(activeSession.id) { ScrollState(0) }
    val horizontalScrollState = remember(activeSession.id) { ScrollState(0) }

    // State auto-scroll cerdas: tetap mengikuti baris terbawah kecuali pengguna sengaja scroll ke atas
    var autoScrollEnabled by remember(activeSession.id) { mutableStateOf(true) }

    // Pantau jika pengguna menggeser scroll manual
    LaunchedEffect(verticalScrollState.isScrollInProgress) {
        if (verticalScrollState.isScrollInProgress) {
            val distFromBottom = verticalScrollState.maxValue - verticalScrollState.value
            autoScrollEnabled = distFromBottom <= 120
        }
    }

    // Helper navigasi tombol panah dinamis (menghormati DECCKM, modifier Shift/Alt/Ctrl, dan sesi aktif)
    val sendArrowWithModifiers: (Char) -> Unit = { dirChar ->
        val s = TerminalSessionManager.getActiveSession()
        val modMask = (if (isShiftActive) 1 else 0) + (if (isAltActive) 2 else 0) + (if (isCtrlActive) 4 else 0)
        if (modMask > 0) {
            val modCode = 1 + modMask
            s.sendText("\u001B[1;${modCode}$dirChar")
            isShiftActive = false
            isAltActive = false
            isCtrlActive = false
        } else {
            if (s.buffer.isApplicationCursorKeys) s.sendText("\u001BO$dirChar")
            else s.sendText("\u001B[$dirChar")
        }
    }
    val sendArrowUp = { sendArrowWithModifiers('A') }
    val sendArrowDown = { sendArrowWithModifiers('B') }
    val sendArrowRight = { sendArrowWithModifiers('C') }
    val sendArrowLeft = { sendArrowWithModifiers('D') }

    LaunchedEffect(Unit) {
        KeepAliveService.startService(context)
        terminalInputView?.requestKeyboard()
    }

    LaunchedEffect(activeSession.isRunning, activeSession.currentDir, TerminalSessionManager.sessions.size) {
        KeepAliveService.updateNotification(context)
    }

    // Auto-scroll reaktif: HANYA scroll saat ada penambahan baris baru (maxValue bertambah),
    // BUKAN pada setiap pengetikan karakter / in-place rewrite agar TUI Bubbletea smooth bebas jitter
    var previousMaxValue by remember(activeSession.id) { mutableIntStateOf(0) }
    LaunchedEffect(verticalScrollState.maxValue) {
        val currentMax = verticalScrollState.maxValue
        if (autoScrollEnabled && currentMax > previousMaxValue && currentMax > 0) {
            verticalScrollState.scrollTo(currentMax)
        }
        previousMaxValue = currentMax
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
        // 2026 Google M3 Pill Session Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TerminalSessionManager.sessions.forEachIndexed { index, session ->
                val isSelected = activeIndex == index
                Surface(
                    color = if (isSelected) DevPrimaryContainer else DevSurfaceVariant,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) DevPrimary else DevBorder
                    ),
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
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (isSelected) DevPrimary else DevOnSurfaceVariant,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = session.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) DevPrimary else DevOnSurfaceVariant
                        )
                        if (session.isAgentRunning && session.activeAgentName != null) {
                            Spacer(modifier = Modifier.width(6.dp))
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
                                color = DevPrimary.copy(alpha = 0.22f * pulseAlpha),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = session.activeAgentName ?: "",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DevPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (TerminalSessionManager.sessions.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Session",
                                tint = if (isSelected) DevPrimary else DevOnSurfaceVariant,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { TerminalSessionManager.closeSession(index) }
                            )
                        }
                    }
                }
            }

            // Add New Session Pill Button (+)
            Surface(
                onClick = { TerminalSessionManager.createNewSession() },
                color = DevSurfaceVariant,
                shape = CircleShape,
                border = BorderStroke(1.dp, DevBorder),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Action Toolbar (Prompt status + Compact Action Controls with Overflow Dropdown)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevBackground)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "coder@devbox",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = DevPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = ":${activeSession.getDisplayDir()}$",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TerminalCyan,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = if (activeSession.isRunning) DevPrimaryContainer else DevSurfaceVariant,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (activeSession.isRunning) DevPrimary.copy(alpha = 0.5f) else DevBorder)
                ) {
                    Text(
                        text = if (activeSession.isRunning) "● PTY" else "● EXITED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeSession.isRunning) DevPrimary else DevOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        maxLines = 1
                    )
                }
                if (activeSession.isAgentRunning && activeSession.activeAgentName != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = DevPrimaryContainer,
                        border = BorderStroke(1.dp, DevPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
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
                                    .background(DevPrimary.copy(alpha = pulseAlpha), shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${activeSession.activeAgentName}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = DevPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        contentDescription = "Keyboard",
                        tint = DevPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        loadAgentInfo()
                        showAgentHubDialog = true
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Antigravity Agent Hub",
                        tint = DevPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = if (orphanProcesses.isNotEmpty()) DevWarning else DevOnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier
                            .background(DevSurface)
                            .border(1.dp, DevBorder, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Terminal Themes & Palettes", color = DevOnSurface, fontSize = 13.sp)
                                        Text(
                                            text = TerminalSessionManager.currentTheme.name,
                                            color = DevPrimary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                showThemeDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Clipboard History (${TerminalSessionManager.clipboardHistory.size})", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                showClipboardDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = null,
                                        tint = if (orphanProcesses.isNotEmpty()) DevWarning else DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (orphanProcesses.isNotEmpty()) "Active Sessions (${orphanProcesses.size} Orphans)" else "Active Sessions",
                                        color = if (orphanProcesses.isNotEmpty()) DevWarning else DevOnSurface,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                showSessionManagerDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Unlock agy DB", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                coroutineScope.launch {
                                    val count = ChrootManager.cleanStaleAgyLocks()
                                    Toast.makeText(context, "$count stale agy locks cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Extract URLs", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                val urls = activeSession.buffer.extractAllUrls()
                                when {
                                    urls.isEmpty() -> Toast.makeText(context, "No URLs found on screen", Toast.LENGTH_SHORT).show()
                                    urls.size == 1 -> activeUrlPrompt = urls.first()
                                    else -> urlListPrompt = urls
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Copy Transcript", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                val text = activeSession.buffer.getTranscriptText()
                                if (text.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Terminal transcript copied", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Buffer is empty", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Paste", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                val clip = clipboardManager.getText()?.text
                                val curSess = TerminalSessionManager.getActiveSession()
                                if (!clip.isNullOrEmpty()) {
                                    if (clip.length > 2000 || clip.count { it == '\n' } > 50) {
                                        pendingLargePaste = clip
                                    } else {
                                        curSess.pasteText(clip)
                                    }
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Zoom In (A+)", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                updateFontSize(terminalFontSize + 0.5f)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOut,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Zoom Out (A-)", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                updateFontSize(terminalFontSize - 0.5f)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = DevOnSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Clear Screen (Ctrl+L)", color = DevOnSurface, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                showOverflowMenu = false
                                TerminalSessionManager.getActiveSession().sendBytes(byteArrayOf(12))
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = DevBorder)

        // Orphan Processes Alert Banner
        AnimatedVisibility(
            visible = orphanProcesses.isNotEmpty() && !isImeVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val totalRamMb = orphanProcesses.sumOf { it.memoryKb } / 1024
            Surface(
                color = Color(0xFF211A0B),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DevWarning.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                            tint = DevWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${orphanProcesses.size} Orphan Processes (~${totalRamMb} MB)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = DevOnSurface
                            )
                            Text(
                                text = "Detached background processes found.",
                                fontSize = 9.sp,
                                color = DevOnSurfaceVariant
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
                                Toast.makeText(context, "$killed orphan processes terminated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevWarning),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(50),
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
                            Text("Kill All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
                        autoScrollEnabled = true
                        val targetSession = TerminalSessionManager.getActiveSession()
                        if (isCtrlActive) {
                            val bytes = mutableListOf<Byte>()
                            if (isAltActive) {
                                bytes.add(27.toByte())
                                isAltActive = false
                            }
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
                            targetSession.sendBytes(bytes.toByteArray())
                            isCtrlActive = false
                            isShiftActive = false
                        } else if (text == "\r" || text == "\n" || text == "\r\n") {
                            if (isAltActive) {
                                targetSession.sendText("\u001B\r")
                                isAltActive = false
                            } else if (isShiftActive) {
                                // Kitty Keyboard: Shift+Enter sends \u001B[13;2u (newline di prompt AI CLI)
                                targetSession.sendText("\u001B[13;2u")
                                isShiftActive = false
                            } else if (isCtrlActive) {
                                // Kitty Keyboard: Ctrl+Enter sends \u001B[13;5u
                                targetSession.sendText("\u001B[13;5u")
                                isCtrlActive = false
                            } else {
                                targetSession.sendText("\r")
                            }
                        } else if (text.length > 1 && (text.contains('\n') || text.contains('\r'))) {
                            targetSession.pasteText(text)
                        } else {
                            var processed = if (isShiftActive) {
                                isShiftActive = false
                                text.uppercase()
                            } else {
                                text
                            }
                            if (isAltActive) {
                                isAltActive = false
                                processed = "\u001B$processed"
                            }
                            targetSession.sendText(processed)
                        }
                    }
                    onBackspace = {
                        autoScrollEnabled = true
                        if (isAltActive) {
                            isAltActive = false
                            TerminalSessionManager.getActiveSession().sendBytes(byteArrayOf(27, 127))
                        } else {
                            TerminalSessionManager.getActiveSession().sendBytes(byteArrayOf(127))
                        }
                    }
                    onSendKey = { keyCode ->
                        val targetSession = TerminalSessionManager.getActiveSession()
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
                                    targetSession.sendText("\u001B[Z")
                                    isShiftActive = false
                                } else {
                                    targetSession.sendBytes(byteArrayOf(9))
                                }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_ESCAPE -> {
                                targetSession.sendBytes(byteArrayOf(27))
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

        // Native 2D VT100 Matrix Canvas / Terminal Text Area with Direct Glyph Drawing & Pinch-to-Zoom
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TerminalBackground)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.size >= 2) {
                                val p1 = activePointers[0].position
                                val p2 = activePointers[1].position
                                val prevP1 = activePointers[0].previousPosition
                                val prevP2 = activePointers[1].previousPosition

                                val currentDist = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)
                                val prevDist = kotlin.math.hypot(prevP1.x - prevP2.x, prevP1.y - prevP2.y)

                                if (prevDist > 0f) {
                                    val scale = currentDist / prevDist
                                    if (scale in 0.5f..2.0f && kotlin.math.abs(scale - 1f) > 0.005f) {
                                        val delta = (scale - 1f) * (terminalFontSize * 1.5f)
                                        updateFontSize(terminalFontSize + delta)
                                        activePointers.forEach { it.consume() }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val density = LocalDensity.current
            val terminalTypeface = remember(context) {
                try {
                    android.graphics.Typeface.createFromAsset(context.assets, "fonts/jetbrains_mono_nerd_font.ttf")
                } catch (_: Exception) {
                    android.graphics.Typeface.MONOSPACE
                }
            }
            val charWidthPx = remember(terminalFontSize, density, terminalTypeface) {
                val p = android.graphics.Paint().apply {
                    typeface = terminalTypeface
                    textSize = with(density) { terminalFontSize.sp.toPx() }
                    isSubpixelText = true
                    isLinearText = true
                }
                p.measureText("M").coerceAtLeast(1f)
            }
            val charHeightPx = remember(terminalFontSize, density, terminalTypeface) {
                val p = android.graphics.Paint().apply {
                    typeface = terminalTypeface
                    textSize = with(density) { terminalFontSize.sp.toPx() }
                    isSubpixelText = true
                    isLinearText = true
                }
                val fm = p.fontMetrics
                ((fm.descent - fm.ascent) * 1.25f).coerceAtLeast(1f)
            }

            var lastCharWidth by remember { mutableFloatStateOf(0f) }
            var lastCharHeight by remember { mutableFloatStateOf(0f) }
            var unconstrainedMaxHeightPx by remember { mutableFloatStateOf(0f) }

            // Lacak tinggi layar saat keyboard tertutup (full viewport height)
            if (!isImeVisible && maxHeight.value > 0f) {
                with(density) {
                    val currentH = maxHeight.toPx()
                    if (currentH > unconstrainedMaxHeightPx) {
                        unconstrainedMaxHeightPx = currentH
                    }
                }
            }

            LaunchedEffect(activeSession, maxWidth, maxHeight, charWidthPx, charHeightPx, isImeVisible) {
                val isFontZoom = lastCharWidth > 0f && (lastCharWidth != charWidthPx || lastCharHeight != charHeightPx)
                lastCharWidth = charWidthPx
                lastCharHeight = charHeightPx

                if (isFontZoom) {
                    delay(120) // Debounce pinch-zoom font resize
                } else {
                    delay(10)
                }

                with(density) {
                    val hPaddingPx = 8.dp.toPx() // 4.dp horizontal margin di setiap sisi
                    val vPaddingPx = 4.dp.toPx() // 2.dp vertical margin di setiap sisi
                    if (charWidthPx > 0 && charHeightPx > 0) {
                        val cols = ((maxWidth.toPx() - hPaddingPx).coerceAtLeast(1f) / charWidthPx).toInt().coerceIn(20, 300)
                        // KUNCI ROWS LINUX (Virtual Viewport Panning):
                        // Saat keyboard virtual HP muncul, JANGAN mengecilkan rows terminal di Linux!
                        // Tetap gunakan tinggi layar penuh unconstrainedMaxHeightPx agar PTY tidak menerima SIGWINCH
                        // dan Bubble Tea/agy tidak pernah terbentur baris 0 (Solusi 1 Dokumen Audit Master).
                        val targetHeightPx = if (isImeVisible && unconstrainedMaxHeightPx > 0f) {
                            unconstrainedMaxHeightPx
                        } else {
                            maxHeight.toPx()
                        }
                        val rows = ((targetHeightPx - vPaddingPx).coerceAtLeast(1f) / charHeightPx).toInt().coerceIn(18, 200)
                        activeSession.updateWindowSize(cols, rows, isImmediate = !isFontZoom)
                    }
                }
            }

            // Manajemen Viewport & Auto-Scroll Cerdas:
            // 1. Saat keyboard muncul, scroll HANYA jika kursor tertutup keyboard di bawah atau di luar viewport.
            // 2. Jika kursor sudah berada di area pandang aktif, pertahankan posisi teks (anti-ketarik ke atas).
            // 3. Saat keyboard ditutup, kembalikan posisi scroll jika konten muat di layar penuh.
            LaunchedEffect(isImeVisible, activeSession.buffer.cursorRow) {
                if (charHeightPx > 0f) {
                    with(density) {
                        val visibleViewportPx = maxHeight.toPx()
                        val historyCount = activeSession.buffer.getScrollbackSize()
                        val cursorRow = activeSession.buffer.cursorRow
                        val cursorYPx = (historyCount + cursorRow) * charHeightPx
                        val currentScroll = verticalScrollState.value.toFloat()
                        val visibleTop = currentScroll
                        val visibleBottom = currentScroll + visibleViewportPx

                        if (isImeVisible && autoScrollEnabled) {
                            if (cursorYPx + charHeightPx > visibleBottom) {
                                // Kursor di bawah viewport (terhalang keyboard) -> scroll secukupnya
                                val targetScroll = (cursorYPx - visibleViewportPx + charHeightPx * 2).toInt().coerceAtLeast(0)
                                if (targetScroll != verticalScrollState.value) {
                                    verticalScrollState.scrollTo(targetScroll)
                                }
                            } else if (cursorYPx < visibleTop) {
                                // Kursor di atas viewport -> scroll ke atas agar kursor terlihat
                                val targetScroll = cursorYPx.toInt().coerceAtLeast(0)
                                if (targetScroll != verticalScrollState.value) {
                                    verticalScrollState.scrollTo(targetScroll)
                                }
                            }
                        } else if (!isImeVisible) {
                            // Saat keyboard ditutup, jika total konten muat di layar penuh dan scroll sempat terdorong, kembalikan ke 0
                            val totalLines = if (activeSession.buffer.isAltBuffer) activeSession.buffer.maxRows else activeSession.buffer.getLineCount()
                            val totalContentHeightPx = totalLines * charHeightPx
                            if (totalContentHeightPx <= visibleViewportPx && verticalScrollState.value > 0) {
                                verticalScrollState.scrollTo(0)
                            }
                        }
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

            // Floating badge jika ada server web lokal (Vite, Next, Flask, Node, dsb.) aktif
            val activeWebPort = TerminalSessionManager.detectedWebPort
            if (activeWebPort != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp, end = 12.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Surface(
                        onClick = {
                            val url = "http://localhost:$activeWebPort"
                            ChrootManager.openUrlInAndroidBrowser(url)
                        },
                        color = DevPrimary,
                        shape = RoundedCornerShape(50),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🌐 :$activeWebPort",
                                color = DevOnPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open →",
                                color = DevOnPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Large Paste Protection Dialog
        pendingLargePaste?.let { largeText ->
            AlertDialog(
                onDismissRequest = { pendingLargePaste = null },
                containerColor = DevSurface,
                titleContentColor = DevOnSurface,
                textContentColor = DevOnSurfaceVariant,
                title = { Text("Large Clipboard Text", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Clipboard contains ${largeText.length} characters (${largeText.lines().size} lines).\n\n" +
                        "Pasting a large log/code block directly into an interactive shell may cause command loops.\n\n" +
                        "Paste anyway?",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val text = pendingLargePaste
                            pendingLargePaste = null
                            if (text != null) {
                                activeSession.pasteText(text)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Paste Anyway", color = DevOnPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingLargePaste = null }) {
                        Text("Cancel", color = DevOnSurfaceVariant)
                    }
                }
            )
        }

        // Rename Session Dialog
        sessionToRename?.let { sess ->
            AlertDialog(
                onDismissRequest = { sessionToRename = null },
                containerColor = DevSurface,
                titleContentColor = DevOnSurface,
                textContentColor = DevOnSurfaceVariant,
                title = { Text("Rename Session", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Enter a custom session title or select 'Auto Detect' to follow active directory/process.",
                            fontSize = 12.sp,
                            color = DevOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = renameInputText,
                            onValueChange = { renameInputText = it },
                            label = { Text("Session Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DevPrimary,
                                unfocusedBorderColor = DevBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameInputText.isNotBlank()) {
                                sess.name = renameInputText.trim()
                                sess.isCustomName = true
                            }
                            sessionToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Save", color = DevOnPrimary, fontWeight = FontWeight.Bold)
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
                            Text("Auto Detect", color = DevPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { sessionToRename = null }) {
                            Text("Cancel", color = DevOnSurfaceVariant)
                        }
                    }
                }
            )
        }

        if (showThemeDialog) {
            TerminalThemeDialog(
                currentTheme = TerminalSessionManager.currentTheme,
                onDismiss = { showThemeDialog = false },
                onThemeSelect = { theme ->
                    TerminalSessionManager.setGlobalTheme(context, theme)
                }
            )
        }

        if (showClipboardDialog) {
            TerminalClipboardDialog(
                history = TerminalSessionManager.clipboardHistory,
                onDismiss = { showClipboardDialog = false },
                onPaste = { snippet ->
                    showClipboardDialog = false
                    TerminalSessionManager.getActiveSession().pasteText(snippet)
                },
                onClear = {
                    TerminalSessionManager.clipboardHistory.clear()
                }
            )
        }

        // Open Single URL Dialog
        activeUrlPrompt?.let { url ->
            AlertDialog(
                onDismissRequest = { activeUrlPrompt = null },
                containerColor = DevSurface,
                titleContentColor = DevOnSurface,
                textContentColor = DevOnSurfaceVariant,
                title = { Text("Open Terminal Link", fontWeight = FontWeight.Bold) },
                text = { Text(url, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DevPrimary) },
                confirmButton = {
                    Button(
                        onClick = {
                            activeUrlPrompt = null
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open link: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Open Browser", color = DevOnPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(url))
                            activeUrlPrompt = null
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copy URL", color = DevOnSurface)
                    }
                }
            )
        }

        // 2026 M3 ModalBottomSheet: Extracted URLs List
        urlListPrompt?.let { urls ->
            ModalBottomSheet(
                onDismissRequest = { urlListPrompt = null },
                containerColor = DevSurface,
                contentColor = DevOnSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                ) {
                    Text(
                        text = "Extracted URLs (${urls.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = DevOnSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        urls.forEach { url ->
                            Surface(
                                color = DevSurfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, DevBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(url))
                                                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = DevOnSurfaceVariant,
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
                                                    Toast.makeText(context, "Failed to open link: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
                                        ) {
                                            Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DevOnPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2026 M3 ModalBottomSheet: Session & Orphan Process Manager
        if (showSessionManagerDialog) {
            ModalBottomSheet(
                onDismissRequest = { showSessionManagerDialog = false },
                containerColor = DevSurface,
                contentColor = DevOnSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = DevPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Session & Process Manager", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DevOnSurface)
                        }
                        Button(
                            onClick = {
                                TerminalSessionManager.createNewSession()
                                showSessionManagerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("+ New Shell", color = DevOnPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ACTIVE SESSIONS (${TerminalSessionManager.sessions.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DevPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TerminalSessionManager.sessions.forEachIndexed { idx, s ->
                        val isSelected = idx == activeIndex
                        Surface(
                            color = if (isSelected) DevPrimaryContainer else DevSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isSelected) DevPrimary else DevBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    TerminalSessionManager.activeIndex.intValue = idx
                                    showSessionManagerDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${idx + 1}. ${s.name}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) DevPrimary else DevOnSurface
                                        )
                                        if (s.isAgentRunning && s.activeAgentName != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• ${s.activeAgentName}",
                                                fontSize = 10.sp,
                                                color = DevPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Dir: ${s.getDisplayDir()}",
                                        fontSize = 11.sp,
                                        color = DevOnSurfaceVariant
                                    )
                                }
                                if (TerminalSessionManager.sessions.size > 1) {
                                    IconButton(
                                        onClick = {
                                            TerminalSessionManager.closeSession(idx)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Session",
                                            tint = DevError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ORPHAN PROCESSES (${orphanProcesses.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (orphanProcesses.isNotEmpty()) DevWarning else DevOnSurfaceVariant
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
                                        Toast.makeText(context, "$killed orphan processes terminated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isCleaningOrphans,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Kill All", fontSize = 12.sp, color = DevWarning, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (orphanProcesses.isEmpty()) {
                        Text(
                            text = "✓ Zero orphan background processes (Memory clean)",
                            fontSize = 12.sp,
                            color = DevPrimary
                        )
                    } else {
                        orphanProcesses.forEach { orphan ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PID ${orphan.pid}: ${orphan.name}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = DevWarning
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
            }
        }

        // 2026 M3 ModalBottomSheet: Antigravity CLI Agent Hub
        if (showAgentHubDialog) {
            ModalBottomSheet(
                onDismissRequest = { showAgentHubDialog = false },
                containerColor = DevSurface,
                contentColor = DevOnSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                        .heightIn(max = 480.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Antigravity Agent Hub",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = DevOnSurface
                            )
                        }
                        TextButton(
                            onClick = {
                                val resp = agentLastResponse
                                if (!resp.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(resp))
                                    Toast.makeText(context, "Response copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Output", color = DevPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Latest Agent Response & Links:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DevOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (isLoadingAgentInfo) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DevPrimary, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Surface(
                            color = DevCanvasDeep,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, DevBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = agentLastResponse ?: "No conversation transcript found yet.",
                                color = DevOnSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        if (agentLastUrls.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected Links (${agentLastUrls.size}):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DevPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            agentLastUrls.forEach { url ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed to open: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = url,
                                        fontSize = 11.sp,
                                        color = DevPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = agentQuickPrompt,
                                onValueChange = { agentQuickPrompt = it },
                                placeholder = { Text("Send quick prompt to active PTY...", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DevPrimary,
                                    unfocusedBorderColor = DevBorder
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = {
                                    if (agentQuickPrompt.isNotBlank()) {
                                        val s = TerminalSessionManager.getActiveSession()
                                        s.sendText(agentQuickPrompt.trim() + "\r")
                                        agentQuickPrompt = ""
                                        showAgentHubDialog = false
                                        Toast.makeText(context, "Prompt sent to active shell", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                color = DevPrimary,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = DevOnPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = DevBorder)

        // Tactile Mobile Extra Keys Dock (#141414 + #252824 + #7DFF4C)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevBackground)
                .padding(vertical = 5.dp),
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
                TerminalModifierKey(label = "ESC") {
                    TerminalSessionManager.getActiveSession().sendBytes(byteArrayOf(27))
                }

                TerminalModifierKey(
                    label = "PASTE",
                    highlight = true,
                    onLongClick = { showClipboardDialog = true }
                ) {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrEmpty()) {
                        if (clip.length > 2000 || clip.count { it == '\n' } > 50) {
                            pendingLargePaste = clip
                        } else {
                            TerminalSessionManager.getActiveSession().pasteText(clip)
                        }
                    } else {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                }

                TerminalModifierKey(label = "THEME") {
                    showThemeDialog = true
                }

                TerminalModifierKey(label = if (isShiftActive) "⇧TAB" else "TAB", highlight = isShiftActive) {
                    val cur = TerminalSessionManager.getActiveSession()
                    if (isShiftActive) {
                        cur.sendText("\u001B[Z")
                        isShiftActive = false
                    } else {
                        cur.sendBytes(byteArrayOf(9))
                    }
                }

                TerminalToggleKey(label = "CTRL", isActive = isCtrlActive) {
                    isCtrlActive = !isCtrlActive
                }

                TerminalModifierKey(label = "Ctrl+C", isSignal = true) {
                    autoScrollEnabled = true
                    TerminalSessionManager.getActiveSession().cancelActiveProcess()
                }

                TerminalToggleKey(label = "ALT", isActive = isAltActive) {
                    isAltActive = !isAltActive
                }

                TerminalToggleKey(label = "SHIFT", isActive = isShiftActive) {
                    isShiftActive = !isShiftActive
                }

                TerminalModifierKey(
                    label = when {
                        isShiftActive -> "NEWLINE"
                        isCtrlActive -> "CTRL+↵"
                        else -> "ENTER"
                    },
                    highlight = true
                ) {
                    autoScrollEnabled = true
                    val s = TerminalSessionManager.getActiveSession()
                    if (isShiftActive) {
                        s.sendText("\u001B[13;2u")
                        isShiftActive = false
                    } else if (isCtrlActive) {
                        s.sendText("\u001B[13;5u")
                        isCtrlActive = false
                    } else {
                        s.sendText("\r")
                    }
                }

                TerminalModifierKey(label = "⌫") {
                    TerminalSessionManager.getActiveSession().sendBytes(byteArrayOf(127))
                }

                TerminalModifierKey(label = "DEL") {
                    TerminalSessionManager.getActiveSession().sendText("\u001B[3~")
                }

                TerminalModifierKey(label = "◀") {
                    sendArrowLeft()
                }

                TerminalModifierKey(label = "▼") {
                    sendArrowDown()
                }

                TerminalModifierKey(label = "▲") {
                    sendArrowUp()
                }

                TerminalModifierKey(label = "▶") {
                    sendArrowRight()
                }

                TerminalModifierKey(label = "HOME") {
                    TerminalSessionManager.getActiveSession().sendText("\u001B[H")
                }

                TerminalModifierKey(label = "END") {
                    TerminalSessionManager.getActiveSession().sendText("\u001B[F")
                }

                TerminalModifierKey(label = "PGUP") {
                    TerminalSessionManager.getActiveSession().sendText("\u001B[5~")
                }

                TerminalModifierKey(label = "PGDN") {
                    TerminalSessionManager.getActiveSession().sendText("\u001B[6~")
                }
            }

            if (!isImeVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            TerminalSessionManager.getActiveSession().sendText(seq)
                        }
                    }

                    val symbols = listOf(
                        "~", "/", "-", "_", "|", "$", "=", ":", ";",
                        "&", "*", "\\", "\"", "'", "`", "<", ">", "!",
                        "?", "(", ")", "[", "]", "{", "}"
                    )
                    symbols.forEach { sym ->
                        TerminalModifierKey(label = sym) {
                            TerminalSessionManager.getActiveSession().sendText(sym)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalModifierKey(
    label: String,
    highlight: Boolean = false,
    isSignal: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val containerBg = when {
        isSignal -> Color(0xFF361515)
        highlight -> DevPrimaryContainer
        else -> DevSurfaceVariant
    }

    val textColor = when {
        isSignal -> DevError
        highlight -> DevPrimary
        else -> DevOnSurface
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(
                width = 1.dp,
                color = when {
                    isSignal -> DevError.copy(alpha = 0.4f)
                    highlight -> DevPrimary.copy(alpha = 0.45f)
                    else -> DevBorder
                },
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
    val containerBg = if (isActive) DevPrimary else DevSurfaceVariant
    val textColor = if (isActive) DevOnPrimary else DevOnSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(
                width = 1.dp,
                color = if (isActive) DevPrimary else DevBorder,
                shape = RoundedCornerShape(8.dp)
            )
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
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var selectionStart by remember { mutableStateOf<GridPoint?>(null) }
    var selectionEnd by remember { mutableStateOf<GridPoint?>(null) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var pasteMenuOffset by remember { mutableStateOf<Offset?>(null) }
    var isDraggingHandle by remember { mutableStateOf(false) }

    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }

    val terminalTypeface = remember(context) {
        try {
            android.graphics.Typeface.createFromAsset(context.assets, "fonts/jetbrains_mono_nerd_font.ttf")
        } catch (_: Exception) {
            android.graphics.Typeface.MONOSPACE
        }
    }

    val textPaint = remember(fontSizeSp, density, terminalTypeface) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
            isLinearText = true
            typeface = terminalTypeface
            textSize = fontSizePx
        }
    }

    val charWidth = remember(textPaint) { textPaint.measureText("M").coerceAtLeast(1f) }
    val fontMetrics = remember(textPaint) { textPaint.fontMetrics }
    val charHeight = remember(fontMetrics) { ((fontMetrics.descent - fontMetrics.ascent) * 1.25f).coerceAtLeast(1f) }
    val baselineOffset = remember(fontMetrics, charHeight) {
        -fontMetrics.ascent + (charHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f
    }

    // Trigger recompose HANYA jika jumlah baris atau buffer mode berubah (bukan per karakter)
    val isAltBuffer = session.buffer.isAltBuffer
    val lineCount = if (isAltBuffer) session.buffer.maxRows else session.buffer.lineCountState.intValue.coerceAtLeast(1)
    val totalWidthPx = (session.buffer.maxCols * charWidth).toInt().coerceAtLeast(1)
    val totalHeightPx = (lineCount * charHeight).toInt().coerceAtLeast(1)

    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val totalHeightDp = with(density) { totalHeightPx.toDp() }

    fun findWordBounds(lineIdx: Int, col: Int): Pair<Int, Int>? {
        val line = session.buffer.getLineAt(lineIdx) ?: return null
        val maxC = minOf(session.buffer.maxCols, line.size)
        if (col !in 0 until maxC) return null

        val targetCell = line.getOrNull(col) ?: return null
        if (targetCell.char == ' ' || targetCell.isWideContinuation) {
            // Sentuhan pada sel kosong TIDAK memilih kata terdekat (agar paste contextual berfungsi)
            return null
        }

        var start = col
        while (start > 0 && line.getOrNull(start - 1)?.let { it.char != ' ' && !it.isWideContinuation } == true) {
            start--
        }
        var end = col
        while (end < maxC - 1 && line.getOrNull(end + 1)?.let { it.char != ' ' && !it.isWideContinuation } == true) {
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
            val maxC = minOf(session.buffer.maxCols, line.size)
            if (maxC <= 0) continue
            val colStart = if (l == start.line) start.col.coerceIn(0, maxC - 1) else 0
            val colEnd = if (l == end.line) end.col.coerceIn(0, maxC - 1) else maxC - 1
            val lineText = StringBuilder()
            for (c in colStart..colEnd) {
                val cell = line.getOrNull(c) ?: continue
                if (cell.isWideContinuation) continue
                lineText.append(cell.char)
                if (cell.extraChar != null) lineText.append(cell.extraChar)
            }
            sb.append(lineText.toString().trimEnd())
            if (l < end.line) sb.append('\n')
        }
        return sb.toString()
    }

    val selStart = selectionStart
    val selEnd = selectionEnd
    val hasSelection = selStart != null && selEnd != null
    val selMin = if (hasSelection) minOf(selStart!!, selEnd!!) else null
    val selMax = if (hasSelection) maxOf(selStart!!, selEnd!!) else null

    val activeTheme = session.buffer.currentTheme
    val canvasBg = if (activeTheme.background == Color.Transparent) DevBackground else activeTheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    verticalScrollState,
                    enabled = !isDraggingHandle
                )
                .horizontalScroll(
                    horizontalScrollState,
                    enabled = !isDraggingHandle
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(width = totalWidthDp, height = totalHeightDp)
                    .pointerInput(session.buffer.isMouseTrackingEnabled, fontSizeSp, isAltBuffer, isShiftActive) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                if (charWidth > 0 && charHeight > 0) {
                                    val col = (offset.x / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                                    val maxLine = (if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()) - 1
                                    val line = (offset.y / charHeight).toInt().coerceIn(0, maxLine.coerceAtLeast(0))

                                    val detectedUrl = session.buffer.getUrlAt(line, col)
                                    if (detectedUrl != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTapUrl(detectedUrl)
                                    } else {
                                        val wordBounds = findWordBounds(line, col)
                                        if (wordBounds != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            pasteMenuOffset = null
                                            selectionStart = GridPoint(line, wordBounds.first)
                                            selectionEnd = GridPoint(line, wordBounds.second)
                                            showSelectionMenu = true
                                        } else {
                                            // Long-press di sel kosong / kursor: Tampilkan menu Tempel jika ada isi clipboard
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrEmpty()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectionStart = null
                                                selectionEnd = null
                                                showSelectionMenu = false
                                                pasteMenuOffset = offset
                                            }
                                        }
                                    }
                                }
                            },
                            onTap = { offset ->
                                if (selectionStart != null || pasteMenuOffset != null) {
                                    selectionStart = null
                                    selectionEnd = null
                                    showSelectionMenu = false
                                    pasteMenuOffset = null
                                } else {
                                    if (charWidth > 0 && charHeight > 0) {
                                        val col = (offset.x / charWidth).toInt().coerceIn(0, session.buffer.maxCols - 1)
                                        val maxLine = (if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()) - 1
                                        val line = (offset.y / charHeight).toInt().coerceIn(0, maxLine.coerceAtLeast(0))
                                        val historyCount = session.buffer.getScrollbackSize()
                                        val activeRow = line - historyCount

                                        val detectedUrl = session.buffer.getUrlAt(line, col)
                                        if (detectedUrl != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onTapUrl(detectedUrl)
                                        } else {
                                            if (session.buffer.isMouseTrackingEnabled && selectionStart == null) {
                                                if (activeRow in 0 until session.buffer.maxRows) {
                                                    session.sendMouseEvent(0, col, activeRow, true)
                                                    session.sendMouseEvent(0, col, activeRow, false)
                                                }
                                            } else {
                                                onFocusRequest()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                try {
                    val v = session.buffer.version.intValue // Daftarkan state di draw phase agar realtime redraw
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val maxCols = session.buffer.maxCols
                    val cursorRow = session.buffer.cursorRow
                    val cursorCol = session.buffer.cursorCol
                    val isCursorVisible = session.buffer.isCursorVisible
                    val historyCount = session.buffer.getScrollbackSize()

                    val selStart = selectionStart
                    val selEnd = selectionEnd
                    val hasSelection = selStart != null && selEnd != null
                    val selMin = if (hasSelection) minOf(selStart!!, selEnd!!) else null
                    val selMax = if (hasSelection) maxOf(selStart!!, selEnd!!) else null

                    // Viewport culling: Hanya render baris yang masuk dalam viewport layar
                    val scrollY = verticalScrollState.value.toFloat()
                    val viewportHeight = if (verticalScrollState.viewportSize == 0) {
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

                    val singleChar = CharArray(1)

                    for (lineIdx in firstVisibleLine..lastVisibleLine) {
                        val line = session.buffer.getLineAt(lineIdx) ?: continue
                        val lineCols = minOf(maxCols, line.size)
                        if (lineCols <= 0) continue
                        val yTop = lineIdx * charHeight
                        val yText = yTop + baselineOffset

                        // 1. Gambar latar belakang sel (Batched runs dengan warna sama)
                        var bgCol = 0
                        while (bgCol < lineCols) {
                            val startCol = bgCol
                            val cell = line.getOrNull(bgCol) ?: break
                            val isCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && bgCol == cursorCol)
                            val pt = GridPoint(lineIdx, bgCol)
                            val isSelected = hasSelection && selMin != null && selMax != null && pt >= selMin && pt <= selMax
                            val invBg = if (cell.isInverse) {
                                if (cell.fgColor == Color.Transparent || cell.fgColor == activeTheme.foreground) activeTheme.foreground
                                else cell.fgColor
                            } else {
                                cell.bgColor
                            }
                            val runBg = when {
                                isSelected -> activeTheme.cursorColor.copy(alpha = 0.35f)
                                isCursor -> activeTheme.cursorColor
                                else -> invBg
                            }

                            val step = if (cell.isWide) 2 else 1
                            var runLen = step
                            bgCol += step

                            while (bgCol < lineCols) {
                                val nextCell = line.getOrNull(bgCol) ?: break
                                val nextIsCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && bgCol == cursorCol)
                                val nextPt = GridPoint(lineIdx, bgCol)
                                val nextIsSelected = hasSelection && selMin != null && selMax != null && nextPt >= selMin && nextPt <= selMax
                                val nextInvBg = if (nextCell.isInverse) {
                                    if (nextCell.fgColor == Color.Transparent || nextCell.fgColor == activeTheme.foreground) activeTheme.foreground
                                    else nextCell.fgColor
                                } else {
                                    nextCell.bgColor
                                }
                                val nextBg = when {
                                    nextIsSelected -> activeTheme.cursorColor.copy(alpha = 0.35f)
                                    nextIsCursor -> activeTheme.cursorColor
                                    else -> nextInvBg
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

                        // 2. Gambar karakter teks presisi per sel monospaced (100% akurat, nol kerning drift, nol loncat kursor)
                        for (col in 0 until lineCols) {
                            val cell = line.getOrNull(col) ?: break
                            if (cell.isWideContinuation || cell.char == ' ') continue

                            val pt = GridPoint(lineIdx, col)
                            val isSelected = hasSelection && selMin != null && selMax != null && pt >= selMin && pt <= selMax
                            val isCursor = (isCursorVisible && (lineIdx - historyCount) == cursorRow && col == cursorCol)

                            val rawFg = if (cell.isDim) cell.fgColor.copy(alpha = 0.6f) else cell.fgColor
                            val invFg = if (cell.isInverse) {
                                if (cell.bgColor == Color.Transparent) (if (activeTheme.background == Color.Transparent) Color(0xFF0D1117) else activeTheme.background)
                                else cell.bgColor
                            } else {
                                rawFg
                            }
                            val fg = when {
                                isSelected -> Color.White
                                isCursor -> if (activeTheme.background == Color.Transparent) Color.Black else activeTheme.background
                                else -> invFg
                            }
                            textPaint.color = fg.toArgb()
                            textPaint.isFakeBoldText = cell.isBold || isSelected
                            textPaint.isUnderlineText = cell.isUnderline || cell.hyperlinkUrl != null
                            val cellX = col * charWidth

                            if (cell.extraChar != null) {
                                nativeCanvas.drawText("${cell.char}${cell.extraChar}", cellX, yText, textPaint)
                            } else {
                                singleChar[0] = cell.char
                                nativeCanvas.drawText(singleChar, 0, 1, cellX, yText, textPaint)
                            }
                        }

                        // Kursor di luar batas kolom karakter
                        if (isCursorVisible && (lineIdx - historyCount) == cursorRow && cursorCol >= maxCols) {
                            drawRect(
                                color = Color(0xFF7DFF4C),
                                topLeft = Offset((maxCols - 1) * charWidth, yTop),
                                size = Size(charWidth, charHeight)
                            )
                        }
                    }

                    // 3. Render Kitty Graphics Protocol Images (Pixel-Perfect Inline Images)
                    val visibleImages = session.buffer.getVisibleImages(firstVisibleLine, lastVisibleLine)
                    for (img in visibleImages) {
                        if (!img.bitmap.isRecycled) {
                            val x = img.startCol * charWidth
                            val y = img.startLine * charHeight
                            val w = img.cellWidth * charWidth
                            val h = img.cellHeight * charHeight
                            drawImage(
                                image = img.bitmap.asImageBitmap(),
                                dstOffset = IntOffset(x.toInt(), y.toInt()),
                                dstSize = IntSize(w.toInt(), h.toInt())
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Safe guard: jika terjadi race condition saat window resize instan, frame render ini diskip secara aman tanpa crash
                }
            }

            // Draggable Interactive Selection Handles (Overlay Handle Composable Pattern)
            if (hasSelection && selMin != null && selMax != null) {
                // Pin Mulai (Start Handle)
                SelectionHandle(
                    isStart = true,
                    gridPoint = selMin,
                    charWidth = charWidth,
                    charHeight = charHeight,
                    maxCols = session.buffer.maxCols,
                    maxRows = lineCount,
                    onPositionChange = { newPoint ->
                        if (selectionStart != null && selectionEnd != null) {
                            if (selectionStart!! <= selectionEnd!!) {
                                selectionStart = newPoint
                            } else {
                                selectionEnd = newPoint
                            }
                        } else {
                            selectionStart = newPoint
                        }
                    },
                    onDragStateChange = { isDragging ->
                        isDraggingHandle = isDragging
                        showSelectionMenu = !isDragging
                    }
                )

                // Pin Selesai (End Handle)
                SelectionHandle(
                    isStart = false,
                    gridPoint = selMax,
                    charWidth = charWidth,
                    charHeight = charHeight,
                    maxCols = session.buffer.maxCols,
                    maxRows = lineCount,
                    onPositionChange = { newPoint ->
                        if (selectionStart != null && selectionEnd != null) {
                            if (selectionStart!! <= selectionEnd!!) {
                                selectionEnd = newPoint
                            } else {
                                selectionStart = newPoint
                            }
                        } else {
                            selectionEnd = newPoint
                        }
                    },
                    onDragStateChange = { isDragging ->
                        isDraggingHandle = isDragging
                        showSelectionMenu = !isDragging
                    }
                )
            }
        }

        // 1. Menu Kontekstual Seleksi Teks (In-Tree Overlay Surface)
        if (hasSelection && selMin != null && selMax != null && showSelectionMenu && !isDraggingHandle) {
            val scrollX = horizontalScrollState.value.toFloat()
            val scrollY = if (!isAltBuffer) verticalScrollState.value else 0

            val topTextScreenY = selMin.line * charHeight - scrollY
            val bottomTextScreenY = (selMax.line + 1) * charHeight - scrollY
            val startScreenX = selMin.col * charWidth - scrollX

            val handleTouchSizePx = with(density) { 44.dp.toPx() }
            val menuHeightPx = with(density) { 40.dp.toPx() }
            val menuWidthPx = with(density) { 260.dp.toPx() }
            val marginPx = with(density) { 8.dp.toPx() }

            // Hitung posisi Y: Letakkan di atas teks jika ada ruang.
            // Jika teks di baris 0 (dekat tepi atas layar), letakkan di bawah kedua pin (di bawah 44dp touch area pin).
            val menuY = if (topTextScreenY >= menuHeightPx + marginPx) {
                (topTextScreenY - menuHeightPx - marginPx).toInt()
            } else {
                (bottomTextScreenY + handleTouchSizePx + marginPx).toInt()
            }

            val maxMenuX = (totalWidthPx - menuWidthPx - marginPx).toInt().coerceAtLeast(marginPx.toInt())
            val menuX = startScreenX.toInt().coerceIn(marginPx.toInt(), maxMenuX)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(menuX, menuY) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Konsumsi sentuhan agar tidak memicu tap deselect di Canvas
                    ),
                shape = RoundedCornerShape(50),
                color = DevSurface,
                border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val start = selectionStart
                            val end = selectionEnd
                            if (start != null && end != null) {
                                val text = getSelectedText(start, end)
                                if (text.isNotEmpty()) {
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Copied (${text.length} chars)", Toast.LENGTH_SHORT).show()
                                }
                            }
                            selectionStart = null
                            selectionEnd = null
                            showSelectionMenu = false
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = DevOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    val clipText = clipboardManager.getText()?.text
                    if (!clipText.isNullOrEmpty()) {
                        TextButton(
                            onClick = {
                                if (clipText.length > 2000 || clipText.count { it == '\n' } > 50) {
                                    onPasteLarge(clipText)
                                } else {
                                    session.pasteText(clipText)
                                }
                                selectionStart = null
                                selectionEnd = null
                                showSelectionMenu = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Paste", color = DevOnSurface, fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            val totalLines = if (isAltBuffer) session.buffer.maxRows else session.buffer.getLineCount()
                            selectionStart = GridPoint(0, 0)
                            selectionEnd = GridPoint((totalLines - 1).coerceAtLeast(0), session.buffer.maxCols - 1)
                            showSelectionMenu = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("All", color = DevOnSurface, fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = {
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
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Text"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            selectionStart = null
                            selectionEnd = null
                            showSelectionMenu = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = DevPrimary, modifier = Modifier.size(15.dp))
                    }

                    IconButton(
                        onClick = {
                            showSelectionMenu = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = DevOnSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // 2. Menu Kontekstual Tempel (In-Tree Overlay Surface)
        val pastePos = pasteMenuOffset
        if (pastePos != null && selectionStart == null) {
            val scrollX = horizontalScrollState.value.toFloat()
            val scrollY = if (!isAltBuffer) verticalScrollState.value else 0
            val screenX = pastePos.x - scrollX
            val screenY = pastePos.y - scrollY

            val pasteMenuHeightPx = with(density) { 40.dp.toPx() }
            val pasteMenuWidthPx = with(density) { 130.dp.toPx() }
            val marginPx = with(density) { 8.dp.toPx() }

            val pasteMenuY = if (screenY >= pasteMenuHeightPx + marginPx) {
                (screenY - pasteMenuHeightPx - marginPx).toInt()
            } else {
                (screenY + marginPx + 16f).toInt()
            }
            val maxPasteX = (totalWidthPx - pasteMenuWidthPx - marginPx).toInt().coerceAtLeast(marginPx.toInt())
            val pasteMenuX = screenX.toInt().coerceIn(marginPx.toInt(), maxPasteX)

            Surface(
                modifier = Modifier
                    .offset { IntOffset(pasteMenuX, pasteMenuY) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(50),
                color = DevSurface,
                border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.8f)),
                shadowElevation = 8.dp
            ) {
                TextButton(
                    onClick = {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrEmpty()) {
                            if (clipText.length > 2000 || clipText.count { it == '\n' } > 50) {
                                onPasteLarge(clipText)
                            } else {
                                session.pasteText(clipText)
                            }
                        }
                        pasteMenuOffset = null
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste", color = DevOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Interactive Selection Handle Composable (Overlay Handle Composable Pattern).
 * Memiliki area sentuh 44dp dan mendeteksi drag terisolasi menggunakan detectDragGestures.
 * Drag delta dihitung relatif terhadap posisi sentuh awal (dragStartLine, dragStartCol),
 * mencegah feedback loop yang membuat handle melompat liar ke baris paling bawah.
 */
@Composable
private fun SelectionHandle(
    isStart: Boolean,
    gridPoint: GridPoint,
    charWidth: Float,
    charHeight: Float,
    maxCols: Int,
    maxRows: Int,
    onPositionChange: (GridPoint) -> Unit,
    onDragStateChange: (Boolean) -> Unit = {}
) {
    val density = LocalDensity.current
    val handleTouchSizeDp = 44.dp
    val handleTouchSizePx = with(density) { handleTouchSizeDp.toPx() }
    val visualPinRadius = 6.dp

    val currentPoint by rememberUpdatedState(gridPoint)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)

    val pinX = if (isStart) gridPoint.col * charWidth else (gridPoint.col + 1) * charWidth
    val pinY = (gridPoint.line + 1) * charHeight

    val offsetX = (pinX - handleTouchSizePx / 2f).coerceAtLeast(0f)
    val offsetY = (pinY - 4f).coerceAtLeast(0f)

    var dragStartLine by remember { mutableStateOf(0) }
    var dragStartCol by remember { mutableStateOf(0) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
            .size(handleTouchSizeDp)
            .pointerInput(isStart) {
                detectDragGestures(
                    onDragStart = {
                        currentOnDragStateChange(true)
                        dragStartLine = currentPoint.line
                        dragStartCol = currentPoint.col
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        currentOnDragStateChange(false)
                    },
                    onDragCancel = {
                        currentOnDragStateChange(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        val deltaCol = (totalDragX / charWidth).toInt()
                        val deltaLine = (totalDragY / charHeight).toInt()

                        val newLine = (dragStartLine + deltaLine).coerceIn(0, (maxRows - 1).coerceAtLeast(0))
                        val newCol = (dragStartCol + deltaCol).coerceIn(0, maxCols - 1)

                        currentOnPositionChange(GridPoint(newLine, newCol))
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.size(handleTouchSizeDp)) {
            val cx = size.width / 2f
            val cy = 12.dp.toPx()
            val r = visualPinRadius.toPx()

            // Glowing touch halo (#7DFF4C)
            drawCircle(
                color = Color(0x447DFF4C),
                radius = r + 4.dp.toPx(),
                center = Offset(cx, cy)
            )
            // Pin head (#7DFF4C)
            drawCircle(
                color = Color(0xFF7DFF4C),
                radius = r,
                center = Offset(cx, cy)
            )
            // Pin pointer pointing to text anchor
            val path = Path().apply {
                moveTo(cx - 3.dp.toPx(), cy - 2.dp.toPx())
                lineTo(cx, 0f)
                lineTo(cx + 3.dp.toPx(), cy - 2.dp.toPx())
                close()
            }
            drawPath(path, color = Color(0xFF7DFF4C))
        }
    }
}

/**
 * Dialog Pemilihan Tema & Palet Warna Terminal DevBox.
 * Mendukung live dynamic preview dan mode hemat daya AMOLED.
 */
@Composable
fun TerminalThemeDialog(
    currentTheme: TerminalTheme,
    onDismiss: () -> Unit,
    onThemeSelect: (TerminalTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Terminal Themes & Palettes",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = DevPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pilih skema warna terminal atau aktifkan mode hemat baterai AMOLED. Efek langsung aktif seketika tanpa me-restart sesi.",
                    fontSize = 12.sp,
                    color = DevOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                TerminalTheme.ALL_THEMES.forEach { theme ->
                    val isSelected = theme.id == currentTheme.id
                    Surface(
                        onClick = { onThemeSelect(theme) },
                        color = if (isSelected) DevPrimaryContainer else DevSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) DevPrimary else DevBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = theme.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) DevPrimary else DevOnSurface
                                )
                                if (isSelected) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = DevPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = theme.description,
                                fontSize = 11.sp,
                                color = DevOnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Baris pill preview palet warna tema
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val previewColors = listOf(
                                    if (theme.background == Color.Transparent) Color(0xFF141414) else theme.background,
                                    theme.foreground,
                                    theme.cursorColor,
                                    theme.ansiColors.getOrElse(1) { Color.Red },
                                    theme.ansiColors.getOrElse(2) { Color.Green },
                                    theme.ansiColors.getOrElse(3) { Color.Yellow },
                                    theme.ansiColors.getOrElse(4) { Color.Blue },
                                    theme.ansiColors.getOrElse(5) { Color.Magenta }
                                )
                                previewColors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(width = 22.dp, height = 12.dp)
                                            .background(color, RoundedCornerShape(3.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary, contentColor = DevBackground)
            ) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DevSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Dialog Riwayat Salinan Clipboard Terminal (Smart Clipboard).
 */
@Composable
fun TerminalClipboardDialog(
    history: List<String>,
    onDismiss: () -> Unit,
    onPaste: (String) -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clipboard History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = DevPrimary
                )
                if (history.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Hapus Riwayat",
                            tint = DevError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada riwayat teks clipboard.",
                        fontSize = 13.sp,
                        color = DevOnSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ketuk cuplikan teks di bawah untuk langsung menempelkannya ke sesi terminal aktif.",
                        fontSize = 11.sp,
                        color = DevOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    history.forEachIndexed { index, snippet ->
                        Surface(
                            onClick = { onPaste(snippet) },
                            color = DevSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DevBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = snippet.take(150) + if (snippet.length > 150) "..." else "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = DevOnSurface,
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "#${index + 1} • ${snippet.length} chars",
                                        fontSize = 9.sp,
                                        color = DevPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Ketuk untuk Paste ↵",
                                        fontSize = 9.sp,
                                        color = DevOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DevSurfaceVariant, contentColor = DevOnSurface)
            ) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DevSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
