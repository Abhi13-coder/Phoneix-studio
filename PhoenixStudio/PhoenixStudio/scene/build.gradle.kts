// Phoenix Studio — :scene
// The scene graph: object hierarchy, transforms, and JSON save/load.
// Depends only on :core (math/logging) — it knows nothing about OpenGL or
// Views, so the same scene file format can eventually be loaded headless
// (e.g. by a future asset-thumbnail renderer or a command-line build tool)
// without pulling in renderer or UI code.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.scene"
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

dependencies {
    implementation(project(":core"))
}
