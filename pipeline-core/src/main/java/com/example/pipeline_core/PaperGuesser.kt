package com.example.pipeline_core

data class PaperGuess(val name: String, val dpi: Int)

object PaperGuesser {
    // portrait ratios
    private const val R_A4     = 297.0 / 210.0   // ≈1.4142
    private const val R_LETTER = 11.0 / 8.5      // ≈1.2941
    private const val R_LEGAL  = 14.0 / 8.5      // ≈1.6471

    /** aspect ≥ 1: longerSide / shorterSide */
    fun guessByAspect(aspect: Double, dpi: Int = 300, tol: Double = 0.08): PaperGuess? {
        fun near(a: Double, b: Double) = kotlin.math.abs(a - b) / b <= tol
        return when {
            near(aspect, R_A4)     -> PaperGuess("A4", dpi)
            near(aspect, R_LETTER) -> PaperGuess("Letter", dpi)
            near(aspect, R_LEGAL)  -> PaperGuess("Legal", dpi)
            else -> null
        }
    }

    /** aspect from quad floats (x0,y0,x1,y1,x2,y2,x3,y3), clockwise */
    fun aspectFromQuad(quad: FloatArray): Double {
        require(quad.size == 8) { "quad must have 8 floats" }
        fun d(ax: Float, ay: Float, bx: Float, by: Float): Double {
            val dx = (bx - ax).toDouble(); val dy = (by - ay).toDouble()
            return kotlin.math.hypot(dx, dy)
        }
        val w = d(quad[0], quad[1], quad[2], quad[3]) // TL->TR
        val h = d(quad[0], quad[1], quad[6], quad[7]) // TL->BL
        val longer = kotlin.math.max(w, h)
        val shorter = kotlin.math.min(w, h).coerceAtLeast(1.0)
        return longer / shorter
    }
}
