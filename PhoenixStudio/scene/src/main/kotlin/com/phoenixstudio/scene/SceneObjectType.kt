package com.phoenixstudio.scene

/**
 * What a [SceneObject] visually represents, if anything. [EMPTY] objects
 * are pure hierarchy/transform nodes (grouping, pivots) with no geometry —
 * the same role an "Empty" plays in Blender or an empty GameObject in Unity.
 *
 * [MODEL] objects reference an imported mesh by asset path (see
 * [SceneObject.modelAssetPath]) rather than embedding geometry directly —
 * the actual vertex data lives in whatever mesh the renderer has loaded
 * and registered for that path (see `PhoenixRenderer.registerModelMesh`).
 */
enum class SceneObjectType {
    EMPTY,
    CUBE,
    MODEL
}
