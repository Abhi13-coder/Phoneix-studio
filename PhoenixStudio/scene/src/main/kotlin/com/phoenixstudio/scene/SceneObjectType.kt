package com.phoenixstudio.scene

/**
 * What a [SceneObject] visually represents, if anything. [EMPTY] objects
 * are pure hierarchy/transform nodes (grouping, pivots) with no geometry —
 * the same role an "Empty" plays in Blender or an empty GameObject in Unity.
 *
 * Deliberately small right now: only the primitives the renderer actually
 * knows how to draw ([CUBE]) plus [EMPTY]. New cases are added here as
 * `:renderer` gains new mesh types, rather than speculatively listing
 * geometry that can't be drawn yet.
 */
enum class SceneObjectType {
    EMPTY,
    CUBE
}
