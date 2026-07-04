package com.phoenixstudio.core.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * A 4x4 matrix stored column-major in a 16-element [FloatArray], matching
 * the memory layout OpenGL ES expects for `glUniformMatrix4fv`. This lets
 * [values] be uploaded directly without a transpose step on every frame,
 * which matters on a 2GB-RAM device like the Redmi 9A where avoiding
 * per-frame allocation and CPU work is the difference between 30 and 60 fps.
 *
 * Element access uses `values[column * 4 + row]`.
 */
class Mat4 private constructor(val values: FloatArray) {

    operator fun times(other: Mat4): Mat4 {
        val result = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += values[k * 4 + row] * other.values[col * 4 + k]
                }
                result[col * 4 + row] = sum
            }
        }
        return Mat4(result)
    }

    fun transformPoint(point: Vec3): Vec3 {
        val x = values[0] * point.x + values[4] * point.y + values[8] * point.z + values[12]
        val y = values[1] * point.x + values[5] * point.y + values[9] * point.z + values[13]
        val z = values[2] * point.x + values[6] * point.y + values[10] * point.z + values[14]
        return Vec3(x, y, z)
    }

    /**
     * Transforms a direction vector (ignores translation) using only the
     * upper-left 3x3 rotation/scale portion of this matrix. Used to carry a
     * ray's direction into an object's local space during picking, where
     * translating a *direction* the same way a *point* is translated would
     * be meaningless.
     */
    fun transformDirection(v: Vec3): Vec3 {
        val x = values[0] * v.x + values[4] * v.y + values[8] * v.z
        val y = values[1] * v.x + values[5] * v.y + values[9] * v.z
        val z = values[2] * v.x + values[6] * v.y + values[10] * v.z
        return Vec3(x, y, z)
    }

    /**
     * Full homogeneous-coordinate transform including the perspective
     * divide by `w`. [transformPoint] deliberately skips this (and the
     * matrix's bottom row entirely) since it assumes an affine matrix —
     * true for every translation/rotation/scale matrix in this engine, but
     * *not* true for a projection matrix or its inverse. This method exists
     * specifically for unprojecting a screen-space point back into world
     * space through the inverse view-projection matrix during ray casting.
     */
    fun transformPointPerspective(point: Vec3): Vec3 {
        val x = values[0] * point.x + values[4] * point.y + values[8] * point.z + values[12]
        val y = values[1] * point.x + values[5] * point.y + values[9] * point.z + values[13]
        val z = values[2] * point.x + values[6] * point.y + values[10] * point.z + values[14]
        val w = values[3] * point.x + values[7] * point.y + values[11] * point.z + values[15]
        return if (kotlin.math.abs(w) > 1e-6f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    /**
     * The inverse of this matrix, computed via Gauss-Jordan elimination on
     * a 4x8 augmented matrix. General-purpose (works for the perspective
     * projection matrices needed by ray casting, not just affine ones),
     * traded deliberately against the extra CPU cost since this is only
     * called once per touch-drag frame during picking, never in the
     * per-triangle hot path.
     *
     * @throws IllegalStateException if this matrix is singular (not invertible).
     */
    fun inverse(): Mat4 {
        // augmented[row] holds this matrix's row (columns 0..3) followed by
        // the corresponding identity row (columns 4..7).
        val augmented = Array(4) { row ->
            FloatArray(8).also { r ->
                for (col in 0 until 4) r[col] = values[col * 4 + row]
                r[4 + row] = 1f
            }
        }

        for (pivotIndex in 0 until 4) {
            var pivotRow = pivotIndex
            var largest = kotlin.math.abs(augmented[pivotIndex][pivotIndex])
            for (candidate in (pivotIndex + 1) until 4) {
                val magnitude = kotlin.math.abs(augmented[candidate][pivotIndex])
                if (magnitude > largest) {
                    largest = magnitude
                    pivotRow = candidate
                }
            }
            check(largest > 1e-8f) { "Mat4.inverse(): matrix is singular and cannot be inverted" }

            if (pivotRow != pivotIndex) {
                val tmp = augmented[pivotIndex]
                augmented[pivotIndex] = augmented[pivotRow]
                augmented[pivotRow] = tmp
            }

            val pivotValue = augmented[pivotIndex][pivotIndex]
            for (col in 0 until 8) augmented[pivotIndex][col] /= pivotValue

            for (row in 0 until 4) {
                if (row == pivotIndex) continue
                val factor = augmented[row][pivotIndex]
                if (factor == 0f) continue
                for (col in 0 until 8) augmented[row][col] -= factor * augmented[pivotIndex][col]
            }
        }

        val result = FloatArray(16)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                result[col * 4 + row] = augmented[row][4 + col]
            }
        }
        return Mat4(result)
    }

    companion object {

        /**
         * Wraps an existing 16-element column-major array as a [Mat4] without
         * copying. Callers must not retain a mutable reference to [values]
         * afterward — used internally by [Quaternion.toMat4] to convert a
         * rotation into matrix form.
         */
        fun fromColumnMajor(values: FloatArray): Mat4 {
            require(values.size == 16) { "Mat4 requires exactly 16 elements, got ${values.size}" }
            return Mat4(values)
        }

        fun identity(): Mat4 = Mat4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        )

        fun translation(t: Vec3): Mat4 = Mat4(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                t.x, t.y, t.z, 1f
            )
        )

        fun scale(s: Vec3): Mat4 = Mat4(
            floatArrayOf(
                s.x, 0f, 0f, 0f,
                0f, s.y, 0f, 0f,
                0f, 0f, s.z, 0f,
                0f, 0f, 0f, 1f
            )
        )

        /** Rotation about an arbitrary unit axis, angle in radians (right-handed). */
        fun rotation(axis: Vec3, angleRadians: Float): Mat4 {
            val a = axis.normalized()
            val c = cos(angleRadians)
            val s = sin(angleRadians)
            val t = 1f - c
            val x = a.x
            val y = a.y
            val z = a.z

            return Mat4(
                floatArrayOf(
                    t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0f,
                    t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0f,
                    t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0f,
                    0f, 0f, 0f, 1f
                )
            )
        }

        /**
         * Right-handed look-at view matrix, mirroring the conventions used by
         * classic OpenGL / GLU so ports of existing shader math behave as expected.
         */
        fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
            val f = (target - eye).normalized()
            val s = f.cross(up).normalized()
            val u = s.cross(f)

            return Mat4(
                floatArrayOf(
                    s.x, u.x, -f.x, 0f,
                    s.y, u.y, -f.y, 0f,
                    s.z, u.z, -f.z, 0f,
                    -s.dot(eye), -u.dot(eye), f.dot(eye), 1f
                )
            )
        }

        /** Standard OpenGL perspective projection. [fovYRadians] is the vertical field of view. */
        fun perspective(fovYRadians: Float, aspectRatio: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan(fovYRadians / 2f)
            val rangeInv = 1f / (near - far)

            return Mat4(
                floatArrayOf(
                    f / aspectRatio, 0f, 0f, 0f,
                    0f, f, 0f, 0f,
                    0f, 0f, (near + far) * rangeInv, -1f,
                    0f, 0f, near * far * rangeInv * 2f, 0f
                )
            )
        }
    }
}        
