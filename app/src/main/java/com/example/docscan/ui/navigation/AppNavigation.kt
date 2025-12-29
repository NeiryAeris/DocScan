package com.example.docscan.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.docscan.App
import com.example.docscan.ui.BottomNavItem
import com.example.docscan.ui.screens.*
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    val ocrGateway = App.ocrGateway
    val bottomNavItems = remember { listOf(BottomNavItem.Home, BottomNavItem.Files, BottomNavItem.Tools, BottomNavItem.Profile) }

    fun getIndex(route: String?): Int {
        return bottomNavItems.indexOfFirst { it.route == route }.takeIf { it != -1 } ?: Int.MAX_VALUE
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedNavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            enterTransition = {
                val fromIndex = getIndex(initialState.destination.route)
                val toIndex = getIndex(targetState.destination.route)
                if (fromIndex < toIndex) {
                    slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300))
                } else {
                    slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300))
                }
            },
            exitTransition = {
                val fromIndex = getIndex(initialState.destination.route)
                val toIndex = getIndex(targetState.destination.route)
                if (fromIndex < toIndex) {
                    slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300))
                } else {
                    slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300))
                }
            },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onScanClick = { navController.navigate("scan") },
                    onIdCardScanClick = { navController.navigate("id_card_scan") },
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
                    },
                    ocrGateway = ocrGateway // Pass the gateway to the HomeScreen
                )
            }
            composable(BottomNavItem.Files.route) { FilesScreen(navController) }
            composable(BottomNavItem.Tools.route) { ToolsScreen(navController, ocrGateway = ocrGateway) }
            composable(BottomNavItem.Profile.route) { ProfileScreen(navController) }

            composable(
                route = "scan?imageUri={imageUri}&pdfUri={pdfUri}",
                arguments = listOf(
                    navArgument("imageUri") { type = NavType.StringType; nullable = true },
                    navArgument("pdfUri") { type = NavType.StringType; nullable = true }
                )
            ) { backStackEntry ->
                val imageUri = backStackEntry.arguments?.getString("imageUri")?.let { Uri.parse(it) }
                val pdfUri = backStackEntry.arguments?.getString("pdfUri")?.let { Uri.parse(it) }
                ScanScreen(navController, imageUri = imageUri, pdfUri = pdfUri)
            }

            composable(route = "signing") {
                if (App.pdfToSign != null) {
                    SigningScreen(navController)
                } else {
                    navController.popBackStack()
                }
            }

            composable("signature_management") { SignatureManagementScreen(navController = navController) }
            composable("add_watermark") { AddWatermarkScreen(navController = navController) }
            composable("preview_screen") { PreviewScreen(navController = navController) }
            composable("chat") { ChatScreen(navController = navController) }

            composable("id_card_scan") {
                IdCardScanScreen(
                    onScanComplete = { uri ->
                        val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                        navController.navigate("scan?pdfUri=$encodedUri") { popUpTo("id_card_scan") { inclusive = true } }
                    }
                )
            }

            composable("text_extraction") { PlaceholderScreen("Trích xuất văn bản (Đã xóa)", navController) }
            composable("import_image") { PlaceholderScreen("Màn hình nhập ảnh", navController) }
            composable("pdf_tools") { FilesScreen(navController) }
            composable(
                route = "pdf_to_image/{documentUri}",
                arguments = listOf(navArgument("documentUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val documentUriString = backStackEntry.arguments?.getString("documentUri")
                if (documentUriString != null) {
                    val decodedUri = URLDecoder.decode(documentUriString, StandardCharsets.UTF_8.name())
                    PdfToImageScreen(navController, encodedUri = decodedUri)
                }
            }

            composable(
                route = "pdf_to_word/{pdfUri}",
                arguments = listOf(navArgument("pdfUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val pdfUriString = backStackEntry.arguments?.getString("pdfUri")
                if (pdfUriString != null) {
                    val decodedUri = URLDecoder.decode(pdfUriString, StandardCharsets.UTF_8.name())
                    PdfToWordScreen(navController, Uri.parse(decodedUri))
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String, navController: NavController) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Placeholder: $name")
                Button(onClick = { navController.popBackStack() }) { Text("Quay lại") }
            }
        }
    }
}