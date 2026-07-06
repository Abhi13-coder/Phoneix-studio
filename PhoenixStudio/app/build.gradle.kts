// Phoenix Studio — :app
// The installable shell. In this bootstrap round it hosts the raw
// PhoenixGLSurfaceView full-screen; the editor chrome (explorer/inspector/
// console/toolbar panels) is added in a later round as the :ui module.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoenixstudio.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phoenixstudio.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("PHOENIX_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PHOENIX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PHOENIX_KEY_ALIAS")
                keyPassword = System.getenv("PHOENIX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only sign with the release config when CI has actually provided
            // keystore env vars; otherwise this build type is left unsigned
            // so local `assembleRelease` runs still succeed without secrets.
            if (System.getenv("PHOENIX_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
    implementation(project(":renderer"))
    implementation(project(":scene"))
    implementation(project(":filesystem"))
    implementation(project(":project"))
    implementation(project(":assets"))
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
