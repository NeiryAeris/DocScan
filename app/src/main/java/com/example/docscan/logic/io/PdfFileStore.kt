package com.example.docscan.logic.io

import android.content.Context
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream

class PdfFileStore {
    fun exportSavedDocToPdf(context: Context, docId: String): File {
        val docDir = File(context.getExternalFilesDir(null), "documents/$docId")
        val pagesDir = File(docDir, "pages")
        require(pagesDir.exists()) { "Missing pagesDir: ${pagesDir.absolutePath}" }

        val pageFiles = pagesDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true))
        }?.sortedBy { it.name } ?: emptyList()

        require(pageFiles.isNotEmpty()) { "No page images found in: ${pagesDir.absolutePath}" }

        val outPdf = File(docDir, "$docId.pdf")

        val pdf = Document(PageSize.A4)
        PdfWriter.getInstance(pdf, FileOutputStream(outPdf))
        pdf.open()

        pageFiles.forEach { file ->
            val img = Image.getInstance(file.absolutePath)

            // Fit image into A4 page while preserving aspect ratio
            img.scaleToFit(PageSize.A4.width, PageSize.A4.height)
            img.setAbsolutePosition(
                (PageSize.A4.width - img.scaledWidth) / 2f,
                (PageSize.A4.height - img.scaledHeight) / 2f
            )

            pdf.newPage()
            pdf.add(img)
        }

        pdf.close()
        return outPdf
    }
}
