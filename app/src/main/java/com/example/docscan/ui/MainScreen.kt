// In ui/MainScreen.kt
package com.example.docscan.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.screens.FilesScreen
import com.example.docscan.ui.screens.HomeScreen
import com.example.docscan.ui.screens.ProfileScreen
import com.example.docscan.ui.screens.ToolsScreen

// Định nghĩa các mục cho Bottom Navigation
sealed class BottomNavItem(val title: String, val icon: ImageVector, val route: String) {
    object Home : BottomNavItem("Trang chủ", Icons.Default.Home, "home")
    object Files : BottomNavItem("Tệp tin", Icons.Default.Folder, "files")
    object Tools : BottomNavItem("Công cụ", Icons.Default.Build, "tools")
    object Profile : BottomNavItem("Tôi", Icons.Default.Person, "profile")
}


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        // Quay lại sử dụng NavigationBar đơn giản để kiểm tra
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    BottomNavItem.Home,
                    BottomNavItem.Files,
                    BottomNavItem.Tools,
                    BottomNavItem.Profile
                )
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                navController.graph.startDestinationRoute?.let { route ->
                                    popUpTo(route) { saveState = true }
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO */ },
                shape = CircleShape
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera")
            }
        }
    ) { innerPadding ->
        // NavHost chịu trách nhiệm hiển thị các màn hình
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding) // Áp dụng padding để nội dung không bị che
        ) {
            composable(BottomNavItem.Home.route) { HomeScreen() }
            composable(BottomNavItem.Files.route) { FilesScreen() }
            composable(BottomNavItem.Tools.route) { ToolsScreen() }
            composable(BottomNavItem.Profile.route) { ProfileScreen() }
        }
    }
}