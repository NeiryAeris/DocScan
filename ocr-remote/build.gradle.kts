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
    // HTTP client
    implementation(libs.okhttp)

    // JSON (Moshi)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Coroutines for suspend functions
    implementation(libs.kotlinx.coroutines.core.v190)
}
