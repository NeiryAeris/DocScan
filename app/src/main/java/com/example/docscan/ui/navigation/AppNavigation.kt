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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.docscan.logic.ocr.OcrGatewayImpl
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.NodeCloudOcrGateway
import com.example.docscan.ui.BottomNavItem
import com.example.docscan.ui.screens.*
import com.example.ocr.core.api.AuthTokenProvider
import com.example.ocr_remote.RemoteOcrClientImpl
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    // Create an instance of the OCR gateway. In a real app, you'd use a DI framework like Hilt.
    val ocrGateway = remember {
        val baseUrl = "https://gateway.neirylittlebox.com"
        val authToken =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyXzEiLCJlbWFpbCI6ImRlbW9AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjU4OTE5MTEsImV4cCI6MTc2NjQ5NjcxMX0.5UL4rGDR3TepmMBsi0pS97MHfhutpWcjGn8v4l93Q84"

        val remoteClient = RemoteOcrClientImpl(
            baseUrl = baseUrl,
            authTokenProvider = { authToken }
        )
        val cloudGateway = NodeCloudOcrGateway(remoteClient)
        OcrGatewayImpl(cloudGateway)
    }

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

            composable("id_card_scan") {
                IdCardScanScreen(
                    onScanComplete = { uri ->
                        val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                        // Navigate to ScanScreen with the new PDF, and remove IdCardScanScreen from the back stack
                        navController.navigate("scan?pdfUri=$encodedUri") {
                            popUpTo("id_card_scan") { inclusive = true }
                        }
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
                val documents by DocumentRepository.documents.collectAsState()
                val document = backStackEntry.arguments?.getString("documentUri")?.let { uriString ->
                    val decodedUri = URLDecoder.decode(uriString, StandardCharsets.UTF_8.name())
                    val documentUri = Uri.parse(decodedUri)
                    documents.find { it.file.toUri() == documentUri }
                }
                PdfToImageScreen(navController, document)
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
                Button(onClick = { navController.popBackStack() }) {
                    Text("Quay lại")
                }
            }
        }
    }
}
