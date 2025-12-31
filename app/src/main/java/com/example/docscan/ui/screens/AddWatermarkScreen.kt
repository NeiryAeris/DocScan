package com.example.docscan.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Image
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.ColumnText
import com.itextpdf.text.pdf.PdfGState
import com.itextpdf.text.pdf.PdfName
import com.itextpdf.text.pdf.PdfNumber
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWatermarkScreen(navController: NavHostController) {
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkText by remember { mutableStateOf("Bản nháp") }
    var opacity by remember { mutableStateOf(0.5f) }
    var scale by remember { mutableStateOf(50f) }
    var textSize by remember { mutableStateOf(50f) }
    var textColor by remember { mutableStateOf(Color.Gray) }
    var tabIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingPreview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val scaffoldState = rememberBottomSheetScaffoldState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    pdfUri = uri
                } catch (e: SecurityException) {
                    Toast.makeText(context, "Không cấp đủ quyền", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                imageUri = uri
            }
        }
    )

    val saverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { savedUri ->
            if (savedUri != null && pdfUri != null) {
                try {
                    context.contentResolver.openInputStream(pdfUri!!)?.use { pdfInputStream ->
                        context.contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                            val reader = PdfReader(pdfInputStream)
                            val stamper = PdfStamper(reader, outputStream)
                            val gstate = PdfGState()
                            gstate.put(PdfName.CA, PdfNumber(opacity))
                            gstate.put(PdfName.ca, PdfNumber(opacity))

                            for (i in 1..reader.numberOfPages) {
                                val over = stamper.getOverContent(i)
                                over.saveState()
                                over.setGState(gstate)

                                if (tabIndex == 0 && imageUri != null) {
                                    context.contentResolver.openInputStream(imageUri!!)?.use { imageInputStream ->
                                        val watermarkImage = Image.getInstance(imageInputStream.readBytes())
                                        watermarkImage.scalePercent(scale)
                                        val x = (reader.getPageSize(i).width - watermarkImage.scaledWidth) / 2
                                        val y = (reader.getPageSize(i).height - watermarkImage.scaledHeight) / 2
                                        watermarkImage.setAbsolutePosition(x, y)
                                        over.addImage(watermarkImage)
                                    }
                                } else if (tabIndex == 1) {
                                    val font = Font(Font.FontFamily.HELVETICA, textSize, Font.BOLD, BaseColor(textColor.toArgb()))
                                    val phrase = Phrase(watermarkText, font)
                                    val x = (reader.getPageSize(i).width / 2)
                                    val y = (reader.getPageSize(i).height / 2)
                                    ColumnText.showTextAligned(over, Element.ALIGN_CENTER, phrase, x, y, 45f)
                                }

                                over.restoreState()
                            }
                            stamper.close()
                            reader.close()
                        }
                    }
                    Toast.makeText(context, "Thêm logo thành công!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    fun generatePreview() {
        previewJob?.cancel()

        if (pdfUri == null) {
            previewBitmap = null
            return
        }

        previewJob = scope.launch {
            isGeneratingPreview = true
            delay(300) // Debounce preview generation
            try {
                val bitmapResult = withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(pdfUri!!, "r")?.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        val page = renderer.openPage(0)
                        
                        val targetWidth = 1080
                        val aspectRatio = page.width.toFloat() / page.height.toFloat()
                        val targetHeight = (targetWidth / aspectRatio).toInt()

                        val pageBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

                        page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()

                        val mutableBitmap = pageBitmap.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = Canvas(mutableBitmap)

                        val isWatermarkReady = (tabIndex == 0 && imageUri != null) || (tabIndex == 1 && watermarkText.isNotBlank())
                        if (isWatermarkReady) {
                            if (tabIndex == 1) { // Text watermark
                                val paint = Paint().apply {
                                    color = textColor.toArgb()
                                    alpha = (opacity * 255).toInt()
                                    this.textSize = textSize * (targetWidth.toFloat() / page.width) // Scale text size to preview
                                    textAlign = Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                val x = canvas.width / 2f
                                val y = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)

                                canvas.save()
                                canvas.rotate(-45f, x, y)
                                canvas.drawText(watermarkText, x, y, paint)
                                canvas.restore()
                            } else { // Image watermark
                                context.contentResolver.openInputStream(imageUri!!)?.use { imageStream ->
                                    val watermarkBitmap = android.graphics.BitmapFactory.decodeStream(imageStream)
                                    val scaleFactor = scale / 100f
                                    val scaledWidth = watermarkBitmap.width * scaleFactor
                                    val scaledHeight = watermarkBitmap.height * scaleFactor

                                    val scaledBitmap = Bitmap.createScaledBitmap(watermarkBitmap, (scaledWidth * (targetWidth.toFloat() / page.width)).toInt(), (scaledHeight * (targetWidth.toFloat() / page.width)).toInt(), true)

                                    val paint = Paint().apply {
                                        alpha = (opacity * 255).toInt()
                                    }

                                    val x = (canvas.width - scaledBitmap.width) / 2f
                                    val y = (canvas.height - scaledBitmap.height) / 2f
                                    canvas.drawBitmap(scaledBitmap, x, y, paint)
                                    scaledBitmap.recycle()
                                    watermarkBitmap.recycle()
                                }
                            }
                        }
                        mutableBitmap
                    }
                }
                previewBitmap = bitmapResult
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Lỗi tạo xem trước: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isGeneratingPreview = false
            }
        }
    }

    LaunchedEffect(pdfUri, imageUri, watermarkText, opacity, scale, textSize, textColor, tabIndex) {
        generatePreview()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(8.dp))
                    BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26E2BC))
                    ) {
                        Text(if (pdfUri == null) "Chọn tệp PDF" else "Thay đổi tệp PDF")
                    }
                    pdfUri?.let { Text("Đã chọn: ${it.path}", style = MaterialTheme.typography.bodySmall) }

                    Spacer(modifier = Modifier.height(16.dp))

                    TabRow(selectedTabIndex = tabIndex) {
                        Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Hình ảnh") })
                        Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Văn bản") })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (tabIndex) {
                        0 -> {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26E2BC))
                            ) {
                                Text("Chọn ảnh logo")
                            }
                            imageUri?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Image(
                                    painter = rememberAsyncImagePainter(it),
                                    contentDescription = "Ảnh logo đã chọn",
                                    modifier = Modifier.size(100.dp).align(Alignment.CenterHorizontally)
                                )
                                Text("Đã chọn: ${it.path}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Kích thước: ${scale.toInt()}%")
                            Slider(value = scale, onValueChange = { scale = it }, valueRange = 10f..100f)
                        }
                        1 -> {
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Văn bản logo") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Kích thước chữ: ${textSize.toInt()}pt")
                            Slider(value = textSize, onValueChange = { textSize = it }, valueRange = 20f..150f)
                            Spacer(modifier = Modifier.height(16.dp))
                            ColorPicker(selectedColor = textColor, onColorSelected = { textColor = it })
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Độ mờ: ${(opacity * 100).toInt()}%")
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f)

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val isReady = pdfUri != null && (tabIndex == 0 && imageUri != null || tabIndex == 1 && watermarkText.isNotBlank())
                            if (isReady) {
                                saverLauncher.launch("watermarked-${System.currentTimeMillis()}.pdf")
                            } else {
                                Toast.makeText(context, "Vui lòng hoàn tất các lựa chọn", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = pdfUri != null && ((tabIndex == 0 && imageUri != null) || (tabIndex == 1 && watermarkText.isNotBlank())),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26E2BC))
                    ) {
                        Text("Lưu tệp PDF")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        },
        sheetPeekHeight = 90.dp,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),

        ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            scope.launch {
                                if (scaffoldState.bottomSheetState.hasExpandedState) {
                                    scaffoldState.bottomSheetState.partialExpand()
                                } 
                            }
                        }
                    )
                }
        ) {
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Xem trước logo mờ",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (pdfUri == null) {
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26E2BC))
                        ) {
                            Text("Chọn tệp PDF để bắt đầu")
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }

            if (isGeneratingPreview) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}


@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.Gray, Color.Red, Color.Blue, Color.Green, Color.Black, Color.White, Color.Yellow, Color.Cyan, Color.Magenta
    )
    Text("Màu chữ:")
    LazyRow(modifier = Modifier.padding(top = 8.dp)) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onColorSelected(color) }
            ) {
                if (color == selectedColor) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
} 
