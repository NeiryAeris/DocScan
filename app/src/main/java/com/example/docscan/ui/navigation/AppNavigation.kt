package com.example.docscan.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.docscan.ui.BottomNavItem
import com.example.docscan.ui.screens.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onScanClick = { navController.navigate("scan") },
                    onShowAllClick = {
                        navController.navigate(BottomNavItem.Files.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onImageImport = {
                        val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
                        navController.navigate("scan?imageUri=$encodedUri")
                    },
                    onDocumentImport = {
                        val encodedUri = URLEncoder.encode(it.toString(), StandardCharsets.UTF_8.toString())
                        navController.navigate("scan?pdfUri=$encodedUri")
                    }
                )
            }
            composable(BottomNavItem.Files.route) { FilesScreen(navController) }
            composable(BottomNavItem.Tools.route) { ToolsScreen(navController) }
            composable(BottomNavItem.Profile.route) { ProfileScreen(navController) }

            composable(
                route = "scan?imageUri={imageUri}&pdfUri={pdfUri}",
                arguments = listOf(
                    navArgument("imageUri") {
                        type = NavType.StringType
                        nullable = true
                    },
                    navArgument("pdfUri") {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val imageUri = backStackEntry.arguments?.getString("imageUri")?.let { Uri.parse(it) }
                val pdfUri = backStackEntry.arguments?.getString("pdfUri")?.let { Uri.parse(it) }
                ScanScreen(navController, imageUri = imageUri, pdfUri = pdfUri)
            }

            composable("text_extraction") { PlaceholderScreen("Trích xuất văn bản (Đã xóa)", navController) }
            composable("import_image") { PlaceholderScreen("Màn hình nhập ảnh", navController) }
            composable("pdf_tools") { PlaceholderScreen("Màn hình công cụ PDF", navController) }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Placeholder: $name")
                Button(onClick = { navController.popBackStack() }) {
                    Text("Quay lại")
                }
            }
        }
    }
}
