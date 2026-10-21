package com.arbdev.devbox.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

class WebTabItem(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String,
    initialTitle: String = "Lokal"
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var webView: WebView? = null
}

@Serializable
data class SavedWebTab(
    val url: String,
    val title: String = "Lokal"
)

object WebPreviewStateManager {
    val tabs = mutableStateListOf<WebTabItem>()
    var activeTabIndex = mutableIntStateOf(0)
    var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences("devbox_web_preview", Context.MODE_PRIVATE)
        val savedTabsJson = prefs.getString("saved_tabs", null)
        val savedIndex = prefs.getInt("active_tab_index", 0)
        val lastUrl = prefs.getString("last_url", "http://127.0.0.1:3000") ?: "http://127.0.0.1:3000"

        if (!savedTabsJson.isNullOrEmpty()) {
            try {
                val list = Json.decodeFromString<List<SavedWebTab>>(savedTabsJson)
                if (list.isNotEmpty()) {
                    tabs.clear()
                    tabs.addAll(list.map { WebTabItem(initialUrl = it.url, initialTitle = it.title) })
                    activeTabIndex.intValue = savedIndex.coerceIn(0, tabs.size - 1)
                    isInitialized = true
                    return
                }
            } catch (_: Exception) {
            }
        }

        tabs.clear()
        tabs.add(WebTabItem(initialUrl = lastUrl, initialTitle = getDisplayTitle(lastUrl)))
        tabs.add(WebTabItem(initialUrl = "http://127.0.0.1:8080", initialTitle = ":8080"))
        activeTabIndex.intValue = 0
        isInitialized = true
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("devbox_web_preview", Context.MODE_PRIVATE)
        val currentTab = tabs.getOrNull(activeTabIndex.intValue)
        val lastUrl = currentTab?.url ?: "http://127.0.0.1:3000"
        val list = tabs.map { SavedWebTab(url = it.url, title = it.title) }
        val json = try {
            Json.encodeToString(list)
        } catch (_: Exception) {
            ""
        }
        prefs.edit()
            .putString("last_url", lastUrl)
            .putString("saved_tabs", json)
            .putInt("active_tab_index", activeTabIndex.intValue)
            .apply()
    }

    fun getDisplayTitle(url: String): String {
        return try {
            val uri = URI(url)
            val port = uri.port
            if (port > 0) ":$port" else uri.host ?: "Web"
        } catch (_: Exception) {
            "Web"
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        WebPreviewStateManager.init(context)
    }

    val tabs = WebPreviewStateManager.tabs
    var activeTabIndex by WebPreviewStateManager.activeTabIndex

    if (tabs.isEmpty()) {
        WebPreviewStateManager.init(context)
    }

    val currentTab = tabs.getOrNull(activeTabIndex) ?: tabs.firstOrNull() ?: WebTabItem(initialUrl = "http://127.0.0.1:3000")

    var urlInput by remember(currentTab.id) { mutableStateOf(currentTab.url) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }

    LaunchedEffect(currentTab.id) {
        urlInput = currentTab.url
        canBack = currentTab.webView?.canGoBack() == true
        canForward = currentTab.webView?.canGoForward() == true
    }

    // Intercept back button for in-webview history navigation
    BackHandler(enabled = canBack) {
        val activeView = currentTab.webView ?: webViewRef
        if (activeView?.canGoBack() == true) {
            activeView.goBack()
        }
    }

    fun navigateTo(url: String) {
        val target = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
        urlInput = target
        currentTab.url = target
        currentTab.title = WebPreviewStateManager.getDisplayTitle(target)
        WebPreviewStateManager.save(context)
        val targetView = currentTab.webView ?: webViewRef
        targetView?.loadUrl(target)
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
                                canBack = tab.webView?.canGoBack() == true
                                canForward = tab.webView?.canGoForward() == true
                                WebPreviewStateManager.save(context)
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
                                    val removed = tabs.removeAt(index)
                                    try {
                                        removed.webView?.destroy()
                                    } catch (_: Exception) {}
                                    if (activeTabIndex >= tabs.size) {
                                        activeTabIndex = tabs.size - 1
                                    }
                                    val newActive = tabs[activeTabIndex]
                                    urlInput = newActive.url
                                    canBack = newActive.webView?.canGoBack() == true
                                    canForward = newActive.webView?.canGoForward() == true
                                    WebPreviewStateManager.save(context)
                                }
                        )
                    }
                }
            }

            // Add Tab Button (+)
            IconButton(
                onClick = {
                    val defaultUrl = "http://127.0.0.1:3000"
                    val newTab = WebTabItem(
                        initialUrl = defaultUrl,
                        initialTitle = WebPreviewStateManager.getDisplayTitle(defaultUrl)
                    )
                    tabs.add(newTab)
                    activeTabIndex = tabs.size - 1
                    urlInput = newTab.url
                    canBack = false
                    canForward = false
                    WebPreviewStateManager.save(context)
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
                onClick = { (currentTab.webView ?: webViewRef)?.goBack() },
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
                onClick = { (currentTab.webView ?: webViewRef)?.goForward() },
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
                onClick = { (currentTab.webView ?: webViewRef)?.reload() },
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

        // Embedded Multi-Tab WebView with Persistent In-Memory DOM State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            key(currentTab.id) {
                AndroidView(
                    factory = { ctx ->
                        val existingView = currentTab.webView
                        if (existingView != null) {
                            (existingView.parent as? ViewGroup)?.removeView(existingView)
                            webViewRef = existingView
                            canBack = existingView.canGoBack()
                            canForward = existingView.canGoForward()
                            urlInput = existingView.url ?: currentTab.url
                            existingView
                        } else {
                            WebView(ctx).apply {
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
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                        isLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrEmpty()) {
                                            val shortTitle = if (title.length > 14) title.take(12) + ".." else title
                                            currentTab.title = shortTitle
                                            WebPreviewStateManager.save(context)
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        val active = tabs.getOrNull(activeTabIndex)
                                        if (active?.id == currentTab.id) {
                                            isLoading = true
                                            canBack = view?.canGoBack() == true
                                            canForward = view?.canGoForward() == true
                                            url?.let { urlInput = it }
                                        }
                                        url?.let {
                                            currentTab.url = it
                                            WebPreviewStateManager.save(context)
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        val active = tabs.getOrNull(activeTabIndex)
                                        if (active?.id == currentTab.id) {
                                            isLoading = false
                                            canBack = view?.canGoBack() == true
                                            canForward = view?.canGoForward() == true
                                            url?.let { urlInput = it }
                                        }
                                        url?.let {
                                            currentTab.url = it
                                            WebPreviewStateManager.save(context)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        val targetUrl = request?.url?.toString() ?: return false
                                        view?.loadUrl(targetUrl)
                                        return true
                                    }
                                }

                                loadUrl(currentTab.url)
                                currentTab.webView = this
                                webViewRef = this
                            }
                        }
                    },
                    update = { view ->
                        webViewRef = view
                        canBack = view.canGoBack()
                        canForward = view.canGoForward()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
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
