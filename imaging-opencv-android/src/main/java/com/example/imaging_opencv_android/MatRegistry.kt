package com.example.imaging_opencv_android

import com.example.domain.types.ImageRef
import org.opencv.core.Mat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stores Mats keyed by the *identity* of ImageRef objects (no risk if ImageRef is a data class).
 * Callers never see Mat; they only get/hold ImageRef from :domain.
 */
internal object MatRegistry {

    private data class State(val mat: Mat, val released: AtomicBoolean = AtomicBoolean(false))

    private val lock = Any()
    private val map = IdentityHashMap<ImageRef, State>()

    /** Register a Mat and return the ref you passed (for chaining). */
    fun put(ref: ImageRef, mat: Mat): ImageRef = synchronized(lock) {
        map[ref] = State(mat)
        return ref
    }

    /** Get the live Mat; throws if unknown or released. */
    fun requireMat(ref: ImageRef): Mat = synchronized(lock) {
        val st = map[ref] ?: error("Unknown ImageRef (not registered in MatRegistry)")
        if (st.released.get()) error("ImageRef already released")
        return st.mat
    }

    /**
     * Mark released, free native memory, and remove from map (idempotent).
     * Important: release native Mat OUTSIDE the lock.
     */
    fun release(ref: ImageRef) {
        val matToRelease: Mat? = synchronized(lock) {
            val st = map[ref] ?: return
            if (!st.released.compareAndSet(false, true)) return
            map.remove(ref)
            st.mat
        }
        matToRelease?.release()
    }
}
