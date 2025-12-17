package com.example.docscan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.docscan.logic.storage.AppStorage
import com.example.docscan.ui.MainScreen
import com.example.docscan.ui.theme.DocScanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DocScanTheme(darkTheme = true) {
                var hasStoragePermission by remember {
                    // On Android Q (29) and above, we don't need legacy storage permissions
                    mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        if (granted) {
                            hasStoragePermission = true
                            // Attempt to create directory again after permission is granted
                            AppStorage.getPublicAppDir()
                        } else {
                            Toast.makeText(this, "Storage permission is required to save files.", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                // Request storage permission on startup if needed (for older Android versions)
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        when (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            PackageManager.PERMISSION_GRANTED -> {
                                hasStoragePermission = true
                            }
                            else -> {
                                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                    }

                    // If permission is already available, create the directory
                    if (hasStoragePermission) {
                       val appDir = AppStorage.getPublicAppDir()
                        if (appDir == null) {
                            // This might happen on some devices or if storage is corrupted
                            Toast.makeText(this@MainActivity, "Failed to create app directory.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasStoragePermission) {
                        MainScreen()
                    } else {
                        // Show a placeholder screen while waiting for permission
                        Box(contentAlignment = Alignment.Center) {
                            Text("Requesting storage permission...")
                        }
                    }
                }
            }
        }
    }
}
