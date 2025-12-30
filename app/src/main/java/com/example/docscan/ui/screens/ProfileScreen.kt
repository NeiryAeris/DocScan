package com.example.docscan.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.docscan.auth.AuthState
import com.example.docscan.auth.GoogleAuthManager
import com.example.docscan.ui.components.AppBackground
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember(context) { GoogleAuthManager(context.findActivity()) }

    var authState by remember { mutableStateOf<AuthState>(AuthState.SignedOut) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Listen for authentication state changes to handle initial state and sign-out
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            authState = if (firebaseUser != null) {
                AuthState.SignedIn(
                    uid = firebaseUser.uid,
                    displayName = firebaseUser.displayName,
                    email = firebaseUser.email,
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    idToken = "" // Not needed for UI
                )
            } else {
                AuthState.SignedOut
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            coroutineScope.launch {
                val resultState = authManager.handleSignInResult(result.data)
                // The AuthStateListener will automatically update the UI.
                // We just show a toast message and log based on the result.
                if (resultState is AuthState.SignedIn) {
                    Log.d("ProfileScreen", "Sign-in successful")
                    Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                } else if (resultState is AuthState.Error) {
                    Log.e("ProfileScreen", "Sign-in failed: ${resultState.message}")
                    Toast.makeText(context, "Đăng nhập thất bại: ${resultState.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.w("ProfileScreen", "Sign-in activity result was not OK. Result code: ${result.resultCode}")
        }
    }

    AppBackground {
        LazyColumn {
            when (val state = authState) {
                is AuthState.SignedIn -> {
                    item { UserInfoHeader(state = state, onProfileClick = { navController.navigate("account") }) }
                    item {
                        ProfileItem(
                            icon = Icons.Default.AccountCircle,
                            title = "Tài khoản",
                            onClick = { navController.navigate("account") }
                        )
                    }
                }
                else -> { // Covers SignedOut and Error states
                    item {
                        ProfileItem(
                            icon = Icons.Default.Login,
                            title = "Đăng nhập tài khoản Google",
                            onClick = { launcher.launch(authManager.getSignInIntent()) }
                        )
                    }
                }
            }
            item {
                ProfileItem(
                    icon = Icons.Default.Brightness6,
                    title = "Chủ đề ứng dụng",
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                ProfileItem(
                    icon = Icons.Default.Feedback,
                    title = "Gửi phản hồi",
                    onClick = { /* Placeholder for feedback action */ }
                )
            }
            item {
                ProfileItem(
                    icon = Icons.Default.Star,
                    title = "Đánh giá ứng dụng",
                    onClick = { /* Placeholder for rating action */ }
                )
            }
            item {
                ProfileItem(
                    icon = Icons.Default.Share,
                    title = "Mời bạn bè",
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this amazing document scanner app! [App Link]")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }
                )
            }
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            onDismiss = { showThemeDialog = false },
            onThemeSelected = {
                // Placeholder to handle theme change
                Toast.makeText(context, "Chủ đề đã chọn: $it", Toast.LENGTH_SHORT).show()
                showThemeDialog = false
            }
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themes = listOf("Sáng", "Tối", "Mặc định hệ thống")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn chủ đề") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                themes.forEach { theme ->
                    Text(
                        text = theme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 16.dp)
                    )
                }
            }
        },
        confirmButton = { // No confirm button needed as selection is instant
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun UserInfoHeader(state: AuthState.SignedIn, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.photoUrl != null) {
            AsyncImage(
                model = state.photoUrl,
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile picture",
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.displayName ?: "User", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
            Text(state.email ?: "", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun AccountScreen(navController: NavHostController) {
    val context = LocalContext.current
    val authManager = remember(context) { GoogleAuthManager(context.findActivity()) }
    val firebaseUser by rememberUpdatedState(FirebaseAuth.getInstance().currentUser)
    val uriHandler = LocalUriHandler.current

    var showDeleteDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "N/A"
        }
    }

    // This effect will trigger when the composable enters or when firebaseUser becomes null.
    // If the user signs out, firebaseUser becomes null, and we pop the back stack.
    LaunchedEffect(firebaseUser) {
        if (firebaseUser == null) {
            navController.popBackStack()
        }
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for bottom info
            ) {
                item {
                    firebaseUser?.let { user ->
                        // This is just a visual header, not the clickable UserInfoHeader
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (user.photoUrl != null) {
                                AsyncImage(
                                    model = user.photoUrl.toString(),
                                    contentDescription = "Profile picture",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Profile picture",
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.displayName ?: "User",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.Black
                                )
                                Text(user.email ?: "", fontSize = 14.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                item { Divider(modifier = Modifier.padding(horizontal = 16.dp)) }

                // Account Management
                item {
                    ProfileItem(
                        icon = Icons.Default.Settings,
                        title = "Quản lý tài khoản Google",
                        onClick = { uriHandler.openUri("https://myaccount.google.com/") }
                    )
                }
                item {
                    ProfileItem(
                        icon = Icons.Default.Delete,
                        title = "Xóa tài khoản",
                        onClick = { showDeleteDialog = true }
                    )
                }

                item { Divider(modifier = Modifier.padding(horizontal = 16.dp)) }

                // Legal
                item {
                    ProfileItem(
                        icon = Icons.Default.Policy,
                        title = "Chính sách quyền riêng tư",
                        onClick = { /* Placeholder */
                            Toast.makeText(context, "Chưa có liên kết", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                item {
                    ProfileItem(
                        icon = Icons.Default.Description,
                        title = "Điều khoản dịch vụ",
                        onClick = {  /* Placeholder */
                            Toast.makeText(context, "Chưa có liên kết", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item { Divider(modifier = Modifier.padding(horizontal = 16.dp)) }

                // Logout
                item {
                    ProfileItem(
                        icon = Icons.Default.Logout,
                        title = "Đăng xuất",
                        onClick = { authManager.signOut() }
                    )
                }
            }

            // Bottom Info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                firebaseUser?.uid?.let { uid ->
                    Text(
                        text = "User ID: $uid",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = "Phiên bản $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Xóa tài khoản") },
            text = { Text("Bạn có chắc chắn muốn xóa tài khoản vĩnh viễn không? Hành động này không thể hoàn tác và toàn bộ dữ liệu của bạn sẽ bị mất.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        firebaseUser?.delete()?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Tài khoản đã được xóa.", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("AccountScreen", "Account deletion failed", task.exception)
                                Toast.makeText(context, "Lỗi: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
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

private fun Context.findActivity(): Activity {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    throw IllegalStateException("An Activity was not found in the context hierarchy.")
}
