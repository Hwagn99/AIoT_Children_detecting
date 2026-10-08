package com.childguard.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.childguard.app.ChildGuardApp
import com.childguard.app.ui.screens.ChatScreen
import com.childguard.app.ui.screens.ControlScreen
import com.childguard.app.ui.screens.DashboardScreen
import com.childguard.app.ui.screens.HistoryScreen
import com.childguard.app.ui.screens.LoginScreen
import com.childguard.app.ui.screens.ReportsScreen
import com.childguard.app.ui.screens.SettingsScreen
import com.childguard.app.ui.viewmodel.*

private object Route {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val CONTROL = "control"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.DASHBOARD, "Giám sát", Icons.Filled.Dashboard),
    Tab(Route.CONTROL, "Điều khiển", Icons.Filled.Tune),
    Tab(Route.HISTORY, "Lịch sử", Icons.Filled.History),
    Tab(Route.REPORTS, "Báo cáo", Icons.Filled.Description),
    Tab(Route.CHAT, "Hỏi AI", Icons.Filled.Chat),
    Tab(Route.SETTINGS, "Cài đặt", Icons.Filled.Settings),
)

@Composable
fun AppNav() {
    val app = LocalContext.current.applicationContext as ChildGuardApp
    val factory: ViewModelProvider.Factory = appViewModelFactory(app.repository)
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (current != null && current != Route.LOGIN) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Route.LOGIN, modifier = Modifier.padding(inner)) {
            composable(Route.LOGIN) {
                val vm: LoginViewModel = viewModel(factory = factory)
                LoginScreen(vm) {
                    nav.navigate(Route.DASHBOARD) { popUpTo(Route.LOGIN) { inclusive = true } }
                }
            }
            composable(Route.DASHBOARD) { DashboardScreen(viewModel(factory = factory)) }
            composable(Route.CONTROL) { ControlScreen(viewModel(factory = factory)) }
            composable(Route.HISTORY) { HistoryScreen(viewModel(factory = factory)) }
            composable(Route.REPORTS) { ReportsScreen(viewModel(factory = factory)) }
            composable(Route.CHAT) { ChatScreen(viewModel(factory = factory)) }
            composable(Route.SETTINGS) {
                SettingsScreen(viewModel(factory = factory)) {
                    nav.navigate(Route.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }
}
