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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.docscan.logic.storage.AppStorage
import com.example.docscan.ui.MainScreen
import com.example.docscan.ui.navigation.AppNavigation
import com.example.docscan.ui.theme.DocScanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DocScanTheme(darkTheme = true) {
                val scope = rememberCoroutineScope()
                var hasStoragePermission by remember {
                    mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        if (granted) {
                            hasStoragePermission = true
                            scope.launch {
                                AppStorage.getPublicAppDir()
                            }
                        } else {
                            Toast.makeText(this, "Storage permission is required to save files.", Toast.LENGTH_LONG).show()
                        }
                    }
                )

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

                    if (hasStoragePermission) {
                       val appDir = AppStorage.getPublicAppDir()
                        if (appDir == null) {
                            Toast.makeText(this@MainActivity, "Failed to create app directory.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasStoragePermission) {
                        val navController = rememberNavController()
                        MainScreen(navController = navController) {
                            AppNavigation(navController = navController, modifier = Modifier.padding(it))
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Requesting storage permission...")
                        }
                    }
                }
            }
        }
    }
}
