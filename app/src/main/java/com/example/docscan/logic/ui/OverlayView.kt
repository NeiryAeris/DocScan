package com.example.docscan.logic.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Transparent overlay that draws a 4-point polygon (quad).
 * You pass points in *view coordinates* (not image coords).
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.GREEN
    }
    private val path = Path()
    private var pts: List<PointF>? = null

    fun setQuad(viewPoints: List<PointF>?) {
        pts = viewPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val q = pts ?: return
        if (q.size != 4) return

        path.reset()
        path.moveTo(q[0].x, q[0].y)
        path.lineTo(q[1].x, q[1].y)
        path.lineTo(q[2].x, q[2].y)
        path.lineTo(q[3].x, q[3].y)
        path.close()
        canvas.drawPath(path, paint)
    }
}
