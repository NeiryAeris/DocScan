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
            HomeScreen(navController)
        }
        composable(BottomNavItem.Files.route) {
            FilesScreen(navController)
        }
        composable(BottomNavItem.Tools.route) {
            ToolsScreen(navController)
        }
        composable(BottomNavItem.Profile.route) {
            ProfileScreen(navController)
        }
    }
}