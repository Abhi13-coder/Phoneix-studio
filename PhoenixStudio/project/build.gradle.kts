// Phoenix Studio — :project
// The project format: a named project can contain multiple scene files
// (Scenes/level1.json, Scenes/level2.json, ...) rather than exactly one,
// so a large streamed-chunk world (see the project's GTA-scale ambitions)
// can eventually be split across many scene files instead of one giant one.
// Built on top of :filesystem (where files live) and :scene (what's in them).

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.project"
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
    implementation(project(":filesystem"))
}
