package com.arbdev.devbox.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.data.ChrootManager
import com.arbdev.devbox.ui.theme.DevBackground
import com.arbdev.devbox.ui.theme.DevBorder
import com.arbdev.devbox.ui.theme.DevOnPrimary
import com.arbdev.devbox.ui.theme.DevOnSurface
import com.arbdev.devbox.ui.theme.DevOnSurfaceVariant
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevPrimaryContainer
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebIdeScreen() {
    val scope = rememberCoroutineScope()
    var targetUrl by remember { mutableStateOf("http://127.0.0.1:8080") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isStartingServer by remember { mutableStateOf(false) }
    var probeAttempt by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevBackground)
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevSurface)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = DevPrimaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = DevPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Code-Server IDE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DevOnSurface
                        )
                        Text(
                            text = "127.0.0.1:8080",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DevPrimary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = webViewRef?.canGoBack() == true,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (webViewRef?.canGoBack() == true) DevOnSurface else DevOnSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = webViewRef?.canGoForward() == true,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (webViewRef?.canGoForward() == true) DevOnSurface else DevOnSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = DevOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { isFullscreen = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = DevPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = DevBorder)
        }

        if (isLoading && !hasError) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = DevPrimary,
                trackColor = DevSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(0xFF0D1117.toInt())
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
                                androidx.webkit.WebSettingsCompat.setForceDark(this, androidx.webkit.WebSettingsCompat.FORCE_DARK_ON)
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                @Suppress("DEPRECATION")
                                forceDark = WebSettings.FORCE_DARK_ON
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress == 100) {
                                    isLoading = false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                hasError = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    hasError = true
                                    isLoading = false
                                }
                            }
                        }

                        loadUrl(targetUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isFullscreen) {
                Surface(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    color = DevSurface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DevPrimary.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Fullscreen",
                            tint = DevPrimary
                        )
                    }
                }
            }

            if (hasError) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DevBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DevSurface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, DevBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = DevPrimaryContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = DevPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Code-Server Belum Aktif",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DevOnSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tidak dapat terhubung ke 127.0.0.1:8080. Pastikan layanan code-server diaktifkan di dalam container DevBox.",
                                fontSize = 12.sp,
                                color = DevOnSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isStartingServer = true
                                        probeAttempt = 0
                                        ChrootManager.toggleCodeServer(true)
                                        for (attempt in 1..40) {
                                            probeAttempt = attempt
                                            kotlinx.coroutines.delay(500)
                                            val listening = try {
                                                java.io.File("/proc/net/tcp").takeIf { it.exists() }
                                                    ?.readLines()
                                                    ?.any { it.contains(":1F90 ") && it.contains(" 0A ") } == true
                                            } catch (_: Exception) { false }
                                            if (listening) break
                                        }
                                        isStartingServer = false
                                        hasError = false
                                        webViewRef?.reload()
                                    }
                                },
                                enabled = !isStartingServer,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DevOnPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isStartingServer) "Menyiapkan Code-Server ($probeAttempt/40)..." else "Jalankan Code-Server",
                                    color = DevOnPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
