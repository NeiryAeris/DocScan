package com.example.docscan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController

@Composable
fun ProfileScreen(navController: NavHostController) {

    // Remember navigation actions to make them stable
    val navigateToProfile = remember { { navController.navigate("profile") } }
    val navigateToBenefits = remember { { /*TODO*/ } }
    val navigateToSync = remember { { /*TODO*/ } }
    val navigateToSettings = remember { { /*TODO*/ } }
    val openBalance = remember { { /* maybe open balance */ } }
    val openCloud = remember { { } }
    val openTasks = remember { { } }
    val upgradeNow = remember { { /*TODO*/ } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFA9FFF8),
                        Color(0xFFF4FAFE),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        LazyColumn {
            item {
                // Upgrade section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nâng cấp tài khoản của tôi", style = MaterialTheme.typography.titleMedium)
                        Text("Mở khóa 20+ đặc quyền Premium", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = upgradeNow, modifier = Modifier.align(Alignment.End)) {
                            Text("Nâng cấp ngay")
                        }
                    }
                }
            }

            // Profile options
            item { ProfileItem(icon = Icons.Default.Cloud, title = "Đám mây", onClick = openCloud) }
            item { ProfileItem(icon = Icons.Default.CheckCircle, title = "Nhiệm vụ", onClick = openTasks) }
            item { ProfileItem(icon = Icons.Default.MonetizationOn, title = "Số dư điểm C", subtitle = "0 điểm", onClick = openBalance) }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            item { ProfileItem(icon = Icons.Default.AccountCircle, title = "Tài khoản", onClick = navigateToProfile) }
            item { ProfileItem(icon = Icons.Default.CardGiftcard, title = "Lợi ích EDU", subtitle = "Premium miễn phí", onClick = navigateToBenefits) }
            item { ProfileItem(icon = Icons.Default.Sync, title = "Đồng bộ", onClick = navigateToSync) }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            item { ProfileItem(icon = Icons.Default.Settings, title = "Cài đặt khác", onClick = navigateToSettings) }
        }
    }
}

@Composable
fun ProfileItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = Color.Black)
        }
        if (subtitle != null) {
            Text(subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Preview(name = "ProfileScreen Preview", showBackground = true)
@Composable
fun Preview_ProfileScreen() {
    ProfileScreen(navController = rememberNavController())
}
