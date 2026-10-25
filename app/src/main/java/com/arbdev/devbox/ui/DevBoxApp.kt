package com.arbdev.devbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arbdev.devbox.terminal.TerminalSessionManager
import com.arbdev.devbox.ui.screens.DashboardScreen
import com.arbdev.devbox.ui.screens.LogScreen
import com.arbdev.devbox.ui.screens.TerminalScreen
import com.arbdev.devbox.ui.screens.WebIdeScreen
import com.arbdev.devbox.ui.screens.WebPreviewScreen
import com.arbdev.devbox.ui.theme.DevBackground
import com.arbdev.devbox.ui.theme.DevBorder
import com.arbdev.devbox.ui.theme.DevError
import com.arbdev.devbox.ui.theme.DevOnPrimary
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevPrimaryContainer
import com.arbdev.devbox.ui.theme.DevSurface
import com.arbdev.devbox.util.AppLogger
import com.arbdev.devbox.util.LogLevel

enum class DevBoxTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dns),
    TERMINAL("Terminal", Icons.Default.Terminal),
    LOGS("Logs", Icons.Default.BugReport),
    IDE("Web IDE", Icons.Default.Code),
    PREVIEW("Preview", Icons.Default.Language)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevBoxApp() {
    var selectedTab by remember { mutableStateOf(DevBoxTab.DASHBOARD) }
    var previousTabIndex by remember { mutableIntStateOf(0) }
    var pendingTerminalCommand by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedTab != DevBoxTab.DASHBOARD) {
        selectedTab = DevBoxTab.DASHBOARD
    }

    val isImeVisible = WindowInsets.isImeVisible
    val sessionCount = TerminalSessionManager.sessions.size
    val errorLogCount = AppLogger.logs.count { it.level == LogLevel.ERROR }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DevBackground,
        bottomBar = {
            if (!isImeVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = DevBorder)
                    NavigationBar(
                        containerColor = DevSurface,
                        tonalElevation = 0.dp
                    ) {
                        DevBoxTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    previousTabIndex = selectedTab.ordinal
                                    selectedTab = tab
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab == DevBoxTab.TERMINAL && sessionCount > 1) {
                                                Badge(
                                                    containerColor = DevPrimary,
                                                    contentColor = DevOnPrimary
                                                ) {
                                                    Text(
                                                        text = sessionCount.toString(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else if (tab == DevBoxTab.LOGS && errorLogCount > 0) {
                                                Badge(
                                                    containerColor = DevError,
                                                    contentColor = DevOnPrimary
                                                ) {
                                                    Text(
                                                        text = errorLogCount.coerceAtMost(99).toString(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DevPrimary,
                                    selectedTextColor = DevPrimary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = DevPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(140)) + scaleIn(
                    initialScale = 0.985f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )) togetherWith fadeOut(animationSpec = tween(100))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = if (isImeVisible) 0.dp else innerPadding.calculateBottomPadding()
                ),
            label = "tabTransition"
        ) { currentTab ->
            when (currentTab) {
                DevBoxTab.DASHBOARD -> DashboardScreen(
                    onNavigateToTerminal = { cmd ->
                        pendingTerminalCommand = cmd
                        previousTabIndex = selectedTab.ordinal
                        selectedTab = DevBoxTab.TERMINAL
                    },
                    onNavigateToIde = {
                        previousTabIndex = selectedTab.ordinal
                        selectedTab = DevBoxTab.IDE
                    },
                    onNavigateToLogs = {
                        previousTabIndex = selectedTab.ordinal
                        selectedTab = DevBoxTab.LOGS
                    }
                )
                DevBoxTab.TERMINAL -> {
                    val cmd = pendingTerminalCommand
                    pendingTerminalCommand = null
                    TerminalScreen(initialCommand = cmd)
                }
                DevBoxTab.LOGS -> LogScreen()
                DevBoxTab.IDE -> WebIdeScreen()
                DevBoxTab.PREVIEW -> WebPreviewScreen()
            }
        }
    }
}
