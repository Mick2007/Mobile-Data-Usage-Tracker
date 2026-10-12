package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.dataguard.ui.screen.AppsScreen
import com.example.dataguard.ui.screen.DashboardScreen
import com.example.dataguard.ui.screen.FirewallScreen
import com.example.dataguard.ui.screen.HotspotScreen
import com.example.dataguard.ui.screen.SettingsScreen
import com.example.dataguard.ui.screen.StatsScreen
import com.example.dataguard.ui.screen.WebsitesScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DataGuardNavigationHost()
            }
        }
    }
}

@Composable
fun DataGuardNavigationHost() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("main_bottom_nav")) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val items = listOf(
                    NavTabItem("dashboard", "Dashboard", Icons.Default.Home, "nav_dashboard"),
                    NavTabItem("websites", "Websites", Icons.Default.Language, "nav_websites"),
                    NavTabItem("hotspot", "Hotspot", Icons.Default.WifiTethering, "nav_hotspot"),
                    NavTabItem("firewall", "Firewall", Icons.Default.Security, "nav_firewall"),
                    NavTabItem("apps", "Apps", Icons.Default.Apps, "nav_apps"),
                    NavTabItem("stats", "Analytics", Icons.Default.BarChart, "nav_stats"),
                    NavTabItem("settings", "Settings", Icons.Default.Settings, "nav_settings")
                )

                items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selected,
                        modifier = Modifier.testTag(item.testTag),
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen()
            }
            composable("websites") {
                WebsitesScreen()
            }
            composable("hotspot") {
                HotspotScreen()
            }
            composable("firewall") {
                FirewallScreen()
            }
            composable("apps") {
                AppsScreen()
            }
            composable("stats") {
                StatsScreen()
            }
            composable("settings") {
                SettingsScreen(onNavigateToPlans = { navController.navigate("plans") })
            }
            composable("plans") {
                com.example.dataguard.ui.screen.PlanAndAlertScreen()
            }
        }
    }
}

private data class NavTabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)
