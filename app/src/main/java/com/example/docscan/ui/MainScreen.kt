package com.example.docscan.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.screens.FilesScreen
import com.example.docscan.ui.screens.HomeScreen
import com.example.docscan.ui.screens.ProfileScreen
import com.example.docscan.ui.screens.ScanScreen
import com.example.docscan.ui.screens.ToolsScreen

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Trang chủ", Icons.Default.Home)
    object Files : BottomNavItem("files", "Tài liệu", Icons.Default.Folder)
    object Tools : BottomNavItem("tools", "Công cụ", Icons.Default.Build)
    object Profile : BottomNavItem("profile", "Cá nhân", Icons.Default.Person)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    // Sử dụng remember để tránh tạo lại danh sách mỗi lần recompose
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
            // Chỉ hiện BottomBar khi ở các màn hình chính
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
        }
    ) { innerPadding ->
        // Sử dụng Surface làm nền để tránh hiện tượng trong suốt (nhìn xuyên thấu màn hình cũ)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Home.route,
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) }
            ) {
                // Các màn hình chính (Tab)
                composable(BottomNavItem.Home.route) { 
                    HomeScreen(
                        onScanClick = {
                            navController.navigate("scan")
                        }
                    ) 
                }
                composable(BottomNavItem.Files.route) { FilesScreen(navController) }
                composable(BottomNavItem.Tools.route) { ToolsScreen(navController) }
                composable(BottomNavItem.Profile.route) { ProfileScreen(navController) }

                // Màn hình chức năng
                composable("scan") { ScanScreen(navController) }
                
                // Placeholder cho các màn hình chưa có hoặc đã xóa logic cũ
                composable("text_extraction") { PlaceholderScreen("Text Extraction (Removed)", navController) }
                composable("import_image") { PlaceholderScreen("Import Image Screen", navController) }
                composable("pdf_tools") { PlaceholderScreen("PDF Tools Screen", navController) }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, navController: androidx.navigation.NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Placeholder: $name")
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
        }
    }
}
