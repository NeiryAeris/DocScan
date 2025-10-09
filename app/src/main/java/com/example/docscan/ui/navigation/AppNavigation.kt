// In ui/navigation/AppNavigation.kt
package com.example.docscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.docscan.ui.BottomNavItem
import com.example.docscan.ui.screens.*

@Composable
fun AppNavigation(navController: NavHostController) {
    // NavHost là nơi quyết định màn hình nào sẽ được hiển thị
    // dựa trên route (đường dẫn) hiện tại.
    NavHost(navController, startDestination = BottomNavItem.Home.route) {
        composable(BottomNavItem.Home.route) {
            HomeScreen()
        }
        composable(BottomNavItem.Files.route) {
            // Thay thế bằng màn hình FilesScreen của bạn khi đã tạo
            // FilesScreen()
        }
        composable(BottomNavItem.Tools.route) {
            // Thay thế bằng màn hình ToolsScreen của bạn khi đã tạo
            // ToolsScreen()
        }
        composable(BottomNavItem.Profile.route) {
            // Thay thế bằng màn hình ProfileScreen của bạn khi đã tạo
            // ProfileScreen()
        }
    }
}