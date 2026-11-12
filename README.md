[![official project](http://jb.gg/badges/official.svg)](https://github.com/JetBrains#jetbrains-on-github)

# Multiplatform library template

## What is it?

This repository contains a simple library project, intended to demonstrate a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library that is deployable to [Maven Central](https://central.sonatype.com/).

The library has only one function: generate the [Fibonacci sequence](https://en.wikipedia.org/wiki/Fibonacci_sequence) starting from platform-provided numbers. Also, it has a test for each platform just to be sure that tests run.

Note that no other actions or tools usually required for the library development are set up, such as [tracking of backwards compatibility](https://kotlinlang.org/docs/jvm-api-guidelines-backward-compatibility.html#tools-designed-to-enforce-backward-compatibility), explicit API mode, licensing, contribution guideline, code of conduct and others. You can find a guide for best practices for designing Kotlin libraries [here](https://kotlinlang.org/docs/api-guidelines-introduction.html).

## Project structure

The example apps follow the [default Kotlin Multiplatform project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html) introduced for Android Gradle Plugin 9: one shared Kotlin Multiplatform library module plus a thin entry-point module per platform.

```
.
├── library/              # The published KMP library (android, jvm, iosArm64, iosSimulatorArm64, linuxX64, js, wasmJs)
└── example/
    ├── shared/           # Shared Compose Multiplatform UI (KMP library, depends on :library)
    ├── androidApp/       # Android entry point (com.android.application)
    ├── desktopApp/       # Desktop (JVM) entry point
    └── iosApp/           # iOS entry point (Xcode project, imports the `Shared` framework)
```

| Module | Gradle plugins |
| --- | --- |
| `:library` | `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`, `com.vanniktech.maven.publish` |
| `:example:shared` | `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`, `org.jetbrains.compose`, `kotlin.plugin.compose` |
| `:example:androidApp` | `com.android.application` (built-in Kotlin), `kotlin.plugin.compose` |
| `:example:desktopApp` | `kotlin.jvm`, `org.jetbrains.compose`, `kotlin.plugin.compose` |

### Running the examples

* **Android**: run the `example.androidApp` configuration, or `./gradlew :example:androidApp:installDebug`
* **Desktop**: `./gradlew :example:desktopApp:run`
* **iOS**: open `example/iosApp/iosApp.xcodeproj` in Xcode and run. The build phase calls `./gradlew :example:shared:embedAndSignAppleFrameworkForXcode`.

### Running the tests

* `./gradlew jvmTest` / `testAndroidHostTest` / `iosSimulatorArm64Test` / `jsTest` / `wasmJsTest` / `linuxX64Test` (Linux host only)

### JDK

The Gradle daemon JVM is pinned in `gradle/gradle-daemon-jvm.properties` (JetBrains Runtime 25, the JDK bundled with Android Studio), so `./gradlew` behaves the same regardless of `JAVA_HOME`; Gradle downloads it automatically if it isn't installed. Sources are compiled with a JDK 17 toolchain (`jvmToolchain(17)`), auto-provisioned through the foojay resolver when missing.
To change the daemon JVM, run `./gradlew updateDaemonJvm --jvm-version=<version> --jvm-vendor=<vendor>`.

## Guide

Please find the detailed guide [here](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html).

# Other resources
* [Publishing via the Central Portal](https://central.sonatype.org/publish-ea/publish-ea-guide/)
* [Gradle Maven Publish Plugin \- Publishing to Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
