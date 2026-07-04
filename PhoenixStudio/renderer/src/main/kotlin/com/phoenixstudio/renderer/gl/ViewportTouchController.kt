package com.phoenixstudio.renderer.gl

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.phoenixstudio.core.math.Ray
import com.phoenixstudio.core.math.Vec3
import com.phoenixstudio.scene.picking.Picking
import kotlin.math.hypot

/**
 * Translates raw touch input on the viewport into camera gestures and, new
 * in this round, scene object selection/manipulation:
 *
 *  - Tap (minimal finger movement)     -> select the object under the tap, if any
 *  - One finger drag, nothing selected -> orbit the camera around its target
 *  - One finger drag, starting on the
 *    currently selected object         -> move that object instead of orbiting
 *  - Two finger drag                   -> pan the camera target
 *  - Pinch                             -> zoom (dolly distance)
 *
 * Holds a reference to the whole [PhoenixRenderer] rather than just its
 * camera, since picking needs the renderer's current [PhoenixRenderer.scene]
 * and needs to write [PhoenixRenderer.selectedObject] — reading both
 * through the renderer means this controller always sees whatever scene is
 * currently assigned, even if it's swapped out after construction.
 */
class ViewportTouchController(
    context: Context,
    private val renderer: PhoenixRenderer
) : View.OnTouchListener {

    private var lastX = 0f
    private var lastY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var downX = 0f
    private var downY = 0f
    private var viewportWidth = 1
    private var viewportHeight = 1

    /** True once an ACTION_DOWN has landed on the currently selected object, until the finger lifts. */
    private var isDraggingSelectedObject = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // scaleFactor > 1 means fingers moved apart (zoom in), so invert
                // for OrbitCamera.zoom where factor > 1 means zoom out.
                renderer.camera.zoom(1f / detector.scaleFactor)
                return true
            }
        }
    )

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        viewportWidth = view.width.coerceAtLeast(1)
        viewportHeight = view.height.coerceAtLeast(1)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.getX(0)
                downY = event.getY(0)
                lastX = downX
                lastY = downY
                lastFocusX = averageX(event)
                lastFocusY = averageY(event)

                isDraggingSelectedObject = false
                val selected = renderer.selectedObject
                if (selected != null) {
                    val ray = renderer.camera.screenPointToRay(downX, downY, viewportWidth, viewportHeight)
                    isDraggingSelectedObject = Picking.intersect(selected, ray) != null
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger arriving always hands control to pan/zoom,
                // even if the first finger had grabbed the selected object.
                isDraggingSelectedObject = false
                lastFocusX = averageX(event)
                lastFocusY = averageY(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    lastFocusX = averageX(event)
                    lastFocusY = averageY(event)
                } else if (event.pointerCount >= 2) {
                    val focusX = averageX(event)
                    val focusY = averageY(event)
                    val dx = focusX - lastFocusX
                    val dy = focusY - lastFocusY
                    // Screen-space pixels to world units; PAN_SPEED tuned for
                    // comfortable one-handed use on a ~6" 720p phone panel.
                    renderer.camera.pan(-dx * PAN_SPEED, dy * PAN_SPEED)
                    lastFocusX = focusX
                    lastFocusY = focusY
                } else if (isDraggingSelectedObject) {
                    moveSelectedObjectTo(event.getX(0), event.getY(0))
                } else if (event.pointerCount == 1) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    val dx = x - lastX
                    val dy = y - lastY
                    renderer.camera.orbit(-dx * ORBIT_SPEED, dy * ORBIT_SPEED)
                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Recompute anchors from the remaining pointer(s) so the next
                // MOVE doesn't jump using a stale finger's last position.
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastX = event.getX(remainingIndex)
                    lastY = event.getY(remainingIndex)
                }
                lastFocusX = averageX(event)
                lastFocusY = averageY(event)
            }

            MotionEvent.ACTION_UP -> {
                if (!isDraggingSelectedObject) {
                    val upX = event.getX(0)
                    val upY = event.getY(0)
                    val travelled = hypot((upX - downX).toDouble(), (upY - downY).toDouble()).toFloat()
                    if (travelled <= TAP_SLOP_PIXELS) {
                        trySelectAt(upX, upY)
                    }
                }
                isDraggingSelectedObject = false
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingSelectedObject = false
            }
        }
        return true
    }

    /** Casts a ray through (x, y) and updates [PhoenixRenderer.selectedObject] with whatever it hits, if anything. */
    private fun trySelectAt(x: Float, y: Float) {
        val scene = renderer.scene ?: return
        val ray = renderer.camera.screenPointToRay(x, y, viewportWidth, viewportHeight)
        renderer.selectedObject = Picking.raycastClosest(scene, ray)
    }

    /**
     * Moves [PhoenixRenderer.selectedObject] so it tracks the finger at
     * screen coordinate (x, y), by intersecting the new touch ray with a
     * plane parallel to the camera's view direction, passing through the
     * object's current position. This is the standard "drag on a
     * screen-facing plane" technique — it keeps the object under the
     * finger regardless of camera angle, rather than moving it along a
     * single fixed world axis.
     *
     * Sets [com.phoenixstudio.scene.Transform.position] directly in local
     * space; for a root-level object (no parent) that's equivalent to
     * world space, which covers every object in the current starter scene.
     * An object with a parent would be dragged relative to that parent's
     * orientation instead — a reasonable, well-defined behavior, just not
     * one exercised yet since nothing in the bootstrap scenes is parented.
     */
    private fun moveSelectedObjectTo(x: Float, y: Float) {
        val obj = renderer.selectedObject ?: return
        val camera = renderer.camera
        val ray = camera.screenPointToRay(x, y, viewportWidth, viewportHeight)

        val planeNormal = (camera.target - camera.eyePosition()).normalized()
        val planePoint = obj.transform.position

        val newPosition = intersectPlane(ray, planePoint, planeNormal) ?: return
        obj.transform.position = newPosition
    }

    /** Ray/plane intersection; returns null if the ray is parallel to the plane or the hit is behind the ray's origin. */
    private fun intersectPlane(ray: Ray, planePoint: Vec3, planeNormal: Vec3): Vec3? {
        val denominator = ray.direction.dot(planeNormal)
        if (kotlin.math.abs(denominator) < 1e-6f) return null

        val t = (planePoint - ray.origin).dot(planeNormal) / denominator
        if (t < 0f) return null

        return ray.origin + ray.direction * t
    }

    private fun averageX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getX(i)
        return sum / event.pointerCount
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount
    }

    companion object {
        private const val ORBIT_SPEED = 0.008f
        private const val PAN_SPEED = 0.01f
        private const val TAP_SLOP_PIXELS = 20f
    }
}
