package com.phoenixstudio.renderer.shader

/**
 * Hand-written GLSL ES 3.00 sources for the two shader programs the
 * viewport currently needs: flat-lit solid geometry (the cube) and
 * unlit vertex-colored lines (the grid). Kept as plain string constants
 * rather than loaded from assets so the very first frame can render
 * before the asset/filesystem module exists.
 */
object Shaders {

    const val LIT_VERTEX_SOURCE = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;

        uniform mat4 uModel;
        uniform mat4 uView;
        uniform mat4 uProjection;

        out vec3 vWorldNormal;

        void main() {
            vWorldNormal = mat3(uModel) * aNormal;
            gl_Position = uProjection * uView * uModel * vec4(aPosition, 1.0);
        }
    """

    const val LIT_FRAGMENT_SOURCE = """#version 300 es
        precision mediump float;

        in vec3 vWorldNormal;
        out vec4 fragColor;

        uniform vec3 uBaseColor;
        uniform vec3 uLightDirection;

        void main() {
            vec3 normal = normalize(vWorldNormal);
            float diffuse = max(dot(normal, normalize(-uLightDirection)), 0.0);
            float ambient = 0.25;
            float lighting = ambient + diffuse * 0.75;
            fragColor = vec4(uBaseColor * lighting, 1.0);
        }
    """

    const val UNLIT_VERTEX_SOURCE = """#version 300 es
        layout(location = 0) in vec3 aPosition;

        uniform mat4 uView;
        uniform mat4 uProjection;
        uniform vec3 uColor;

        out vec3 vColor;

        void main() {
            vColor = uColor;
            gl_Position = uProjection * uView * vec4(aPosition, 1.0);
        }
    """

    const val UNLIT_FRAGMENT_SOURCE = """#version 300 es
        precision mediump float;

        in vec3 vColor;
        out vec4 fragColor;

        void main() {
            fragColor = vec4(vColor, 1.0);
        }
    """
}
