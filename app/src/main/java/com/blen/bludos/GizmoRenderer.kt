package com.blen.bludos

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GizmoRenderer(private val program: Int) {

    private val vertexBuffer: FloatBuffer
    private val colorBuffer: FloatBuffer
    private val lineCount: Int

    var originX = 0f
    var originY = 0f
    var originZ = 0f

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    var activeMode: Char = 'g' // 'g' for translate, 'r' for rotate, 's' for scale

    // Buffers for translate
    private val tVertexBuffer: FloatBuffer
    private val tColorBuffer: FloatBuffer
    private val tLineCount: Int

    // Buffers for scale
    private val sVertexBuffer: FloatBuffer
    private val sColorBuffer: FloatBuffer
    private val sLineCount: Int

    // Buffers for rotate
    private val rVertexBuffer: FloatBuffer
    private val rColorBuffer: FloatBuffer
    private val rLineCount: Int

    init {
        // --- TRANSLATION GIZMO (Arrows, simplified to lines) ---
        val tVertices = floatArrayOf(
            0f, 0f, 0f, 1.5f, 0f, 0f, // X axis
            0f, 0f, 0f, 0f, 1.5f, 0f, // Y axis
            0f, 0f, 0f, 0f, 0f, 1.5f  // Z axis
        )
        tLineCount = tVertices.size / 3
        val tColors = floatArrayOf(
            1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, // Red
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, // Green
            0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f  // Blue
        )
        tVertexBuffer = ByteBuffer.allocateDirect(tVertices.size * 4).run { order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(tVertices); position(0) } }
        tColorBuffer = ByteBuffer.allocateDirect(tColors.size * 4).run { order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(tColors); position(0) } }

        // --- SCALE GIZMO (Lines with boxes, simplified to lines) ---
        val sVertices = floatArrayOf(
            0f, 0f, 0f, 1.5f, 0f, 0f, // X axis
            0f, 0f, 0f, 0f, 1.5f, 0f, // Y axis
            0f, 0f, 0f, 0f, 0f, 1.5f  // Z axis
        )
        sLineCount = sVertices.size / 3
        val sColors = floatArrayOf(
            1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, // Red
            0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, // Green
            0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f  // Blue
        )
        sVertexBuffer = ByteBuffer.allocateDirect(sVertices.size * 4).run { order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(sVertices); position(0) } }
        sColorBuffer = ByteBuffer.allocateDirect(sColors.size * 4).run { order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(sColors); position(0) } }

        // --- ROTATION GIZMO (Intersecting Rings) ---
        val segments = 32
        val rVerticesList = mutableListOf<Float>()
        val rColorsList = mutableListOf<Float>()

        for (i in 0 until segments) {
            val theta1 = (i.toFloat() / segments) * 2.0 * Math.PI
            val theta2 = ((i + 1).toFloat() / segments) * 2.0 * Math.PI

            // X-axis ring (Red)
            rVerticesList.add(0f); rVerticesList.add(Math.cos(theta1).toFloat()); rVerticesList.add(Math.sin(theta1).toFloat())
            rVerticesList.add(0f); rVerticesList.add(Math.cos(theta2).toFloat()); rVerticesList.add(Math.sin(theta2).toFloat())
            rColorsList.add(1f); rColorsList.add(0f); rColorsList.add(0f); rColorsList.add(1f)
            rColorsList.add(1f); rColorsList.add(0f); rColorsList.add(0f); rColorsList.add(1f)

            // Y-axis ring (Green)
            rVerticesList.add(Math.cos(theta1).toFloat()); rVerticesList.add(0f); rVerticesList.add(Math.sin(theta1).toFloat())
            rVerticesList.add(Math.cos(theta2).toFloat()); rVerticesList.add(0f); rVerticesList.add(Math.sin(theta2).toFloat())
            rColorsList.add(0f); rColorsList.add(1f); rColorsList.add(0f); rColorsList.add(1f)
            rColorsList.add(0f); rColorsList.add(1f); rColorsList.add(0f); rColorsList.add(1f)

            // Z-axis ring (Blue)
            rVerticesList.add(Math.cos(theta1).toFloat()); rVerticesList.add(Math.sin(theta1).toFloat()); rVerticesList.add(0f)
            rVerticesList.add(Math.cos(theta2).toFloat()); rVerticesList.add(Math.sin(theta2).toFloat()); rVerticesList.add(0f)
            rColorsList.add(0f); rColorsList.add(0f); rColorsList.add(1f); rColorsList.add(1f)
            rColorsList.add(0f); rColorsList.add(0f); rColorsList.add(1f); rColorsList.add(1f)
        }

        val rVertices = rVerticesList.toFloatArray()
        val rColors = rColorsList.toFloatArray()
        rLineCount = rVertices.size / 3
        rVertexBuffer = ByteBuffer.allocateDirect(rVertices.size * 4).run { order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(rVertices); position(0) } }
        rColorBuffer = ByteBuffer.allocateDirect(rColors.size * 4).run { order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(rColors); position(0) } }

        vertexBuffer = tVertexBuffer
        colorBuffer = tColorBuffer
        lineCount = tLineCount
    }

    fun draw(viewProjectionMatrix: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, originX, originY, originZ)

        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(program)

        // Disable depth test so gizmos render on top of objects
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glLineWidth(5f) // Thicker lines for gizmo

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer)

        val colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        GLES20.glEnableVertexAttribArray(colorHandle)
        val curVBuf = when (activeMode) {
            'r' -> rVertexBuffer
            's' -> sVertexBuffer
            else -> tVertexBuffer
        }
        val curCBuf = when (activeMode) {
            'r' -> rColorBuffer
            's' -> sColorBuffer
            else -> tColorBuffer
        }
        val curLCount = when (activeMode) {
            'r' -> rLineCount
            's' -> sLineCount
            else -> tLineCount
        }

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, curVBuf)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 4 * 4, curCBuf)

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Zero out albedo for gizmos so it falls back to vColor in shader
        val uAlbedo = GLES20.glGetUniformLocation(program, "uAlbedo")
        if (uAlbedo >= 0) GLES20.glUniform4f(uAlbedo, 0f, 0f, 0f, 0f)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, curLCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)

        // Restore state
        GLES20.glLineWidth(1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }
}