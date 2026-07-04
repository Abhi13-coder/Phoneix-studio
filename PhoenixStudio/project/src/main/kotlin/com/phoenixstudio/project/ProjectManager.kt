package com.phoenixstudio.project

import android.content.Context
import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.filesystem.PhoenixFileSystem
import com.phoenixstudio.project.serialization.ProjectSerializer
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.serialization.SceneSerializer
import java.io.File

private const val TAG = "ProjectManager"
private const val PROJECT_METADATA_FILE_NAME = "project.json"

/**
 * The main entry point for working with projects: creating them, saving
 * and loading their scenes, and listing what's on disk. This is where
 * `:filesystem` (directory layout) and `:scene` (what a scene contains)
 * actually meet — everything below writes and reads real files, not just
 * JSON strings held in memory, unlike the bootstrap-round demo in
 * `MainActivity` from earlier rounds.
 */
class ProjectManager(context: Context) {

    private val fileSystem = PhoenixFileSystem(context.applicationContext)

    /** Names of every project currently on disk. */
    fun listProjectNames(): List<String> = fileSystem.listProjectNames()

    /**
     * Creates a new project on disk: the full folder tree plus a
     * `project.json` metadata file. If a project with this name already
     * exists, its existing metadata is loaded and returned instead of
     * being overwritten.
     */
    fun createProject(name: String): Project {
        val directories = fileSystem.ensureProjectStructure(name)
        val metadataFile = File(directories.root, PROJECT_METADATA_FILE_NAME)

        if (metadataFile.exists()) {
            Logger.i(TAG, "Project '$name' already exists on disk; loading existing metadata")
            return ProjectSerializer.fromJson(metadataFile.readText())
        }

        val project = Project(name = name)
        saveProjectMetadata(project)
        Logger.i(TAG, "Created new project '$name' at ${directories.root.absolutePath}")
        return project
    }

    /** Loads an existing project's metadata by name, or null if no such project exists on disk. */
    fun loadProject(name: String): Project? {
        val directories = fileSystem.ensureProjectStructure(name)
        val metadataFile = File(directories.root, PROJECT_METADATA_FILE_NAME)
        if (!metadataFile.exists()) return null

        return ProjectSerializer.fromJson(metadataFile.readText())
    }

    /** Saves [project]'s metadata (name, tracked scene files, etc.) to `project.json`. */
    fun saveProjectMetadata(project: Project) {
        val directories = fileSystem.ensureProjectStructure(project.name)
        val metadataFile = File(directories.root, PROJECT_METADATA_FILE_NAME)
        metadataFile.writeText(ProjectSerializer.toJson(project))
    }

    /**
     * Saves [scene] to `Scenes/[sceneFileName]` under [project], registering
     * that filename with the project's metadata if it isn't already tracked.
     * [sceneFileName] should include the `.json` extension, e.g. "Main.json".
     */
    fun saveScene(project: Project, scene: Scene, sceneFileName: String) {
        val directories = fileSystem.ensureProjectStructure(project.name)
        val sceneFile = File(directories.scenes, sceneFileName)
        sceneFile.writeText(SceneSerializer.toJson(scene))

        project.addSceneFileName(sceneFileName)
        project.lastOpenedSceneFileName = sceneFileName
        saveProjectMetadata(project)

        Logger.i(TAG, "Saved scene '${scene.name}' to ${sceneFile.absolutePath}")
    }

    /** Loads a previously saved scene by filename from [project], or null if it doesn't exist on disk. */
    fun loadScene(project: Project, sceneFileName: String): Scene? {
        val directories = fileSystem.ensureProjectStructure(project.name)
        val sceneFile = File(directories.scenes, sceneFileName)
        if (!sceneFile.exists()) return null

        val scene = SceneSerializer.fromJson(sceneFile.readText())
        Logger.i(TAG, "Loaded scene '${scene.name}' from ${sceneFile.absolutePath}")
        return scene
    }

    /** Permanently deletes a project and everything inside it. Callers must confirm with the user first. */
    fun deleteProject(name: String): Boolean = fileSystem.deleteProject(name)
}
