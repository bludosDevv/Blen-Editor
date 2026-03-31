package com.blen.bludos

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class CubeRenderer(private val program: Int) {

    private val vertexBuffer: FloatBuffer
    private val colorBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    // Transformation properties
    var px = 0f; var py = 0f; var pz = 0f
    var rx = 0f; var ry = 0f; var rz = 0f
    var sx = 1f; var sy = 1f; var sz = 1f

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    var isSelected = false

    init {
        // A simple 1x1x1 cube centered at origin
        val vertices = floatArrayOf(
            -0.5f, -0.5f, -0.5f, // 0: Bottom-left-back
             0.5f, -0.5f, -0.5f, // 1: Bottom-right-back
             0.5f,  0.5f, -0.5f, // 2: Top-right-back
            -0.5f,  0.5f, -0.5f, // 3: Top-left-back
            -0.5f, -0.5f,  0.5f, // 4: Bottom-left-front
             0.5f, -0.5f,  0.5f, // 5: Bottom-right-front
             0.5f,  0.5f,  0.5f, // 6: Top-right-front
            -0.5f,  0.5f,  0.5f  // 7: Top-left-front
        )

        // Base color: light grey
        val cr = 0.8f
        val cg = 0.8f
        val cb = 0.8f

        val colors = floatArrayOf(
            cr, cg, cb, 1f,
            cr, cg, cb, 1f,
            cr, cg, cb, 1f,
            cr, cg, cb, 1f,
            cr, cg, cb, 1f,
            cr, cg, cb, 1f,
            cr, cg, cb, 1f,
            cr, cg, cb, 1f
        )

        val indices = shortArrayOf(
            0, 4, 5, 0, 5, 1, // Bottom
            1, 5, 6, 1, 6, 2, // Right
            2, 6, 7, 2, 7, 3, // Top
            3, 7, 4, 3, 4, 0, // Left
            4, 7, 6, 4, 6, 5, // Front
            3, 0, 1, 3, 1, 2  // Back
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        }

        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(colors)
                position(0)
            }
        }

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(indices)
                position(0)
            }
        }
    }

    fun draw(viewProjectionMatrix: FloatArray) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, px, py, pz)
        Matrix.rotateM(modelMatrix, 0, rx, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, ry, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)

        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer)

        val colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        GLES20.glEnableVertexAttribArray(colorHandle)

        // If selected, override color to Blender Orange (#ea7600 -> 0.91f, 0.46f, 0.0f)
        if (isSelected) {
            // we bypass the color array and set a static color for simplicity here,
            // but standard openGL 2.0 without a uniform for object color requires us to rewrite buffer or use a different shader
            // For now, let's use glVertexAttrib4f to set a generic attribute value when array is disabled
            GLES20.glDisableVertexAttribArray(colorHandle)
            GLES20.glVertexAttrib4f(colorHandle, 0.91f, 0.46f, 0.0f, 1.0f)
        } else {
            GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 4 * 4, colorBuffer)
        }

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        if (!isSelected) {
            GLES20.glDisableVertexAttribArray(colorHandle)
        }
    }
}