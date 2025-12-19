package com.example.docscan.logic.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a document file stored in the app's public directory.
 */
data class DocumentFile(
    val file: File,
    val name: String,
    val formattedDate: String,
    val pageCount: Int // Placeholder, actual page count might need to be extracted from PDF
)

object AppStorage {

    private const val ROOT_DIR_NAME = "DocscanFile"

    /**
     * Returns the root directory for all app-related public files.
     * This will be a folder named "DocscanFile" in the public Documents directory.
     * It will be created if it doesn't exist.
     */
    suspend fun getPublicAppDir(): File? = withContext(Dispatchers.IO) {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir == null || (!documentsDir.exists() && !documentsDir.mkdirs())) {
            return@withContext null
        }
        val appDir = File(documentsDir, ROOT_DIR_NAME)
        try {
            if (!appDir.exists()) {
                if (appDir.mkdirs()) appDir else null
            } else {
                appDir
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lists all PDF document files from the public app directory, sorted by modification date (newest first).
     *
     * @return A list of [DocumentFile] objects.
     */
    suspend fun listPdfDocuments(): List<DocumentFile> = withContext(Dispatchers.IO) {
        val appDir = getPublicAppDir() ?: return@withContext emptyList()

        val pdfs = appDir.listFiles { _, name -> name.lowercase(Locale.ROOT).endsWith(".pdf") }
            ?: return@withContext emptyList()

        // Sort by last modified date, newest first
        pdfs.sortByDescending { it.lastModified() }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        pdfs.map {
            DocumentFile(
                file = it,
                name = it.nameWithoutExtension,
                formattedDate = dateFormat.format(Date(it.lastModified())),
                pageCount = 1 // TODO: Implement actual PDF page count extraction if needed
            )
        }
    }

    /**
     * Deletes a document file.
     *
     * @param documentFile The document to delete.
     * @return True if the file was deleted, false otherwise.
     */
    suspend fun deleteDocument(documentFile: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        try {
            documentFile.file.delete()
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Renames a document file.
     *
     * @param documentFile The document to rename.
     * @param newName The new name for the document (without extension).
     * @return True if the file was renamed, false otherwise.
     */
    suspend fun renameDocument(documentFile: DocumentFile, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val newFile = File(documentFile.file.parent, "$newName.pdf")
            documentFile.file.renameTo(newFile)
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates a new PDF file from a list of image URIs.
     */
    suspend fun createPdfFromImages(context: Context, imageUris: List<Uri>): File? = withContext(Dispatchers.IO) {
        val appDir = getPublicAppDir() ?: return@withContext null
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val pdfFile = File(appDir, "SCAN_$timeStamp.pdf")

        try {
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(pdfFile))
            document.open()

            for (uri in imageUris) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val byteArray = stream.toByteArray()
                    val image = Image.getInstance(byteArray)

                    // Scale image to fit page
                    image.scaleToFit(PageSize.A4.width - document.leftMargin() - document.rightMargin(), PageSize.A4.height - document.topMargin() - document.bottomMargin())
                    document.add(image)
                }
            }
            document.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            // Clean up if something went wrong
            if (pdfFile.exists()) {
                pdfFile.delete()
            }
            null
        }
    }


    suspend fun convertPdfToImages(context: Context, documentFile: DocumentFile): Boolean = withContext(Dispatchers.IO) {
        val appDir = getPublicAppDir() ?: return@withContext false
        val imageDir = File(appDir, documentFile.name)
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }

        try {
            val parcelFileDescriptor = ParcelFileDescriptor.open(documentFile.file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(parcelFileDescriptor)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imageFile = File(imageDir, "${documentFile.name}_page_${i + 1}.png")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            renderer.close()
            parcelFileDescriptor.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}