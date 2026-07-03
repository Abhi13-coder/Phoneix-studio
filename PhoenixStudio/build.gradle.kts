// Phoenix Studio — root build script.
// Declares plugin versions once; each module applies what it needs without
// re-specifying a version, keeping the whole project on one toolchain.

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
