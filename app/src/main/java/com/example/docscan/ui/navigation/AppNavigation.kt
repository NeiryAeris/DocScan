package com.example.docscan.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.docscan.ui.BottomNavItem
import com.example.docscan.ui.screens.*

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route
            // Transitions removed for immediate screen switching
            // enterTransition = { fadeIn(animationSpec = tween(200)) },
            // exitTransition = { fadeOut(animationSpec = tween(200)) },
            // popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            // popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            // Main screens (Tabs)
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

            // Functional screens
            composable("scan") { ScanScreen(navController) }

            // Placeholder screens for features not yet implemented or deprecated
            composable("text_extraction") { PlaceholderScreen("Text Extraction (Removed)", navController) }
            composable("import_image") { PlaceholderScreen("Import Image Screen", navController) }
            composable("pdf_tools") { PlaceholderScreen("PDF Tools Screen", navController) }
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
                    Text("Go Back")
                }
            }
        }
    }
}
