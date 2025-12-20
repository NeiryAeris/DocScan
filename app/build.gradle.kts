plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.docscan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.docscan"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core module
    implementation(project(":pipeline-core")) {
        exclude(group = "org.openpnp", module = "opencv")
    }
    implementation(project(":ocr-core"))
    implementation(project(":ocr-remote"))
    implementation(project(":imaging-opencv-android"))
    implementation(project(":domain"))

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))

    // Compose core
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)

    // Tooling for @Preview
    implementation(libs.androidx.ui.tooling.preview)   // gives you @Preview annotation
    debugImplementation(libs.androidx.ui.tooling)      // interactive preview in Android Studio

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // OpenCV
    implementation(libs.opencv)

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Unsure external dependency
    implementation(libs.core)
    implementation(libs.coil.compose)

    //File ops
    implementation(libs.itextg)
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.kotlinx.coroutines.android)

}