// Phoenix Studio — :core
// Pure Kotlin/Android-library module: math primitives, logging, result types.
// Deliberately has zero dependency on renderer/ui/editor so it can be reused
// by every other module without pulling in OpenGL or View code.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
