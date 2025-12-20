package com.example.docscan.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.SectionTitle
import com.example.domain.interfaces.ocr.OcrGateway
import com.example.ocr.core.api.OcrImage
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.LocationTextExtractionStrategy
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavHostController, ocrGateway: OcrGateway) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var extractedText by remember { mutableStateOf<String?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var pdfUriToConvertToWord by remember { mutableStateOf<Uri?>(null) }

    val onImageResult = remember(navController) {
        { uri: Uri? ->
            if (uri != null) {
                val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                navController.navigate("scan?imageUri=${'$'}encodedUri")
            }
        }
    }

    val onDocumentResult = remember(context, navController) {
        { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                    navController.navigate("scan?pdfUri=${'$'}encodedUri")
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${'$'}e.message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val onOcrImageResult = remember(context, scope, ocrGateway) {
        { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    isOcrLoading = true
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }
                            val bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
                            bitmap.copyPixelsToBuffer(buffer)

                            val ocrImage = OcrImage.Rgba8888(
                                bytes = buffer.array(),
                                width = bitmap.width,
                                height = bitmap.height,
                                rowStride = bitmap.rowBytes
                            )

                            ocrGateway.recognize(
                                docId = "transient-doc-${'$'}System.currentTimeMillis()",
                                pageId = "transient-page-${'$'}System.currentTimeMillis()",
                                image = ocrImage
                            )
                        }

                        extractedText = if (result.text.raw.isNotBlank()) {
                            result.text.raw
                        } else {
                            "Không tìm thấy văn bản"
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi OCR: ${'$'}e.message", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    } finally {
                        isOcrLoading = false
                    }
                }
            }
        }
    }

    val onPdfToImageResult = remember(context, navController) {
        { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                    navController.navigate("pdf_to_image/${'$'}encodedUri")
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${'$'}e.message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val wordSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        onResult = { savedDocxUri ->
            if (savedDocxUri != null) {
                val sourcePdfUri = pdfUriToConvertToWord
                if (sourcePdfUri != null) {
                    scope.launch {
                        isConverting = true
                        Toast.makeText(context, "Đang chuyển đổi...", Toast.LENGTH_SHORT).show()
                        try {
                            withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(sourcePdfUri)
                                    ?: throw IOException("Không thể mở tệp PDF đầu vào.")
                                val reader = PdfReader(inputStream)
                                val n = reader.numberOfPages
                                val document = XWPFDocument()

                                for (i in 0 until n) {
                                    val strategy = LocationTextExtractionStrategy()
                                    val text = PdfTextExtractor.getTextFromPage(reader, i + 1, strategy)
                                    text.split('\n').forEach { line ->
                                        val para = document.createParagraph()
                                        para.createRun().setText(line)
                                    }
                                }
                                reader.close()

                                val outputStream = context.contentResolver.openOutputStream(savedDocxUri)
                                    ?: throw IOException("Không thể tạo tệp Word đầu ra.")

                                outputStream.use { out ->
                                    document.write(out)
                                }
                                document.close()
                            }
                            Toast.makeText(context, "Tệp đã được lưu thành công", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Lỗi khi lưu tệp: ${'$'}e.message", Toast.LENGTH_LONG).show()
                        } finally {
                            isConverting = false
                            pdfUriToConvertToWord = null
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Đã hủy lưu", Toast.LENGTH_SHORT).show()
                pdfUriToConvertToWord = null
            }
        }
    )

    val onPdfToWordResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    pdfUriToConvertToWord = uri
                    Toast.makeText(context, "Đã chọn tệp PDF. Bây giờ, hãy chọn nơi lưu tệp Word.", Toast.LENGTH_LONG).show()
                    val defaultFileName = "converted-${'$'}System.currentTimeMillis()}.docx"
                    wordSaverLauncher.launch(defaultFileName)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${'$'}e.message", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = onImageResult
    )

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onDocumentResult
    )

    val ocrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = onOcrImageResult
    )

    val pdfToImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onPdfToImageResult
    )

    val scanActions = remember(navController, ocrImagePickerLauncher) {
        listOf(
            ActionItemData(Icons.Default.Scanner, "Quét tài liệu") { navController.navigate("scan") },
            ActionItemData(Icons.Default.CreditCard, "Thẻ ID") { navController.navigate("id_card_scan") },
            ActionItemData(Icons.Default.TextFields, "Trích xuất văn bản") { ocrImagePickerLauncher.launch("image/*") }
        )
    }
    val importActions = remember(imagePickerLauncher, documentPickerLauncher) {
        listOf(
            ActionItemData(Icons.Default.Image, "Nhập ảnh") { imagePickerLauncher.launch("image/*") },
            ActionItemData(Icons.Default.UploadFile, "Nhập tệp tin") { documentPickerLauncher.launch(arrayOf("application/pdf")) }
        )
    }
    val convertActions = remember(navController, pdfToImagePickerLauncher, onPdfToWordResult) {
        listOf(
            ActionItemData(Icons.Default.Description, "Thành Word") { onPdfToWordResult.launch(arrayOf("application/pdf")) },
            ActionItemData(Icons.Default.Slideshow, "Thành PPT") { navController.navigate("pdf_tools") },
            ActionItemData(Icons.Default.Photo, "PDF thành ảnh") { pdfToImagePickerLauncher.launch(arrayOf("application/pdf")) }
        )
    }
    val editActions = remember(context) {
        listOf(
            ActionItemData(Icons.Default.Draw, "Ký tên") { Toast.makeText(context, "Chức năng đang được phát triển", Toast.LENGTH_SHORT).show() },
            ActionItemData(Icons.Default.BrandingWatermark, "Thêm logo mờ") { Toast.makeText(context, "Chức năng đang được phát triển", Toast.LENGTH_SHORT).show() },
            ActionItemData(Icons.Default.AutoFixHigh, "Xóa thông minh") { Toast.makeText(context, "Chức năng đang được phát triển", Toast.LENGTH_SHORT).show() },
            ActionItemData(Icons.Default.MergeType, "Hợp nhất tập tin") { Toast.makeText(context, "Chức năng đang được phát triển", Toast.LENGTH_SHORT).show() }
        )
    }

    val onDismissDialog = remember { { extractedText = null } }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
                item { SectionTitle("Quét") }
                item { ActionGrid(scanActions) }

                item { SectionTitle("Nhập") }
                item { ActionGrid(importActions, columnCount = 2) }

                item { SectionTitle("Chuyển đổi") }
                item { ActionGrid(convertActions) }

                item { SectionTitle("Sửa") }
                item { ActionGrid(editActions) }
            }
        }

        if (extractedText != null) {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("Văn bản được trích xuất") },
                text = {
                    SelectionContainer {
                        Text(extractedText ?: "")
                    }
                },
                confirmButton = {
                    Button(onClick = onDismissDialog) {
                        Text("OK")
                    }
                }
            )
        }

        if (isOcrLoading || isConverting) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Preview(name = "ToolsScreen Preview", showBackground = true)
@Composable
fun Preview_ToolsScreen() {
    val navController = rememberNavController()
    val ocrGateway = object : OcrGateway {
        override suspend fun recognize(docId: String, pageId: String, image: OcrImage, policy: com.example.domain.types.ocr.OcrPolicy): OcrGateway.Result {
            return OcrGateway.Result(
                page = com.example.ocr.core.api.OcrPageResult(0, "", emptyList()),
                text = com.example.domain.types.ocr.OcrText("", "", ""),
                words = emptyList()
            )
        }
    }
    ToolsScreen(navController = navController, ocrGateway = ocrGateway)
}
