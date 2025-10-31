pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DocScan"
include(":app")
include(":pipeline-core")
include(":domain")
include(":imaging-opencv-android")
include(":ocr-core")
include(":ocr-mlkit-android")
include(":ocr-tesseract-android")
include(":ocr-remote")
