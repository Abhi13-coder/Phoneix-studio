// Phoenix Studio — :filesystem
// Owns the on-disk directory layout: Projects/, and within each project,
// Assets/Scenes/Textures/Models/Scripts/Plugins/Autosaves/Logs. Depends
// only on :core (logging) — it knows nothing about what a Scene or
// Project actually contains, just where their files should live.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.filesystem"
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
