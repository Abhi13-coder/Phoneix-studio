package com.phoenixstudio.core.math

import kotlin.math.sqrt

/**
 * A mutable-free 3-component single-precision vector.
 *
 * Instances are immutable value holders; every operation returns a new
 * [Vec3] rather than mutating the receiver. This trades a small amount of
 * allocation pressure for predictability, which matters more once the
 * scene graph and undo system start passing vectors around by reference.
 * Hot paths inside the renderer (per-frame camera math) use [FloatArray]
 * buffers directly instead of this type — see [com.phoenixstudio.renderer.camera.OrbitCamera].
 */
data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float
) {

    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    /** Component-wise product, useful for scale transforms. */
    fun multiply(other: Vec3): Vec3 = Vec3(x * other.x, y * other.y, z * other.z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun lengthSquared(): Float = x * x + y * y + z * z

    /**
     * Returns a unit-length vector pointing in the same direction as this one.
     * Falls back to [ZERO] when the vector has (near) zero length, since a
     * normalized zero vector is mathematically undefined and callers in the
     * renderer would rather get a stable zero than a NaN.
     */
    fun normalized(): Vec3 {
        val len = length()
        return if (len < EPSILON) ZERO else Vec3(x / len, y / len, z / len)
    }

    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)

    companion object {
        private const val EPSILON = 1e-6f

        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UP = Vec3(0f, 1f, 0f)
        val RIGHT = Vec3(1f, 0f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}
