package com.phoenixstudio.core.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A unit quaternion representing a 3D rotation. Used by the scene system for
 * object transforms so that repeated edits (e.g. dragging an inspector
 * gizmo) don't accumulate gimbal-lock artifacts the way Euler angles would.
 * Euler angles are still exposed to the UI layer for editing convenience,
 * but converted through this type internally.
 */
data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float
) {

    operator fun times(other: Quaternion): Quaternion = Quaternion(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z
    )

    fun normalized(): Quaternion {
        val len = sqrt(x * x + y * y + z * z + w * w)
        return if (len < 1e-6f) IDENTITY else Quaternion(x / len, y / len, z / len, w / len)
    }

    /** Converts to a column-major rotation matrix, ready for [Mat4] composition. */
    fun toMat4(): Mat4 {
        val q = normalized()
        val xx = q.x * q.x
        val yy = q.y * q.y
        val zz = q.z * q.z
        val xy = q.x * q.y
        val xz = q.x * q.z
        val yz = q.y * q.z
        val wx = q.w * q.x
        val wy = q.w * q.y
        val wz = q.w * q.z

        return Mat4.fromColumnMajor(
            floatArrayOf(
                1f - 2f * (yy + zz), 2f * (xy + wz), 2f * (xz - wy), 0f,
                2f * (xy - wz), 1f - 2f * (xx + zz), 2f * (yz + wx), 0f,
                2f * (xz + wy), 2f * (yz - wx), 1f - 2f * (xx + yy), 0f,
                0f, 0f, 0f, 1f
            )
        )
    }

    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)

        /** Builds a rotation quaternion from an axis-angle pair (angle in radians). */
        fun fromAxisAngle(axis: Vec3, angleRadians: Float): Quaternion {
            val a = axis.normalized()
            val half = angleRadians / 2f
            val s = sin(half)
            return Quaternion(a.x * s, a.y * s, a.z * s, cos(half)).normalized()
        }

        /** Builds a rotation quaternion from Euler angles (radians, applied Y * X * Z). */
        fun fromEuler(pitchX: Float, yawY: Float, rollZ: Float): Quaternion {
            val qx = fromAxisAngle(Vec3.RIGHT, pitchX)
            val qy = fromAxisAngle(Vec3.UP, yawY)
            val qz = fromAxisAngle(Vec3(0f, 0f, 1f), rollZ)
            return (qy * qx * qz).normalized()
        }
    }
}
