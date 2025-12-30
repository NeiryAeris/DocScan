package com.example.docscan.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.docscan.R

sealed class BottomNavItem(val route: String, val label: String, @DrawableRes val iconResId: Int) {
    object Home : BottomNavItem("home", "Trang chủ", R.drawable.home)
    object Files : BottomNavItem("files", "Tài liệu", R.drawable.file)
    object Tools : BottomNavItem("tools", "Công cụ", R.drawable.tool)
    object Profile : BottomNavItem("profile", "Cá nhân", R.drawable.profile)
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
                NavigationBar(containerColor = Color(0xFFCCFCFA)) {
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    painter = painterResource(id = screen.iconResId), 
                                    contentDescription = screen.label
                                )
                             },
                            label = { Text(screen.label) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF2F7E77),
                                unselectedIconColor = Color(0xFF9E9E9E),
                                selectedTextColor = Color(0xFF2F7E77),
                                unselectedTextColor = Color(0xFF9E9E9E),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        },
        content = content
    )
}
