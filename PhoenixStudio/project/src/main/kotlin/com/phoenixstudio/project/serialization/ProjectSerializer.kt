package com.phoenixstudio.project.serialization

import com.phoenixstudio.project.Project
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts [Project] metadata to and from its on-disk `project.json`
 * representation. Uses `org.json` (bundled with the Android platform SDK)
 * for the same reason [com.phoenixstudio.scene.serialization.SceneSerializer]
 * does: the project format requirement is JSON-only, and the format here
 * is simple enough that a third-party serialization library would be
 * unjustified extra weight.
 */
object ProjectSerializer {

    private const val FORMAT_VERSION = 1

    fun toJson(project: Project): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("name", project.name)
        root.put("createdAtMillis", project.createdAtMillis)
        root.put("lastOpenedSceneFileName", project.lastOpenedSceneFileName)

        val sceneArray = JSONArray()
        for (sceneFileName in project.sceneFileNames) {
            sceneArray.put(sceneFileName)
        }
        root.put("sceneFileNames", sceneArray)

        return root.toString(2)
    }

    fun fromJson(json: String): Project {
        val root = JSONObject(json)

        val sceneFileNames = mutableListOf<String>()
        val sceneArray = root.optJSONArray("sceneFileNames") ?: JSONArray()
        for (i in 0 until sceneArray.length()) {
            sceneFileNames.add(sceneArray.getString(i))
        }

        return Project(
            name = root.getString("name"),
            sceneFileNames = sceneFileNames,
            lastOpenedSceneFileName = root.optString("lastOpenedSceneFileName", null),
            createdAtMillis = root.optLong("createdAtMillis", System.currentTimeMillis())
        )
    }
}
