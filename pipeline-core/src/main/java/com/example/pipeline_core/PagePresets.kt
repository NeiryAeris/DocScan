package com.example.pipeline_core

import com.example.domain.types.Size

private fun mmToPx(mm: Double, dpi: Int) = ((mm / 25.4) * dpi).toInt()

object PagePresets {
    fun A4(dpi: Int)     = Size(mmToPx(210.0, dpi), mmToPx(297.0, dpi))
    fun Letter(dpi: Int) = Size((8.5 * dpi).toInt(), (11.0 * dpi).toInt())
    fun Legal(dpi: Int)  = Size((8.5 * dpi).toInt(), (14.0 * dpi).toInt())
}
