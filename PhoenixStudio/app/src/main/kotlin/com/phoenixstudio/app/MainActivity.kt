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
        buildInitialSceneAndAssignToRenderer()
    }

    /**
     * Builds a small starter scene, round-trips it through [SceneSerializer]
     * (proving save/load actually works, not just that it compiles), and
     * assigns the *reloaded* copy to [PhoenixRenderer.scene] so what's on
     * screen is genuinely coming from the scene graph + JSON pipeline —
     * not a hardcoded renderer mesh anymore.
     *
     * Two cubes at different positions are used (rather than one, matching
     * the old hardcoded look) specifically so the change is visually
     * obvious: if this scene weren't actually driving the renderer, you'd
     * still see one cube at the origin instead of two side by side.
     */
    private fun buildInitialSceneAndAssignToRenderer() {
        val scene = Scene(name = "Starter Scene")

        val cubeA = SceneObject(name = "Cube A", type = SceneObjectType.CUBE)
        cubeA.transform.position = Vec3(-1f, 0.5f, 0f)
        scene.addRootObject(cubeA)

        val cubeB = SceneObject(name = "Cube B", type = SceneObjectType.CUBE)
        cubeB.transform.position = Vec3(1.2f, 0.5f, 0.4f)
        scene.addRootObject(cubeB)

        val json = SceneSerializer.toJson(scene)
        Logger.i(TAG, "Serialized starter scene:\n$json")

        val reloaded = SceneSerializer.fromJson(json)
        Logger.i(TAG, "Reloaded scene '${reloaded.name}' has ${reloaded.objectCount()} object(s)")

        viewport.renderer.scene = reloaded
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
