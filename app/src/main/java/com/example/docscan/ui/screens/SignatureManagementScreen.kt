package com.example.docscan.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.docscan.ui.components.SignaturePad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureManagementScreen(navController: NavController) {
    val showSignaturePad = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Quản lý chữ ký") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSignaturePad.value = true }) {
                Icon(Icons.Default.Add, contentDescription = "Thêm chữ ký")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (showSignaturePad.value) {
                SignaturePad(onSignatureChanged = { /*TODO*/ })
            }
        }
    }
}
