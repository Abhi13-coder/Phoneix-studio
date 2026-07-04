package com.phoenixstudio.scene

import com.phoenixstudio.core.math.Mat4
import com.phoenixstudio.core.math.Quaternion
import com.phoenixstudio.core.math.Vec3

/**
 * The position/rotation/scale of a single [SceneObject], expressed in the
 * space of its parent (or world space, for a root-level object).
 *
 * Stored as a mutable data holder rather than an immutable value type,
 * unlike [Vec3]/[Quaternion] — the inspector panel (added in the `:ui`
 * module) needs to mutate a live object's transform in place as the user
 * drags a gizmo or edits a number field, without the overhead of replacing
 * the whole [SceneObject] on every intermediate drag frame.
 */
class Transform(
    var position: Vec3 = Vec3.ZERO,
    var rotation: Quaternion = Quaternion.IDENTITY,
    var scale: Vec3 = Vec3.ONE
) {

    /**
     * Composes position, rotation and scale into a single local-space
     * matrix, in the standard T * R * S order. Recomputed on demand rather
     * than cached, since the inspector/gizmo mutate the fields directly and
     * there is currently no dirty-flag plumbing to know when a cache would
     * be stale; profiling can add caching later if this shows up as a
     * bottleneck on real scenes.
     */
    fun localMatrix(): Mat4 =
        Mat4.translation(position) * rotation.toMat4() * Mat4.scale(scale)

    fun copy(): Transform = Transform(position, rotation, scale)
}
