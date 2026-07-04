package com.phoenixstudio.app

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.core.math.Vec3
import com.phoenixstudio.renderer.gl.PhoenixGLSurfaceView
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.SceneObject
import com.phoenixstudio.scene.SceneObjectType
import com.phoenixstudio.scene.serialization.SceneSerializer

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
        runSceneSerializationDemo()
    }

    /**
     * Bootstrap-round demo of the `:scene` module: builds a small scene in
     * memory, serializes it to JSON, parses that JSON back into a new
     * [Scene], and logs both so `adb logcat -s MainActivity` (or the
     * on-device log, once the `:ui` console panel exists) shows a real
     * save/load round trip actually working, not just compiling.
     *
     * This scene is not yet connected to the renderer — [viewport] still
     * draws its own hardcoded cube — that wiring lands once `:renderer`
     * gains the ability to draw an arbitrary [Scene] instead of one fixed
     * mesh, which is planned for a following bootstrap round.
     */
    private fun runSceneSerializationDemo() {
        val scene = Scene(name = "Demo Scene")
        val cube = SceneObject(name = "Cube", type = SceneObjectType.CUBE)
        cube.transform.position = Vec3(0f, 0.5f, 0f)
        scene.addRootObject(cube)

        val json = SceneSerializer.toJson(scene)
        Logger.i(TAG, "Serialized demo scene:\n$json")

        val reloaded = SceneSerializer.fromJson(json)
        Logger.i(
            TAG,
            "Reloaded scene '${reloaded.name}' has ${reloaded.objectCount()} object(s); " +
                "first object position = ${reloaded.rootObjects.first().transform.position}"
        )
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
     *
     * [WindowCompat.setDecorFitsSystemWindows] with `false` must be called
     * *before* [WindowInsetsController.hide] on API 30+, or the window
     * never actually goes edge-to-edge — some OEM skins (MIUI in
     * particular) then leave a solid status-bar-colored strip on screen
     * even though the controller reports the bars as hidden.
     */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
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
        
