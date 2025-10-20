// In ui/MainScreen.kt
package com.example.docscan.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.screens.FilesScreen
import com.example.docscan.ui.screens.HomeScreen
import com.example.docscan.ui.screens.ProfileScreen
import com.example.docscan.ui.screens.ToolsScreen
import com.example.docscan.logic.camera.CameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.rememberAsyncImagePainter
import com.example.docscan.logic.utils.FileOps
import com.example.docscan.logic.utils.ScanSession
import java.io.File
import kotlinx.coroutines.launch
import com.example.docscan.logic.utils.runPipelineAsync

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
                onClick = { navController.navigate("scan") },
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
            composable(BottomNavItem.Home.route) { HomeScreen(navController) }
            composable(BottomNavItem.Files.route) { FilesScreen(navController) }
            composable(BottomNavItem.Tools.route) { ToolsScreen(navController) }
            composable(BottomNavItem.Profile.route) { ProfileScreen(navController) }

            // Simple destinations for actions
            composable("scan") { CameraScreen() }
            composable("import_image") { ImportImageScreen() }
            composable("pdf_tools") { SimplePlaceholderScreen(title = "PDF Tools") }
            composable("text_extraction") { SimplePlaceholderScreen(title = "Text Extraction") }
        }
    }
}

@Composable
fun SimplePlaceholderScreen(title: String) {
    // Minimal placeholder screen; real implementations should be provided separately
    Box(modifier = Modifier.padding(16.dp)) {
        Text(text = title)
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { CameraController(context) }
    val coroutineScope = rememberCoroutineScope()

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        hasPermission = granted
    }

    if (!hasPermission) {
        // Show a simple UI prompting for permission
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)) {
            Text(text = "Camera permission is required to scan documents.")
            Spacer(modifier = Modifier.size(12.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(text = "Grant Camera Permission")
            }
        }
        // Ensure camera is unbound if permission lost
        DisposableEffect(Unit) {
            onDispose { cameraController.unbindAll() }
        }
        return
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).also { view ->
                previewView = view
            }
        }, update = { view ->
            // No-op
        }, modifier = Modifier.fillMaxSize())

        // Snackbar host
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))

        // Capture button overlay
        Column(modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 36.dp)) {
            Button(
                onClick = {
                    val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                    cameraController.takePhoto(
                        file,
                        onSaved = { saved ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Saved: ${'$'}{saved.name}")
                            }
                        },
                        onError = { err ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Capture failed: ${'$'}{err.message}")
                            }
                        }
                    )
                },
                shape = CircleShape
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
            }
        }
    }

    LaunchedEffect(previewView, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        previewView?.let { view ->
            try {
                cameraController.start(lifecycleOwner, view)
            } catch (t: Throwable) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Camera start failed: ${'$'}{t.message}")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.unbindAll()
        }
    }
}

@Composable
fun ImportImageScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var overlayBitmap by remember { mutableStateOf<Bitmap?>(null) }   // preview with quad
    var enhancedBitmap by remember { mutableStateOf<Bitmap?>(null) }  // final result
    var isFullScreen by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    // New: use the shared pipeline via our bridge
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        if (uri == null) return@rememberLauncherForActivityResult

        isBusy = true
        scope.launch {
            try {
                val bmp = FileOps.loadImageFromUri(context, uri)
                val res = runPipelineAsync(bmp, mode = "color")
                overlayBitmap = res.overlay
                enhancedBitmap = res.enhanced
                ScanSession.add(res.page)
            } catch (t: Throwable) {
                // Optional: show a snackbar or log
                // e.g., DebugLog.e("Import failed", tr = t)
                overlayBitmap = null
                enhancedBitmap = null
            } finally {
                isBusy = false
            }
        }
    }

    if (isFullScreen && enhancedBitmap != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = enhancedBitmap!!.asImageBitmap(),
                contentDescription = "Enhanced Image Fullscreen",
                modifier = Modifier.fillMaxSize()
            )
            Button(
                onClick = { isFullScreen = false },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) { Text("Back") }
        }
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Import Image", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { if (!isBusy) launcher.launch("image/*") }, enabled = !isBusy) {
                Text(if (isBusy) "Processing…" else "Select Image from Device")
            }

            selectedImageUri?.let { uri ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Selected: ${uri.path}")
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp)
                )
            }

            // Optional: show overlay (quad preview)
            overlayBitmap?.let { bmp ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Overlay:")
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Overlay", modifier = Modifier.size(200.dp))
            }

            enhancedBitmap?.let { bmp ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("Enhanced Output:")
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Enhanced Image", modifier = Modifier.size(200.dp))
                Button(onClick = { isFullScreen = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("View Fullscreen")
                }
            }
        }
    }
}

