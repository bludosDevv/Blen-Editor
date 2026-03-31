package com.blen.bludos

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BlenRenderer : GLSurfaceView.Renderer {

    // Matrices
    private val vPMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    val viewMatrix = FloatArray(16)

    // Camera parameters
    var cameraX = 0f
    var cameraY = 5f
    var cameraZ = 10f

    var targetX = 0f
    var targetY = 0f
    var targetZ = 0f

    // Shader program
    var mProgram: Int = 0

    private lateinit var gridRenderer: GridRenderer
    lateinit var cubeRenderer: CubeRenderer
    lateinit var gizmoRenderer: GizmoRenderer

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame color to #1c1c1c (blen_viewport_bg)
        // #1c1c1c = 28 / 255 = ~0.11f
        GLES20.glClearColor(0.11f, 0.11f, 0.11f, 1.0f)

        // Enable depth testing for 3D
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;

            attribute vec4 vPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;

            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vFragPos;

            void main() {
                gl_Position = uMVPMatrix * vPosition;
                vFragPos = vec3(uModelMatrix * vPosition);
                // In a real app we'd use the normal matrix (transpose of inverse of model matrix)
                // but for simple uniform scaling, model matrix works okay for normals
                vNormal = mat3(uModelMatrix) * aNormal;
                vColor = aColor;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;

            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vFragPos;

            uniform vec4 uAlbedo;
            uniform float uMetallic;
            uniform float uRoughness;
            uniform vec4 uEmission;
            uniform float uEmissionIntensity;
            uniform int uIsSelected;

            void main() {
                vec3 norm = normalize(vNormal);
                // Directional light coming from top-right-front
                vec3 lightDir = normalize(vec3(1.0, 1.0, 1.0));

                // Basic diffuse lighting (Lambert)
                float diff = max(dot(norm, lightDir), 0.0);

                // Ambient light
                vec3 ambient = vec3(0.2);

                // Apply PBR approximation (simplified)
                // Roughness affects specular highlight spread (not fully implemented in this basic shader)
                // Metallic darkens diffuse and relies on environment reflections (we just darken diffuse here)

                vec3 diffuseLight = diff * vec3(1.0) * (1.0 - uMetallic);
                vec3 finalColor = uAlbedo.rgb * (ambient + diffuseLight);

                // Add emission
                finalColor += uEmission.rgb * uEmissionIntensity;

                // Override for selection highlight (Blender Orange)
                if (uIsSelected == 1) {
                    finalColor = mix(finalColor, vec3(0.91, 0.46, 0.0), 0.5);
                } else if (uIsSelected == 2) {
                     // Not selected, but we don't have a uniform for lines so lines use vColor
                     // Wait, lines don't have normals.
                }

                // Fallback to vColor if albedo is black (for gizmos and grid)
                if (uAlbedo.a == 0.0) {
                    gl_FragColor = vColor;
                } else {
                    gl_FragColor = vec4(finalColor, uAlbedo.a);
                }
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        gridRenderer = GridRenderer(mProgram)
        cubeRenderer = CubeRenderer(mProgram)
        gizmoRenderer = GizmoRenderer(mProgram)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    fun updateViewMatrix() {
        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0, cameraX, cameraY, cameraZ, targetX, targetY, targetZ, 0f, 1.0f, 0.0f)
    }

    override fun onDrawFrame(unused: GL10) {
        // Redraw background color and clear depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        updateViewMatrix()

        // Calculate the projection and view transformation
        Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Render objects here using vPMatrix
        gridRenderer.draw(vPMatrix)

        if (this::cubeRenderer.isInitialized) {
            cubeRenderer.draw(vPMatrix)
            if (cubeRenderer.isSelected && this::gizmoRenderer.isInitialized) {
                // Attach gizmo to selected cube
                gizmoRenderer.originX = cubeRenderer.px
                gizmoRenderer.originY = cubeRenderer.py
                gizmoRenderer.originZ = cubeRenderer.pz
                gizmoRenderer.draw(vPMatrix)
            }
        }
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }
}