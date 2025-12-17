package com.example.docscan.logic.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileOpener {

    /**
     * Opens a PDF file using an external PDF viewer app.
     *
     * @param context The context needed to create the Intent and get the FileProvider authority.
     * @param file The PDF file to open.
     */
    fun openPdf(context: Context, file: File) {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer app found.", Toast.LENGTH_SHORT).show()
        }
    }
}
