// KMPLiquidGlass backdrop module (https://github.com/Kashif-E/KMPLiquidGlass)
// Vendored as an Android-only KMP submodule so expect/actual sources compile
// with the project's Kotlin 2.0.21 toolchain (published 0.0.1-alpha02 needs Kotlin 2.3).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xexpect-actual-classes"
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation("androidx.compose.foundation:foundation:1.7.4")
            implementation("androidx.compose.ui:ui:1.7.4")
            implementation("androidx.compose.ui:ui-graphics:1.7.4")
        }
    }
}

android {
    namespace = "com.kashif_e.backdrop"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}
