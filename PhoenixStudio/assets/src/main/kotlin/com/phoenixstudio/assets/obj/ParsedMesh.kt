package com.phoenixstudio.assets.obj

/**
 * The result of parsing an OBJ file: an interleaved position(3)+normal(3)
 * per-vertex [FloatArray], plus a matching index buffer — the exact layout
 * `CubeMesh`/`StaticMesh` in `:renderer` upload straight to a VBO/IBO with
 * no further conversion.
 *
 * Vertices are *not* deduplicated across faces (each face's corners get
 * their own entry, the same approach `CubeMesh` already uses for flat
 * per-face shading), so [indexData] is simply 0, 1, 2, 3... in order. This
 * is less memory-efficient than a properly indexed mesh with shared
 * vertices, but it's simple and correct — worth revisiting only if a
 * specific imported model turns out to be memory-heavy in practice.
 */
data class ParsedMesh(
    val vertexData: FloatArray,
    val indexData: ShortArray
)
