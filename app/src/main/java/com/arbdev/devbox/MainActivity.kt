package com.arbdev.devbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.service.KeepAliveService
import com.arbdev.devbox.ui.DevBoxApp
import com.arbdev.devbox.ui.theme.DevBoxTheme
import com.arbdev.devbox.updater.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        KeepAliveService.startService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF141414.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF1C1E1B.toInt())
        )
        // Kunci eksplisit SOFT_INPUT_ADJUST_RESIZE untuk mencegah sistem Android melakukan adjustPan (window shift)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Inisialisasi path rootfs persisten dari SharedPreferences dan tema terminal
        ChrootManager.init(applicationContext)
        com.arbdev.devbox.terminal.TerminalSessionManager.initTheme(applicationContext)

        // Otomatis bersihkan sisa APK lama, lock proses agy usang, dan set OOM score -1000
        lifecycleScope.launch(Dispatchers.IO) {
            UpdateManager.cleanupOldApks(applicationContext)
            ChrootManager.cleanStaleAgyLocks()
            ChrootManager.cleanDeadTmpSessions()
            if (!com.arbdev.devbox.data.RootExecutor.hasAppliedSystemTweaks) {
                com.arbdev.devbox.data.RootExecutor.hasAppliedSystemTweaks = true
                try {
                    val myPid = android.os.Process.myPid()
                    com.arbdev.devbox.data.RootExecutor.executeSync(
                        "echo -1000 > /proc/$myPid/oom_score_adj 2>/dev/null; sysctl -w vm.swappiness=60 2>/dev/null",
                        silent = true
                    )
                } catch (_: Exception) {
                    com.arbdev.devbox.data.RootExecutor.hasAppliedSystemTweaks = false
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                KeepAliveService.startService(this)
            }
        } else {
            KeepAliveService.startService(this)
        }

        // Izin Penyimpanan Eksternal (/sdcard)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {}
                }
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val needed = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), 101)
            }
        }

        setContent {
            DevBoxTheme {
                DevBoxApp()
            }
        }
    }
}
