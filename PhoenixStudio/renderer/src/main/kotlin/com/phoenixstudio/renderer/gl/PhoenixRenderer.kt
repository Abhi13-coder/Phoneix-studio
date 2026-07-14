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

class PhoenixRenderer(val camera: OrbitCamera) : GLSurfaceView.Renderer {

    val fpsCounter = FpsCounter()

    var scene: Scene? = null

    var selectedObject: SceneObject? = null

    private val modelMeshes = mutableMapOf<String, StaticMesh>()
    private val loggedModelDrawPaths = mutableSetOf<String>()
    private val loggedDrawErrorPaths = mutableSetOf<String>()

    private lateinit var litShader: ShaderProgram
    private lateinit var unlitShader: ShaderProgram
    private lateinit var cubeMesh: CubeMesh
    private lateinit var gridMesh: GridMesh

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    var onFrameRendered: ((fps: Int) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Logger.i(TAG, "Surface created; GL vendor=${GLES30.glGetString(GLES30.GL_VENDOR)} renderer=${GLES30.glGetString(GLES30.GL_RENDERER)}")

        GLES30.glClearColor(0.11f, 0.11f, 0.13f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

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
                    val path = obj.modelAssetPath
                    val mesh = path?.let { modelMeshes[it] }

                    if (path != null && path !in loggedModelDrawPaths) {
                        loggedModelDrawPaths.add(path)
                        val m = obj.worldMatrix()
                        Logger.i(
                            TAG,
                            "Draw MODEL '$path': meshFound=${mesh != null} enabled=${obj.enabled} " +
                                "worldPos=(${m.values[12]}, ${m.values[13]}, ${m.values[14]})"
                        )
                    }

                    if (mesh != null) {
                        drawLitMesh(obj.worldMatrix(), view, projection, isSelected) { mesh.draw(it) }
                        if (path !in loggedDrawErrorPaths) {
                            loggedDrawErrorPaths.add(path!!)
                            val error = GLES30.glGetError()
                            if (error != GLES30.GL_NO_ERROR) {
                                Logger.e(TAG, "GL error after drawing MODEL '$path': 0x${Integer.toHexString(error)}")
                            } else {
                                Logger.i(TAG, "No GL error after drawing MODEL '$path'")
                            }
                        }
                    }
                }
                SceneObjectType.EMPTY -> Unit
            }
        }
    }

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

    fun registerModelMesh(assetPath: String, mesh: StaticMesh) {
        modelMeshes[assetPath] = mesh
    }
}            
