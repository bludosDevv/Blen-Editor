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
            attribute vec4 vPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                vColor = aColor;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
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