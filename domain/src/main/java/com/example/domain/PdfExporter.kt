package com.example.domain

// Interface for exporting processed images to PDF
interface PdfExporter {
    // Convert a list of processed image references to a PDF
    fun exportToPdf(images: List<ByteArray>, filePath: String)
}