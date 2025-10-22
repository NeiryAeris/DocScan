package com.example.domain

// Interface for image processing
interface Imaging {
    // Convert a byte array (JPEG) to an image reference
    fun fromBytes(jpeg: ByteArray): ImageRef

    // Convert an image reference back to a JPEG byte array
    fun toJpeg(img: ImageRef, quality: Int = 85): ByteArray

    // Returns 8 floats (x0,y0,x1,y1,x2,y2,x3,y3) in clockwise order, or null if not found.
    fun detectDocumentQuad(src: ImageRef): FloatArray?

    // Apply a perspective warp to an image
    fun warpPerspective(src: ImageRef, quad: FloatArray): ImageRef

    // Light deskewing of an image
    fun deskewLight(src: ImageRef, maxDeg: Double = 3.0): ImageRef

    // Normalize the illumination in an image
    fun illuminationNormalize(gray: ImageRef, medianK: Int): ImageRef

    // Denoise an image
    fun denoise(src: ImageRef): ImageRef

    // Apply CLAHE (Contrast Limited Adaptive Histogram Equalization)
    fun clahe(gray: ImageRef, clip: Double, tiles: Int): ImageRef

    // Apply unsharp masking to an image
    fun unsharp(src: ImageRef, sigma: Double, amount: Double): ImageRef

    // Convert the image to black and white using adaptive thresholding
    fun toBWAdaptive(src: ImageRef): ImageRef

    // Release resources associated with an image
//    fun release(img: ImageRef)

    fun resize(src: ImageRef, maxSide: Double = 1000.0): ImageRef
    fun grayscale(src: ImageRef): ImageRef
    fun blur(src: ImageRef, kernelSize: Size = Size(5, 5)): ImageRef
    fun threshold(src: ImageRef, blur: ImageRef): ImageRef
    fun canny(src: ImageRef, threshold1: Double = 50.0, threshold2: Double = 150.0): ImageRef
    fun morphologyEx(src: ImageRef, kernel: ImageRef): ImageRef
    fun findContours(src: ImageRef): List<List<Point>> // Replace MatOfPoint with List<Point>
    fun fourPointWarp(src: ImageRef, quad: Array<Point>): ImageRef
    fun enhanceDocument(src: ImageRef, mode: String): ImageRef
}