package com.phoenixstudio.scene

import java.util.UUID

/**
 * A single node in the scene hierarchy: the editor's equivalent of a
 * Blender object or Unity GameObject. Holds its own [transform] (always
 * relative to [parent], or to world space if [parent] is null) plus a
 * stable [id] used for JSON references, undo-stack diffing, and selection
 * tracking in the (future) inspector panel.
 *
 * Parent/child links are held as direct references rather than looked up
 * by id on every access, since the editor's typical operations (walk the
 * hierarchy to render, walk it to draw the explorer tree) are read-heavy
 * and happen every frame or every UI refresh.
 */
   class SceneObject(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: SceneObjectType = SceneObjectType.EMPTY,
    val transform: Transform = Transform(),
    var enabled: Boolean = true,
    /** For [SceneObjectType.MODEL] objects: which imported mesh to draw. Ignored for other types. */
    var modelAssetPath: String? = null
) {

    var parent: SceneObject? = null
        private set

    private val mutableChildren = mutableListOf<SceneObject>()
    val children: List<SceneObject> get() = mutableChildren

    /**
     * Attaches [child] under this object, detaching it from any previous
     * parent first. A no-op if [child] is already parented here, and
     * refuses to create a cycle (attaching an ancestor as its own
     * descendant), which would otherwise infinite-loop [worldMatrix] and
     * every hierarchy walk in the explorer/renderer.
     */
    fun addChild(child: SceneObject) {
        require(child !== this) { "SceneObject '$name' cannot be its own parent" }
        require(!isAncestorOf(child)) {
            "Cannot add '${child.name}' as a child of '$name': would create a cycle"
        }
        if (child.parent === this) return

        child.parent?.mutableChildren?.remove(child)
        child.parent = this
        mutableChildren.add(child)
    }

    fun removeChild(child: SceneObject) {
        if (child.parent !== this) return
        mutableChildren.remove(child)
        child.parent = null
    }

    /** True if [other] is somewhere below this object in the hierarchy (this object is its ancestor). */
    private fun isAncestorOf(other: SceneObject): Boolean {
        var current: SceneObject? = other.parent
        while (current != null) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }

    /**
     * The object's transform composed with every ancestor's transform, i.e.
     * where this object actually sits in world space. Walks up to the root
     * on every call for the same reason [Transform.localMatrix] doesn't
     * cache: no dirty-flagging exists yet to know when a cached value would
     * be stale after a parent's transform changes.
     */
    fun worldMatrix(): com.phoenixstudio.core.math.Mat4 {
        val local = transform.localMatrix()
        val parentMatrix = parent?.worldMatrix()
        return if (parentMatrix != null) parentMatrix * local else local
    }

    /** Depth-first walk of this object and all descendants, this object first. */
    fun forEachInHierarchy(action: (SceneObject) -> Unit) {
        action(this)
        for (child in mutableChildren) {
            child.forEachInHierarchy(action)
        }
    }
}
