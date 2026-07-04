package com.phoenixstudio.project

/**
 * Metadata for a single Phoenix Studio project, saved as `project.json` at
 * the project's root folder (see [com.phoenixstudio.filesystem.PhoenixFileSystem]).
 *
 * [sceneFileNames] is a list, not a single scene reference, deliberately:
 * a large open-world project (the engine's eventual GTA-scale goal) will
 * need to split its world across many scene files — one per streamed
 * region/chunk — rather than one ever-growing scene. Designing the project
 * format around "many scenes" from the start avoids a breaking format
 * change later once chunk streaming is actually built.
 */
data class Project(
    val name: String,
    val sceneFileNames: MutableList<String> = mutableListOf(),
    var lastOpenedSceneFileName: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    /** Registers [sceneFileName] as belonging to this project, if it isn't already tracked. */
    fun addSceneFileName(sceneFileName: String) {
        if (sceneFileName !in sceneFileNames) {
            sceneFileNames.add(sceneFileName)
        }
    }
}
