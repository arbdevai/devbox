package com.arbdev.devbox.ui.screens

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.data.ChrootState
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevSecondary
import com.arbdev.devbox.ui.theme.DevSuccess
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import com.arbdev.devbox.ui.theme.DevWarning
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onNavigateToTerminal: (initialCommand: String?) -> Unit,
    onNavigateToIde: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ChrootState()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var wakeLockActive by remember { mutableStateOf(KeepAliveService.isRunning) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun refreshState() {
        scope.launch {
            isRefreshing = true
            state = ChrootManager.getChrootState()
            wakeLockActive = KeepAliveService.isRunning
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
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
                    text = "Linux Chroot & Dev Environment",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { refreshState() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Segarkan",
                    tint = DevPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        // Status Card
        StatusCard(state = state)

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Primary Launchers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onNavigateToTerminal(null) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Terminal", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { onNavigateToIde() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DevSecondary)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Web IDE", color = Color.Black, fontWeight = FontWeight.SemiBold)
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

        Spacer(modifier = Modifier.height(12.dp))

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
                            actionMessage = if (enable) "Memasang mount chroot..." else "Membersihkan mount chroot..."
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
                    subtitle = if (wakeLockActive) "CPU aktif saat layar mati" else "Hemat baterai (CPU sleep)",
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
            text = "Telemetri Sistem",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                MetricRow(label = "Proses Chroot", value = "${state.activeProcesses} proses")
                Spacer(modifier = Modifier.height(8.dp))
                MetricRow(label = "Rootfs Path", value = ChrootManager.DEFAULT_ROOTFS)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Shell Shortcuts
        Text(
            text = "Pintasan Perintah Cepat",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onNavigateToTerminal("ub status") },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ub status", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(
                onClick = { onNavigateToTerminal("ub scan") },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ub scan", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(
                onClick = { onNavigateToTerminal("ub stop") },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ub stop", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Exit & Stop Background Service Button
        OutlinedButton(
            onClick = {
                KeepAliveService.stopService(context)
                (context as? Activity)?.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
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
            Text("Tutup Aplikasi & Hentikan Layanan", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatusCard(state: ChrootState) {
    val (statusColor, statusTitle, statusDesc) = when {
        !state.isRootGranted -> Triple(
            DevError,
            "Akses Root Tidak Terdeteksi",
            "Aplikasi membutuhkan izin Superuser (su). Periksa Magisk/KernelSU."
        )
        !state.isRootfsExists -> Triple(
            DevWarning,
            "Rootfs Tidak Ditemukan",
            "Direktori /data/local/ubuntu-chroot belum ada di penyimpanan."
        )
        state.isMounted -> Triple(
            DevSuccess,
            "Container Aktif & Siap",
            "Ubuntu chroot berjalan normal dan siap digunakan."
        )
        else -> Triple(
            DevPrimary,
            "Container Siap (Standby)",
            "Rootfs terverifikasi. Klik tombol Terminal untuk mulai sesi."
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
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
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
