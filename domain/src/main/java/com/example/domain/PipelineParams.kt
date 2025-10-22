package com.example.domain

// PipelineParams class to hold configuration parameters for image processing
data class PipelineParams(
    val illumKernel: Int = 81,  // Kernel size for illumination correction
    val claheClip: Double = 3.0, // CLAHE clip limit
    val claheTiles: Int = 8,    // CLAHE tile grid size
    val unsharpSigma: Double = 1.2, // Sigma for unsharp masking
    val unsharpAmount: Double = 0.7, // Amount for unsharp masking
    val bwMode: Boolean = false // Whether to convert the image to black and white
)