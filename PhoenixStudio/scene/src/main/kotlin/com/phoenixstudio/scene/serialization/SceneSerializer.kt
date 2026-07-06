package com.phoenixstudio.scene.serialization

import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.core.math.Quaternion
import com.phoenixstudio.core.math.Vec3
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.SceneObject
import com.phoenixstudio.scene.SceneObjectType
import com.phoenixstudio.scene.Transform
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SceneSerializer"

/**
 * Converts a [Scene] to and from its on-disk JSON representation.
 *
 * The project format requirement is JSON-only, never binary — see the
 * project brief — so this uses `org.json`, which ships with the Android
 * platform SDK, rather than pulling in a third-party serialization library
 * for a format this simple.
 *
 * The hierarchy is stored as a **flat list** of objects with a `parentId`
 * reference rather than nested `children` arrays. This makes partial
 * scene edits (the future undo system replacing a single object's JSON
 * entry) and id-based lookups during load straightforward, at the cost of
 * a two-pass reconstruction in [fromJson] — first create every object,
 * then wire up parent/child links once every id is known.
 */
object SceneSerializer {

    private const val FORMAT_VERSION = 1

    fun toJson(scene: Scene): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("name", scene.name)

        val objectsArray = JSONArray()
        scene.forEachObject { obj ->
            objectsArray.put(objectToJson(obj))
        }
        root.put("objects", objectsArray)

        return root.toString(2)
    }

    fun fromJson(json: String): Scene {
        val root = JSONObject(json)
        val scene = Scene(name = root.optString("name", "Untitled Scene"))

        val objectsArray = root.optJSONArray("objects") ?: JSONArray()

        // Pass 1: create every object (without hierarchy links yet) so that
        // every id referenced by a later parentId is guaranteed to resolve,
        // regardless of the order objects happen to appear in the file.
        val createdById = LinkedHashMap<String, SceneObject>()
        val parentIdByObjectId = HashMap<String, String?>()

        for (i in 0 until objectsArray.length()) {
            val objJson = objectsArray.getJSONObject(i)
            val obj = objectFromJson(objJson)
            createdById[obj.id] = obj
            parentIdByObjectId[obj.id] = objJson.optString("parentId", "").ifEmpty { null }
        }

        // Pass 2: wire up parent/child links, then anything left unparented
        // becomes a root object.
        for ((id, obj) in createdById) {
            val parentId = parentIdByObjectId[id]
            val parent = parentId?.let { createdById[it] }
            if (parent != null) {
                parent.addChild(obj)
            } else {
                scene.addRootObject(obj)
            }
        }

        Logger.i(TAG, "Loaded scene '${scene.name}' with ${scene.objectCount()} object(s)")
        return scene
    }

    private fun objectToJson(obj: SceneObject): JSONObject {
        val json = JSONObject()
        json.put("id", obj.id)
        json.put("name", obj.name)
        json.put("type", obj.type.name)
        json.put("enabled", obj.enabled)
        json.put("parentId", obj.parent?.id)
        json.put("modelAssetPath", obj.modelAssetPath)
        json.put("transform", transformToJson(obj.transform))
        return json
    }

    private fun objectFromJson(json: JSONObject): SceneObject {
        val typeName = json.optString("type", SceneObjectType.EMPTY.name)
        val type = runCatching { SceneObjectType.valueOf(typeName) }
            .getOrElse {
                Logger.w(TAG, "Unknown SceneObjectType '$typeName', defaulting to EMPTY")
                SceneObjectType.EMPTY
            }

        return SceneObject(
            id = json.getString("id"),
            name = json.optString("name", "Object"),
            type = type,
            transform = transformFromJson(json.getJSONObject("transform")),
            enabled = json.optBoolean("enabled", true),
            modelAssetPath = json.optString("modelAssetPath", "").ifEmpty { null }
        )
    }

    private fun transformToJson(transform: Transform): JSONObject {
        val json = JSONObject()
        json.put("position", vec3ToJson(transform.position))
        json.put("rotation", quaternionToJson(transform.rotation))
        json.put("scale", vec3ToJson(transform.scale))
        return json
    }

    private fun transformFromJson(json: JSONObject): Transform = Transform(
        position = vec3FromJson(json.getJSONArray("position")),
        rotation = quaternionFromJson(json.getJSONArray("rotation")),
        scale = vec3FromJson(json.getJSONArray("scale"))
    )

    private fun vec3ToJson(v: Vec3): JSONArray = JSONArray().put(v.x).put(v.y).put(v.z)

    private fun vec3FromJson(array: JSONArray): Vec3 =
        Vec3(array.getDouble(0).toFloat(), array.getDouble(1).toFloat(), array.getDouble(2).toFloat())

    private fun quaternionToJson(q: Quaternion): JSONArray =
        JSONArray().put(q.x).put(q.y).put(q.z).put(q.w)

    private fun quaternionFromJson(array: JSONArray): Quaternion = Quaternion(
        array.getDouble(0).toFloat(),
        array.getDouble(1).toFloat(),
        array.getDouble(2).toFloat(),
        array.getDouble(3).toFloat()
    )
}        
