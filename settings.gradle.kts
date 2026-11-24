rootProject.name = "navite"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Auto-provisions the JDK requested by `jvmToolchain(...)` when it is not installed locally (e.g. on CI)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Published library
include(":library")

// Example apps (new KMP default structure: one shared module + one thin entry-point module per platform)
include(":example:shared")
include(":example:androidApp")
include(":example:desktopApp")
// example/iosApp is an Xcode project, not a Gradle module
