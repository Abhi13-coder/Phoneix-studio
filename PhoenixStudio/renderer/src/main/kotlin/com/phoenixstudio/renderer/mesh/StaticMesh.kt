package com.phoenixstudio.renderer.mesh

import android.opengl.GLES30
import com.phoenixstudio.core.log.Logger
import com.phoenixstudio.renderer.shader.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "StaticMesh"

class StaticMesh(
    private val vertexData: FloatArray,
    private val indexData: ShortArray
) {

    private var vbo = 0
    private var ibo = 0
    private var vao = 0
    private val indexCount = indexData.size

    fun upload() {
        val vertexBuffer = ByteBuffer
            .allocateDirect(vertexData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
            .apply { position(0) }

        val indexBuffer = ByteBuffer
            .allocateDirect(indexData.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indexData)
            .apply { position(0) }

        val vaoHandle = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoHandle, 0)
        vao = vaoHandle[0]
        GLES30.glBindVertexArray(vao)

        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        vbo = buffers[0]
        ibo = buffers[1]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexData.size * Float.SIZE_BYTES,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indexData.size * Short.SIZE_BYTES,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        val stride = 6 * Float.SIZE_BYTES
        GLES30.glEnableVertexAttribArray(POSITION_ATTRIB_INDEX)
        GLES30.glVertexAttribPointer(POSITION_ATTRIB_INDEX, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(NORMAL_ATTRIB_INDEX)
        GLES30.glVertexAttribPointer(NORMAL_ATTRIB_INDEX, 3, GLES30.GL_FLOAT, false, stride, 3 * Float.SIZE_BYTES)

        GLES30.glBindVertexArray(0)

        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Logger.e(TAG, "GL error after upload() (vertexCount=${vertexData.size / 6}): 0x${Integer.toHexString(error)}")
        }
    }

    fun draw(shader: ShaderProgram) {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
    }

    companion object {
        private const val POSITION_ATTRIB_INDEX = 0
        private const val NORMAL_ATTRIB_INDEX = 1
    }
}            
