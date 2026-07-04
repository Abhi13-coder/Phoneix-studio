package com.phoenixstudio.core.math

/**
 * A ray in 3D space: a starting point plus a direction to travel along.
 * Used by the viewport's tap-to-select picking — see
 * [com.phoenixstudio.renderer.camera.OrbitCamera.screenPointToRay] for how
 * a screen touch becomes a [Ray], and `Picking.raycastClosest` in the
 * `:scene` module for how a [Ray] is tested against scene objects.
 *
 * [direction] is expected to already be normalized by whoever constructs a
 * [Ray] — callers that need the unnormalized form (e.g. for distance
 * scaling) should track that separately rather than relying on this type
 * to do it, keeping this a plain, cheap-to-construct value type.
 */
data class Ray(
    val origin: Vec3,
    val direction: Vec3
)
