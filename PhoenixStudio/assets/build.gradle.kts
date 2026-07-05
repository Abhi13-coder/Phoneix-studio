// Phoenix Studio — :assets
// Parses external 3D asset formats into engine-ready geometry data.
// Starts with Wavefront OBJ (a simple, well-documented text format many
// free low-poly packs — Kenney, Quaternius — ship in) since it's far
// simpler to hand-write a correct parser for than glTF's binary/JSON
// structure. Depends only on :core: parsing is pure math/text, no OpenGL
// or Android APIs involved — uploading the parsed data to the GPU is
// :renderer's job (see StaticMesh), and reading the file bytes is the
// caller's job (Context.assets, or a file picker in a later round).

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.assets"
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
