// Phoenix Studio — root settings.
// Only modules that contain real, compiling code are included here.
// Additional modules (scene, ui, assets, project, plugins, filesystem, editor)
// are added in later bootstrap rounds as they are implemented, to avoid
// committing empty placeholder modules.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PhoenixStudio"

include(":app")
include(":core")
include(":renderer")
include(":scene")
include(":filesystem")
include(":project")
include(":assets")
