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
            var target: Size = when (guess.name) {
                "A4"     -> PagePresets.A4(guess.dpi)
                "Letter" -> PagePresets.Letter(guess.dpi)
                "Legal"  -> PagePresets.Legal(guess.dpi)
                else     -> PagePresets.A4(guess.dpi)
            }
            ImagingInterop.tryWarpToAuto(imaging, src, quad, target, policy)
                ?: imaging.warpPerspective(src, quad)
            // --- keep sideways if the detected quad is landscape ---
                        // Compute document orientation from quad geometry
            fun dist(ax: Float, ay: Float, bx: Float, by: Float): Double {
                val dx = (bx - ax).toDouble(); val dy = (by - ay).toDouble()
                return kotlin.math.hypot(dx, dy)
            }
            val w = dist(quad[0], quad[1], quad[2], quad[3]) // TL->TR
            val h = dist(quad[0], quad[1], quad[6], quad[7]) // TL->BL
            val isLandscapeDoc = w > h

            // If detected doc is landscape, flip the target to landscape (no rotation, just swap dims)
            if (isLandscapeDoc && target.height > target.width) {
                target = Size(target.height, target.width)
            }

            // Prefer the explicit-size warp (sideways-safe), else generic
            ImagingInterop.tryWarpToAuto(imaging, src, quad, target, policy)
                ?: run {
                    // If no 'fourPointWarpToAuto' or 'fourPointWarpTo' exist, fallback to generic
                    // NOTE: tryWarpToAuto already tries fourPointWarpTo(target) before null
                    imaging.warpPerspective(src, quad)
                }
        } else {
            imaging.warpPerspective(src, quad)
        }
    }
}