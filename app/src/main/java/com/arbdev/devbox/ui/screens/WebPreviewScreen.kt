package com.arbdev.devbox.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.ui.theme.DevPrimary
import java.util.UUID

data class WebTabItem(
    val id: String = UUID.randomUUID().toString(),
    var url: String,
    var title: String = "Lokal",
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewScreen() {
    val focusManager = LocalFocusManager.current

    val tabs = remember {
        mutableStateListOf(
            WebTabItem(url = "http://127.0.0.1:3000", title = ":3000"),
            WebTabItem(url = "http://127.0.0.1:8080", title = ":8080")
        )
    }
    var activeTabIndex by remember { mutableIntStateOf(0) }

    val currentTab = tabs.getOrNull(activeTabIndex) ?: tabs.firstOrNull() ?: WebTabItem(url = "http://127.0.0.1:3000")

    var urlInput by remember { mutableStateOf(currentTab.url) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }

    // Intercept back button for in-webview history navigation
    BackHandler(enabled = canBack) {
        webViewRef?.goBack()
    }

    fun navigateTo(url: String) {
        val target = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
        urlInput = target
        currentTab.url = target
        webViewRef?.loadUrl(target)
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Multi-Tab Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF080808))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == activeTabIndex
                Row(
                    modifier = Modifier
                        .background(
                            color = if (isActive) Color(0xFF161616) else Color(0xFF0C0C0C),
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) DevPrimary.copy(alpha = 0.6f) else Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .clickable {
                            if (activeTabIndex != index) {
                                activeTabIndex = index
                                urlInput = tab.url
                                webViewRef?.loadUrl(tab.url)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) DevPrimary else Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tabs.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Tab",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    tabs.removeAt(index)
                                    if (activeTabIndex >= tabs.size) {
                                        activeTabIndex = tabs.size - 1
                                    }
                                    val newActive = tabs[activeTabIndex]
                                    urlInput = newActive.url
                                    webViewRef?.loadUrl(newActive.url)
                                }
                        )
                    }
                }
            }

            // Add Tab Button (+)
            IconButton(
                onClick = {
                    val newTab = WebTabItem(url = "http://127.0.0.1:5173", title = ":5173")
                    tabs.add(newTab)
                    activeTabIndex = tabs.size - 1
                    urlInput = newTab.url
                    webViewRef?.loadUrl(newTab.url)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tab Baru",
                    tint = DevPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Navigation Bar with History (Back, Forward, Reload) and URL input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // History Back Button
            IconButton(
                onClick = { webViewRef?.goBack() },
                enabled = canBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = if (canBack) Color.White else Color.DarkGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            // History Forward Button
            IconButton(
                onClick = { webViewRef?.goForward() },
                enabled = canForward,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Maju",
                    tint = if (canForward) Color.White else Color.DarkGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Reload Button
            IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Segarkan",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // URL Address Input Field
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = DevPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DevPrimary,
                    unfocusedBorderColor = Color(0xFF252525),
                    focusedContainerColor = Color(0xFF050505),
                    unfocusedContainerColor = Color(0xFF050505)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateTo(urlInput) })
            )
        }

        // Port Presets Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PortChip(label = ":3000") { navigateTo("http://127.0.0.1:3000") }
            PortChip(label = ":5173 (Vite)") { navigateTo("http://127.0.0.1:5173") }
            PortChip(label = ":8000 (FastAPI)") { navigateTo("http://127.0.0.1:8000") }
            PortChip(label = ":8080 (VS Code)") { navigateTo("http://127.0.0.1:8080") }
        }

        // Loading Progress Bar
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = DevPrimary
            )
        }

        // Embedded Multi-Tab WebView
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrEmpty()) {
                                    val shortTitle = if (title.length > 14) title.take(12) + ".." else title
                                    currentTab.title = shortTitle
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                canBack = view?.canGoBack() == true
                                canForward = view?.canGoForward() == true
                                url?.let {
                                    urlInput = it
                                    currentTab.url = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canBack = view?.canGoBack() == true
                                canForward = view?.canGoForward() == true
                                url?.let {
                                    urlInput = it
                                    currentTab.url = it
                                }
                            }
                        }

                        loadUrl(currentTab.url)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PortChip(
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
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFF141414))
    )
}
