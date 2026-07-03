package com.phoenixstudio.renderer.mesh

import android.opengl.GLES30
import com.phoenixstudio.renderer.shader.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A unit cube (extents -0.5..0.5 on each axis) with 24 vertices — four
 * per face rather than a shared 8 — so each face gets a flat normal for
 * correct diffuse shading. Geometry is uploaded to a single interleaved
 * VBO once in [upload] and never rebuilt afterward; per-frame draw calls
 * only bind and issue [GLES30.glDrawElements], allocating nothing.
 */
class CubeMesh {

    private var vbo = 0
    private var ibo = 0
    private var vao = 0
    private val indexCount: Int

    // Interleaved position(3) + normal(3) per vertex, 4 vertices per face * 6 faces.
    private val vertexData: FloatArray
    private val indexData: ShortArray

    init {
        val faces = listOf(
            // position offsets for 4 corners, normal for the face
            Face(
                corners = listOf(
                    floatArrayOf(-0.5f, -0.5f, 0.5f),
                    floatArrayOf(0.5f, -0.5f, 0.5f),
                    floatArrayOf(0.5f, 0.5f, 0.5f),
                    floatArrayOf(-0.5f, 0.5f, 0.5f)
                ),
                normal = floatArrayOf(0f, 0f, 1f)
            ),
            Face(
                corners = listOf(
                    floatArrayOf(0.5f, -0.5f, -0.5f),
                    floatArrayOf(-0.5f, -0.5f, -0.5f),
                    floatArrayOf(-0.5f, 0.5f, -0.5f),
                    floatArrayOf(0.5f, 0.5f, -0.5f)
                ),
                normal = floatArrayOf(0f, 0f, -1f)
            ),
            Face(
                corners = listOf(
                    floatArrayOf(-0.5f, 0.5f, 0.5f),
                    floatArrayOf(0.5f, 0.5f, 0.5f),
                    floatArrayOf(0.5f, 0.5f, -0.5f),
                    floatArrayOf(-0.5f, 0.5f, -0.5f)
                ),
                normal = floatArrayOf(0f, 1f, 0f)
            ),
            Face(
                corners = listOf(
                    floatArrayOf(-0.5f, -0.5f, -0.5f),
                    floatArrayOf(0.5f, -0.5f, -0.5f),
                    floatArrayOf(0.5f, -0.5f, 0.5f),
                    floatArrayOf(-0.5f, -0.5f, 0.5f)
                ),
                normal = floatArrayOf(0f, -1f, 0f)
            ),
            Face(
                corners = listOf(
                    floatArrayOf(0.5f, -0.5f, 0.5f),
                    floatArrayOf(0.5f, -0.5f, -0.5f),
                    floatArrayOf(0.5f, 0.5f, -0.5f),
                    floatArrayOf(0.5f, 0.5f, 0.5f)
                ),
                normal = floatArrayOf(1f, 0f, 0f)
            ),
            Face(
                corners = listOf(
                    floatArrayOf(-0.5f, -0.5f, -0.5f),
                    floatArrayOf(-0.5f, -0.5f, 0.5f),
                    floatArrayOf(-0.5f, 0.5f, 0.5f),
                    floatArrayOf(-0.5f, 0.5f, -0.5f)
                ),
                normal = floatArrayOf(-1f, 0f, 0f)
            )
        )

        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        var baseIndex: Short = 0

        for (face in faces) {
            for (corner in face.corners) {
                vertices.add(corner[0])
                vertices.add(corner[1])
                vertices.add(corner[2])
                vertices.add(face.normal[0])
                vertices.add(face.normal[1])
                vertices.add(face.normal[2])
            }
            // Two triangles per quad face.
            indices.add(baseIndex)
            indices.add((baseIndex + 1).toShort())
            indices.add((baseIndex + 2).toShort())
            indices.add(baseIndex)
            indices.add((baseIndex + 2).toShort())
            indices.add((baseIndex + 3).toShort())
            baseIndex = (baseIndex + 4).toShort()
        }

        vertexData = vertices.toFloatArray()
        indexData = indices.toShortArray()
        indexCount = indexData.size
    }

    /** Allocates GPU buffers. Must be called once with a current GL context, e.g. onSurfaceCreated. */
    fun upload() {
        val vertexBuffer: FloatBuffer = ByteBuffer
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

    private data class Face(val corners: List<FloatArray>, val normal: FloatArray)

    companion object {
        private const val POSITION_ATTRIB_INDEX = 0
        private const val NORMAL_ATTRIB_INDEX = 1
    }
}
