package com.example.docscan.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Trang chủ", Icons.Default.Home)
    object Files : BottomNavItem("files", "Tài liệu", Icons.Default.Folder)
    object Tools : BottomNavItem("tools", "Công cụ", Icons.Default.Build)
    object Profile : BottomNavItem("profile", "Cá nhân", Icons.Default.Person)
}

@Composable
fun MainScreen(navController: NavHostController, content: @Composable (PaddingValues) -> Unit) {
    val items = remember {
        listOf(
            BottomNavItem.Home,
            BottomNavItem.Files,
            BottomNavItem.Tools,
            BottomNavItem.Profile
        )
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val isMainScreen = items.any { it.route == currentRoute }

            if (isMainScreen) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
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
        },
        content = content
    )
}
