package com.phoenixstudio.assets.obj

import com.phoenixstudio.core.math.Vec3

/**
 * Parses Wavefront OBJ text into a [ParsedMesh] ready for GPU upload.
 *
 * Supports:
 *  - `v x y z` position lines
 *  - `vn x y z` normal lines
 *  - `f ...` faces with 3+ vertices (fan-triangulated if more than a
 *    triangle), where each face-vertex token may be `v`, `v/vt`, `v//vn`,
 *    or `v/vt/vn` — texture-coordinate indices are read past but not used
 *    yet, since nothing in the renderer samples textures at this point.
 *
 * Not supported (deliberately, to keep the parser simple and correct for
 * the common case rather than fully OBJ-spec-complete):
 *  - Negative (relative) vertex indices
 *  - Multiple objects/groups (`o`/`g` lines are ignored; every face in the
 *    file becomes part of one single mesh)
 *  - Materials (`mtllib`/`usemtl`/`.mtl` files)
 *
 * If a face vertex has no normal index — common in very simple hand-made
 * or auto-generated OBJ files — a flat per-face normal is computed from
 * the triangle's own geometry instead, so the mesh still renders with
 * correct (if faceted rather than smooth) lighting.
 */
object ObjParser {

    fun parse(objText: String): ParsedMesh {
        val positions = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val outVertexData = mutableListOf<Float>()
        var vertexCount = 0

        for (rawLine in objText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val tokens = line.split(WHITESPACE_REGEX)
            when (tokens.getOrNull(0)) {
                "v" -> positions.add(
                    Vec3(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                )
                "vn" -> normals.add(
                    Vec3(tokens[1].toFloat(), tokens[2].toFloat(), tokens[3].toFloat())
                )
                "f" -> {
                    val faceVertices = tokens.drop(1).map { parseFaceToken(it) }
                    vertexCount += emitFaceTriangles(faceVertices, positions, normals, outVertexData)
                }
                else -> Unit // group/object/material/etc. lines are intentionally ignored
            }
        }

        val indexData = ShortArray(vertexCount) { it.toShort() }
        return ParsedMesh(outVertexData.toFloatArray(), indexData)
    }

    /** Fan-triangulates one face (3+ vertices) and appends interleaved position+normal data for each resulting triangle corner. Returns how many vertices were emitted. */
    private fun emitFaceTriangles(
        faceVertices: List<FaceVertexRef>,
        positions: List<Vec3>,
        normals: List<Vec3>,
        outVertexData: MutableList<Float>
    ): Int {
        var emitted = 0
        for (i in 1 until faceVertices.size - 1) {
            val triangle = listOf(faceVertices[0], faceVertices[i], faceVertices[i + 1])
            val trianglePositions = triangle.map { positions[it.positionIndex] }

            // Only fall back to a computed flat normal if the file didn't
            // supply an explicit normal for every corner of this triangle.
            val flatNormal = if (triangle.any { it.normalIndex == null }) {
                computeFlatNormal(trianglePositions)
            } else {
                null
            }

            for ((corner, vertexRef) in triangle.withIndex()) {
                val position = trianglePositions[corner]
                val normal = vertexRef.normalIndex?.let { normals[it] } ?: flatNormal!!

                outVertexData.add(position.x)
                outVertexData.add(position.y)
                outVertexData.add(position.z)
                outVertexData.add(normal.x)
                outVertexData.add(normal.y)
                outVertexData.add(normal.z)
                emitted++
            }
        }
        return emitted
    }

    private fun computeFlatNormal(triangle: List<Vec3>): Vec3 {
        val edge1 = triangle[1] - triangle[0]
        val edge2 = triangle[2] - triangle[0]
        return edge1.cross(edge2).normalized()
    }

    /** Parses one face-vertex token (e.g. "3", "3/1", "3//2", "3/1/2") into 0-based position/normal indices. */
    private fun parseFaceToken(token: String): FaceVertexRef {
        val parts = token.split("/")
        val positionIndex = parts[0].toInt() - 1 // OBJ indices are 1-based
        val normalIndex = if (parts.size >= 3 && parts[2].isNotEmpty()) parts[2].toInt() - 1 else null
        return FaceVertexRef(positionIndex, normalIndex)
    }

    private data class FaceVertexRef(val positionIndex: Int, val normalIndex: Int?)

    private val WHITESPACE_REGEX = Regex("\\s+")
}
