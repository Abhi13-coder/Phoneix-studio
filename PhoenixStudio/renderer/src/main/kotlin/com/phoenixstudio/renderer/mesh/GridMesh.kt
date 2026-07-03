package com.phoenixstudio.renderer.mesh

import android.opengl.GLES30
import com.phoenixstudio.renderer.shader.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A flat reference grid on the XZ plane, centered at the origin, drawn with
 * [GLES30.GL_LINES]. Line count is fixed at construction time — the editor
 * does not currently support resizing the grid at runtime, which keeps this
 * mesh as simple and allocation-free as [CubeMesh].
 */
class GridMesh(private val halfExtent: Float = 10f, private val divisions: Int = 20) {

    private var vbo = 0
    private var vao = 0
    private val vertexCount: Int
    private val vertexData: FloatArray

    init {
        val lines = mutableListOf<Float>()
        val step = (halfExtent * 2f) / divisions

        for (i in 0..divisions) {
            val offset = -halfExtent + i * step

            // Line parallel to X axis.
            lines.add(-halfExtent); lines.add(0f); lines.add(offset)
            lines.add(halfExtent); lines.add(0f); lines.add(offset)

            // Line parallel to Z axis.
            lines.add(offset); lines.add(0f); lines.add(-halfExtent)
            lines.add(offset); lines.add(0f); lines.add(halfExtent)
        }

        vertexData = lines.toFloatArray()
        vertexCount = vertexData.size / 3
    }

    fun upload() {
        val buffer: FloatBuffer = ByteBuffer
            .allocateDirect(vertexData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
            .apply { position(0) }

        val vaoHandle = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoHandle, 0)
        vao = vaoHandle[0]
        GLES30.glBindVertexArray(vao)

        val vboHandle = IntArray(1)
        GLES30.glGenBuffers(1, vboHandle, 0)
        vbo = vboHandle[0]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexData.size * Float.SIZE_BYTES,
            buffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glEnableVertexAttribArray(POSITION_ATTRIB_INDEX)
        GLES30.glVertexAttribPointer(POSITION_ATTRIB_INDEX, 3, GLES30.GL_FLOAT, false, 3 * Float.SIZE_BYTES, 0)

        GLES30.glBindVertexArray(0)
    }

    fun draw(shader: ShaderProgram) {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
    }

    companion object {
        private const val POSITION_ATTRIB_INDEX = 0
    }
}
