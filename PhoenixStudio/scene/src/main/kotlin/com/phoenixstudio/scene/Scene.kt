package com.phoenixstudio.scene

/**
 * The root container for a single scene file: an ordered list of top-level
 * [SceneObject]s (objects with no parent), plus a display [name] used as
 * the scene's title in the explorer panel and as the default filename when
 * saving under `Scenes/` (see the future `:filesystem` module).
 *
 * A [Scene] does not itself know how to draw anything — `:renderer` walks
 * it read-only via [forEachObject] — and does not know how to read/write
 * files — `:filesystem` and [com.phoenixstudio.scene.serialization.SceneSerializer]
 * own that. This keeps the scene graph reusable by tooling (e.g. a future
 * command-line scene validator) that has no renderer or file I/O at all.
 */
class Scene(var name: String = "Untitled Scene") {

    private val mutableRootObjects = mutableListOf<SceneObject>()
    val rootObjects: List<SceneObject> get() = mutableRootObjects

    /** Adds a top-level object. If [obj] currently has a parent, it is detached first. */
    fun addRootObject(obj: SceneObject) {
        obj.parent?.removeChild(obj)
        if (obj !in mutableRootObjects) {
            mutableRootObjects.add(obj)
        }
    }

    /** Removes [obj] from the root list. Does nothing if it isn't a root object (e.g. it's parented). */
    fun removeRootObject(obj: SceneObject) {
        mutableRootObjects.remove(obj)
    }

    /** Depth-first walk of every object in the scene, roots and all descendants. */
    fun forEachObject(action: (SceneObject) -> Unit) {
        for (root in mutableRootObjects) {
            root.forEachInHierarchy(action)
        }
    }

    /** Finds an object anywhere in the scene by its stable [SceneObject.id], or null if absent. */
    fun findById(id: String): SceneObject? {
        var found: SceneObject? = null
        forEachObject { obj ->
            if (obj.id == id) found = obj
        }
        return found
    }

    /** Total object count across the whole hierarchy, roots and descendants alike. */
    fun objectCount(): Int {
        var count = 0
        forEachObject { count++ }
        return count
    }
}
