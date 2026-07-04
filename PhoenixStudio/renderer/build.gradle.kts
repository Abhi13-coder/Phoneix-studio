// Phoenix Studio — :renderer
// Owns GLSurfaceView, the GLES 3.2 render loop, camera math, meshes and
// shader compilation. Depends only on :core (math/logging) — it knows
// nothing about the scene graph's JSON format or the editor UI, so it can
// be reused headless (e.g. for thumbnail generation) later.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.renderer"
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
    implementation(project(":scene"))
}
