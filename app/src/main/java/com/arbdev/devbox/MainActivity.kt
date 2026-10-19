package com.arbdev.devbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
        enableEdgeToEdge()

        // Otomatis bersihkan seluruh sisa file APK lama saat aplikasi dibuka
        lifecycleScope.launch(Dispatchers.IO) {
            UpdateManager.cleanupOldApks(applicationContext)
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

        setContent {
            DevBoxTheme {
                DevBoxApp()
            }
        }
    }
}
