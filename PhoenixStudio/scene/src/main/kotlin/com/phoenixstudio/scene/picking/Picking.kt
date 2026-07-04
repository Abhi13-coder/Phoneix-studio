package com.phoenixstudio.scene.picking

import com.phoenixstudio.core.math.Ray
import com.phoenixstudio.core.math.Vec3
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.SceneObject
import com.phoenixstudio.scene.SceneObjectType
import kotlin.math.abs

/**
 * Ray-vs-object hit testing for tap-to-select. Lives in `:scene` rather
 * than `:renderer` because it's pure geometry over the scene graph — no
 * OpenGL involved — matching the same reasoning that keeps [Scene] itself
 * renderer-agnostic.
 *
 * Every [SceneObjectType.CUBE] object is treated as a unit cube (extents
 * -0.5..0.5 on each local axis, matching `CubeMesh` in `:renderer`) for hit
 * testing purposes. If a mesh type's on-screen bounds ever diverge from its
 * logical extents, this is the place to add a per-type bounding size.
 */
object Picking {

    private const val CUBE_HALF_EXTENT = 0.5f

    /**
     * Finds the closest [SceneObject] in [scene] that [ray] hits, or null
     * if it hits nothing. "Closest" is measured by hit distance along the
     * ray, so overlapping objects resolve to whichever is nearer the
     * camera, matching how selection should behave visually.
     */
    fun raycastClosest(scene: Scene, ray: Ray): SceneObject? {
        var closest: SceneObject? = null
        var closestDistance = Float.MAX_VALUE

        scene.forEachObject { obj ->
            if (obj.enabled && obj.type == SceneObjectType.CUBE) {
                val distance = intersect(obj, ray)
                if (distance != null && distance < closestDistance) {
                    closestDistance = distance
                    closest = obj
                }
            }
        }
        return closest
    }

    /**
     * Tests [ray] against a single [obj], returning the hit distance along
     * the ray, or null if it misses. Exposed publicly (not just used
     * internally by [raycastClosest]) so the touch controller can cheaply
     * re-check "is the currently selected object still under my finger"
     * without re-testing the whole scene.
     */
    fun intersect(obj: SceneObject, ray: Ray): Float? {
        val inverseWorld = obj.worldMatrix().inverse()
        // obj.worldMatrix() is always affine (built from translation *
        // rotation * scale only), so its inverse is affine too — the plain
        // transformPoint/transformDirection methods (no perspective divide)
        // are correct here, unlike the camera's projection unprojection.
        val localOrigin = inverseWorld.transformPoint(ray.origin)
        val localDirection = inverseWorld.transformDirection(ray.direction)
        return intersectUnitCube(localOrigin, localDirection)
    }

    /**
     * Slab-method ray/AABB intersection against the unit cube spanning
     * -0.5..0.5 on every axis, in whatever space [origin]/[direction] are
     * already expressed in (the caller is expected to have transformed
     * them into the object's local space first).
     */
    private fun intersectUnitCube(origin: Vec3, direction: Vec3): Float? {
        var tMin = Float.NEGATIVE_INFINITY
        var tMax = Float.POSITIVE_INFINITY

        for (axis in 0 until 3) {
            val originComponent = origin.component(axis)
            val directionComponent = direction.component(axis)

            if (abs(directionComponent) < 1e-8f) {
                // Ray is parallel to this axis's slab; it only hits if the
                // origin already lies within the slab's bounds.
                if (originComponent < -CUBE_HALF_EXTENT || originComponent > CUBE_HALF_EXTENT) {
                    return null
                }
            } else {
                var tNear = (-CUBE_HALF_EXTENT - originComponent) / directionComponent
                var tFar = (CUBE_HALF_EXTENT - originComponent) / directionComponent
                if (tNear > tFar) {
                    val swap = tNear; tNear = tFar; tFar = swap
                }
                tMin = maxOf(tMin, tNear)
                tMax = minOf(tMax, tFar)
                if (tMin > tMax) return null
            }
        }

        // A hit behind the ray's origin doesn't count as a valid pick.
        return if (tMax >= 0f) maxOf(tMin, 0f) else null
    }

    private fun Vec3.component(axis: Int): Float = when (axis) {
        0 -> x
        1 -> y
        else -> z
    }
}
