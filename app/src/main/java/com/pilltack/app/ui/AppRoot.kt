package com.pilltack.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.pilltack.app.data.Repo

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(repo: Repo) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: ""
    val tabs = listOf(
        Tab("home", "Today", Icons.Filled.Today),
        Tab("add?med=-1", "Add Reminder", Icons.Filled.AddCircleOutline),
        Tab("calendar", "Calendar", Icons.Filled.CalendarMonth),
        Tab("history?med=-1", "History", Icons.Filled.History)
    )
    val showBar = route == "home" || route == "calendar" || route.startsWith("history") || route == "medicines"
    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = PT.Blue) {
                tabs.forEach { t ->
                    val selected = route.startsWith(t.route.substringBefore('?')) && !t.route.startsWith("add")
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (t.route.startsWith("add")) nav.navigate(t.route)
                            else nav.navigate(t.route) { popUpTo("home"); launchSingleTop = true }
                        },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, fontSize = 12.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, selectedTextColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.65f), unselectedTextColor = Color.White.copy(alpha = 0.65f),
                            indicatorColor = Color.White.copy(alpha = 0.18f))
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, "splash", Modifier.padding(pad)) {
            composable("splash") { SplashScreen(nav, repo) }
            composable("onboarding") { OnboardingScreen(nav, repo) }
            composable("home") { HomeScreen(nav, repo) }
            composable("add?med={med}", listOf(navArgument("med") { type = NavType.LongType; defaultValue = -1L })) {
                AddScreen(nav, repo, it.arguments?.getLong("med") ?: -1L)
            }
            composable("calendar") { CalendarScreen(repo) }
            composable("history?med={med}", listOf(navArgument("med") { type = NavType.LongType; defaultValue = -1L })) {
                HistoryScreen(nav, repo, it.arguments?.getLong("med") ?: -1L)
            }
            composable("medicines") { MedicinesScreen(nav, repo) }
            composable("refill") { RefillScreen(nav, repo) }
            composable("menu") { MenuScreen(nav) }
            composable("settings") { SettingsScreen(nav, repo) }
            composable("success") { SuccessScreen(nav) }
        }
    }
}
