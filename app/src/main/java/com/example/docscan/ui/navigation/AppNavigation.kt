// In ui/navigation/AppNavigation.kt
package com.example.docscan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.MainScreen
import com.example.docscan.ui.screens.HomeScreen

object Routes {
    const val Main = "main"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = Routes.Main
    ) {
        composable(Routes.Main) { HomeScreen() }
    }
}
