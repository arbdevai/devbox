package com.arbdev.devbox.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevSecondary
import com.arbdev.devbox.ui.theme.DevSuccess
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import com.arbdev.devbox.ui.theme.DevWarning
import com.arbdev.devbox.updater.UpdateInfo
import com.arbdev.devbox.updater.UpdateManager
import com.arbdev.devbox.updater.UpdateStatus
import com.arbdev.devbox.util.AppLogger
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    // In-App Updater State
    val updateStatus by UpdateManager.updateStatus.collectAsState()

    fun refreshState() {
        scope.launch {
            isRefreshing = true
            state = ChrootManager.getChrootState()
            isInitialLoading = false
            val activeIds = TerminalSessionManager.sessions.map { it.id }
            val activePids = TerminalSessionManager.sessions.mapNotNull { it.getProcessPid() }
            orphanProcesses = ChrootManager.getOrphanChrootProcesses(activeIds, activePids)
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DevBox",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Standalone Linux Container & AI Agent CLI",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // In-App Update Icon Button with Changelog Dialog
                IconButton(
                    onClick = {
                        scope.launch {
                            Toast.makeText(context, "Memeriksa pembaruan...", Toast.LENGTH_SHORT).show()
                            val res = UpdateManager.checkForUpdates(context, force = true)
                            if (res.isSuccess) {
                                val info = res.getOrNull()
                                if (info != null && !info.hasUpdate) {
                                    showCurrentChangelogDialog = true
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Cek Pembaruan",
                        tint = DevPrimary
                    )
                }

                // Refresh Button
                IconButton(onClick = { refreshState() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Segarkan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Mengunduh Pembaruan (${status.progressPercent}%)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = DevPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            is UpdateStatus.ReadyToInstall -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pembaruan Siap Dipasang",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevSuccess
                        )
                        Button(
                            onClick = { UpdateManager.installApk(context, status.apkFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = DevSuccess)
                        ) {
                            Text("Pasang APK", color = Color.Black, fontWeight = FontWeight.Bold)
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
                    colors = CardDefaults.cardColors(containerColor = DevSurfaceVariant)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Sesi Usang di Background Banner (Jika Ada)
        AnimatedVisibility(visible = orphanProcesses.isNotEmpty()) {
            val totalRamMb = orphanProcesses.sumOf { it.memoryKb } / 1024
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF231602)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.7f))
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
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${orphanProcesses.size} Sesi Usang di Background (~${totalRamMb} MB)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Sisa force close lama yang memakan RAM. Bersihkan agar HP tidak lag.",
                                fontSize = 11.sp,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                    }
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
                                Toast.makeText(context, "$killed sesi lama berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isCleaningOrphans
                    ) {
                        if (isCleaningOrphans) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Bersihkan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Status Card dengan Proteksi Flash State Loading
        StatusCard(state = state, isLoading = isInitialLoading || isRefreshing)

        // ROOTFS SETUP OPTIONS IF NOT INSTALLED
        if (!state.isRootfsExists && state.isRootGranted) {
            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DevWarning.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = DevWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pemasangan Rootfs Mandiri",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Direktori target (${ChrootManager.activeRootfs}) belum memiliki binary sistem Linux (/bin/bash). Pilih salah satu metode di bawah:",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isOperating) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            color = DevPrimary
                        )
                    }

                    if (state.isSourceChrootAvailable) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = DevSuccess.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, DevSuccess.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💡 Terdeteksi Sistem Ubuntu di /data/local/ubuntu-chroot!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = DevSuccess
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kloning langsung dalam 15-20 detik tanpa kuota internet. Semua tools yang sudah Anda instal akan langsung tersedia di DevBox.",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    // Option 1: Clone from existing local chroot (Recommended)
                    Button(
                        onClick = {
                            scope.launch {
                                isOperating = true
                                actionMessage = "Menyalin direktori sistem dari /data/local/ubuntu-chroot ke ${ChrootManager.activeRootfs}... (15-20 detik)"
                                val res = ChrootManager.cloneExistingChroot(
                                    sourceChroot = "/data/local/ubuntu-chroot",
                                    targetRootfs = ChrootManager.activeRootfs
                                )
                                actionMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                refreshState()
                                isOperating = false
                            }
                        },
                        enabled = !isOperating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isSourceChrootAvailable) "1. Kloning dari /data/local/ubuntu-chroot (Instan ~15s)" else "1. Kloning dari chroot lokal",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 2: Download Official Ubuntu 24.04 ARM64
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isOperating = true
                                actionMessage = "Menyiapkan Ubuntu 24.04 ARM64 resmi (29 MB)..."
                                val res = ChrootManager.downloadUbuntuOfficialRootfs(ChrootManager.activeRootfs)
                                actionMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                refreshState()
                                isOperating = false
                            }
                        },
                        enabled = !isOperating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = DevSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. Unduh Ubuntu Base 24.04 ARM64 (Resmi Canonical 29 MB)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 3: Change rootfs path / use existing path directly
                    OutlinedButton(
                        onClick = {
                            tempPathInput = ChrootManager.activeRootfs
                            showCustomPathDialog = true
                        },
                        enabled = !isOperating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("3. Gunakan Path Rootfs Kustom Lain", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // View Error Logs Button
                    TextButton(
                        onClick = { onNavigateToLogs() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = DevWarning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Log Error & Diagnostik", color = DevWarning, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Primary Launchers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onNavigateToTerminal(null) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Terminal", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Button(
                onClick = { onNavigateToIde() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevSecondary)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Web IDE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = { onNavigateToLogs() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = DevWarning)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Container Services Controls
        Text(
            text = "Layanan & Kontrol Container",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Chroot Mount Toggle
                ControlRow(
                    icon = Icons.Default.Dns,
                    title = "Mount Chroot Filesystem",
                    subtitle = if (state.isMounted) "Proc, Sys, Dev & SDCard terpasang" else "Unmounted / Nonaktif",
                    isChecked = state.isMounted,
                    onCheckedChange = { enable ->
                        scope.launch {
                            actionMessage = if (enable) "Memasang mount container..." else "Membersihkan mount container..."
                            if (enable) ChrootManager.mountAll() else ChrootManager.unmountAll()
                            refreshState()
                            actionMessage = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Code Server Toggle
                ControlRow(
                    icon = Icons.Default.Code,
                    title = "Code-Server (Port 8080)",
                    subtitle = if (state.isCodeServerRunning) "Aktif di http://127.0.0.1:8080" else "Nonaktif",
                    isChecked = state.isCodeServerRunning,
                    onCheckedChange = { enable ->
                        scope.launch {
                            actionMessage = if (enable) "Menyalakan Code-Server..." else "Mematikan Code-Server..."
                            ChrootManager.toggleCodeServer(enable)
                            refreshState()
                            actionMessage = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // WakeLock Toggle
                ControlRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Background CPU WakeLock",
                    subtitle = if (wakeLockActive) "CPU aktif saat layar mati (notifikasi status bar aktif)" else "Hemat baterai (CPU sleep)",
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

        Spacer(modifier = Modifier.height(20.dp))

        // System Telemetry Card
        Text(
            text = "Telemetri Sistem & Jalur Rootfs",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MetricRow(label = "RAM Tersedia", value = "${state.ramAvailableMb} MB")
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "Swap Terpakai", value = "${state.swapUsedMb} MB")
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "Proses Container", value = "${state.activeProcesses} proses")
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "Sesi Terminal Aktif", value = "${TerminalSessionManager.sessions.size} sesi")
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(
                    label = "Sesi Background Usang",
                    value = if (orphanProcesses.isEmpty()) "Bersih (0)" else "${orphanProcesses.size} proses (${orphanProcesses.sumOf { it.memoryKb } / 1024} MB)"
                )
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "Jalur Rootfs Aktif", value = ChrootManager.activeRootfs)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Exit & Stop Container Button
        OutlinedButton(
            onClick = {
                KeepAliveService.killAllAndExit(context)
                (context as? Activity)?.finishAffinity()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevError),
            border = BorderStroke(1.dp, DevError.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = DevError)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Matikan DevBox & Keluar Total", fontWeight = FontWeight.SemiBold)
        }
    }

    // Custom Path Dialog
    if (showCustomPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPathDialog = false },
            title = { Text("Ubah Jalur Rootfs Target") },
            text = {
                Column {
                    Text(
                        text = "Masukkan path absolut direktori rootfs di perangkat Anda:",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPathInput,
                        onValueChange = { tempPathInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DevPrimary,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempPathInput.trim()
                        if (trimmed.startsWith("/")) {
                            ChrootManager.activeRootfs = trimmed
                            AppLogger.i("ChrootManager", "Jalur rootfs diubah ke: $trimmed")
                            refreshState()
                            showCustomPathDialog = false
                        } else {
                            Toast.makeText(context, "Path harus diawali dengan /", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
                ) {
                    Text("Terapkan", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPathDialog = false }) {
                    Text("Batal", color = Color.White)
                }
            }
        )
    }

    // Dialog Changelog Versi Terbaru / Saat Ini
    if (showCurrentChangelogDialog) {
        val info = UpdateManager.lastCheckedInfo
        AlertDialog(
            onDismissRequest = { showCurrentChangelogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DevSuccess,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Versi Terbaru (${info?.latestVersion ?: "v${com.arbdev.devbox.BuildConfig.VERSION_NAME}"})",
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
                        text = "Aplikasi Anda sudah menggunakan rilis paling mutakhir.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF101010),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Catatan Rilis (Changelog):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DevPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = info?.releaseNotes?.ifBlank { "Pembaruan kestabilan dan optimalisasi container DevBox." }
                                    ?: "Pembaruan kestabilan dan optimalisasi container DevBox.",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrentChangelogDialog = false }) {
                    Text("Tutup", color = DevPrimary, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E14)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DevSuccess.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = DevSuccess.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "UPDATE TERSEDIA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = DevSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = info.latestVersion,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Versi saat ini: ${info.currentVersion} • Ukuran: ${"%.1f".format(info.apkSizeMb)} MB",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
                Button(
                    onClick = onUpdateClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DevSuccess),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Perbarui", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tombol Toggle Changelog
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showChangelog = !showChangelog }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (showChangelog) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = DevSuccess,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showChangelog) "Sembunyikan Catatan Rilis (Changelog)" else "Lihat Catatan Rilis (Changelog)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DevSuccess
                )
            }

            // Konten Changelog
            AnimatedVisibility(visible = showChangelog) {
                Surface(
                    color = Color(0xFF09140C),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DevSuccess.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Apa yang Baru di ${info.latestVersion}:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevSuccess
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.releaseNotes.ifBlank { "Tidak ada catatan rilis rinci yang disertakan." },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFD0E0D4),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(state: ChrootState, isLoading: Boolean = false) {
    val (statusColor, statusTitle, statusDesc) = when {
        isLoading -> Triple(
            DevPrimary,
            "Memeriksa Status Container & Root...",
            "Sedang memverifikasi izin Superuser (su) dan integritas container DevBox."
        )
        !state.isRootGranted -> Triple(
            DevError,
            "Akses Root Tidak Terdeteksi",
            "Aplikasi membutuhkan izin Superuser (su). Periksa Magisk/KernelSU."
        )
        !state.isRootfsExists -> Triple(
            DevWarning,
            "Rootfs Belum Terpasang",
            "Direktori ${ChrootManager.activeRootfs} belum memiliki binary sistem Linux (/bin/bash)."
        )
        state.isMounted -> Triple(
            DevSuccess,
            "Container DevBox Aktif",
            "Rootfs berjalan normal dan siap digunakan."
        )
        else -> Triple(
            DevPrimary,
            "Container DevBox Siap (Standby)",
            "Rootfs terverifikasi. Masuk ke Terminal untuk memulai sesi."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = DevPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusDesc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DevPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = DevPrimary
            )
        )
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
