package com.example.imaging_opencv_android

data class DetectParams(
    val downscaleMaxSide: Int = 1000,
    val areaMinRatio: Double = 0.02,     // 2% of image area
    val areaMaxRatio: Double = 0.90,     // ignore near-full-frame
    val topN: Int = 15,
    val approxEpsRatio: Double = 0.08,   // polyDP epsilon ratio
    val blurK: Int = 5,                  // Gaussian ksize for pre-blur
    val closeK: Int = 7,                 // morph CLOSE kernel (square)
    val canny1: Double = 50.0,
    val canny2: Double = 150.0
)

data class AutoProParams(
    val claheClip: Double = 2.0,
    val claheTiles: Int = 4,
    val unsharpA: Double = 1.6,          // addWeighted alpha
    val unsharpB: Double = -0.6,         // addWeighted beta
    val gamma: Double = 0.85,
    val medianKFactor: Int = 20          // minDim / factor -> odd
)

data class ColorProParams(
    val denoiseH: Float = 3f,
    val blurSigma: Double = 1.0,
    val unsharpA: Double = 1.4,
    val unsharpB: Double = -0.4,
    val maskBlockFactor: Int = 40,       // minDim / factor -> odd
    val maskC: Double = 10.0,
    val maskCloseK: Int = 5
)

data class BwProParams(
    val medKFactor: Int = 20,            // minDim / factor -> odd
    val blockFactor: Int = 32,           // minDim / factor -> odd
    val c: Double = 10.0
)

data class EnhanceParams(
    val autoPro: AutoProParams = AutoProParams(),
    val colorPro: ColorProParams = ColorProParams(),
    val bwPro: BwProParams = BwProParams()
)

data class ImagingConfig(
    val detect: DetectParams = DetectParams(),
    val enhance: EnhanceParams = EnhanceParams()
)
