package com.example.docscan.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.docscan.App
import com.example.docscan.R
import com.example.docscan.data.UserPreferencesRepository
import com.example.docscan.ui.components.ActionGrid
import com.example.docscan.ui.components.ActionItemData
import com.example.docscan.ui.components.AppBackground
import com.example.docscan.ui.components.SectionTitle
import com.example.domain.interfaces.ocr.OcrGateway
import com.example.ocr.core.api.OcrImage
import com.example.ocr_remote.RemoteHandwritingResult
import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.SlideLayout
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


private fun backupFileToDriveIfNeeded(scope: CoroutineScope, context: Context, fileUri: Uri, defaultFileName: String, mimeType: String) {
    scope.launch(Dispatchers.IO) {
        val userPreferences = UserPreferencesRepository(context)
        if (userPreferences.isBackupEnabled.first()) {
            try {
                val driveStatus = App.driveClient.status()
                if (driveStatus.linked) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backing up to Google Drive...", Toast.LENGTH_SHORT).show()
                    }
                    val fileBytes = context.contentResolver.openInputStream(fileUri)?.readBytes()
                    if (fileBytes != null) {
                        App.driveClient.upload(fileBytes, defaultFileName, mimeType)
                    } else {
                        throw IOException("Could not read file for backup.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavHostController, ocrGateway: OcrGateway) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var extractedText by remember { mutableStateOf<String?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var pdfUriToConvertToWord by remember { mutableStateOf<Uri?>(null) }
    var pdfUriToConvertToPpt by remember { mutableStateOf<Uri?>(null) }
    var pdfUriToConvertToImage by remember { mutableStateOf<Uri?>(null) }
    var pdfUriToConvertToExcel by remember { mutableStateOf<Uri?>(null) }
    var pdfUrisToMerge by remember { mutableStateOf<List<Uri>?>(null) }
    var isHandwritingRemovalLoading by remember { mutableStateOf(false) }

    val onImageResult = remember(navController) {
        { uri: Uri? ->
            if (uri != null) {
                val encodedUri = URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8.toString())
                navController.navigate("scan?imageUri=$encodedUri")
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
                    navController.navigate("scan?pdfUri=$encodedUri")
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
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
                                docId = "transient-doc-${System.currentTimeMillis()}",
                                pageId = "transient-page-${System.currentTimeMillis()}",
                                image = ocrImage
                            )
                        }

                        extractedText = result.text.raw.ifBlank { "Không tìm thấy văn bản" }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi OCR: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    } finally {
                        isOcrLoading = false
                    }
                }
            }
        }
    }

    val handwritingRemovalImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    isHandwritingRemovalLoading = true
                    Toast.makeText(context, "Đang xóa chữ viết tay...", Toast.LENGTH_SHORT).show()
                    try {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                        val result = withContext(Dispatchers.IO) {
                            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                            }
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            val imageBytes = outputStream.toByteArray()

                            App.handwritingClient.removeHandwriting(
                                pageId = "transient-page-${System.currentTimeMillis()}",
                                imageBytes = imageBytes,
                                mimeType = "image/png",
                                strength = "high"
                            )
                        }

                        when (result) {
                            is RemoteHandwritingResult.ImageBytes -> {
                                App.previewImageBytes = result.bytes
                                App.previewTitle = "Xem trước xóa chữ viết tay"
                                App.previewMimeType = "image/png"
                                App.previewDefaultFileName = "cleaned-${System.currentTimeMillis()}.png"
                                navController.navigate("preview_screen")
                            }
                            is RemoteHandwritingResult.Error -> {
                                Toast.makeText(context, "Lỗi: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                Toast.makeText(context, "Lỗi không xác định", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Lỗi khi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isHandwritingRemovalLoading = false
                    }
                }
            }
        }
    )

    val onSignPdfResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    App.pdfToSign = uri
                    navController.navigate("signing")
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val mergedPdfSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { outputUri ->
            if (outputUri != null) {
                val sourcePdfUris = pdfUrisToMerge
                if (!sourcePdfUris.isNullOrEmpty()) {
                    scope.launch {
                        isConverting = true
                        Toast.makeText(context, "Đang hợp nhất các tệp...", Toast.LENGTH_SHORT).show()
                        try {
                            withContext(Dispatchers.IO) {
                                val outputStream = context.contentResolver.openOutputStream(outputUri)
                                    ?: throw IOException("Không thể tạo tệp PDF đầu ra.")
                                val document = Document()
                                val pdfCopy = PdfCopy(document, outputStream)
                                document.open()
                                sourcePdfUris.forEach { uri ->
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val reader = PdfReader(inputStream)
                                    for (i in 1..reader.numberOfPages) {
                                        pdfCopy.addPage(pdfCopy.getImportedPage(reader, i))
                                    }
                                    pdfCopy.freeReader(reader)
                                    reader.close()
                                }
                                document.close()
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Các tệp đã được hợp nhất thành công", Toast.LENGTH_LONG).show()
                                backupFileToDriveIfNeeded(scope, context, outputUri, "merged-${System.currentTimeMillis()}.pdf", "application/pdf")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Lỗi khi hợp nhất tệp: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            isConverting = false
                            pdfUrisToMerge = null
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Đã hủy lưu", Toast.LENGTH_SHORT).show()
                pdfUrisToMerge = null
            }
        }
    )

    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                if (uris.size < 2) {
                    Toast.makeText(context, "Vui lòng chọn ít nhất 2 tệp để hợp nhất", Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                try {
                    uris.forEach { uri ->
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    }
                    pdfUrisToMerge = uris
                    Toast.makeText(context, "${uris.size} tệp được chọn. Bây giờ, hãy chọn nơi lưu tệp đã hợp nhất.", Toast.LENGTH_LONG).show()
                    val defaultFileName = "merged-${System.currentTimeMillis()}.pdf"
                    mergedPdfSaverLauncher.launch(defaultFileName)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val imageSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { zipFileUri ->
            if (zipFileUri != null) {
                val sourcePdfUri = pdfUriToConvertToImage
                if (sourcePdfUri != null) {
                    scope.launch {
                        isConverting = true
                        Toast.makeText(context, "Đang chuyển đổi và nén...", Toast.LENGTH_SHORT).show()
                        try {
                            withContext(Dispatchers.IO) {
                                val pfd = context.contentResolver.openFileDescriptor(sourcePdfUri, "r")
                                    ?: throw IOException("Không thể mở tệp PDF.")
                                val zipOutputStream = ZipOutputStream(context.contentResolver.openOutputStream(zipFileUri))

                                pfd.use { parcelFileDescriptor ->
                                    val renderer = PdfRenderer(parcelFileDescriptor)
                                    val pageCount = renderer.pageCount
                                    if (pageCount > 10) { // Add a limit to avoid memory issues
                                        withContext(Dispatchers.Main){
                                            Toast.makeText(context, "Chỉ hỗ trợ chuyển đổi tối đa 10 trang.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    zipOutputStream.use { zos ->
                                        for (i in 0 until minOf(pageCount, 10)) {
                                            val page = renderer.openPage(i)
                                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            page.close()

                                            val entry = ZipEntry("page_${i + 1}.png")
                                            zos.putNextEntry(entry)
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, zos)
                                            zos.closeEntry()
                                            bitmap.recycle()
                                        }
                                    }
                                    renderer.close()
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Tệp ZIP đã được lưu thành công", Toast.LENGTH_LONG).show()
                                backupFileToDriveIfNeeded(scope, context, zipFileUri, "converted-images-${System.currentTimeMillis()}.zip", "application/zip")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main){
                                Toast.makeText(context, "Lỗi khi lưu tệp ZIP: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        } finally {
                            isConverting = false
                            pdfUriToConvertToImage = null
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Đã hủy lưu", Toast.LENGTH_SHORT).show()
                pdfUriToConvertToImage = null
            }
        }
    )

    val onPdfToImageResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    pdfUriToConvertToImage = uri
                    Toast.makeText(context, "Đã chọn tệp PDF. Bây giờ, hãy chọn nơi lưu tệp ZIP.", Toast.LENGTH_LONG).show()
                    val defaultFileName = "converted-images-${System.currentTimeMillis()}.zip"
                    imageSaverLauncher.launch(defaultFileName)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val excelSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        onResult = { savedExcelUri ->
            if (savedExcelUri != null) {
                val sourcePdfUri = pdfUriToConvertToExcel
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
                                val workbook = XSSFWorkbook()

                                for (i in 1..n) {
                                    val sheet = workbook.createSheet("Trang $i")
                                    val text = PdfTextExtractor.getTextFromPage(reader, i)
                                    text.lines().forEachIndexed { rowIndex, line ->
                                        val row = sheet.createRow(rowIndex)
                                        row.createCell(0).setCellValue(line)
                                    }
                                }
                                reader.close()

                                val outputStream = context.contentResolver.openOutputStream(savedExcelUri)
                                    ?: throw IOException("Không thể tạo tệp Excel đầu ra.")

                                outputStream.use { out ->
                                    workbook.write(out)
                                }
                                workbook.close()
                            }
                            Toast.makeText(context, "Tệp đã được lưu thành công", Toast.LENGTH_LONG).show()
                            backupFileToDriveIfNeeded(scope, context, savedExcelUri, "converted-${System.currentTimeMillis()}.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Lỗi khi lưu tệp: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isConverting = false
                            pdfUriToConvertToExcel = null
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Đã hủy lưu", Toast.LENGTH_SHORT).show()
                pdfUriToConvertToExcel = null
            }
        }
    )

    val onPdfToExcelResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    pdfUriToConvertToExcel = uri
                    Toast.makeText(context, "Đã chọn tệp PDF. Bây giờ, hãy chọn nơi lưu tệp Excel.", Toast.LENGTH_LONG).show()
                    val defaultFileName = "converted-${System.currentTimeMillis()}.xlsx"
                    excelSaverLauncher.launch(defaultFileName)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )


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

                                for (i in 1..n) {
                                    val text = PdfTextExtractor.getTextFromPage(reader, i)
                                    text.lines().forEach { line ->
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
                            backupFileToDriveIfNeeded(scope, context, savedDocxUri, "converted-${System.currentTimeMillis()}.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Lỗi khi lưu tệp: ${e.message}", Toast.LENGTH_LONG).show()
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

    val pptSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        onResult = { savedPptxUri ->
            if (savedPptxUri != null) {
                val sourcePdfUri = pdfUriToConvertToPpt
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
                                val ppt = XMLSlideShow()

                                for (i in 1..n) {
                                    val text = PdfTextExtractor.getTextFromPage(reader, i)

                                    val slideMaster = ppt.slideMasters[0]
                                    val slideLayout = slideMaster.getLayout(SlideLayout.TITLE_AND_CONTENT)
                                    val slide = ppt.createSlide(slideLayout)

                                    val titleShape = slide.getPlaceholder(0) as XSLFTextShape
                                    titleShape.text = "Trang $i"

                                    val contentShape = slide.getPlaceholder(1) as XSLFTextShape
                                    contentShape.clearText()
                                    contentShape.text = text
                                }
                                reader.close()

                                val outputStream = context.contentResolver.openOutputStream(savedPptxUri)
                                    ?: throw IOException("Không thể tạo tệp PPT đầu ra.")

                                outputStream.use { out ->
                                    ppt.write(out)
                                }
                                ppt.close()
                            }
                            Toast.makeText(context, "Tệp đã được lưu thành công", Toast.LENGTH_LONG).show()
                            backupFileToDriveIfNeeded(scope, context, savedPptxUri, "converted-${System.currentTimeMillis()}.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, "Lỗi khi lưu tệp: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isConverting = false
                            pdfUriToConvertToPpt = null
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Đã hủy lưu", Toast.LENGTH_SHORT).show()
                pdfUriToConvertToPpt = null
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
                    val defaultFileName = "converted-${System.currentTimeMillis()}.docx"
                    wordSaverLauncher.launch(defaultFileName)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val onPdfToPptResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    pdfUriToConvertToPpt = uri
                    Toast.makeText(context, "Đã chọn tệp PDF. Bây giờ, hãy chọn nơi lưu tệp PPT.", Toast.LENGTH_LONG).show()
                    val defaultFileName = "converted-${System.currentTimeMillis()}.pptx"
                    pptSaverLauncher.launch(defaultFileName)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi quyền: ${e.message}", Toast.LENGTH_LONG).show()
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

    val scanActions = remember(navController, ocrImagePickerLauncher) {
        listOf(
            ActionItemData(R.drawable.quet_tai_lieu, "Quét tài liệu") { navController.navigate("scan") },
            ActionItemData(R.drawable.the_id, "Thẻ ID") { navController.navigate("id_card_scan") },
            ActionItemData(R.drawable.trich_xuat_van_ban, "Trích xuất văn bản") { ocrImagePickerLauncher.launch("image/*") }
        )
    }
    val importActions = remember(imagePickerLauncher, documentPickerLauncher) {
        listOf(
            ActionItemData(R.drawable.nhap_anh, "Nhập ảnh") { imagePickerLauncher.launch("image/*") },
            ActionItemData(R.drawable.nhap_tep_tin, "Nhập tệp tin") { documentPickerLauncher.launch(arrayOf("application/pdf")) }
        )
    }
    val convertActions = remember(onPdfToImageResultLauncher, onPdfToWordResult, onPdfToPptResult, onPdfToExcelResultLauncher) {
        listOf(
            ActionItemData(R.drawable.thanh_word, "Thành Word") { onPdfToWordResult.launch(arrayOf("application/pdf")) },
            ActionItemData(R.drawable.thanh_ppt, "Thành PPT") { onPdfToPptResult.launch(arrayOf("application/pdf")) },
            ActionItemData(R.drawable.thanh_excel, "Thành Excel") { onPdfToExcelResultLauncher.launch(arrayOf("application/pdf")) },
            ActionItemData(R.drawable.thanh_anh, "PDF thành ảnh") { onPdfToImageResultLauncher.launch(arrayOf("application/pdf")) }
        )
    }
    val editActions = remember(context, onSignPdfResult, navController, mergePdfLauncher) {
        listOf(
            ActionItemData(R.drawable.ky_ten, "Ký tên") { onSignPdfResult.launch(arrayOf("application/pdf")) },
            ActionItemData(R.drawable.them_logo_mo, "Thêm logo mờ") { navController.navigate("add_watermark") },
            ActionItemData(R.drawable.xoa_thong_minh, "Xóa thông minh") { handwritingRemovalImagePickerLauncher.launch("image/*") },
            ActionItemData(R.drawable.hop_nhat_tep_tin, "Hợp nhất tập tin") { mergePdfLauncher.launch(arrayOf("application/pdf")) },
            ActionItemData(R.drawable.ic_ai_assistant, "Trợ lý AI") {
                if (App.isUserLoggedIn) {
                    navController.navigate("chat")
                } else {
                    Toast.makeText(context, "Vui lòng đăng nhập để sử dụng Trợ lý AI", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val onDismissDialog = remember { { extractedText = null } }

    AppBackground {
        LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
            item { SectionTitle("Quét") }
            item { ActionGrid(scanActions) }

            item { SectionTitle("Nhập") }
            item { ActionGrid(importActions) }

            item { SectionTitle("Chuyển đổi") }
            item { ActionGrid(convertActions) }

            item { SectionTitle("Sửa") }
            item { ActionGrid(editActions) }
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

        if (isOcrLoading || isConverting || isHandwritingRemovalLoading) {
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
