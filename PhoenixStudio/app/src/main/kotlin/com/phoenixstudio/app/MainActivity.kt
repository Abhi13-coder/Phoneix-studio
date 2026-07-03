package com.phoenixstudio.app

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.renderer.gl.PhoenixGLSurfaceView

private const val TAG = "MainActivity"

/**
 * Entry point for this bootstrap round of Phoenix Studio.
 *
 * Hosts [PhoenixGLSurfaceView] full-screen with a minimal FPS readout.
 * This activity intentionally does not yet contain the editor chrome
 * (project explorer, inspector, console, toolbar) — that arrives with the
 * `:ui` module in a later bootstrap round, at which point this class will
 * host a `MainEditorLayout` instead of the raw viewport directly.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewport: PhoenixGLSurfaceView
    private lateinit var fpsLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enterImmersiveMode()

        viewport = findViewById(R.id.viewport)
        fpsLabel = findViewById(R.id.fpsLabel)

        viewport.renderer.onFrameRendered = { fps ->
            // onFrameRendered fires on the GL thread; UI must be touched on
            // the main thread only.
            runOnUiThread {
                fpsLabel.text = getString(R.string.fps_label_format, fps)
            }
        }

        Logger.i(TAG, "Phoenix Studio viewport initialized")
    }

    override fun onResume() {
        super.onResume()
        viewport.onResume()
    }

    override fun onPause() {
        viewport.onPause()
        super.onPause()
    }

    /**
     * Hides system bars for a distraction-free editor viewport, matching
     * the "modern, professional, desktop-editor-like" UI requirement even
     * on a small phone screen.
     */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }
}
