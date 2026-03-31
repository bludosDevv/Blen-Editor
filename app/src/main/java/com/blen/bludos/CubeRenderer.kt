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

    private val normalBuffer: FloatBuffer

    // Transformation properties
    var px = 0f; var py = 0f; var pz = 0f
    var rx = 0f; var ry = 0f; var rz = 0f
    var sx = 1f; var sy = 1f; var sz = 1f

    // PBR Material Properties
    var albedo = floatArrayOf(0.8f, 0.8f, 0.8f, 1f)
    var metallic = 0.0f
    var roughness = 0.5f
    var emission = floatArrayOf(0f, 0f, 0f, 1f)
    var emissionIntensity = 0.0f

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    var isSelected = false

    init {
        // 24 vertices for proper normals (4 per face * 6 faces)
        val vertices = floatArrayOf(
            // Front face
            -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
            // Right face
             0.5f, -0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,
            // Back face
             0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,
            // Left face
            -0.5f, -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f,
            // Top face
            -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
            // Bottom face
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f
        )

        val normals = floatArrayOf(
            // Front
            0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
            // Right
            1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,
            // Back
            0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f,
            // Left
            -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f,
            // Top
            0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,
            // Bottom
            0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f
        )
        // 24 vertices, same color for all
        val colors = FloatArray(24 * 4) { 1f }

        val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3,    // Front
            4, 5, 6, 4, 6, 7,    // Right
            8, 9, 10, 8, 10, 11, // Back
            12, 13, 14, 12, 14, 15, // Left
            16, 17, 18, 16, 18, 19, // Top
            20, 21, 22, 20, 22, 23  // Bottom
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        }

        normalBuffer = ByteBuffer.allocateDirect(normals.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(normals); position(0) }
        }

        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(colors); position(0) }
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

        val normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        if (normalHandle >= 0) {
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, normalBuffer)
        }

        // Send PBR Material Uniforms
        val uAlbedo = GLES20.glGetUniformLocation(program, "uAlbedo")
        val uMetallic = GLES20.glGetUniformLocation(program, "uMetallic")
        val uRoughness = GLES20.glGetUniformLocation(program, "uRoughness")
        val uEmission = GLES20.glGetUniformLocation(program, "uEmission")
        val uEmissionInt = GLES20.glGetUniformLocation(program, "uEmissionIntensity")
        val uIsSelected = GLES20.glGetUniformLocation(program, "uIsSelected")

        if (uAlbedo >= 0) {
            GLES20.glUniform4fv(uAlbedo, 1, albedo, 0)
            GLES20.glUniform1f(uMetallic, metallic)
            GLES20.glUniform1f(uRoughness, roughness)
            GLES20.glUniform4fv(uEmission, 1, emission, 0)
            GLES20.glUniform1f(uEmissionInt, emissionIntensity)
            GLES20.glUniform1i(uIsSelected, if (isSelected) 1 else 0)
        }

        val modelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
        if (modelMatrixHandle >= 0) {
            GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        }

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        if (normalHandle >= 0) GLES20.glDisableVertexAttribArray(normalHandle)
    }
}