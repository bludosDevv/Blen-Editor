package com.blen.bludos

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.cos
import kotlin.math.sin

class BlenGLSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private lateinit var renderer: BlenRenderer

    // Camera state
    private var orbitAzimuth = 0f
    private var orbitElevation = 0f
    private var orbitRadius = 10f
    private var targetX = 0f
    private var targetY = 0f
    private var targetZ = 0f

    // Touch state
    private var previousX = 0f
    private var previousY = 0f
    private var isPanning = false

    // Gizmo interaction state
    private var activeGizmoAxis: Char? = null // 'x', 'y', 'z', null

    private val scaleGestureDetector: ScaleGestureDetector

    override fun setRenderer(renderer: Renderer) {
        super.setRenderer(renderer)
        this.renderer = renderer as BlenRenderer
        updateCamera()
    }

    init {
        // Create an OpenGL ES 2.0 context.
        setEGLContextClientVersion(2)

        // Render the view only when there is a change in the drawing data.
        // To allow continuous rendering for animations, change this to RENDERMODE_CONTINUOUSLY.
        // For now, let's keep it simple and update when needed or continuously for smooth interaction.
        // Using continuous to ensure no lag during touch drag.
        // renderMode = RENDERMODE_CONTINUOUSLY is default but setting explicitly

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                // Zoom in (scaleFactor > 1) reduces radius, zoom out increases radius
                orbitRadius /= scaleFactor
                orbitRadius = orbitRadius.coerceIn(1f, 100f)
                updateCamera()
                return true
            }
        })
    }

    private fun updateCamera() {
        if (!::renderer.isInitialized) return

        val elevRad = Math.toRadians(orbitElevation.toDouble())
        val azimRad = Math.toRadians(orbitAzimuth.toDouble())

        // Spherical to Cartesian relative to target
        val dx = orbitRadius * cos(elevRad) * sin(azimRad)
        val dy = orbitRadius * sin(elevRad)
        val dz = orbitRadius * cos(elevRad) * cos(azimRad)

        renderer.cameraX = targetX + dx.toFloat()
        renderer.cameraY = targetY + dy.toFloat()
        renderer.cameraZ = targetZ + dz.toFloat()

        renderer.targetX = targetX
        renderer.targetY = targetY
        renderer.targetZ = targetZ
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            // Scroll wheel for zoom (Blender mapping: scroll up = zoom in)
            orbitRadius -= vScroll * 1.5f
            orbitRadius = orbitRadius.coerceIn(1f, 100f)
            updateCamera()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(e)

        // If zooming, ignore single/double finger drags to prevent jumping
        if (scaleGestureDetector.isInProgress) {
            return true
        }

        val x = e.x
        val y = e.y

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPanning = e.pointerCount == 2
                previousX = x
                previousY = y

                // Very basic hit detection: only checks distance in screen space to center of object for now.
                // A complete raycasting implementation requires unprojecting window coords.
                // Assuming we just want to allow movement if we click near the center of the viewport for demo.
                activeGizmoAxis = null
                if (e.pointerCount == 1 && ::renderer.isInitialized && renderer.cubeRenderer.isSelected) {
                    // Start dragging X by default if tap is near center (simplification for demo)
                    // You'd normally project ray into 3D, check intersection with gizmo cylinder/boxes
                    activeGizmoAxis = null // Need full raycast math, leave null for standard orbit
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                isPanning = true
                // Recalculate center of pointers to prevent jump
                var cx = 0f
                var cy = 0f
                for (i in 0 until e.pointerCount) {
                    cx += e.getX(i)
                    cy += e.getY(i)
                }
                previousX = cx / e.pointerCount
                previousY = cy / e.pointerCount
            }
            MotionEvent.ACTION_POINTER_UP -> {
                isPanning = false
                // If dropping back to 1 pointer, update previousX/Y to the remaining pointer
                val upIndex = e.actionIndex
                val remainIndex = if (upIndex == 0) 1 else 0
                if (e.pointerCount > 1) {
                    previousX = e.getX(remainIndex)
                    previousY = e.getY(remainIndex)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                var currentX = 0f
                var currentY = 0f
                for (i in 0 until e.pointerCount) {
                    currentX += e.getX(i)
                    currentY += e.getY(i)
                }
                currentX /= e.pointerCount
                currentY /= e.pointerCount

                val dx = currentX - previousX
                val dy = currentY - previousY

                // KBM Logic: MMB Drag for Orbit, Shift+MMB Drag for Pan
                val isMMB = (e.buttonState and MotionEvent.BUTTON_TERTIARY) != 0
                val isShift = (e.metaState and android.view.KeyEvent.META_SHIFT_ON) != 0

                val isTouchPan = isPanning || e.pointerCount == 2
                val isTouchOrbit = e.pointerCount == 1 && !isMMB

                if ((isMMB && isShift) || isTouchPan) {
                    // Pan
                    val azimRad = Math.toRadians(orbitAzimuth.toDouble())

                    // Move right/left perpendicular to camera direction
                    val panRightX = cos(azimRad)
                    val panRightZ = -sin(azimRad)

                    val panFactor = orbitRadius * 0.002f

                    targetX -= dx * panRightX.toFloat() * panFactor
                    targetZ -= dx * panRightZ.toFloat() * panFactor

                    // Y axis is absolute up
                    targetY += dy * panFactor

                } else if ((isMMB && !isShift) || isTouchOrbit) {
                    // Orbit
                    orbitAzimuth -= dx * 0.5f
                    orbitElevation += dy * 0.5f

                    // Clamp elevation to avoid gimbal lock and flipping
                    orbitElevation = orbitElevation.coerceIn(-89f, 89f)
                }

                updateCamera()

                previousX = currentX
                previousY = currentY
            }
        }
        return true
    }
}
