package com.phoenixstudio.filesystem

import android.content.Context
import com.phoenixstudio.core.log.Logger
import java.io.File

private const val TAG = "PhoenixFileSystem"

/**
 * Owns the on-disk directory layout for every Phoenix Studio project.
 *
 * Everything lives under [Context.getFilesDir] — the app's private internal
 * storage — rather than external/shared storage. On API 29+ this needs no
 * runtime permissions at all (unlike external storage, which is subject to
 * scoped-storage restrictions), which matters for an editor meant to work
 * standalone on a phone with no setup friction.
 *
 * Layout:
 * ```
 * files/PhoenixStudio/
 *   Projects/
 *     <project name>/
 *       Assets/
 *       Scenes/
 *       Textures/
 *       Models/
 *       Scripts/
 *       Plugins/
 *       Autosaves/
 *       Logs/
 * ```
 *
 * This class only creates and locates directories — it has no idea what a
 * "project" or "scene" actually contains. That's `:project`'s job, built on
 * top of the paths this class hands out.
 */
class PhoenixFileSystem(context: Context) {

    val rootDir: File = File(context.filesDir, "PhoenixStudio")
    val projectsDir: File = File(rootDir, "Projects")

    init {
        ensureDirectoryExists(rootDir)
        ensureDirectoryExists(projectsDir)
    }

    /** All top-level project names currently on disk, derived from folder names under [projectsDir]. */
    fun listProjectNames(): List<String> =
        projectsDir.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    /** The root folder for a given project, creating it (and every subfolder below) if it doesn't exist yet. */
    fun ensureProjectStructure(projectName: String): ProjectDirectories {
        val safeName = sanitizeProjectName(projectName)
        val projectDir = File(projectsDir, safeName)

        val directories = ProjectDirectories(
            root = projectDir,
            assets = File(projectDir, "Assets"),
            scenes = File(projectDir, "Scenes"),
            textures = File(projectDir, "Textures"),
            models = File(projectDir, "Models"),
            scripts = File(projectDir, "Scripts"),
            plugins = File(projectDir, "Plugins"),
            autosaves = File(projectDir, "Autosaves"),
            logs = File(projectDir, "Logs")
        )

        for (dir in directories.all()) {
            ensureDirectoryExists(dir)
        }

        return directories
    }

    /** Deletes a project's entire folder tree. There is no undo — callers must confirm with the user first. */
    fun deleteProject(projectName: String): Boolean {
        val projectDir = File(projectsDir, sanitizeProjectName(projectName))
        val deleted = projectDir.deleteRecursively()
        Logger.i(TAG, "Deleted project '$projectName': $deleted")
        return deleted
    }

    private fun ensureDirectoryExists(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.w(TAG, "Failed to create directory: ${dir.absolutePath}")
        }
    }

    /**
     * Strips characters that aren't safe as a single path segment, so a
     * project name typed by the user can't accidentally (or maliciously)
     * escape [projectsDir] via `..` or path separators.
     */
    private fun sanitizeProjectName(name: String): String {
        val cleaned = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "_")
        return cleaned.ifEmpty { "Untitled Project" }
    }
}

/**
 * The full set of subfolder paths belonging to a single project, as
 * returned by [PhoenixFileSystem.ensureProjectStructure].
 */
data class ProjectDirectories(
    val root: File,
    val assets: File,
    val scenes: File,
    val textures: File,
    val models: File,
    val scripts: File,
    val plugins: File,
    val autosaves: File,
    val logs: File
) {
    fun all(): List<File> = listOf(root, assets, scenes, textures, models, scripts, plugins, autosaves, logs)
}
