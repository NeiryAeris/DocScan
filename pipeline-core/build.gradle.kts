plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)   // uses Kotlin 2.0.21 from your catalog
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    // Desktop OpenCV with org.opencv.* API + bundled natives
    implementation(libs.openpnp.opencv)
    testImplementation(kotlin("test"))
//    implementation("nu.pattern:opencv:2.4.9-7")  // add this line
}