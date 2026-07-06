package com.phoenixstudio.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.phoenixstudio.assets.obj.ObjParser
import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.core.math.Vec3
import com.phoenixstudio.filesystem.PhoenixFileSystem
import com.phoenixstudio.project.Project
import com.phoenixstudio.project.ProjectManager
import com.phoenixstudio.renderer.gl.PhoenixGLSurfaceView
import com.phoenixstudio.renderer.mesh.StaticMesh
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.SceneObject
import com.phoenixstudio.scene.SceneObjectType
import java.io.File

private const val TAG = "MainActivity"
private const val SANDBOX_PROJECT_NAME = "Sandbox"
private const val MAIN_SCENE_FILE_NAME = "Main.json"
private const val MAX_CONSOLE_LINES = 200
private const val SAMPLE_MODEL_ASSET_PATH = "models/sample_pyramid.obj"

/**
 * Entry point for Phoenix Studio, and as of this round, host of the first
 * real editor shell: toolbar, explorer, viewport, inspector, and console
 * panels (see `res/layout/activity_main.xml`).
 *
 * The explorer/inspector/console are wired to *live* data here rather than
 * being static chrome:
 *  - Explorer lists [currentScene]'s root objects; tapping one selects it
 *    in the viewport, the same as tapping it directly in 3D would.
 *  - Inspector shows the currently selected object's name and position,
 *    refreshed every frame via the existing [PhoenixGLSurfaceView]
 *    frame-rendered callback (no renderer changes needed for this).
 *  - Console mirrors [Logger] output on-device, using the sink hook that's
 *    existed since the `:core` module's first round.
 *
 * None of these panels support *editing* yet (dragging in the viewport is
 * still the only way to move an object) — that's planned for a following
 * round once inspector fields become interactive.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewport: PhoenixGLSurfaceView
    private lateinit var fpsLabel: TextView
    private lateinit var explorerList: LinearLayout
    private lateinit var inspectorObjectName: TextView
    private lateinit var inspectorPosX: EditText
    private lateinit var inspectorPosY: EditText
    private lateinit var inspectorPosZ: EditText
    private lateinit var consoleLog: TextView
    private lateinit var consoleScroll: ScrollView

    private lateinit var projectManager: ProjectManager
    private lateinit var currentProject: Project
    private lateinit var currentScene: Scene
    private lateinit var fileSystem: PhoenixFileSystem

    private val consoleLines = ArrayDeque<String>()
    private var logSink: ((Logger.Entry) -> Unit)? = null

    /**
     * Launches the system file picker (Storage Access Framework) so the
     * user can choose a `.obj` file anywhere on their phone — Downloads,
     * a cloud-synced folder, etc. — without Phoenix Studio ever needing a
     * broad storage permission; SAF grants access to just the one file
     * picked. Must be a property, not created lazily inside a click
     * handler, since [registerForActivityResult] requires being called
     * before the activity reaches STARTED.
     */
    private val openObjDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModelFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enterImmersiveMode()

        viewport = findViewById(R.id.viewport)
        fpsLabel = findViewById(R.id.fpsLabel)
        explorerList = findViewById(R.id.explorerList)
        inspectorObjectName = findViewById(R.id.inspectorObjectName)
        inspectorPosX = findViewById(R.id.inspectorPosX)
        inspectorPosY = findViewById(R.id.inspectorPosY)
        inspectorPosZ = findViewById(R.id.inspectorPosZ)
        consoleLog = findViewById(R.id.consoleLog)
        consoleScroll = findViewById(R.id.consoleScroll)

        findViewById<TextView>(R.id.importModelButton).setOnClickListener {
            openObjDocumentLauncher.launch(arrayOf("*/*"))
        }

        setUpPositionFields()

        viewport.renderer.onFrameRendered = { fps ->
            // Fires on the GL thread; UI must only be touched from the main thread.
            runOnUiThread {
                fpsLabel.text = getString(R.string.fps_label_format, fps)
                updateInspector()
            }
        }

        setUpConsole()
        Logger.i(TAG, "Phoenix Studio viewport initialized")

        fileSystem = PhoenixFileSystem(this)
        projectManager = ProjectManager(this)
        currentProject = projectManager.createProject(SANDBOX_PROJECT_NAME)
        loadOrCreateSceneAndAssignToRenderer()
        loadSampleModelAsset()
        populateExplorer()
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
     * Reads the bundled sample OBJ from Android assets, parses and
     * uploads it via [registerObjMesh] — the first real proof that Phoenix
     * Studio can render geometry it didn't have hand-coded into the
     * renderer. [importModelFromUri] does the same thing for a
     * user-picked file, sharing this same underlying registration step.
     *
     * Also ensures the scene has one [SceneObjectType.MODEL] object
     * pointing at this asset, so it's actually visible — but only adds one
     * if the loaded/saved scene doesn't already have one, so relaunching
     * the app doesn't keep appending duplicates.
     */
    private fun loadSampleModelAsset() {
        val objText = assets.open(SAMPLE_MODEL_ASSET_PATH).bufferedReader().use { it.readText() }
        registerObjMesh(SAMPLE_MODEL_ASSET_PATH, objText)

        val alreadyPresent = currentScene.rootObjects.any { it.modelAssetPath == SAMPLE_MODEL_ASSET_PATH }
        if (!alreadyPresent) {
            val modelObject = SceneObject(name = "Sample Model", type = SceneObjectType.MODEL)
            modelObject.modelAssetPath = SAMPLE_MODEL_ASSET_PATH
            modelObject.transform.position = Vec3(0f, 0.5f, -1.5f)
            currentScene.addRootObject(modelObject)
        }
    }

    /**
     * Parses [objText] via [ObjParser] and uploads the result to the GPU as
     * a [StaticMesh] registered under [assetPath]. GPU upload happens
     * inside [PhoenixGLSurfaceView.queueEvent] rather than directly here,
     * since [StaticMesh.upload] issues real OpenGL calls that require a
     * current GL context — one only exists on the GLSurfaceView's own
     * render thread, never on the UI thread this function runs on.
     */
    private fun registerObjMesh(assetPath: String, objText: String) {
        val parsed = ObjParser.parse(objText)
        logBoundingBox(assetPath, parsed.vertexData)
        viewport.queueEvent {
            val mesh = StaticMesh(parsed.vertexData, parsed.indexData)
            mesh.upload()
            viewport.renderer.registerModelMesh(assetPath, mesh)
        }
        Logger.i(TAG, "Parsed '$assetPath': ${parsed.vertexData.size / 6} vertices")
    }

    /**
     * Logs the min/max extent of a parsed mesh's positions on each axis —
     * diagnostic output to catch scale mismatches (a model authored in
     * centimeters, or at 100x/0.01x the expected size) that would
     * otherwise just show up as "the model is invisible" with no clue why.
     */
    private fun logBoundingBox(assetPath: String, vertexData: FloatArray) {
        if (vertexData.isEmpty()) return
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE

        var i = 0
        while (i < vertexData.size) {
            val x = vertexData[i]
            val y = vertexData[i + 1]
            val z = vertexData[i + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
            i += 6
        }

        Logger.i(
            TAG,
            "Bounding box for '$assetPath': " +
                "X[%.2f, %.2f] Y[%.2f, %.2f] Z[%.2f, %.2f] (size %.2f x %.2f x %.2f)".format(
                    minX, maxX, minY, maxY, minZ, maxZ,
                    maxX - minX, maxY - minY, maxZ - minZ
                )
        )
    }

    /**
     * Handles a file picked via [openObjDocumentLauncher]: reads it (SAF
     * grants temporary read access to just this one URI, no broader
     * storage permission needed), copies its contents into this project's
     * `Models/` folder so it's a real, permanent part of the project
     * rather than a dangling reference to a file outside Phoenix Studio's
     * control, then registers and adds it to the scene exactly like the
     * bundled sample model.
     *
     * Only `.obj` files are supported right now — anything else (`.glb`,
     * `.fbx`, etc.) is rejected with a console message rather than a
     * crash, since [ObjParser] would otherwise fail confusingly on
     * completely different file contents.
     */
    private fun importModelFromUri(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "imported_model.obj"
        if (!displayName.endsWith(".obj", ignoreCase = true)) {
            Logger.w(
                TAG,
                "Skipped '$displayName': Phoenix Studio can only import Wavefront OBJ (.obj) files right now"
            )
            return
        }

        val objText = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (objText == null) {
            Logger.e(TAG, "Failed to read selected file '$displayName'")
            return
        }

        val modelsDir = fileSystem.ensureProjectStructure(currentProject.name).models
        val destinationFile = File(modelsDir, displayName)
        destinationFile.writeText(objText)

        val assetPath = destinationFile.absolutePath
        registerObjMesh(assetPath, objText)

        val modelObject = SceneObject(name = displayName.removeSuffix(".obj"), type = SceneObjectType.MODEL)
        modelObject.modelAssetPath = assetPath
        modelObject.transform.position = Vec3(0f, 0.5f, 1.5f)
        currentScene.addRootObject(modelObject)
        populateExplorer()

        Logger.i(TAG, "Imported '$displayName' into project '${currentProject.name}'")
    }

    /** Looks up a content URI's human-readable filename via the standard OpenableColumns query. */
    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    /**
     * Fills the explorer panel with one row per root-level object in
     * [currentScene]. Only root objects for now — child objects will need
     * an indented/expandable tree once the scene actually uses parenting,
     * which nothing does yet (see [SceneObject.addChild] in `:scene`).
     */
    private fun populateExplorer() {
        explorerList.removeAllViews()
        for (obj in currentScene.rootObjects) {
            val row = TextView(this).apply {
                text = obj.name
                textSize = 12f
                setTextColor(getColorCompat(R.color.editor_text_primary))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 10, 4, 10)
                setOnClickListener {
                    viewport.renderer.selectedObject = obj
                    updateInspector()
                }
            }
            explorerList.addView(row)
        }
    }

    /**
     * Refreshes the inspector panel to reflect the current selection.
     * Each position field is only overwritten if the user isn't currently
     * typing in it ([EditText.hasFocus] false) — this runs every frame (via
     * [PhoenixGLSurfaceView]'s frame callback), so without that guard,
     * typing a new value would be overwritten mid-keystroke by the next
     * frame's refresh.
     */
    private fun updateInspector() {
        val selected = viewport.renderer.selectedObject
        if (selected == null) {
            inspectorObjectName.text = "No selection"
            setFieldTextIfUnfocused(inspectorPosX, "")
            setFieldTextIfUnfocused(inspectorPosY, "")
            setFieldTextIfUnfocused(inspectorPosZ, "")
            return
        }

        inspectorObjectName.text = selected.name
        val p = selected.transform.position
        setFieldTextIfUnfocused(inspectorPosX, formatAxisValue(p.x))
        setFieldTextIfUnfocused(inspectorPosY, formatAxisValue(p.y))
        setFieldTextIfUnfocused(inspectorPosZ, formatAxisValue(p.z))
    }

    private fun setFieldTextIfUnfocused(field: EditText, value: String) {
        if (!field.hasFocus() && field.text.toString() != value) {
            field.setText(value)
        }
    }

    private fun formatAxisValue(value: Float): String = "%.2f".format(value)

    /**
     * Wires each position [EditText] so typing a new value writes straight
     * to [PhoenixGLSurfaceView.renderer]'s selected object's transform —
     * this is what makes the inspector an *editor*, not just a readout.
     * Invalid/incomplete input (e.g. a lone "-" while typing a negative
     * number) is ignored rather than applied, since [String.toFloatOrNull]
     * returns null for it.
     */
    private fun setUpPositionFields() {
        inspectorPosX.addTextChangedListener(axisWatcher { text ->
            applyAxisEdit(axis = 0, text = text)
        })
        inspectorPosY.addTextChangedListener(axisWatcher { text ->
            applyAxisEdit(axis = 1, text = text)
        })
        inspectorPosZ.addTextChangedListener(axisWatcher { text ->
            applyAxisEdit(axis = 2, text = text)
        })

        val clearFocusOnDone = TextView.OnEditorActionListener { view, _, _ ->
            view.clearFocus()
            false
        }
        inspectorPosX.setOnEditorActionListener(clearFocusOnDone)
        inspectorPosY.setOnEditorActionListener(clearFocusOnDone)
        inspectorPosZ.setOnEditorActionListener(clearFocusOnDone)
    }

    private fun axisWatcher(onChanged: (CharSequence?) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged(s)
    }

    /** axis: 0 = X, 1 = Y, 2 = Z. Only applies when the corresponding field actually has focus (i.e. the user is editing it). */
    private fun applyAxisEdit(axis: Int, text: CharSequence?) {
        val field = when (axis) { 0 -> inspectorPosX; 1 -> inspectorPosY; else -> inspectorPosZ }
        if (!field.hasFocus()) return

        val selected = viewport.renderer.selectedObject ?: return
        val value = text?.toString()?.toFloatOrNull() ?: return
        val p = selected.transform.position
        selected.transform.position = when (axis) {
            0 -> Vec3(value, p.y, p.z)
            1 -> Vec3(p.x, value, p.z)
            else -> Vec3(p.x, p.y, value)
        }
    }

    /**
     * Mirrors [Logger] output into the on-screen console panel. Uses the
     * sink hook [Logger] has exposed since its very first round — this is
     * the first thing to actually consume it.
     */
    private fun setUpConsole() {
        val sink: (Logger.Entry) -> Unit = { entry ->
            runOnUiThread { appendConsoleLine(entry) }
        }
        logSink = Logger.addSink(sink)
    }

    private fun appendConsoleLine(entry: Logger.Entry) {
        val line = "[${entry.level}] ${entry.tag}: ${entry.message}"
        consoleLines.addLast(line)
        while (consoleLines.size > MAX_CONSOLE_LINES) {
            consoleLines.removeFirst()
        }
        consoleLog.text = consoleLines.joinToString("\n")
        consoleScroll.post { consoleScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun getColorCompat(colorRes: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, colorRes)

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

    override fun onDestroy() {
        logSink?.let { Logger.removeSink(it) }
        super.onDestroy()
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
        
