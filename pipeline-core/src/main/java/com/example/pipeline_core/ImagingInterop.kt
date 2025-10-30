package com.example.pipeline_core

import com.example.domain.ImageRef
import com.example.domain.Imaging
import com.example.domain.Point
import com.example.domain.Size

internal object ImagingInterop {

    /** Try to call OpenCvImaging.fourPointWarpToAuto(...). Returns null if not available. */
    fun tryWarpToAuto(
        imaging: Imaging,
        src: ImageRef,
        quad: FloatArray,
        target: Size,
        policy: OrientationPolicy
    ): ImageRef? {
        val cls = imaging::class.java
        val pts = arrayOf(
            Point(quad[0].toDouble(), quad[1].toDouble()),
            Point(quad[2].toDouble(), quad[3].toDouble()),
            Point(quad[4].toDouble(), quad[5].toDouble()),
            Point(quad[6].toDouble(), quad[7].toDouble())
        )
        // Prefer fourPointWarpToAuto if present
        cls.methods.firstOrNull { it.name == "fourPointWarpToAuto" && it.parameterTypes.size == 4 }
            ?.let { m -> return m.invoke(imaging, src, pts, target, policy) as ImageRef }
        // Fallback: fixed size without auto-rotate
        cls.methods.firstOrNull { it.name == "fourPointWarpTo" && it.parameterTypes.size == 3 }
            ?.let { m -> return m.invoke(imaging, src, pts, target) as ImageRef }
        return null
    }

    /** Try to call drawQuadOverlayJpeg if available. Else return null. */
    fun tryDrawOverlayJpeg(imaging: Imaging, src: ImageRef, quad: FloatArray, quality: Int = 80): ByteArray? {
        val cls = imaging::class.java
        cls.methods.firstOrNull { it.name == "drawQuadOverlayJpeg" && it.parameterTypes.size in 2..3 }
            ?.let { m ->
                return if (m.parameterTypes.size == 2)
                    m.invoke(imaging, src, quad) as ByteArray
                else
                    m.invoke(imaging, src, quad, quality) as ByteArray
            }
        return null
    }

    /** Try to call release(ImageRef) if the impl supports it. Safe no-op otherwise. */
    fun tryRelease(imaging: Imaging, vararg refs: ImageRef?) {
        val method = imaging::class.java.methods.firstOrNull {
            it.name == "release" && it.parameterTypes.size == 1
        } ?: return
        refs.filterNotNull().forEach { method.invoke(imaging, it) }
    }
}
