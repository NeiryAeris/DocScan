package com.example.docscan.logic.io

import android.content.Context
import android.graphics.BitmapFactory
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream

class PdfFileStore(private val context: Context) {

    fun createPdfFromImages(imageFiles: List<File>, fileName: String = "scanned_document.pdf"): File {
        val pdfFile = File(context.getExternalFilesDir(null), fileName)
        val document = Document(PageSize.A4)
        val writer = PdfWriter.getInstance(document, FileOutputStream(pdfFile))
        document.open()

        imageFiles.forEach { file ->
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            val img = Image.getInstance(file.absolutePath)
            val scale = minOf(
                PageSize.A4.width / img.width,
                PageSize.A4.height / img.height
            )
            img.scalePercent(scale * 100)
            img.alignment = Image.ALIGN_CENTER
            document.add(img)
            document.newPage()
            bmp.recycle()
        }

        document.close()
        writer.close()
        return pdfFile
    }
}
