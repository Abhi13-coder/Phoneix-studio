package com.phoenixstudio.renderer.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.core.math.Mat4
import com.phoenixstudio.core.math.Vec3
import com.phoenixstudio.renderer.camera.OrbitCamera
import com.phoenixstudio.renderer.mesh.CubeMesh
import com.phoenixstudio.renderer.mesh.GridMesh
import com.phoenixstudio.renderer.mesh.StaticMesh
import com.phoenixstudio.renderer.shader.ShaderProgram
import com.phoenixstudio.renderer.shader.Shaders
import com.phoenixstudio.scene.Scene
import com.phoenixstudio.scene.SceneObject
import com.phoenixstudio.scene.SceneObjectType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "PhoenixRenderer"

/**
 * The engine's root [GLSurfaceView.Renderer]. Owns the two shader programs
 * and the mesh types the viewport currently knows how to draw (grid, cube,
 * and imported models via [StaticMesh]), plus the FPS counter shown by the
 * toolbar. GL resource creation happens in [onSurfaceCreated] only, so a
 * context loss (e.g. app backgrounded and the surface recreated) correctly
 * re-uploads everything rather than touching now-invalid handles.
 *
 * Draws from [scene] when one is assigned: every enabled [SceneObjectType.CUBE]
 * or [SceneObjectType.MODEL] object in the scene is drawn at its
 * [com.phoenixstudio.scene.SceneObject.worldMatrix], so editing a scene's
 * objects (moving, adding, removing) is immediately visible on the next
 * frame with no other renderer changes needed. If [scene] is null, falls
 * back to drawing a single cube at the origin, so the viewport never
 * regresses to a blank screen while a scene is loading.
 *
 * [SceneObjectType.MODEL] objects are drawn by looking up their
 * [SceneObject.modelAssetPath] in [modelMeshes] — a mesh only appears once
 * something has called [registerModelMesh] for that path (parsing an OBJ
 * file and building a [StaticMesh] is the caller's job, e.g.
 * `MainActivity`; this class only knows how to draw a mesh once it has
 * one, not how to obtain one).
 *
 * [selectedObject], if set, is drawn in a highlighted color so tap-to-select
 * (handled by [ViewportTouchController]) has visible feedback even before
 * the `:ui` module's inspector panel exists.
 */
class PhoenixRenderer(val camera: OrbitCamera) : GLSurfaceView.Renderer {

    val fpsCounter = FpsCounter()

    /** Assign a scene to have the viewport draw its objects; see class doc for fallback behavior. */
    var scene: Scene? = null

    /** The currently tap-selected object, if any; drawn highlighted. Set by [ViewportTouchController]. */
    var selectedObject: SceneObject? = null

    private val modelMeshes = mutableMapOf<String, StaticMesh>()

    private lateinit var litShader: ShaderProgram
    private lateinit var unlitShader: ShaderProgram
    private lateinit var cubeMesh: CubeMesh
    private lateinit var gridMesh: GridMesh

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    /** Exposed so the editor UI (FPS label in the toolbar) can poll the latest value each frame. */
    var onFrameRendered: ((fps: Int) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Logger.i(TAG, "Surface created; GL vendor=${GLES30.glGetString(GLES30.GL_VENDOR)} renderer=${GLES30.glGetString(GLES30.GL_RENDERER)}")

        GLES30.glClearColor(0.11f, 0.11f, 0.13f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        // Back-face culling intentionally disabled: imported OBJ meshes
        // (hand-authored or downloaded) can't be guaranteed to have
        // consistent front-facing winding, and at our current triangle
        // budgets (thousands, not millions) the cost of drawing back
        // faces too is negligible. Revisit only if profiling on real
        // scenes shows this actually matters.

        litShader = ShaderProgram(Shaders.LIT_VERTEX_SOURCE, Shaders.LIT_FRAGMENT_SOURCE)
        unlitShader = ShaderProgram(Shaders.UNLIT_VERTEX_SOURCE, Shaders.UNLIT_FRAGMENT_SOURCE)

        cubeMesh = CubeMesh().apply { upload() }
        gridMesh = GridMesh().apply { upload() }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        val view = camera.viewMatrix()
        val projection = camera.projectionMatrix(aspect)

        drawGrid(view, projection)
        drawSceneOrFallback(view, projection)

        fpsCounter.tick()
        onFrameRendered?.invoke(fpsCounter.currentFps)
    }

    private fun drawGrid(view: Mat4, projection: Mat4) {
        unlitShader.use()
        unlitShader.setUniformMat4("uView", view.values)
        unlitShader.setUniformMat4("uProjection", projection.values)
        unlitShader.setUniformVec3("uColor", 0.35f, 0.35f, 0.4f)
        gridMesh.draw(unlitShader)
    }

    private fun drawSceneOrFallback(view: Mat4, projection: Mat4) {
        val currentScene = scene
        if (currentScene == null) {
            drawLitMesh(Mat4.identity(), view, projection, isSelected = false) { cubeMesh.draw(it) }
            return
        }

        currentScene.forEachObject { obj ->
            if (!obj.enabled) return@forEachObject
            val isSelected = obj === selectedObject
            when (obj.type) {
                SceneObjectType.CUBE -> {
                    drawLitMesh(obj.worldMatrix(), view, projection, isSelected) { cubeMesh.draw(it) }
                }
                SceneObjectType.MODEL -> {
                    val mesh = obj.modelAssetPath?.let { modelMeshes[it] }
                    if (mesh != null) {
                        drawLitMesh(obj.worldMatrix(), view, projection, isSelected) { mesh.draw(it) }
                    }
                    // If the mesh hasn't been registered yet (still loading,
                    // or load failed), the object is silently skipped this
                    // frame rather than crashing or drawing a placeholder.
                }
                SceneObjectType.EMPTY -> Unit
            }
        }
    }

    /** Sets the standard lit-shader uniforms, then delegates to [drawMesh] to actually issue the draw call for whichever mesh is being rendered. */
    private fun drawLitMesh(model: Mat4, view: Mat4, projection: Mat4, isSelected: Boolean, drawMesh: (ShaderProgram) -> Unit) {
        litShader.use()
        litShader.setUniformMat4("uModel", model.values)
        litShader.setUniformMat4("uView", view.values)
        litShader.setUniformMat4("uProjection", projection.values)
        if (isSelected) {
            litShader.setUniformVec3("uBaseColor", 1f, 0.85f, 0.25f)
        } else {
            litShader.setUniformVec3("uBaseColor", 0.85f, 0.45f, 0.2f)
        }
        val lightDir = Vec3(-0.4f, -1f, -0.3f).normalized()
        litShader.setUniformVec3("uLightDirection", lightDir.x, lightDir.y, lightDir.z)
        drawMesh(litShader)
    }

    /**
     * Registers an already-[StaticMesh.upload]ed mesh under [assetPath], so
     * any [SceneObjectType.MODEL] object whose [SceneObject.modelAssetPath]
     * matches will be drawn using it starting the next frame.
     *
     * Must be called from the GL thread (e.g. via
     * [android.opengl.GLSurfaceView.queueEvent]), since [mesh] is expected
     * to already have GPU buffers allocated, which requires a current GL
     * context.
     */
    fun registerModelMesh(assetPath: String, mesh: StaticMesh) {
        modelMeshes[assetPath] = mesh
    }
}                
