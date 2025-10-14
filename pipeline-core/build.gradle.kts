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
    compileOnly(libs.openpnp.opencv)

    // For the desktop smoke test (JUnit)
    testImplementation(libs.openpnp.opencv)
    testRuntimeOnly(libs.openpnp.opencv)

//    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()   // << THIS is the important bit
    testLogging {
        events("passed", "failed", "skipped", "standardOut", "standardError")
        showExceptions = true
        showStackTraces = true
        showCauses = true
    }
}