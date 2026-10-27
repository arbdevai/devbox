package com.arbdev.devbox.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arbdev.devbox.ui.theme.DevBackground
import com.arbdev.devbox.ui.theme.DevBorder
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevOnPrimary
import com.arbdev.devbox.ui.theme.DevOnSurface
import com.arbdev.devbox.ui.theme.DevOnSurfaceVariant
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevPrimaryContainer
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.ui.theme.DevSurfaceVariant
import com.arbdev.devbox.ui.theme.DevWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

const val DEVBOX_HOME_URL = "devbox:home"
const val CHROME_DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

fun isPortListening(port: Int): Boolean {
    val hexPort = String.format("%04X", port).uppercase()
    return try {
        val tcpFiles = listOf(File("/proc/net/tcp"), File("/proc/net/tcp6"))
        tcpFiles.any { file ->
            file.exists() && file.bufferedReader().useLines { lines ->
                lines.any { line -> line.contains(":$hexPort ") && line.contains(" 0A ") }
            }
        }
    } catch (_: Exception) {
        false
    }
}

fun resolveOmniboxTarget(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.equals("home", ignoreCase = true) || trimmed.equals(DEVBOX_HOME_URL, ignoreCase = true)) {
        return DEVBOX_HOME_URL
    }
    if (trimmed.equals("google", ignoreCase = true) || trimmed.equals("google.com", ignoreCase = true)) {
        return "https://www.google.com"
    }

    val portPattern = Regex("^:?([0-9]{2,5})$")
    val portMatch = portPattern.matchEntire(trimmed)
    if (portMatch != null) {
        val port = portMatch.groupValues[1]
        return "http://127.0.0.1:$port"
    }

    if (trimmed.startsWith("localhost", ignoreCase = true) || trimmed.startsWith("127.0.0.1")) {
        return "http://$trimmed"
    }

    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true) ||
        trimmed.startsWith("about:", ignoreCase = true)
    ) {
        return trimmed
    }

    val isDomain = trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("\n")
    if (isDomain) {
        return "https://$trimmed"
    }

    val query = URLEncoder.encode(trimmed, "UTF-8")
    return "https://www.google.com/search?q=$query"
}

class WebTabItem(
    val id: String = UUID.randomUUID().toString(),
    initialUrl: String = DEVBOX_HOME_URL,
    initialTitle: String = "Beranda",
    initialDesktop: Boolean = false
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var isDesktopMode by mutableStateOf(initialDesktop)
    var hasError by mutableStateOf(false)
    var errorDescription by mutableStateOf("")
    var failingUrl by mutableStateOf("")
    var webView: WebView? = null
}

@Serializable
data class SavedWebTab(
    val url: String,
    val title: String = "Beranda",
    val isDesktop: Boolean = false
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

        if (!savedTabsJson.isNullOrEmpty()) {
            try {
                val list = Json.decodeFromString<List<SavedWebTab>>(savedTabsJson)
                if (list.isNotEmpty()) {
                    tabs.clear()
                    tabs.addAll(list.map {
                        WebTabItem(
                            initialUrl = it.url,
                            initialTitle = it.title,
                            initialDesktop = it.isDesktop
                        )
                    })
                    activeTabIndex.intValue = savedIndex.coerceIn(0, tabs.size - 1)
                    isInitialized = true
                    return
                }
            } catch (_: Exception) {
            }
        }

        tabs.clear()
        tabs.add(WebTabItem(initialUrl = DEVBOX_HOME_URL, initialTitle = "Beranda"))
        activeTabIndex.intValue = 0
        isInitialized = true
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("devbox_web_preview", Context.MODE_PRIVATE)
        val currentTab = tabs.getOrNull(activeTabIndex.intValue)
        val lastUrl = currentTab?.url ?: DEVBOX_HOME_URL
        val list = tabs.map {
            SavedWebTab(
                url = it.url,
                title = it.title,
                isDesktop = it.isDesktopMode
            )
        }
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
        if (url == DEVBOX_HOME_URL || url == "about:blank" || url.isEmpty()) return "Beranda"
        return try {
            val uri = URI(url)
            val host = uri.host ?: ""
            val port = uri.port
            if (host == "127.0.0.1" || host == "localhost") {
                if (port > 0) ":$port" else "Localhost"
            } else if (host.isNotEmpty()) {
                host.removePrefix("www.")
            } else {
                "Web"
            }
        } catch (_: Exception) {
            "Web"
        }
    }
}

@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val strokeWidth = sizePx * 0.22f
        val radius = (sizePx - strokeWidth) / 2f
        val centerOffset = Offset(size.width / 2f, size.height / 2f)
        val arcRect = Rect(
            centerOffset.x - radius,
            centerOffset.y - radius,
            centerOffset.x + radius,
            centerOffset.y + radius
        )
        val style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

        // Blue (#4285F4)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = style
        )
        // Green (#34A853)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = style
        )
        // Yellow (#FBBC05)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = style
        )
        // Red (#EA4335)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = style
        )
        // Horizontal crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(centerOffset.x, centerOffset.y),
            end = Offset(centerOffset.x + radius + (strokeWidth / 2f), centerOffset.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Square
        )
    }
}

data class SpeedDialItem(
    val title: String,
    val url: String,
    val icon: ImageVector,
    val iconTint: Color
)

@Composable
fun DevBoxBrowserHomeScreen(
    onNavigate: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var homeSearchQuery by remember { mutableStateOf("") }

    var port3000Active by remember { mutableStateOf(false) }
    var port5173Active by remember { mutableStateOf(false) }
    var port8000Active by remember { mutableStateOf(false) }
    var port8080Active by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            port3000Active = isPortListening(3000)
            port5173Active = isPortListening(5173)
            port8000Active = isPortListening(8000)
            port8080Active = isPortListening(8080)
            delay(2000)
        }
    }

    val speedDials = remember {
        listOf(
            SpeedDialItem("Google", "https://www.google.com", Icons.Default.Language, Color(0xFF4285F4)),
            SpeedDialItem("GitHub", "https://github.com", Icons.Default.Code, Color.White),
            SpeedDialItem("Stack Overflow", "https://stackoverflow.com", Icons.Default.Layers, Color(0xFFF48024)),
            SpeedDialItem("MDN Web", "https://developer.mozilla.org", Icons.Default.MenuBook, Color(0xFF83BFFF)),
            SpeedDialItem("DevDocs", "https://devdocs.io", Icons.Default.Public, DevPrimary),
            SpeedDialItem("Vite", "https://vite.dev", Icons.Default.Bolt, Color(0xFFBD34FE)),
            SpeedDialItem("FastAPI", "https://fastapi.tiangolo.com", Icons.Default.Bolt, Color(0xFF009688)),
            SpeedDialItem("YouTube", "https://www.youtube.com", Icons.Default.PlayCircle, Color(0xFFFF0000))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Google / DevBox Hero Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GoogleLogo(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Google",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DevOnSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "DevBox Browser Engine",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DevPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Modern Google Search Box
        OutlinedTextField(
            value = homeSearchQuery,
            onValueChange = { homeSearchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            placeholder = {
                Text(
                    text = "Telusuri Google atau ketik URL / port...",
                    fontSize = 13.sp,
                    color = DevOnSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = DevPrimary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (homeSearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onNavigate(homeSearchQuery)
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DevPrimary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Search",
                                    tint = DevOnPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(27.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DevPrimary,
                unfocusedBorderColor = DevBorder,
                focusedContainerColor = DevSurfaceVariant,
                unfocusedContainerColor = DevSurfaceVariant,
                focusedTextColor = DevOnSurface,
                unfocusedTextColor = DevOnSurface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onNavigate(homeSearchQuery)
                }
            )
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Speed Dial Shortcuts
        Text(
            text = "PINTASAN PENGEMBANG",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = DevOnSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(speedDials) { item ->
                Surface(
                    onClick = { onNavigate(item.url) },
                    shape = RoundedCornerShape(14.dp),
                    color = DevSurface,
                    border = BorderStroke(1.dp, DevBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DevSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = DevOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Localhost Dev Servers Monitor Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SERVER PENGEMBANGAN LOKAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DevOnSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "Live Scan",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = DevPrimary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PortCard(
                port = ":3000",
                desc = "Node / Next.js",
                isOnline = port3000Active,
                modifier = Modifier.weight(1f)
            ) { onNavigate("http://127.0.0.1:3000") }

            PortCard(
                port = ":5173",
                desc = "Vite / React",
                isOnline = port5173Active,
                modifier = Modifier.weight(1f)
            ) { onNavigate("http://127.0.0.1:5173") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PortCard(
                port = ":8000",
                desc = "FastAPI / Python",
                isOnline = port8000Active,
                modifier = Modifier.weight(1f)
            ) { onNavigate("http://127.0.0.1:8000") }

            PortCard(
                port = ":8080",
                desc = "Code-Server IDE",
                isOnline = port8080Active,
                modifier = Modifier.weight(1f)
            ) { onNavigate("http://127.0.0.1:8080") }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Terminal Info Tip Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DevSurfaceVariant),
            border = BorderStroke(1.dp, DevBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = DevPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = DevPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Jalankan Server di Terminal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DevOnSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ketik perintah 'npm run dev' atau 'python3 -m http.server 3000' pada tab Terminal untuk mengaktifkan web preview.",
                        fontSize = 11.sp,
                        color = DevOnSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PortCard(
    port: String,
    desc: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = DevSurface,
        border = BorderStroke(1.dp, if (isOnline) DevPrimary.copy(alpha = 0.5f) else DevBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = port,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = DevOnSurface
                )
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = DevOnSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isOnline) DevPrimaryContainer else DevSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = if (isOnline) "● AKTIF" else "○ OFFLINE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) DevPrimary else DevOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BrowserErrorScreen(
    failingUrl: String,
    errorDescription: String,
    onReload: () -> Unit,
    onGoHome: () -> Unit,
    onOpenGoogle: () -> Unit
) {
    val isLocalhost = failingUrl.contains("127.0.0.1") || failingUrl.contains("localhost")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = DevSurfaceVariant,
            border = BorderStroke(1.dp, DevBorder),
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isLocalhost) Icons.Default.Dns else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isLocalhost) DevPrimary else DevWarning,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isLocalhost) "Server Lokal Belum Aktif" else "Situs Tidak Dapat Dijangkau",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = DevOnSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = failingUrl,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = DevPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = DevSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = errorDescription.ifEmpty { "ERR_CONNECTION_REFUSED" },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = DevOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DevSurface),
            border = BorderStroke(1.dp, DevBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isLocalhost) "Panduan Perintah Terminal:" else "Saran Perbaikan:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DevOnSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isLocalhost) {
                        "Server web development belum merespons pada port ini. Buka tab Terminal DevBox lalu jalankan server web proyek Anda (contoh: 'npm run dev', 'vite', atau 'python3 -m http.server 3000')."
                    } else {
                        "Periksa koneksi internet perangkat Anda atau pastikan alamat situs web yang dimasukkan sudah benar."
                    },
                    fontSize = 11.sp,
                    color = DevOnSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReload,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DevPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = DevOnPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Coba Lagi", color = DevOnPrimary, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onGoHome,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, DevBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = DevOnSurface, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Beranda", color = DevOnSurface, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenGoogle,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, DevBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Cari di Google", color = DevOnSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherSheet(
    tabs: List<WebTabItem>,
    activeIndex: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DevSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tab Terbuka (${tabs.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DevOnSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tabs.size > 1) {
                        IconButton(onClick = onCloseAllTabs) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Tutup Semua",
                                tint = DevError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNewTab) {
                        Surface(
                            shape = CircleShape,
                            color = DevPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tab Baru",
                                    tint = DevPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = DevBorder, modifier = Modifier.padding(vertical = 10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabs) { index, tab ->
                    val isActive = index == activeIndex
                    Surface(
                        onClick = { onSelectTab(index) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) DevPrimaryContainer else DevSurfaceVariant,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isActive) DevPrimary else DevBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tab.url == DEVBOX_HOME_URL) Icons.Default.Home else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isActive) DevPrimary else DevOnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = tab.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isActive) DevPrimary else DevOnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (tab.url == DEVBOX_HOME_URL) "Beranda Google / DevBox" else tab.url,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = DevOnSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (tabs.size > 1) {
                                IconButton(
                                    onClick = { onCloseTab(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup Tab",
                                        tint = if (isActive) DevPrimary else DevOnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        WebPreviewStateManager.init(context)
    }

    val tabs = WebPreviewStateManager.tabs
    var activeTabIndex by WebPreviewStateManager.activeTabIndex

    if (tabs.isEmpty()) {
        WebPreviewStateManager.init(context)
    }

    val currentTab = tabs.getOrNull(activeTabIndex) ?: tabs.firstOrNull() ?: WebTabItem()

    var urlInput by remember(currentTab.id) {
        mutableStateOf(if (currentTab.url == DEVBOX_HOME_URL) "" else currentTab.url)
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }

    var showBrowserMenu by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }

    // Find In Page State
    var isFindInPageActive by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchOrdinal by remember { mutableIntStateOf(0) }
    var findMatchCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentTab.id) {
        urlInput = if (currentTab.url == DEVBOX_HOME_URL) "" else currentTab.url
        canBack = currentTab.webView?.canGoBack() == true
        canForward = currentTab.webView?.canGoForward() == true
    }

    BackHandler(enabled = isFindInPageActive || canBack || currentTab.url != DEVBOX_HOME_URL) {
        if (isFindInPageActive) {
            isFindInPageActive = false
            currentTab.webView?.clearMatches()
            findMatchCount = 0
            findMatchOrdinal = 0
            return@BackHandler
        }
        val activeView = currentTab.webView ?: webViewRef
        if (activeView?.canGoBack() == true) {
            activeView.goBack()
        } else if (currentTab.url != DEVBOX_HOME_URL) {
            currentTab.url = DEVBOX_HOME_URL
            currentTab.title = "Beranda"
            currentTab.hasError = false
            urlInput = ""
            WebPreviewStateManager.save(context)
        }
    }

    fun navigateTo(rawInput: String) {
        val target = resolveOmniboxTarget(rawInput)
        currentTab.hasError = false
        currentTab.url = target
        currentTab.title = WebPreviewStateManager.getDisplayTitle(target)
        urlInput = if (target == DEVBOX_HOME_URL) "" else target
        WebPreviewStateManager.save(context)

        if (target != DEVBOX_HOME_URL) {
            val targetView = currentTab.webView ?: webViewRef
            targetView?.loadUrl(target)
        }
        focusManager.clearFocus()
    }

    fun closeTab(index: Int) {
        if (index in tabs.indices) {
            val removed = tabs.removeAt(index)
            try {
                removed.webView?.destroy()
            } catch (_: Exception) {
            }
            if (activeTabIndex >= tabs.size) {
                activeTabIndex = (tabs.size - 1).coerceAtLeast(0)
            }
            val newActive = tabs.getOrNull(activeTabIndex)
            if (newActive != null) {
                urlInput = if (newActive.url == DEVBOX_HOME_URL) "" else newActive.url
                canBack = newActive.webView?.canGoBack() == true
                canForward = newActive.webView?.canGoForward() == true
            }
            WebPreviewStateManager.save(context)
        }
    }

    fun createNewTab(initialTarget: String = DEVBOX_HOME_URL) {
        val newTab = WebTabItem(
            initialUrl = initialTarget,
            initialTitle = WebPreviewStateManager.getDisplayTitle(initialTarget)
        )
        tabs.add(newTab)
        activeTabIndex = tabs.size - 1
        urlInput = if (initialTarget == DEVBOX_HOME_URL) "" else initialTarget
        canBack = false
        canForward = false
        WebPreviewStateManager.save(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevBackground)
    ) {
        // Modern Chrome / Brave Omnibox Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevSurface)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { (currentTab.webView ?: webViewRef)?.goBack() },
                enabled = canBack,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = if (canBack) DevOnSurface else DevOnSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = { (currentTab.webView ?: webViewRef)?.goForward() },
                enabled = canForward,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Maju",
                    tint = if (canForward) DevOnSurface else DevOnSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = {
                    val activeView = currentTab.webView ?: webViewRef
                    if (isLoading) {
                        activeView?.stopLoading()
                        isLoading = false
                    } else {
                        currentTab.hasError = false
                        if (currentTab.url == DEVBOX_HOME_URL) {
                            // Refresh start page
                        } else {
                            activeView?.reload()
                        }
                    }
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Hentikan" else "Muat Ulang",
                    tint = DevOnSurface,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = { navigateTo(DEVBOX_HOME_URL) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Beranda",
                    tint = if (currentTab.url == DEVBOX_HOME_URL) DevPrimary else DevOnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Omnibox URL / Search Bar
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                placeholder = {
                    Text(
                        text = "Telusuri atau ketik URL/port",
                        fontSize = 12.sp,
                        color = DevOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DevOnSurface
                ),
                leadingIcon = {
                    val iconVector = when {
                        currentTab.url == DEVBOX_HOME_URL -> Icons.Default.Search
                        currentTab.url.startsWith("https://") -> Icons.Default.Lock
                        currentTab.url.contains("127.0.0.1") || currentTab.url.contains("localhost") -> Icons.Default.Dns
                        else -> Icons.Default.Warning
                    }
                    val iconTint = when {
                        currentTab.url == DEVBOX_HOME_URL -> DevPrimary
                        currentTab.url.startsWith("https://") -> DevPrimary
                        currentTab.url.contains("127.0.0.1") || currentTab.url.contains("localhost") -> DevPrimary
                        else -> DevWarning
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus",
                                tint = DevOnSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DevPrimary,
                    unfocusedBorderColor = DevBorder,
                    focusedContainerColor = DevSurfaceVariant,
                    unfocusedContainerColor = DevSurfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateTo(urlInput) })
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Chrome-Style Tab Count Square Badge
            Surface(
                onClick = { showTabSwitcher = true },
                shape = RoundedCornerShape(7.dp),
                color = DevSurfaceVariant,
                border = BorderStroke(1.5.dp, DevBorder),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${tabs.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DevPrimary
                    )
                }
            }

            // Chrome Overflow Menu (3-dots)
            Box {
                IconButton(
                    onClick = { showBrowserMenu = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu Browser",
                        tint = DevOnSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showBrowserMenu,
                    onDismissRequest = { showBrowserMenu = false },
                    modifier = Modifier.background(DevSurface)
                ) {
                    // Desktop Site Toggle
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = DevPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Situs Desktop", fontSize = 13.sp, color = DevOnSurface)
                                }
                                Switch(
                                    checked = currentTab.isDesktopMode,
                                    onCheckedChange = { checked ->
                                        currentTab.isDesktopMode = checked
                                        currentTab.webView?.settings?.let { s ->
                                            s.userAgentString = if (checked) CHROME_DESKTOP_UA else null
                                            s.useWideViewPort = true
                                            s.loadWithOverviewMode = true
                                        }
                                        currentTab.webView?.reload()
                                        WebPreviewStateManager.save(context)
                                        showBrowserMenu = false
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = DevPrimary,
                                        checkedTrackColor = DevPrimaryContainer
                                    )
                                )
                            }
                        },
                        onClick = {
                            val next = !currentTab.isDesktopMode
                            currentTab.isDesktopMode = next
                            currentTab.webView?.settings?.let { s ->
                                s.userAgentString = if (next) CHROME_DESKTOP_UA else null
                                s.useWideViewPort = true
                                s.loadWithOverviewMode = true
                            }
                            currentTab.webView?.reload()
                            WebPreviewStateManager.save(context)
                            showBrowserMenu = false
                        }
                    )

                    HorizontalDivider(thickness = 1.dp, color = DevBorder)

                    DropdownMenuItem(
                        text = { Text("Tab Baru", fontSize = 13.sp, color = DevOnSurface) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            createNewTab()
                            showBrowserMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Cari di Halaman", fontSize = 13.sp, color = DevOnSurface) },
                        leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            isFindInPageActive = true
                            showBrowserMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Salin URL", fontSize = 13.sp, color = DevOnSurface) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", currentTab.url))
                            Toast.makeText(context, "URL disalin ke papan klip", Toast.LENGTH_SHORT).show()
                            showBrowserMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Bagikan", fontSize = 13.sp, color = DevOnSurface) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentTab.url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan URL"))
                            showBrowserMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Hapus Cache & Reload", fontSize = 13.sp, color = DevOnSurface) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            currentTab.webView?.clearCache(true)
                            currentTab.webView?.reload()
                            Toast.makeText(context, "Cache dibersihkan", Toast.LENGTH_SHORT).show()
                            showBrowserMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Buka di Browser Luar", fontSize = 13.sp, color = DevOnSurface) },
                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentTab.url))
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Tidak ada browser eksternal", Toast.LENGTH_SHORT).show()
                            }
                            showBrowserMenu = false
                        }
                    )
                }
            }
        }

        // Animated In-Page Search Bar
        AnimatedVisibility(
            visible = isFindInPageActive,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DevSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = {
                        findQuery = it
                        if (it.isNotEmpty()) {
                            currentTab.webView?.findAllAsync(it)
                        } else {
                            currentTab.webView?.clearMatches()
                            findMatchCount = 0
                            findMatchOrdinal = 0
                        }
                    },
                    placeholder = { Text("Cari di halaman...", fontSize = 12.sp, color = DevOnSurfaceVariant) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = DevOnSurface),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DevPrimary,
                        unfocusedBorderColor = DevBorder,
                        focusedContainerColor = DevSurface,
                        unfocusedContainerColor = DevSurface
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (findMatchCount > 0) "$findMatchOrdinal/$findMatchCount" else "0/0",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DevPrimary
                )

                IconButton(
                    onClick = { currentTab.webView?.findNext(false) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Sebelumnya", tint = DevOnSurface, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { currentTab.webView?.findNext(true) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Berikutnya", tint = DevOnSurface, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = {
                        isFindInPageActive = false
                        currentTab.webView?.clearMatches()
                        findMatchCount = 0
                        findMatchOrdinal = 0
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = DevOnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Port Presets Quick Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DevBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortChip(label = ":3000 (Node)", port = 3000) { navigateTo("http://127.0.0.1:3000") }
            PortChip(label = ":5173 (Vite)", port = 5173) { navigateTo("http://127.0.0.1:5173") }
            PortChip(label = ":8000 (FastAPI)", port = 8000) { navigateTo("http://127.0.0.1:8000") }
            PortChip(label = ":8080 (IDE)", port = 8080) { navigateTo("http://127.0.0.1:8080") }
            PortChip(label = "Google", port = -1) { navigateTo("https://www.google.com") }
        }

        HorizontalDivider(thickness = 1.dp, color = DevBorder)

        if (isLoading && currentTab.url != DEVBOX_HOME_URL) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = DevPrimary,
                trackColor = DevSurfaceVariant
            )
        }

        // Main Web Viewport or Native Start / Error Pages
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DevBackground)
        ) {
            if (currentTab.url == DEVBOX_HOME_URL || currentTab.url == "about:blank") {
                DevBoxBrowserHomeScreen(
                    onNavigate = { navigateTo(it) }
                )
            } else if (currentTab.hasError) {
                BrowserErrorScreen(
                    failingUrl = currentTab.failingUrl,
                    errorDescription = currentTab.errorDescription,
                    onReload = {
                        currentTab.hasError = false
                        currentTab.webView?.reload()
                    },
                    onGoHome = {
                        navigateTo(DEVBOX_HOME_URL)
                    },
                    onOpenGoogle = {
                        navigateTo("https://www.google.com")
                    }
                )
            } else {
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
                                    // Zero Flash of White: Always dark background
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
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false

                                        if (currentTab.isDesktopMode) {
                                            userAgentString = CHROME_DESKTOP_UA
                                        }

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            @Suppress("DEPRECATION")
                                            forceDark = WebSettings.FORCE_DARK_ON
                                        }
                                    }

                                    setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                        findMatchOrdinal = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
                                        findMatchCount = numberOfMatches
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
                                            if (!title.isNullOrEmpty() && currentTab.url != DEVBOX_HOME_URL) {
                                                val shortTitle = if (title.length > 16) title.take(14) + ".." else title
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
                                                url?.let {
                                                    if (it != DEVBOX_HOME_URL) urlInput = it
                                                }
                                            }
                                            url?.let {
                                                if (it != DEVBOX_HOME_URL) {
                                                    currentTab.url = it
                                                    WebPreviewStateManager.save(context)
                                                }
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            val active = tabs.getOrNull(activeTabIndex)
                                            if (active?.id == currentTab.id) {
                                                isLoading = false
                                                canBack = view?.canGoBack() == true
                                                canForward = view?.canGoForward() == true
                                                url?.let {
                                                    if (it != DEVBOX_HOME_URL) urlInput = it
                                                }
                                            }
                                            url?.let {
                                                if (it != DEVBOX_HOME_URL) {
                                                    currentTab.url = it
                                                    WebPreviewStateManager.save(context)
                                                }
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                val failUrl = request.url?.toString() ?: currentTab.url
                                                if (!failUrl.startsWith("devbox:")) {
                                                    currentTab.hasError = true
                                                    currentTab.failingUrl = failUrl
                                                    currentTab.errorDescription = error?.description?.toString() ?: "ERR_CONNECTION_REFUSED"
                                                    isLoading = false
                                                }
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString() ?: return false
                                            if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                                return false
                                            }
                                            // Handle external intents (mailto, tel, etc)
                                            return try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                                context.startActivity(intent)
                                                true
                                            } catch (_: Exception) {
                                                false
                                            }
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

    if (showTabSwitcher) {
        TabSwitcherSheet(
            tabs = tabs,
            activeIndex = activeTabIndex,
            onSelectTab = { index ->
                activeTabIndex = index
                val selected = tabs[index]
                urlInput = if (selected.url == DEVBOX_HOME_URL) "" else selected.url
                canBack = selected.webView?.canGoBack() == true
                canForward = selected.webView?.canGoForward() == true
                WebPreviewStateManager.save(context)
                showTabSwitcher = false
            },
            onCloseTab = { index ->
                closeTab(index)
            },
            onNewTab = {
                createNewTab()
                showTabSwitcher = false
            },
            onCloseAllTabs = {
                tabs.forEach {
                    try {
                        it.webView?.destroy()
                    } catch (_: Exception) {
                    }
                }
                tabs.clear()
                createNewTab()
                showTabSwitcher = false
            },
            onDismiss = { showTabSwitcher = false }
        )
    }
}

@Composable
fun PortChip(
    label: String,
    port: Int,
    onClick: () -> Unit
) {
    var isListening by remember(port) { mutableStateOf(false) }

    LaunchedEffect(port) {
        if (port > 0) {
            isListening = isPortListening(port)
        }
    }

    FilterChip(
        selected = false,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = if (isListening) DevPrimary.copy(alpha = 0.6f) else DevBorder
        ),
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (port > 0) {
                    Text(
                        text = if (isListening) "● " else "○ ",
                        fontSize = 8.sp,
                        color = if (isListening) DevPrimary else DevOnSurfaceVariant
                    )
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isListening) DevPrimary else DevOnSurfaceVariant
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(containerColor = DevSurfaceVariant)
    )
}
