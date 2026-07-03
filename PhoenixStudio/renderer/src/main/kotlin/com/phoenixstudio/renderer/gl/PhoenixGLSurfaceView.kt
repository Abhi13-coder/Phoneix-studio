package com.phoenixstudio.renderer.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.phoenixstudio.renderer.camera.OrbitCamera

/**
 * The engine viewport widget: a [GLSurfaceView] requesting an OpenGL ES 3.2
 * context, hosting [PhoenixRenderer], and wired to [ViewportTouchController]
 * for orbit/pan/zoom gestures. This is the class the editor's center panel
 * instantiates directly in its layout XML.
 */
class PhoenixGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val camera = OrbitCamera()
    val renderer = PhoenixRenderer(camera)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        val touchController = ViewportTouchController(
            context = context,
            camera = camera,
            onCameraChanged = { /* Continuous render mode already redraws every frame. */ }
        )
        setOnTouchListener(touchController)
    }
}
