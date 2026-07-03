package com.phoenixstudio.renderer.shader

import android.opengl.GLES30
import com.phoenixstudio.core.log.Logger

private const val TAG = "ShaderProgram"

/**
 * Compiles a vertex + fragment shader pair and links them into a GL program.
 *
 * Must be constructed on a thread with a current GL context — in practice
 * this means from within [android.opengl.GLSurfaceView.Renderer.onSurfaceCreated]
 * or later callbacks, never from the UI thread.
 */
class ShaderProgram(vertexSource: String, fragmentSource: String) {

    val programId: Int = GLES30.glCreateProgram()

    private val attributeLocations = mutableMapOf<String, Int>()
    private val uniformLocations = mutableMapOf<String, Int>()

    init {
        val vertexShader = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            throw IllegalStateException("Failed to link shader program: $log")
        }

        // Shaders are linked into the program now; the standalone objects
        // are no longer needed and would otherwise leak GL resources.
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == GLES30.GL_FALSE) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            val typeName = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw IllegalStateException("Failed to compile $typeName shader: $log")
        }
        return shader
    }

    fun use() {
        GLES30.glUseProgram(programId)
    }

    /** Attribute locations are cached per-program since they never change after linking. */
    fun attributeLocation(name: String): Int = attributeLocations.getOrPut(name) {
        val location = GLES30.glGetAttribLocation(programId, name)
        if (location == -1) {
            Logger.w(TAG, "Attribute '$name' not found or optimized out of program $programId")
        }
        location
    }

    fun uniformLocation(name: String): Int = uniformLocations.getOrPut(name) {
        val location = GLES30.glGetUniformLocation(programId, name)
        if (location == -1) {
            Logger.w(TAG, "Uniform '$name' not found or optimized out of program $programId")
        }
        location
    }

    fun setUniformMat4(name: String, values: FloatArray) {
        GLES30.glUniformMatrix4fv(uniformLocation(name), 1, false, values, 0)
    }

    fun setUniformVec3(name: String, x: Float, y: Float, z: Float) {
        GLES30.glUniform3f(uniformLocation(name), x, y, z)
    }

    fun setUniformFloat(name: String, value: Float) {
        GLES30.glUniform1f(uniformLocation(name), value)
    }

    fun release() {
        GLES30.glDeleteProgram(programId)
    }
}
