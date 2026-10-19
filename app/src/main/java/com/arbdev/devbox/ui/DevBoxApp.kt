package com.arbdev.devbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
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
import com.arbdev.devbox.ui.screens.DashboardScreen
import com.arbdev.devbox.ui.screens.LogScreen
import com.arbdev.devbox.ui.screens.TerminalScreen
import com.arbdev.devbox.ui.screens.WebIdeScreen
import com.arbdev.devbox.ui.screens.WebPreviewScreen
import com.arbdev.devbox.ui.theme.DevBackground
import com.arbdev.devbox.ui.theme.DevPrimary
import com.arbdev.devbox.ui.theme.DevSurface

enum class DevBoxTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Status", Icons.Default.Dns),
    TERMINAL("Terminal", Icons.Default.Terminal),
    LOGS("Log", Icons.Default.BugReport),
    IDE("Web IDE", Icons.Default.Code),
    PREVIEW("Preview", Icons.Default.Language)
}

@Composable
fun DevBoxApp() {
    var selectedTab by remember { mutableStateOf(DevBoxTab.DASHBOARD) }
    var previousTabIndex by remember { mutableIntStateOf(0) }
    var pendingTerminalCommand by remember { mutableStateOf<String?>(null) }

    // Multi-level BackHandler: back to dashboard if on another tab
    BackHandler(enabled = selectedTab != DevBoxTab.DASHBOARD) {
        selectedTab = DevBoxTab.DASHBOARD
    }

    val isImeVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DevBackground,
        bottomBar = {
            if (!isImeVisible) {
                NavigationBar(
                    containerColor = DevSurface
                ) {
                    DevBoxTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                previousTabIndex = selectedTab.ordinal
                                selectedTab = tab
                            },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(text = tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DevPrimary,
                                selectedTextColor = DevPrimary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = DevSurface
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState.ordinal > previousTabIndex
                if (forward) {
                    (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                }
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
