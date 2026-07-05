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
import com.phoenixstudio.project.Project
import com.phoenixstudio.project.ProjectManager
import com.phoenixstudio.renderer.gl.PhoenixGLSurfaceView
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.SceneObject
import com.phoenixstudio.scene.SceneObjectType

private const val TAG = "MainActivity"
private const val SANDBOX_PROJECT_NAME = "Sandbox"
private const val MAIN_SCENE_FILE_NAME = "Main.json"

/**
 * Entry point for this bootstrap round of Phoenix Studio.
 *
 * Hosts [PhoenixGLSurfaceView] full-screen with a minimal FPS readout.
 * This activity intentionally does not yet contain the editor chrome
 * (project explorer, inspector, console, toolbar) — that arrives with the
 * `:ui` module in a later bootstrap round, at which point this class will
 * host a `MainEditorLayout` instead of the raw viewport directly.
 *
 * As of this round, the scene shown is loaded from — and saved back to —
 * a real project on disk via [ProjectManager], rather than being rebuilt
 * from scratch on every launch. There is exactly one hardcoded project
 * ("Sandbox") and one scene file ("Main.json") for now; real project
 * creation/selection UI arrives with `:ui`.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewport: PhoenixGLSurfaceView
    private lateinit var fpsLabel: TextView

    private lateinit var projectManager: ProjectManager
    private lateinit var currentProject: Project
    private lateinit var currentScene: Scene

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

        projectManager = ProjectManager(this)
        currentProject = projectManager.createProject(SANDBOX_PROJECT_NAME)
        loadOrCreateSceneAndAssignToRenderer()
    }

    /**
     * Loads "Main.json" from the Sandbox project if it exists on disk
     * (i.e. this isn't the first launch, or a previous session saved
     * something), otherwise builds a fresh starter scene. Either way, the
     * result is assigned to the renderer so it's what actually appears in
     * the viewport.
     */
    private fun loadOrCreateSceneAndAssignToRenderer() {
        val loaded = projectManager.loadScene(currentProject, MAIN_SCENE_FILE_NAME)
        if (loaded != null) {
            currentScene = loaded
            Logger.i(TAG, "Loaded '${currentScene.name}' from disk with ${currentScene.objectCount()} object(s)")
        } else {
            currentScene = buildStarterScene()
            Logger.i(TAG, "No saved scene found on disk; created '${currentScene.name}' instead")
        }
        viewport.renderer.scene = currentScene
    }

    /** Two cubes at different positions, used only when no saved scene exists yet. */
    private fun buildStarterScene(): Scene {
        val scene = Scene(name = "Starter Scene")

        val cubeA = SceneObject(name = "Cube A", type = SceneObjectType.CUBE)
        cubeA.transform.position = Vec3(-1f, 0.5f, 0f)
        scene.addRootObject(cubeA)

        val cubeB = SceneObject(name = "Cube B", type = SceneObjectType.CUBE)
        cubeB.transform.position = Vec3(1.2f, 0.5f, 0.4f)
        scene.addRootObject(cubeB)

        return scene
    }

    /**
     * Writes [currentScene] back to `Main.json` so any changes made during
     * this session — right now, that means any object dragged to a new
     * position via [com.phoenixstudio.renderer.gl.ViewportTouchController] —
     * are still there the next time the app launches.
     */
    private fun saveCurrentSceneToDisk() {
        if (!::currentScene.isInitialized || !::currentProject.isInitialized) return
        projectManager.saveScene(currentProject, currentScene, MAIN_SCENE_FILE_NAME)
        Logger.i(TAG, "Saved '${currentScene.name}' to disk (${currentScene.objectCount()} object(s))")
    }

    override fun onResume() {
        super.onResume()
        viewport.onResume()
    }

    override fun onPause() {
        saveCurrentSceneToDisk()
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
}* [WindowCompat.setDecorFitsSystemWindows] with `false` must be called
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
