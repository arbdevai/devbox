package com.arbdev.devbox.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import com.arbdev.devbox.ui.theme.shimmerEffect
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.data.ChrootProcessInfo
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.ui.theme.DevBackground
import com.arbdev.devbox.ui.theme.DevBorder
import com.arbdev.devbox.ui.theme.DevCanvasDeep
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevOnPrimary
import com.arbdev.devbox.ui.theme.DevOnPrimaryContainer
import com.arbdev.devbox.ui.theme.DevOnSurface
import com.arbdev.devbox.ui.theme.DevOnSurfaceVariant
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevPrimaryContainer
import com.arbdev.devbox.ui.theme.DevSecondary
import com.arbdev.devbox.ui.theme.DevSuccess
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import com.arbdev.devbox.ui.theme.DevWarning
import com.arbdev.devbox.updater.UpdateInfo
import com.arbdev.devbox.updater.UpdateManager
import com.arbdev.devbox.updater.UpdateStatus
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    onNavigateToTerminal: (initialCommand: String?) -> Unit,
    onNavigateToIde: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Muat langsung dari cache singleton agar bebas buffering / zero delay saat berpindah tab
    var state by remember { mutableStateOf(ChrootManager.lastKnownState ?: ChrootState(isRootGranted = true)) }
    var isInitialLoading by remember { mutableStateOf(ChrootManager.lastKnownState == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var wakeLockActive by remember { mutableStateOf(KeepAliveService.isRunning) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    // Sesi Latar Belakang Usang (Orphan Sessions)
    var orphanProcesses by remember { mutableStateOf<List<ChrootProcessInfo>>(emptyList()) }
    var isCleaningOrphans by remember { mutableStateOf(false) }

    // Dialog Custom Rootfs Path
    var showCustomPathDialog by remember { mutableStateOf(false) }
    var tempPathInput by remember { mutableStateOf(ChrootManager.activeRootfs) }

    // Dialog Changelog Versi Saat Ini
    var showCurrentChangelogDialog by remember { mutableStateOf(false) }

    // Dialog Konfirmasi Keluar Total
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var exitProgress by remember { mutableFloatStateOf(0f) }
    var exitStatusText by remember { mutableStateOf("") }

    // Dialog Konfirmasi Hapus Rootfs (Uninstall)
    var showUninstallDialog by remember { mutableStateOf(false) }
    var isUninstalling by remember { mutableStateOf(false) }
    var uninstallLogs by remember { mutableStateOf(listOf<String>()) }
    var uninstallFinished by remember { mutableStateOf(false) }
    var uninstallSuccess by remember { mutableStateOf(false) }

    // Dialog Instalasi Ubuntu Base Resmi (Download & Ekstraksi)
    var showInstallDialog by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableFloatStateOf(0f) }
    var installStatusText by remember { mutableStateOf("") }
    var installLogs by remember { mutableStateOf(listOf<String>()) }
    var installFinished by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf(false) }

    // In-App Updater State
    val updateStatus by UpdateManager.updateStatus.collectAsStateWithLifecycle()

    fun refreshState() {
        scope.launch {
            isRefreshing = true
            state = ChrootManager.getChrootState()
            if (state.isRootGranted && state.isRootfsExists) {
                val activeIds = TerminalSessionManager.sessions.map { it.id }
                val activePids = TerminalSessionManager.sessions.mapNotNull { it.getProcessPid() }
                orphanProcesses = ChrootManager.getOrphanChrootProcesses(activeIds, activePids)
            } else {
                orphanProcesses = emptyList()
            }
            isInitialLoading = false
            wakeLockActive = KeepAliveService.isRunning
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
        // Cek update otomatis di background & bersihkan APK usang (dithrottle)
        UpdateManager.checkForUpdates(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        // 2026 Google Expressive Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DevBox",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DevOnSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = DevPrimaryContainer,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "v${com.arbdev.devbox.BuildConfig.VERSION_NAME}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Linux Container Engine • AI Agent Runtime",
                    fontSize = 12.sp,
                    color = DevOnSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = {
                        scope.launch {
                            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                            val res = UpdateManager.checkForUpdates(context, force = true)
                            if (res.isSuccess) {
                                val info = res.getOrNull()
                                if (info != null && !info.hasUpdate) {
                                    showCurrentChangelogDialog = true
                                }
                            }
                        }
                    },
                    color = DevSurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Check Updates",
                            tint = DevPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Surface(
                    onClick = { refreshState() },
                    color = DevSurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = DevOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // In-App Update Notification Card
        when (val status = updateStatus) {
            is UpdateStatus.Available -> {
                UpdateBannerCard(
                    info = status.info,
                    onUpdateClick = {
                        scope.launch {
                            UpdateManager.startDownload(context, status.info)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            is UpdateStatus.Downloading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DevSurface),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Downloading Update (${status.progressPercent}%)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = DevPrimary,
                            trackColor = DevSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is UpdateStatus.ReadyToInstall -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DevPrimaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, DevPrimary)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Update Ready to Install",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary
                        )
                        Button(
                            onClick = { UpdateManager.installApk(context, status.apkFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Install APK", color = DevOnPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            else -> {}
        }

        // Action Message Banner
        AnimatedVisibility(visible = actionMessage != null) {
            actionMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = DevPrimaryContainer),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = DevPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = DevOnPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Orphan Processes Alert Card
        AnimatedVisibility(visible = orphanProcesses.isNotEmpty()) {
            val totalRamMb = orphanProcesses.sumOf { it.memoryKb } / 1024
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF211A0B)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, DevWarning.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${orphanProcesses.size} Orphan Processes (~${totalRamMb} MB)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DevOnSurface
                            )
                            Text(
                                text = "Detached background processes found. Kill to free RAM.",
                                fontSize = 11.sp,
                                color = DevOnSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isCleaningOrphans = true
                                val activeIds = TerminalSessionManager.sessions.map { it.id }
                                val activePids = TerminalSessionManager.sessions.mapNotNull { it.getProcessPid() }
                                val killed = ChrootManager.killOrphanChrootProcesses(activeIds, activePids)
                                orphanProcesses = emptyList()
                                isCleaningOrphans = false
                                refreshState()
                                Toast.makeText(context, "$killed orphan processes terminated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevWarning),
                        shape = RoundedCornerShape(50),
                        enabled = !isCleaningOrphans
                    ) {
                        if (isCleaningOrphans) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Kill All", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Hero Container Status Card (24.dp Expressive Squircle)
        StatusCard(
            state = state,
            isLoading = isInitialLoading || isRefreshing,
            onOpenTerminal = { onNavigateToTerminal(null) }
        )

        // ROOTFS SETUP OPTIONS IF NOT INSTALLED
        if (!state.isRootfsExists && state.isRootGranted) {
            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DevSurface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, DevWarning.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = DevWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rootfs Provisioning",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevOnSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Target directory (${ChrootManager.activeRootfs}) has no Linux rootfs (/bin/bash). Choose a setup method:",
                        fontSize = 12.sp,
                        color = DevOnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isOperating) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = DevPrimary,
                            trackColor = DevSurfaceVariant
                        )
                    }

                    if (state.isSourceChrootAvailable) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = DevPrimaryContainer),
                            border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚡ Existing Ubuntu Chroot Detected (/data/local/ubuntu-chroot)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DevPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Clone locally in ~15 seconds with zero internet usage. All installed packages and CLI tools are preserved.",
                                    fontSize = 11.sp,
                                    color = DevOnSurface
                                )
                            }
                        }
                    }

                    // Option 1: Clone from existing local chroot
                    Button(
                        onClick = {
                            scope.launch {
                                isOperating = true
                                actionMessage = "Cloning /data/local/ubuntu-chroot to ${ChrootManager.DEFAULT_ROOTFS}..."
                                val res = ChrootManager.cloneExistingChroot(
                                    sourceChroot = ChrootManager.LEGACY_ROOTFS,
                                    targetRootfs = ChrootManager.DEFAULT_ROOTFS
                                )
                                if (res.isSuccess) {
                                    ChrootManager.setActiveRootfs(context, ChrootManager.DEFAULT_ROOTFS)
                                }
                                actionMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                refreshState()
                                isOperating = false
                            }
                        },
                        enabled = !isOperating,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevOnPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isSourceChrootAvailable) "Clone Local Ubuntu Chroot (~15s)" else "Clone Local Chroot",
                            color = DevOnPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 2: Download Official Ubuntu 24.04 ARM64
                    OutlinedButton(
                        onClick = {
                            showInstallDialog = true
                            isInstalling = true
                            installProgress = 0f
                            installStatusText = "Preparing download..."
                            installLogs = listOf("[INIT] Preparing Ubuntu Base 24.04 ARM64 installation...")
                            installFinished = false
                            installSuccess = false

                            scope.launch {
                                val res = ChrootManager.downloadUbuntuOfficialRootfs(
                                    targetRootfs = ChrootManager.activeRootfs,
                                    onProgress = { prog, status ->
                                        installProgress = prog
                                        installStatusText = status
                                    },
                                    onLogLine = { line ->
                                        installLogs = (installLogs + line).takeLast(200)
                                    }
                                )
                                isInstalling = false
                                installFinished = true
                                installSuccess = res.isSuccess
                                if (res.isSuccess) {
                                    installStatusText = "Installation complete!"
                                    installProgress = 1f
                                } else {
                                    installStatusText = "Failed: ${res.exceptionOrNull()?.message ?: "Unknown error"}"
                                }
                                refreshState()
                            }
                        },
                        enabled = !isOperating && !isInstalling,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Ubuntu 24.04 ARM64 (Official 29 MB)", color = DevOnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 3: Custom Rootfs Path
                    OutlinedButton(
                        onClick = {
                            tempPathInput = ChrootManager.activeRootfs
                            showCustomPathDialog = true
                        },
                        enabled = !isOperating,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DevBorder)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Set Custom Rootfs Path", color = DevOnSurface, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = { onNavigateToLogs() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Diagnostics & Logs", color = DevPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Action Pill Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onNavigateToTerminal(null) },
                modifier = Modifier
                    .weight(1.2f)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = DevOnPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Terminal", color = DevOnPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }

            Surface(
                onClick = { onNavigateToIde() },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = DevPrimaryContainer,
                border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Web IDE", color = DevPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Surface(
                onClick = { onNavigateToLogs() },
                modifier = Modifier
                    .weight(0.9f)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = DevSurface,
                border = BorderStroke(1.dp, DevBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Logs", color = DevOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 2x2 Bento Grid System Telemetry
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System Telemetry",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DevOnSurface
            )
            Surface(
                onClick = {
                    tempPathInput = ChrootManager.activeRootfs
                    showCustomPathDialog = true
                },
                color = DevSurfaceVariant,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = ChrootManager.activeRootfs,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DevPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val isBentoLoading = isInitialLoading || isRefreshing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoMetricTile(
                title = "Available RAM",
                value = "${state.ramAvailableMb} MB",
                subtitle = if (state.ramAvailableMb > 700) "Optimal" else "High Pressure",
                accentColor = if (state.ramAvailableMb > 450) DevPrimary else DevWarning,
                isLoading = isBentoLoading,
                modifier = Modifier.weight(1f)
            )
            BentoMetricTile(
                title = "Swap / ZRAM",
                value = "${state.swapUsedMb} MB",
                subtitle = "Active Swap",
                accentColor = DevPrimary,
                isLoading = isBentoLoading,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoMetricTile(
                title = "Container PIDs",
                value = "${state.activeProcesses}",
                subtitle = if (orphanProcesses.isEmpty()) "0 Orphans" else "${orphanProcesses.size} Orphans",
                accentColor = if (orphanProcesses.isEmpty()) DevPrimary else DevWarning,
                isLoading = isBentoLoading,
                modifier = Modifier.weight(1f)
            )
            BentoMetricTile(
                title = "PTY Sessions",
                value = "${TerminalSessionManager.sessions.size}",
                subtitle = "Interactive Shells",
                accentColor = DevPrimary,
                isLoading = isBentoLoading,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Services & Runtime Controls
        Text(
            text = "Services & Runtime",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DevOnSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DevSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DevBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ControlRow(
                    icon = Icons.Default.Dns,
                    title = "Filesystem Mounts",
                    subtitle = if (state.isMounted) "/proc, /sys, /dev/pts & /sdcard mounted" else "Unmounted / Standby",
                    isChecked = state.isMounted,
                    onCheckedChange = { enable ->
                        scope.launch {
                            actionMessage = if (enable) "Mounting container filesystems..." else "Unmounting container..."
                            if (enable) ChrootManager.mountAll() else ChrootManager.unmountAll()
                            refreshState()
                            actionMessage = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                ControlRow(
                    icon = Icons.Default.Code,
                    title = "Code-Server Web IDE",
                    subtitle = if (state.isCodeServerRunning) "Listening on 127.0.0.1:8080" else "Stopped",
                    isChecked = state.isCodeServerRunning,
                    onCheckedChange = { enable ->
                        scope.launch {
                            actionMessage = if (enable) "Starting Code-Server..." else "Stopping Code-Server..."
                            ChrootManager.toggleCodeServer(enable)
                            refreshState()
                            actionMessage = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                ControlRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Background CPU WakeLock",
                    subtitle = if (wakeLockActive) "Keeps container alive when screen is off" else "Battery saver (CPU may sleep)",
                    isChecked = wakeLockActive,
                    onCheckedChange = { enable ->
                        if (enable) {
                            KeepAliveService.startService(context)
                        } else {
                            KeepAliveService.stopService(context)
                        }
                        wakeLockActive = enable
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Danger Zone / Power Actions
        Text(
            text = "System & Power",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DevOnSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { showUninstallDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DevError),
                border = BorderStroke(1.dp, DevError.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DevError, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wipe Rootfs", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }

            Button(
                onClick = { showExitConfirmationDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D1616))
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = DevError, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Shutdown & Exit", color = DevError, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Shutdown & Exit Confirmation Dialog
    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { if (!isExiting) showExitConfirmationDialog = false },
            containerColor = DevSurface,
            titleContentColor = DevOnSurface,
            textContentColor = DevOnSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = DevError)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shutdown DevBox & Exit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (isExiting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { exitProgress },
                            color = DevError,
                            trackColor = DevError.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exitStatusText,
                                fontSize = 12.sp,
                                color = DevOnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(exitProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DevError
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Stop all Linux container processes, unmount filesystems, and close background services?",
                        color = DevOnSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                if (!isExiting) {
                    Button(
                        onClick = {
                            scope.launch {
                                isExiting = true
                                exitProgress = 0.20f
                                exitStatusText = "Menghentikan seluruh sesi terminal..."
                                withContext(Dispatchers.IO) {
                                    com.arbdev.devbox.terminal.TerminalSessionManager.terminateAll()
                                }
                                exitProgress = 0.55f
                                exitStatusText = "Menyapu bersih proses container di /proc..."
                                withContext(Dispatchers.IO) {
                                    com.arbdev.devbox.data.ChrootManager.killDevboxProcessesAndUnmountSync()
                                }
                                exitProgress = 0.85f
                                exitStatusText = "Melepas service background & penyimpanan..."
                                withContext(Dispatchers.IO) {
                                    com.arbdev.devbox.service.KeepAliveService.stopService(context)
                                }
                                exitProgress = 1.0f
                                exitStatusText = "Selesai. Menutup DevBox..."
                                kotlinx.coroutines.delay(200)
                                (context as? Activity)?.finishAndRemoveTask()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevError),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Shutdown & Exit", color = DevOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (!isExiting) {
                    TextButton(onClick = { showExitConfirmationDialog = false }) {
                        Text("Cancel", color = DevOnSurfaceVariant)
                    }
                }
            }
        )
    }

    // Wipe Rootfs Confirmation Dialog
    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isUninstalling) {
                    showUninstallDialog = false
                    uninstallFinished = false
                    uninstallLogs = emptyList()
                }
            },
            containerColor = DevSurface,
            titleContentColor = DevOnSurface,
            textContentColor = DevOnSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        isUninstalling -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = DevError,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wiping Rootfs...", color = DevError, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        uninstallFinished -> {
                            if (uninstallSuccess) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DevPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rootfs Wiped Clean", color = DevPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Error, contentDescription = null, tint = DevError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wipe Failed", color = DevError, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DevError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wipe Rootfs & Free Storage", color = DevError, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                if (isUninstalling || uninstallFinished) {
                    val logListState = rememberLazyListState()
                    LaunchedEffect(uninstallLogs.size) {
                        if (uninstallLogs.isNotEmpty()) {
                            logListState.animateScrollToItem(uninstallLogs.size - 1)
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when {
                                isUninstalling -> "Stopping processes, unmounting filesystems, and removing Linux files. Please wait..."
                                uninstallSuccess -> "All Linux rootfs files at ${ChrootManager.activeRootfs} have been removed."
                                else -> "An error occurred while removing rootfs. Check execution log below:"
                            },
                            fontSize = 12.sp,
                            color = DevOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 220.dp)
                                .background(DevCanvasDeep, RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    when {
                                        isUninstalling -> DevError.copy(alpha = 0.5f)
                                        uninstallSuccess -> DevPrimary.copy(alpha = 0.6f)
                                        else -> DevError
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(8.dp)
                        ) {
                            LazyColumn(state = logListState) {
                                items(uninstallLogs) { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = when {
                                            line.contains("SUCCESS", ignoreCase = true) || line.contains("selesai", ignoreCase = true) || line.contains("berhasil", ignoreCase = true) -> DevPrimary
                                            line.contains("GAGAL", ignoreCase = true) || line.contains("ERROR", ignoreCase = true) || line.contains("PERINGATAN", ignoreCase = true) -> DevError
                                            line.startsWith("[") -> DevOnPrimaryContainer
                                            else -> DevOnSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (ChrootManager.activeRootfs == ChrootManager.LEGACY_ROOTFS) {
                        Column {
                            Text(
                                text = "🛡️ TERMUX CHROOT PROTECTION ACTIVE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DevPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Active path is linked to: /data/local/ubuntu-chroot (Termux).",
                                fontSize = 12.sp,
                                color = DevOnSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "For safety, DevBox will NOT delete your Termux Linux directory. This will only stop DevBox processes, unmount filesystems, and clean temporary bridge files.",
                                fontSize = 12.sp,
                                color = DevOnPrimaryContainer
                            )
                        }
                    } else {
                        Column {
                            Text(
                                text = "WARNING: This will stop the container, unmount all filesystems, and PERMANENTLY DELETE all files in:",
                                fontSize = 13.sp,
                                color = DevOnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ChrootManager.activeRootfs,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DevPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This frees 2–5 GB of internal storage. Container data cannot be recovered once wiped.",
                                fontSize = 12.sp,
                                color = Color(0xFFFFB4AB)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    isUninstalling -> {}
                    uninstallFinished -> {
                        Button(
                            onClick = {
                                showUninstallDialog = false
                                uninstallFinished = false
                                uninstallLogs = emptyList()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uninstallSuccess) DevPrimary else DevSurfaceVariant
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Close", color = if (uninstallSuccess) DevOnPrimary else DevOnSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    isUninstalling = true
                                    uninstallFinished = false
                                    uninstallSuccess = false
                                    uninstallLogs = listOf("Starting rootfs wipe...")
                                    val res = ChrootManager.uninstallRootfs(ChrootManager.activeRootfs) { line ->
                                        uninstallLogs = (uninstallLogs + line).takeLast(80)
                                    }
                                    isUninstalling = false
                                    uninstallFinished = true
                                    uninstallSuccess = res.isSuccess
                                    refreshState()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DevError),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Wipe Now", color = DevOnPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                when {
                    isUninstalling -> {}
                    uninstallFinished -> {
                        if (!uninstallSuccess && uninstallLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("DevBox Uninstall Log", uninstallLogs.joinToString("\n"))
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Log")
                            }
                        }
                    }
                    else -> {
                        TextButton(onClick = { showUninstallDialog = false }) {
                            Text("Cancel", color = DevOnSurfaceVariant)
                        }
                    }
                }
            }
        )
    }

    // Ubuntu 24.04 Download & Extraction Dialog
    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isInstalling) {
                    showInstallDialog = false
                    installFinished = false
                    installLogs = emptyList()
                    installProgress = 0f
                }
            },
            containerColor = DevSurface,
            titleContentColor = DevOnSurface,
            textContentColor = DevOnSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        isInstalling -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = DevPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (installProgress < 1f && !installStatusText.startsWith("Mengekstrak")) "Downloading Ubuntu 24.04..." else "Extracting Rootfs...",
                                color = DevPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        installFinished -> {
                            if (installSuccess) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DevPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Installation Complete", color = DevPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Error, contentDescription = null, tint = DevError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Installation Failed", color = DevError, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = DevPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ubuntu Base 24.04", color = DevPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            text = {
                val logListState = rememberLazyListState()
                LaunchedEffect(installLogs.size) {
                    if (installLogs.isNotEmpty()) {
                        logListState.animateScrollToItem(installLogs.size - 1)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (installStatusText.isNotBlank()) installStatusText else "Preparing official Canonical Ubuntu 24.04 ARM64...",
                        fontSize = 12.sp,
                        color = DevOnSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isInstalling) {
                        if (installProgress > 0f && installProgress < 1f) {
                            LinearProgressIndicator(
                                progress = { installProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = DevPrimary,
                                trackColor = DevSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(installProgress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = DevPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.End)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = DevPrimary,
                                trackColor = DevSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp)
                            .background(DevCanvasDeep, RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                when {
                                    isInstalling -> DevPrimary.copy(alpha = 0.5f)
                                    installSuccess -> DevPrimary.copy(alpha = 0.7f)
                                    else -> DevError.copy(alpha = 0.6f)
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp)
                    ) {
                        LazyColumn(state = logListState) {
                            items(installLogs) { line ->
                                Text(
                                    text = line,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = when {
                                        line.contains("BERHASIL", ignoreCase = true) || line.contains("SUCCESS", ignoreCase = true) -> DevPrimary
                                        line.contains("GAGAL", ignoreCase = true) || line.contains("FAILED", ignoreCase = true) || line.contains("ERROR", ignoreCase = true) -> DevError
                                        line.startsWith("[") -> DevOnPrimaryContainer
                                        else -> DevOnSurface
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    isInstalling -> {}
                    installFinished -> {
                        Button(
                            onClick = {
                                showInstallDialog = false
                                installFinished = false
                                installLogs = emptyList()
                                installProgress = 0f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (installSuccess) DevPrimary else DevSurfaceVariant
                            ),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Close", color = if (installSuccess) DevOnPrimary else DevOnSurface, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                when {
                    isInstalling -> {}
                    installFinished -> {
                        if (!installSuccess && installLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("DevBox Install Log", installLogs.joinToString("\n"))
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Log")
                            }
                        }
                    }
                }
            }
        )
    }

    // Custom Path Dialog
    if (showCustomPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPathDialog = false },
            containerColor = DevSurface,
            titleContentColor = DevOnSurface,
            textContentColor = DevOnSurfaceVariant,
            title = { Text("Target Rootfs Path", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter the absolute rootfs directory path on your device:",
                        fontSize = 13.sp,
                        color = DevOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPathInput,
                        onValueChange = { tempPathInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = DevOnSurface),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DevPrimary,
                            unfocusedBorderColor = DevBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempPathInput.trim()
                        if (trimmed.startsWith("/")) {
                            ChrootManager.setActiveRootfs(context, trimmed)
                            refreshState()
                            showCustomPathDialog = false
                        } else {
                            Toast.makeText(context, "Path must start with /", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Apply", color = DevOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPathDialog = false }) {
                    Text("Cancel", color = DevOnSurfaceVariant)
                }
            }
        )
    }

    // Current Release Notes Dialog
    if (showCurrentChangelogDialog) {
        val info = UpdateManager.lastCheckedInfo
        AlertDialog(
            onDismissRequest = { showCurrentChangelogDialog = false },
            containerColor = DevSurface,
            titleContentColor = DevOnSurface,
            textContentColor = DevOnSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DevPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Up to Date (${info?.latestVersion ?: "v${com.arbdev.devbox.BuildConfig.VERSION_NAME}"})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "You are running the latest version of DevBox.",
                        fontSize = 12.sp,
                        color = DevOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = DevCanvasDeep,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DevBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Release Notes:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DevPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = info?.releaseNotes?.ifBlank { "Container stability and UI/UX improvements." }
                                    ?: "Container stability and UI/UX improvements.",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = DevOnSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrentChangelogDialog = false }) {
                    Text("Close", color = DevPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun UpdateBannerCard(
    info: UpdateInfo,
    onUpdateClick: () -> Unit
) {
    var showChangelog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DevPrimaryContainer),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = DevPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "UPDATE AVAILABLE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DevPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = info.latestVersion,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevOnSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current: ${info.currentVersion} • ${"%.1f".format(info.apkSizeMb)} MB",
                        fontSize = 11.sp,
                        color = DevOnSurfaceVariant
                    )
                }
                Button(
                    onClick = onUpdateClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = DevOnPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update", color = DevOnPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showChangelog = !showChangelog }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (showChangelog) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = DevPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showChangelog) "Hide Release Notes" else "View Release Notes",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DevPrimary
                )
            }

            AnimatedVisibility(visible = showChangelog) {
                Surface(
                    color = DevCanvasDeep,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "What's new in ${info.latestVersion}:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.releaseNotes.ifBlank { "Bug fixes and performance improvements." },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DevOnSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    state: ChrootState,
    isLoading: Boolean = false,
    onOpenTerminal: () -> Unit = {}
) {
    val (statusColor, badgeText, statusTitle, statusDesc) = when {
        isLoading -> listOf(
            DevPrimary,
            "VERIFYING",
            "Checking Container Engine...",
            "Inspecting root access (su) and Linux rootfs integrity."
        )
        !state.isRootGranted -> listOf(
            DevError,
            "NO ROOT",
            "Root Access Required",
            "Grant Superuser (su) permission in Magisk or KernelSU."
        )
        !state.isRootfsExists -> listOf(
            DevWarning,
            "UNINSTALLED",
            "Rootfs Not Installed",
            "No /bin/bash found in ${ChrootManager.activeRootfs}."
        )
        state.isMounted -> listOf(
            DevPrimary,
            "RUNNING",
            "Ubuntu Container Active",
            "Filesystems mounted and ready for PTY sessions."
        )
        else -> listOf(
            DevPrimary,
            "STANDBY",
            "Container Ready",
            "Rootfs verified. Open Terminal to auto-mount and start."
        )
    }

    val accent = statusColor as Color
    val badge = badgeText as String
    val title = statusTitle as String
    val desc = statusDesc as String

    val infiniteTransition = rememberInfiniteTransition(label = "heroStatusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroPulseAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DevSurface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = accent,
                                strokeWidth = 1.8.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = if (state.isMounted) pulseAlpha else 1f))
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                    }
                }

                if (state.isRootfsExists && state.isRootGranted) {
                    Surface(
                        onClick = onOpenTerminal,
                        color = DevPrimaryContainer,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Open Shell →",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DevOnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = DevOnSurfaceVariant
            )
        }
    }
}

@Composable
fun BentoMetricTile(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.then(
            if (isLoading) Modifier.shimmerEffect() else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = DevSurface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DevBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DevOnSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = DevOnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isChecked) DevPrimaryContainer else DevSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isChecked) DevPrimary.copy(alpha = 0.4f) else DevBorder),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isChecked) DevPrimary else DevOnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DevOnSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isChecked) DevPrimary.copy(alpha = 0.85f) else DevOnSurfaceVariant
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DevOnPrimary,
                checkedTrackColor = DevPrimary,
                uncheckedThumbColor = DevOnSurfaceVariant,
                uncheckedTrackColor = DevSurfaceVariant,
                uncheckedBorderColor = DevBorder
            )
        )
    }
}
