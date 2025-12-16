package com.example.pipeline_core.scan

import com.example.domain.interfaces.Imaging
import com.example.pipeline_core.DocumentLayouter
import com.example.pipeline_core.ImagingInterop
import com.example.pipeline_core.OrientationPolicy
import com.example.pipeline_core.PaperGuesser

class CamScanPipeline(
    private val imaging: Imaging,
    private val defaultDpi: Int = 150,
    private val policy: OrientationPolicy = OrientationPolicy.PortraitPreferred,
    private val tol: Double = 0.08
) {
    data class Options(
        val enhanceMode: String = "auto_pro",
        val jpegQuality: Int = 85,
        val includeOverlay: Boolean = false,
        val overlayQuality: Int = 80
    )

    data class Result(
        val outJpeg: ByteArray,
        val quad: FloatArray,
        val paperName: String?,       // "A4" / "Letter" / "Legal" / null
        val overlayJpeg: ByteArray?   // optional debug
    )

    /**
     * Input: camera-like JPEG bytes
     * Output: processed JPEG bytes + quad + optional overlay
     *
     * Throws if no quad is detected (caller decides how to handle).
     */
    fun processJpeg(cameraJpeg: ByteArray, options: Options = Options()): Result {
        val layouter = DocumentLayouter(
            imaging = imaging,
            defaultDpi = defaultDpi,
            policy = policy,
            tol = tol
        )

        val src = imaging.fromBytes(cameraJpeg)
        val quad = imaging.detectDocumentQuad(src) ?: run {
            ImagingInterop.tryRelease(imaging, src)
            error("No document quad detected")
        }

        val paper = PaperGuesser.guessByAspect(
            aspect = PaperGuesser.aspectFromQuad(quad),
            dpi = defaultDpi,
            tol = tol
        )?.name

        val warped = layouter.warpAutoPaper(src, quad)
        val enhanced = imaging.enhanceDocument(warped, options.enhanceMode)
        val outJpeg = imaging.toJpeg(enhanced, options.jpegQuality)

        val overlay = if (options.includeOverlay) {
            ImagingInterop.tryDrawOverlayJpeg(imaging, src, quad, options.overlayQuality)
        } else null

        ImagingInterop.tryRelease(imaging, src, warped, enhanced)

        return Result(
            outJpeg = outJpeg,
            quad = quad,
            paperName = paper,
            overlayJpeg = overlay
        )
    }
}