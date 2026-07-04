package com.phoenixstudio.renderer.camera

import com.phoenixstudio.core.math.Mat4
import com.phoenixstudio.core.math.Ray
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

    /**
     * Converts a screen-space touch coordinate into a world-space [Ray]
     * from the camera through that pixel — the standard "unprojection"
     * technique used for tap-to-select picking. [screenX]/[screenY] are in
     * pixels with the origin at the top-left (matching Android's
     * [android.view.MotionEvent] convention), and [viewportWidth]/
     * [viewportHeight] should be the GL surface's current pixel size.
     *
     * Works by unprojecting a single point on the screen ray (at NDC
     * z = 0, i.e. midway through the depth range) via the inverse
     * view-projection matrix, then building a ray from the known eye
     * position through that point — any point along a perspective camera's
     * view ray for a given pixel lies on the same line as the eye, so one
     * unprojected point plus the eye is sufficient; no second point at a
     * different depth is needed.
     */
    fun screenPointToRay(
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int
    ): Ray {
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        val viewProjection = projectionMatrix(aspect) * viewMatrix()
        val inverseViewProjection = viewProjection.inverse()

        val ndcX = (2f * screenX / viewportWidth) - 1f
        val ndcY = 1f - (2f * screenY / viewportHeight) // screen Y is top-down; NDC Y is bottom-up

        val worldPoint = inverseViewProjection.transformPointPerspective(Vec3(ndcX, ndcY, 0f))
        val eye = eyePosition()
        return Ray(eye, (worldPoint - eye).normalized())
    }

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
