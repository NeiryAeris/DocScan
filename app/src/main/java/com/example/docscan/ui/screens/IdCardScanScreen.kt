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
import androidx.camera.core.UseCaseGroup
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
import kotlin.math.max
import kotlin.math.min

enum class ScanStep {
    FRONT, BACK
}

@Composable
fun IdCardScanScreen(
    onScanComplete: (Uri) -> Unit
) {
    val context = LocalContext.current
    var hasCamPermission by remember { mutableStateOf(false) }
    val onPermissionResult = remember { { granted: Boolean -> hasCamPermission = granted } }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onPermissionResult
    )

    LaunchedEffect(key1 = true) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var scanStep by remember { mutableStateOf(ScanStep.FRONT) }
    var frontImageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context).apply { this.scaleType = PreviewView.ScaleType.FILL_CENTER } }


    val onCaptureClick = remember(isProcessing, scanStep, frontImageUri, imageCapture, onScanComplete) {
        {
            if (isProcessing) return@remember

            scope.launch(Dispatchers.Main) {
                isProcessing = true
                var imageProxy: ImageProxy? = null
                try {
                    imageProxy = takePictureAsynchronously(context, imageCapture)

                    val croppedUri = withContext(Dispatchers.IO) {
                        val croppedBitmap = cropImageToViewfinder(imageProxy, previewView)
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
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.delete(frontUri, null, null)
                                    context.contentResolver.delete(backImageUri, null, null)
                                }
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
                    imageProxy?.close()
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (hasCamPermission) {
                FloatingActionButton(
                    onClick = onCaptureClick,
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
                    imageCapture = imageCapture,
                    previewView = previewView
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
    imageCapture: ImageCapture,
    previewView: PreviewView
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }


    AndroidView(
        factory = { previewView },
        modifier = modifier
    ) {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .setViewPort(previewView.viewPort!!)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )
            } catch (exc: Exception) {
                // Log or handle exceptions
            }
        }, ContextCompat.getMainExecutor(context))
    }
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

private fun cropImageToViewfinder(image: ImageProxy, previewView: PreviewView): Bitmap {
    // 1. Get the rotated bitmap from the ImageProxy
    val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    val originalBitmap = ImageUtils.imageProxyToBitmap(image)
    val rotatedBitmap = Bitmap.createBitmap(
        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
    )

    // 2. Get dimensions
    val imageWidth = rotatedBitmap.width
    val imageHeight = rotatedBitmap.height
    val viewWidth = previewView.width
    val viewHeight = previewView.height

    // If the view is not laid out, we can't calculate the crop. Return the rotated image.
    if (viewWidth == 0 || viewHeight == 0) {
        return rotatedBitmap
    }

    // 3. Calculate the transformation from image coordinates to view coordinates.
    //    This logic mirrors PreviewView's "FILL_CENTER" scale type.
    val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
    val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()

    val scale: Float
    if (imageAspectRatio > viewAspectRatio) {
        // Image is wider than the view. It's scaled to fill the view's height and cropped horizontally.
        scale = viewHeight.toFloat() / imageHeight.toFloat()
    } else {
        // Image is taller than or has the same aspect ratio as the view.
        // It's scaled to fill the view's width and cropped vertically.
        scale = viewWidth.toFloat() / imageWidth.toFloat()
    }

    val scaledImageWidth = imageWidth * scale
    val scaledImageHeight = imageHeight * scale

    // The offset of the scaled image's top-left corner relative to the view's top-left corner.
    val offsetX = (viewWidth - scaledImageWidth) / 2f
    val offsetY = (viewHeight - scaledImageHeight) / 2f

    // 4. Calculate the viewfinder rectangle in view coordinates (in pixels).
    val density = previewView.resources.displayMetrics.density
    val viewfinderWidthPx = 320.dp.value * density
    val viewfinderHeightPx = 200.dp.value * density
    val viewfinderLeftInView = (viewWidth - viewfinderWidthPx) / 2f
    val viewfinderTopInView = (viewHeight - viewfinderHeightPx) / 2f

    // 5. Map the viewfinder rectangle from view coordinates to bitmap coordinates.
    //    - First, adjust for the offset to get coordinates relative to the scaled image.
    //    - Then, divide by the scale factor to get coordinates in the original bitmap's space.
    val cropLeft = (viewfinderLeftInView - offsetX) / scale
    val cropTop = (viewfinderTopInView - offsetY) / scale
    val cropRight = cropLeft + (viewfinderWidthPx / scale)
    val cropBottom = cropTop + (viewfinderHeightPx / scale)

    // 6. Clamp the resulting crop rectangle to the bounds of the bitmap.
    val finalCropRectLeft = max(0f, cropLeft).toInt()
    val finalCropRectTop = max(0f, cropTop).toInt()
    val finalCropRectRight = min(imageWidth.toFloat(), cropRight).toInt()
    val finalCropRectBottom = min(imageHeight.toFloat(), cropBottom).toInt()

    // 7. Calculate the final width and height from the clamped rectangle.
    val finalWidth = finalCropRectRight - finalCropRectLeft
    val finalHeight = finalCropRectBottom - finalCropRectTop

    // 8. Sanity check: If the final dimensions are not positive, it means the
    //    viewfinder was entirely outside the visible portion of the image. Return
    //    the uncropped (but rotated) image as a fallback.
    if (finalWidth <= 0 || finalHeight <= 0) {
        return rotatedBitmap
    }

    // 9. Perform the crop.
    return Bitmap.createBitmap(
        rotatedBitmap,
        finalCropRectLeft,
        finalCropRectTop,
        finalWidth,
        finalHeight
    )
}
