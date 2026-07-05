package com.phoenixstudio.renderer.mesh

import android.opengl.GLES30
import com.phoenixstudio.renderer.shader.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A GPU-uploadable mesh built from arbitrary interleaved position(3)+normal(3)
 * vertex data and a matching index buffer — e.g. the output of
 * [com.phoenixstudio.assets.obj.ObjParser], for imported models, as opposed
 * to [CubeMesh]'s hand-authored geometry.
 *
 * Shares the same upload/draw structure as [CubeMesh] (VAO + interleaved
 * VBO + IBO, same attribute layout) — duplicated here rather than
 * refactored into a shared base class for now. Once a third mesh type
 * needs the same logic, extracting a common base becomes worthwhile; with
 * just two, the duplication is small and keeps each class simple to read
 * on its own.
 */
class StaticMesh(
    private val vertexData: FloatArray,
    private val indexData: ShortArray
) {

    private var vbo = 0
    private var ibo = 0
    private var vao = 0
    private val indexCount = indexData.size

    /** Allocates GPU buffers. Must be called with a current GL context — i.e. via GLSurfaceView.queueEvent, never from the UI thread directly. */
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
