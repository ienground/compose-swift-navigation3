import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechMavenPublish)
}

group = "zone.ien.navigation"
version = libs.versions.lib.version.name.get()

kotlin {
    jvmToolchain(17)

    jvm()

    android {
        namespace = "zone.ien.navigation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTest {}
        withDeviceTest {}

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // `js` and `wasmJs` share the `webMain` / `webTest` source sets.
    // Browser only: Compose UI's Skiko runtime cannot load in Node.js.
    // `binaries.executable()` bundles the tests with webpack so the Skiko runtime can load
    // (checked by the Compose plugin, see CMP-4906).
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Public API exposes @Composable functions, foundation scroll states and Navigation 3 entries
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.navigation3.runtime)
            // Color (rememberNativeScroll background) is part of the public API
            api(libs.compose.ui)
        }

        iosMain.dependencies {
            // Owners provided to the headless navigation host composition
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.savedstate.compose)
            // Back handlers of a screen's content (BackHandler / NavigationBackHandler)
            implementation(libs.androidx.navigationevent.compose)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "navigation", version.toString())

    pom {
        name = "navigation"
        description = "Native iOS navigation (UIKit) for Compose Multiplatform Navigation 3 apps."
        inceptionYear = "2026"
        url = "https://github.com/ienground/compose-swift-navigation3"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "ienground"
                name = "IENGROUND"
                url = "https://github.com/ienground"
            }
        }
        scm {
            url = "https://github.com/ienground/compose-swift-navigation3"
            connection = "scm:git:https://github.com/ienground/compose-swift-navigation3.git"
            developerConnection = "scm:git:https://github.com/ienground/compose-swift-navigation3.git"
        }
    }
}
