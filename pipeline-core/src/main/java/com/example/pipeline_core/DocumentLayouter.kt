package com.example.pipeline_core

import com.example.domain.ImageRef
import com.example.domain.Imaging
import com.example.domain.Size

class DocumentLayouter(
    private val imaging: Imaging,
    private val defaultDpi: Int = 300,
    private val policy: OrientationPolicy = OrientationPolicy.PortraitPreferred,
    private val tol: Double = 0.08
) {
    /** Auto-choose A4/Letter/Legal by aspect; enforce upright orientation; fallback to generic warp. */
    fun warpAutoPaper(src: ImageRef, quad: FloatArray): ImageRef {
        val aspect = PaperGuesser.aspectFromQuad(quad)
        val guess = PaperGuesser.guessByAspect(aspect, dpi = defaultDpi, tol = tol)
        return if (guess != null) {
            val target: Size = when (guess.name) {
                "A4"     -> PagePresets.A4(guess.dpi)
                "Letter" -> PagePresets.Letter(guess.dpi)
                "Legal"  -> PagePresets.Legal(guess.dpi)
                else     -> PagePresets.A4(guess.dpi)
            }
            ImagingInterop.tryWarpToAuto(imaging, src, quad, target, policy)
                ?: imaging.warpPerspective(src, quad)
        } else {
            imaging.warpPerspective(src, quad)
        }
    }
}
