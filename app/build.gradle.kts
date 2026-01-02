plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")
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

    // Accompanist
    implementation("com.google.accompanist:accompanist-navigation-animation:0.32.0")

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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

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
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.androidx.material.icons.extended.android)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase Auth
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation ("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")

    // Google Sign In
    implementation ("com.google.android.gms:play-services-auth:21.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")


}
