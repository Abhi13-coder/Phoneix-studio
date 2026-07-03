package com.phoenixstudio.renderer.camera

import com.phoenixstudio.core.math.Mat4
import com.phoenixstudio.core.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A desktop-editor-style orbit camera: the eye orbits a [target] point on a
 * sphere of radius [distance], parameterized by [azimuth] (rotation around
 * the world Y axis) and [elevation] (angle up from the XZ plane). This is
 * the same control scheme as Blender/Unreal's middle-mouse orbit, adapted
 * for touch: one-finger drag orbits, two-finger drag pans, pinch zooms.
 *
 * All angles are radians. State mutation happens on the GL thread inside
 * [PhoenixRenderer.onDrawFrame] in response to gesture deltas queued from
 * the UI thread by [com.phoenixstudio.renderer.gl.ViewportTouchController].
 */
class OrbitCamera(
    var target: Vec3 = Vec3.ZERO,
    var distance: Float = 6f,
    var azimuth: Float = PI.toFloat() / 4f,
    var elevation: Float = PI.toFloat() / 6f
) {

    /** Orbits the camera around [target]. Positive [deltaAzimuth] rotates rightward. */
    fun orbit(deltaAzimuth: Float, deltaElevation: Float) {
        azimuth += deltaAzimuth
        elevation = (elevation + deltaElevation).coerceIn(MIN_ELEVATION, MAX_ELEVATION)
    }

    /** Pans [target] along the camera's local right/up plane, keeping distance fixed. */
    fun pan(deltaRight: Float, deltaUp: Float) {
        val eye = eyePosition()
        val forward = (target - eye).normalized()
        val right = forward.cross(Vec3.UP).normalized()
        val up = right.cross(forward).normalized()
        target += right * deltaRight + up * deltaUp
    }

    /** Zooms by scaling [distance]; [factor] > 1 zooms out, < 1 zooms in. */
    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    fun eyePosition(): Vec3 {
        val x = target.x + distance * cos(elevation) * sin(azimuth)
        val y = target.y + distance * sin(elevation)
        val z = target.z + distance * cos(elevation) * cos(azimuth)
        return Vec3(x, y, z)
    }

    fun viewMatrix(): Mat4 = Mat4.lookAt(eyePosition(), target, Vec3.UP)

    fun projectionMatrix(aspectRatio: Float): Mat4 =
        Mat4.perspective(FIELD_OF_VIEW_RADIANS, aspectRatio, NEAR_PLANE, FAR_PLANE)

    companion object {
        private const val MIN_ELEVATION = -PI.toFloat() / 2f + 0.05f
        private const val MAX_ELEVATION = PI.toFloat() / 2f - 0.05f
        private const val MIN_DISTANCE = 1.5f
        private const val MAX_DISTANCE = 50f
        private const val FIELD_OF_VIEW_RADIANS = PI.toFloat() / 3f // 60 degrees
        private const val NEAR_PLANE = 0.05f
        private const val FAR_PLANE = 1000f
    }
}
