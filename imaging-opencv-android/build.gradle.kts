plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
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
    // Keep the module Android-free, but compile against org.opencv.* API
    // and enable desktop smoke tests with native libs.
    compileOnly(libs.openpnp.opencv)         // API only; not packaged in prod

    // Desktop smoke tests (runs on your dev machine; brings native libs)
    testImplementation(libs.openpnp.opencv)
    testRuntimeOnly(libs.openpnp.opencv)

    // Kotlin test + JUnit (match whatever you already use)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)

    // Implements :domain contracts (Imaging, ImageRef)
    implementation(project(":domain"))
}

tasks.test {
    useJUnit()   // important if your libs.junit is JUnit4; switch to useJUnitPlatform() for JUnit5
    testLogging {
        events("passed", "failed", "skipped", "standardOut", "standardError")
        showExceptions = true
        showStackTraces = true
        showCauses = true
    }
}
