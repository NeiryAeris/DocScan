package com.example.docscan.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.docscan.logic.storage.DocumentRepository
import com.example.docscan.logic.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ScanStep {
    FRONT, BACK
}

@Composable
fun IdCardScanScreen(
    onScanComplete: (Uri) -> Unit
) {
    val context = LocalContext.current
    var hasCamPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasCamPermission = it }
    )

    LaunchedEffect(key1 = true) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var scanStep by remember { mutableStateOf(ScanStep.FRONT) }
    var frontImageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            if (hasCamPermission) {
                FloatingActionButton(
                    onClick = {
                        if (isProcessing) return@FloatingActionButton

                        scope.launch {
                            isProcessing = true
                            var imageProxy: ImageProxy? = null
                            try {
                                imageProxy = takePictureAsynchronously(context, imageCapture)

                                val croppedUri = withContext(Dispatchers.IO) {
                                    val croppedBitmap = cropImageToViewfinder(imageProxy)
                                    ImageUtils.saveBitmapAndGetUri(context, croppedBitmap)
                                }

                                if (croppedUri == null) {
                                    Toast.makeText(context, "Lỗi khi lưu ảnh.", Toast.LENGTH_SHORT).show()
                                    isProcessing = false
                                    return@launch
                                }

                                if (scanStep == ScanStep.FRONT) {
                                    frontImageUri = croppedUri
                                    scanStep = ScanStep.BACK
                                    isProcessing = false
                                } else {
                                    val backImageUri = croppedUri
                                    val frontUri = frontImageUri
                                    if (frontUri != null) {
                                        val pdfUri = DocumentRepository.createPdfFromImages(
                                            context,
                                            listOf(frontUri, backImageUri)
                                        )
                                        if (pdfUri != null) {
                                            context.contentResolver.delete(frontUri, null, null)
                                            context.contentResolver.delete(backImageUri, null, null)
                                            onScanComplete(pdfUri)
                                        } else {
                                            Toast.makeText(context, "Lỗi khi tạo tệp PDF.", Toast.LENGTH_SHORT).show()
                                            isProcessing = false
                                        }
                                    } else {
                                        Toast.makeText(context, "Lỗi: Không tìm thấy ảnh mặt trước.", Toast.LENGTH_SHORT).show()
                                        isProcessing = false
                                    }
                                }
                            } catch (e: ImageCaptureException) {
                                Toast.makeText(context, "Lỗi khi chụp ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
                                isProcessing = false
                            } catch (e: Exception) {
                                Toast.makeText(context, "Đã xảy ra lỗi không mong muốn: ${e.message}", Toast.LENGTH_SHORT).show()
                                isProcessing = false
                            } finally {
                                imageProxy?.close() // Ensure image is closed!
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Default.Camera, contentDescription = "Scan ID Card")
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (hasCamPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = imageCapture
                )
                IdCardViewfinder(scanStep = scanStep)
            } else {
                Text("Quyền truy cập camera là bắt buộc.")
            }
        }
    }
}

@Composable
private fun IdCardViewfinder(scanStep: ScanStep) {
    val instructionText = if (scanStep == ScanStep.FRONT) {
        "Căn chỉnh mặt trước của thẻ vào trong khung"
    } else {
        "Căn chỉnh mặt sau của thẻ vào trong khung"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .height(200.dp) // Aspect ratio is 320/200 = 1.6
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            )
            Text(
                text = instructionText,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    // Log or handle exceptions
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}

private suspend fun takePictureAsynchronously(context: Context, imageCapture: ImageCapture): ImageProxy {
    return suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    continuation.resume(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }
}

private fun cropImageToViewfinder(image: ImageProxy): Bitmap {
    val rotationDegrees = image.imageInfo.rotationDegrees
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val bitmap = ImageUtils.imageProxyToBitmap(image)

    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

    val viewfinderAspectRatio = 1.6f
    val rotatedBitmapAspectRatio = rotatedBitmap.width.toFloat() / rotatedBitmap.height.toFloat()

    var cropWidth = rotatedBitmap.width
    var cropHeight = rotatedBitmap.height

    if (rotatedBitmapAspectRatio > viewfinderAspectRatio) {
        cropWidth = (rotatedBitmap.height * viewfinderAspectRatio).toInt()
    } else {
        cropHeight = (rotatedBitmap.width / viewfinderAspectRatio).toInt()
    }

    val cropX = (rotatedBitmap.width - cropWidth) / 2
    val cropY = (rotatedBitmap.height - cropHeight) / 2

    return Bitmap.createBitmap(rotatedBitmap, cropX, cropY, cropWidth, cropHeight)
}
