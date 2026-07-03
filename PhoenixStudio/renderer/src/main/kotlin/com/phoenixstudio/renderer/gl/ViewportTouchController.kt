package com.phoenixstudio.renderer.gl

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.phoenixstudio.renderer.camera.OrbitCamera

/**
 * Translates raw touch input on the viewport into [OrbitCamera] gestures:
 *
 *  - One finger drag  -> orbit around the target
 *  - Two finger drag  -> pan the target
 *  - Pinch            -> zoom (dolly distance)
 *
 * The camera fields it mutates ([OrbitCamera.orbit], [OrbitCamera.pan],
 * [OrbitCamera.zoom]) are plain floats read once per frame by the GL thread
 * in [PhoenixRenderer.onDrawFrame]; a torn read at worst produces one
 * slightly-off frame, which is an acceptable trade for not needing a lock
 * on every touch event and every frame.
 */
class ViewportTouchController(
    context: Context,
    private val camera: OrbitCamera,
    private val onCameraChanged: () -> Unit
) : View.OnTouchListener {

    private var lastX = 0f
    private var lastY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var activePointerCount = 0

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // scaleFactor > 1 means fingers moved apart (zoom in), so invert
                // for OrbitCamera.zoom where factor > 1 means zoom out.
                val zoomFactor = 1f / detector.scaleFactor
                camera.zoom(zoomFactor)
                onCameraChanged()
                return true
            }
        }
    )

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerCount = event.pointerCount
                lastX = event.getX(0)
                lastY = event.getY(0)
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
                    camera.pan(-dx * PAN_SPEED, dy * PAN_SPEED)
                    lastFocusX = focusX
                    lastFocusY = focusY
                    onCameraChanged()
                } else if (event.pointerCount == 1) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    val dx = x - lastX
                    val dy = y - lastY
                    camera.orbit(-dx * ORBIT_SPEED, dy * ORBIT_SPEED)
                    lastX = x
                    lastY = y
                    onCameraChanged()
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
                activePointerCount = event.pointerCount - 1
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerCount = 0
            }
        }
        return true
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
    }
}
