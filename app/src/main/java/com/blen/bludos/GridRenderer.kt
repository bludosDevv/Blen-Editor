package com.blen.bludos

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GridRenderer(private val program: Int) {
    private val vertexBuffer: FloatBuffer
    private val colorBuffer: FloatBuffer
    private val lineCount: Int

    init {
        val vertices = mutableListOf<Float>()
        val colors = mutableListOf<Float>()

        val size = 50f
        val step = 1f

        var lines = 0

        // Dark grey color for general grid lines (#333333)
        val gridColorR = 0.2f
        val gridColorG = 0.2f
        val gridColorB = 0.2f

        var i = -size
        while (i <= size) {
            // Lines parallel to Z (varying Z, constant X)
            vertices.add(i)
            vertices.add(0f)
            vertices.add(-size)

            vertices.add(i)
            vertices.add(0f)
            vertices.add(size)

            // X-axis (Red)
            if (i == 0f) {
                colors.add(0f)
                colors.add(0.2f)
                colors.add(0.2f)
                colors.add(1f)

                colors.add(0f)
                colors.add(0.2f)
                colors.add(0.2f)
                colors.add(1f) // Keep generic line color to prevent double drawing overlapping axis? No, color the axis lines specifically.
                // Wait, it's Z axis that runs along X=0
                // Z axis (Blue)
                colors.removeAt(colors.size - 1); colors.removeAt(colors.size - 1); colors.removeAt(colors.size - 1); colors.removeAt(colors.size - 1)
                colors.removeAt(colors.size - 1); colors.removeAt(colors.size - 1); colors.removeAt(colors.size - 1); colors.removeAt(colors.size - 1)

                colors.add(0f); colors.add(0.4f); colors.add(0.8f); colors.add(1f)
                colors.add(0f); colors.add(0.4f); colors.add(0.8f); colors.add(1f)
            } else {
                colors.add(gridColorR); colors.add(gridColorG); colors.add(gridColorB); colors.add(1f)
                colors.add(gridColorR); colors.add(gridColorG); colors.add(gridColorB); colors.add(1f)
            }
            lines++

            // Lines parallel to X (varying X, constant Z)
            vertices.add(-size)
            vertices.add(0f)
            vertices.add(i)

            vertices.add(size)
            vertices.add(0f)
            vertices.add(i)

            // X axis (Red)
            if (i == 0f) {
                colors.add(0.8f); colors.add(0.2f); colors.add(0.2f); colors.add(1f)
                colors.add(0.8f); colors.add(0.2f); colors.add(0.2f); colors.add(1f)
            } else {
                colors.add(gridColorR); colors.add(gridColorG); colors.add(gridColorB); colors.add(1f)
                colors.add(gridColorR); colors.add(gridColorG); colors.add(gridColorB); colors.add(1f)
            }
            lines++

            i += step
        }

        lineCount = lines * 2

        val vbb = ByteBuffer.allocateDirect(vertices.size * 4)
        vbb.order(ByteOrder.nativeOrder())
        vertexBuffer = vbb.asFloatBuffer()
        vertexBuffer.put(vertices.toFloatArray())
        vertexBuffer.position(0)

        val cbb = ByteBuffer.allocateDirect(colors.size * 4)
        cbb.order(ByteOrder.nativeOrder())
        colorBuffer = cbb.asFloatBuffer()
        colorBuffer.put(colors.toFloatArray())
        colorBuffer.position(0)
    }

    fun draw(mvpMatrix: FloatArray) {
        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer)

        val colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 4 * 4, colorBuffer)

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
}
