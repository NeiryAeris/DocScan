package com.example.docscan.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.docscan.App
import com.example.docscan.R
import com.example.docscan.auth.AuthState
import com.example.docscan.auth.GoogleAuthManager
import com.example.docscan.data.UserPreferencesRepository
import com.example.docscan.ui.components.AppBackground
import com.example.docscan.ui.theme.Theme
import com.example.docscan.ui.theme.ThemeViewModel
import com.example.docscan.ui.theme.ThemeViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember(context) { GoogleAuthManager(context.findActivity()) }
    val uriHandler = LocalUriHandler.current

    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModelFactory(context))
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(UserPreferencesRepository(context)))

    val isBackupEnabled by profileViewModel.isBackupEnabled.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }

    var showFeedbackSheet by remember { mutableStateOf(false) }
    val feedbackSheetState = rememberModalBottomSheetState()

    var showRatingSheet by remember { mutableStateOf(false) }
    val ratingSheetState = rememberModalBottomSheetState()

    var authState by remember { mutableStateOf<AuthState>(AuthState.SignedOut) }

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
                    icon = Icons.Default.CloudSync,
                    title = "Sao lưu & Đồng bộ hóa",
                    extraContent = {
                        Switch(
                            checked = isBackupEnabled,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) {
                                    coroutineScope.launch {
                                        try {
                                            if (authState !is AuthState.SignedIn) {
                                                Toast.makeText(context, "Vui lòng đăng nhập trước khi bật sao lưu.", Toast.LENGTH_LONG).show()
                                                return@launch
                                            }

                                            val status = App.driveClient.status()
                                            if (status.linked) {
                                                if (status.folderId == null) {
                                                    Log.i("ProfileScreen", "Drive linked, initializing app folder...")
                                                    App.driveClient.initFolder()
                                                }
                                                profileViewModel.setBackupEnabled(true)
                                                Toast.makeText(context, "Đã bật sao lưu và đồng bộ hóa.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Log.i("ProfileScreen", "Drive not linked, starting OAuth flow...")
                                                val oauthStart = App.driveClient.oauthStart()
                                                uriHandler.openUri(oauthStart.url)
                                                Toast.makeText(context, "Vui lòng hoàn tất liên kết với Google Drive trong trình duyệt.", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("ProfileScreen", "Failed to enable backup", e)
                                            Toast.makeText(context, "Lỗi khi bật sao lưu: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    profileViewModel.setBackupEnabled(false)
                                    Toast.makeText(context, "Đã tắt sao lưu.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
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
                    onClick = { showFeedbackSheet = true }
                )
            }
            item {
                ProfileItem(
                    icon = Icons.Default.Star,
                    title = "Đánh giá ứng dụng",
                    onClick = { showRatingSheet = true }
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
                themeViewModel.setTheme(it)
                showThemeDialog = false
            }
        )
    }

    if (showFeedbackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFeedbackSheet = false },
            sheetState = feedbackSheetState
        ) {
            FeedbackSheetContent(onSend = {
                feedbackText ->
                coroutineScope.launch {
                    feedbackSheetState.hide()
                }.invokeOnCompletion {
                    if (!feedbackSheetState.isVisible) {
                        showFeedbackSheet = false
                    }
                }
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:") // only email apps should handle this
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("anquan0298@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Phản hồi ứng dụng DocScan")
                    putExtra(Intent.EXTRA_TEXT, feedbackText)
                }
                try {
                    context.startActivity(Intent.createChooser(emailIntent, "Gửi phản hồi..."))
                    Toast.makeText(context, "Gửi phản hồi thành công!", Toast.LENGTH_SHORT).show()
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "Không tìm thấy ứng dụng email.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    if (showRatingSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRatingSheet = false },
            sheetState = ratingSheetState
        ) {
            RatingSheetContent(
                onRateOnPlayStore = {
                    coroutineScope.launch {
                        ratingSheetState.hide()
                    }.invokeOnCompletion {
                        if (!ratingSheetState.isVisible) {
                            showRatingSheet = false
                        }
                    }
                    Toast.makeText(context, "Chức năng sẽ sớm ra mắt!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun RatingSheetContent(onRateOnPlayStore: () -> Unit) {
    var rating by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val imageRes = when {
            rating in 1..2 -> R.drawable.sad
            rating in 3..4 -> R.drawable.good
            rating == 5 -> R.drawable.happy
            else -> null
        }

        if (imageRes != null) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Rating feedback emoticon",
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        Text("Bạn cảm thấy ứng dụng thế nào?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            (1..5).forEach { index ->
                Icon(
                    imageVector = if (index <= rating) Icons.Filled.Star else Icons.Default.StarBorder,
                    contentDescription = "Star $index",
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { rating = index },
                    tint = if (index <= rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onRateOnPlayStore,
            enabled = rating > 0,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("Đánh giá trên CH Play")
        }
    }
}

@Composable
fun FeedbackSheetContent(onSend: (String) -> Unit) {
    var feedbackText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp), // Extra padding for the bottom
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gửi phản hồi", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = feedbackText,
            onValueChange = { feedbackText = it },
            label = { Text("Nội dung phản hồi") },
            placeholder = { Text("Hãy cho chúng tôi biết suy nghĩ của bạn...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp), // Larger text area
            maxLines = 10,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSend(feedbackText) },
            enabled = feedbackText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gửi")
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (Theme) -> Unit
) {
    val themes = listOf(Theme.LIGHT, Theme.DARK)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn chủ đề") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                themes.forEach { theme ->
                    Text(
                        text = if (theme == Theme.LIGHT) "Sáng" else "Tối",
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
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.displayName ?: "User", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(state.email ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun AccountScreen(navController: NavHostController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember(context) { GoogleAuthManager(context.findActivity()) }
    val uriHandler = LocalUriHandler.current

    var authState by remember { mutableStateOf<AuthState>(AuthState.SignedOut) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Listen for authentication state changes
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

    // Launcher for the sign-in intent
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            coroutineScope.launch {
                val resultState = authManager.handleSignInResult(result.data)
                if (resultState is AuthState.Error) {
                    Toast.makeText(context, "Đăng nhập thất bại: ${resultState.message}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "N/A"
        }
    }

    AppBackground {
        when (val state = authState) {
            is AuthState.SignedIn -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp) // Space for bottom info
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        state.displayName ?: "User",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(state.email ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
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
                                onClick = { Toast.makeText(context, "Chưa có liên kết", Toast.LENGTH_SHORT).show() }
                            )
                        }
                        item {
                            ProfileItem(
                                icon = Icons.Default.Description,
                                title = "Điều khoản dịch vụ",
                                onClick = { Toast.makeText(context, "Chưa có liên kết", Toast.LENGTH_SHORT).show() }
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
                        Text(
                            text = "User ID: ${state.uid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Phiên bản $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> { // SignedOut or Error
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { launcher.launch(authManager.getSignInIntent()) }) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Đăng nhập bằng Google")
                    }
                }
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
                        FirebaseAuth.getInstance().currentUser?.delete()?.addOnCompleteListener { task ->
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
fun ProfileItem(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit = {}, extraContent: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        if (subtitle != null) {
            Text(subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        } else {
            extraContent()
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
