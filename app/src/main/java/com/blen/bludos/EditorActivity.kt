package com.blen.bludos

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import android.view.KeyEvent

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class EditorActivity : AppCompatActivity() {

    private lateinit var projectDir: File
    private var projectData: JSONObject? = null
    private val sceneObjects = mutableListOf<JSONObject>()

    private var autoSaveEnabled = false
    private var scheduledExecutor: ScheduledExecutorService? = null

    private val historyManager = HistoryManager()

    private lateinit var rvHierarchy: RecyclerView
    private lateinit var hierarchyAdapter: HierarchyAdapter

    private lateinit var glSurfaceView: BlenGLSurfaceView
    private lateinit var renderer: BlenRenderer

    // Transform State (G, R, S)
    private var activeTransformMode: Char? = null // 'g', 'r', 's', null
    private var activeAxisLock: Char? = null // 'x', 'y', 'z', null

    // Properties inputs
    private lateinit var etPosX: EditText
    private lateinit var etPosY: EditText
    private lateinit var etPosZ: EditText
    private lateinit var etRotX: EditText
    private lateinit var etRotY: EditText
    private lateinit var etRotZ: EditText
    private lateinit var etScaleX: EditText
    private lateinit var etScaleY: EditText
    private lateinit var etScaleZ: EditText

    private var currentSelectedObject: JSONObject? = null

    private var isUpdatingUI = false

    override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)

         WindowCompat.setDecorFitsSystemWindows(window, false)
         val insetsController = WindowCompat.getInsetsController(window, window.decorView)
         insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
         insetsController?.hide(WindowInsetsCompat.Type.systemBars())

         setContentView(R.layout.activity_editor)

         val path = intent.getStringExtra("PROJECT_PATH")
         if (path == null) {
             finish()
             return
         }
         projectDir = File(path)

         initUI()
         loadProject()
         applyUIScale()
    }

    override fun onResume() {
        super.onResume()
        if (::glSurfaceView.isInitialized) {
            glSurfaceView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::glSurfaceView.isInitialized) {
            glSurfaceView.onPause()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Only process keybinds if we are not actively typing in an EditText
        if (currentFocus is EditText) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_G -> {
                activeTransformMode = 'g'
                activeAxisLock = null
                return true
            }
            KeyEvent.KEYCODE_R -> {
                activeTransformMode = 'r'
                activeAxisLock = null
                return true
            }
            KeyEvent.KEYCODE_S -> {
                activeTransformMode = 's'
                activeAxisLock = null
                return true
            }
            KeyEvent.KEYCODE_X -> {
                if (activeTransformMode != null) {
                    activeAxisLock = 'x'
                    return true
                }
            }
            KeyEvent.KEYCODE_Y -> {
                if (activeTransformMode != null) {
                    activeAxisLock = 'y'
                    return true
                }
            }
            KeyEvent.KEYCODE_Z -> {
                if (activeTransformMode != null) {
                    activeAxisLock = 'z'
                    return true
                }
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ENTER -> {
                // Commit or cancel transform
                activeTransformMode = null
                activeAxisLock = null
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // Mouse state for screen-space transforms
    private var prevMouseX = -1f
    private var prevMouseY = -1f

    // Pass motion events to GL view for transform manipulation if a mode is active
    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        if (activeTransformMode != null && ev.actionMasked == android.view.MotionEvent.ACTION_HOVER_MOVE) {
            val currentX = ev.x
            val currentY = ev.y

            if (prevMouseX != -1f && prevMouseY != -1f) {
                val dx = currentX - prevMouseX
                val dy = currentY - prevMouseY

                // Screen space simple drag factor
                val factor = 0.05f
                val amount = (dx - dy) * factor // Move right/up increases, left/down decreases

                if (::renderer.isInitialized) {
                    val cr = renderer.cubeRenderer
                    when (activeTransformMode) {
                        'g' -> {
                            when (activeAxisLock) {
                                'x' -> cr.px += amount
                                'y' -> cr.py += amount
                                'z' -> cr.pz += amount
                                else -> { cr.px += dx * factor; cr.pz += dy * factor } // Free transform on XZ plane
                            }
                            etPosX.setText(cr.px.toString())
                            etPosY.setText(cr.py.toString())
                            etPosZ.setText(cr.pz.toString())
                        }
                        'r' -> {
                            val rAmount = amount * 10f // Degrees
                            when (activeAxisLock) {
                                'x' -> cr.rx += rAmount
                                'y' -> cr.ry += rAmount
                                'z' -> cr.rz += rAmount
                                else -> { cr.ry += dx * factor * 10f; cr.rx += dy * factor * 10f }
                            }
                            etRotX.setText(cr.rx.toString())
                            etRotY.setText(cr.ry.toString())
                            etRotZ.setText(cr.rz.toString())
                        }
                        's' -> {
                            val sAmount = amount * 0.1f
                            when (activeAxisLock) {
                                'x' -> cr.sx += sAmount
                                'y' -> cr.sy += sAmount
                                'z' -> cr.sz += sAmount
                                else -> { cr.sx += sAmount; cr.sy += sAmount; cr.sz += sAmount }
                            }
                            etScaleX.setText(cr.sx.toString())
                            etScaleY.setText(cr.sy.toString())
                            etScaleZ.setText(cr.sz.toString())
                        }
                    }
                }
            }
            prevMouseX = currentX
            prevMouseY = currentY
            return true // Consume event
        }

        // Reset if we aren't moving in a mode
        if (activeTransformMode == null) {
            prevMouseX = -1f
            prevMouseY = -1f
        }

        return super.dispatchGenericMotionEvent(ev)
    }

    private fun applyUIScale() {
        val prefs = getSharedPreferences("BlenPrefs", Context.MODE_PRIVATE)
        val scalePercent = prefs.getInt("ui_scale", 100)
        val scaleFactor = scalePercent / 100f

        val rootView = findViewById<ViewGroup>(android.R.id.content)
        scaleViewTree(rootView, scaleFactor)
    }

    private fun scaleViewTree(view: View, scaleFactor: Float) {
        if (view is TextView) {
            // Assume 14sp as base size for typical text if not explicitly set
            // In a more robust system, you'd track original dimensions in a custom attribute.
            // For this implementation, we just apply a multiplier to current or base.
            val currentSize = view.textSize
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, currentSize * scaleFactor)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scaleViewTree(view.getChildAt(i), scaleFactor)
            }
        }
    }

    private fun initUI() {
        findViewById<TextView>(R.id.tvProjectTitle).text = projectDir.name

        rvHierarchy = findViewById(R.id.rvHierarchy)
        rvHierarchy.layoutManager = LinearLayoutManager(this)
        hierarchyAdapter = HierarchyAdapter(sceneObjects) { obj ->
            selectObject(obj)
        }
        rvHierarchy.adapter = hierarchyAdapter

        glSurfaceView = findViewById(R.id.glSurfaceView)
        renderer = BlenRenderer()
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY

        etPosX = findViewById(R.id.etPosX)
        etPosY = findViewById(R.id.etPosY)
        etPosZ = findViewById(R.id.etPosZ)
        etRotX = findViewById(R.id.etRotX)
        etRotY = findViewById(R.id.etRotY)
        etRotZ = findViewById(R.id.etRotZ)
        etScaleX = findViewById(R.id.etScaleX)
        etScaleY = findViewById(R.id.etScaleY)
        etScaleZ = findViewById(R.id.etScaleZ)

        val recordWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUpdatingUI) {
                    recordState()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etPosX.addTextChangedListener(recordWatcher)
        etPosY.addTextChangedListener(recordWatcher)
        etPosZ.addTextChangedListener(recordWatcher)
        etRotX.addTextChangedListener(recordWatcher)
        etRotY.addTextChangedListener(recordWatcher)
        etRotZ.addTextChangedListener(recordWatcher)
        etScaleX.addTextChangedListener(recordWatcher)
        etScaleY.addTextChangedListener(recordWatcher)
        etScaleZ.addTextChangedListener(recordWatcher)

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveProject()
        }

        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            val prevState = historyManager.undo()
            if (prevState != null) {
                applyState(prevState)
            }
        }

        findViewById<Button>(R.id.btnRedo).setOnClickListener {
            val nextState = historyManager.redo()
            if (nextState != null) {
                applyState(nextState)
            }
        }
    }

    private fun recordState() {
        if (projectData != null) {
            val objsArray = JSONArray()
            sceneObjects.forEach { obj ->
                if (currentSelectedObject == obj) {
                    val transform = obj.optJSONObject("transform") ?: JSONObject()
                    transform.put("px", etPosX.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("py", etPosY.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("pz", etPosZ.text.toString().toDoubleOrNull() ?: 0.0)

                    transform.put("rx", etRotX.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("ry", etRotY.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("rz", etRotZ.text.toString().toDoubleOrNull() ?: 0.0)

                    transform.put("sx", etScaleX.text.toString().toDoubleOrNull() ?: 1.0)
                    transform.put("sy", etScaleY.text.toString().toDoubleOrNull() ?: 1.0)
                    transform.put("sz", etScaleZ.text.toString().toDoubleOrNull() ?: 1.0)

                    obj.put("transform", transform)
                }
                objsArray.put(obj)
            }
            projectData!!.put("objects", objsArray)
            historyManager.pushState(projectData.toString())
        }
    }

    private fun applyState(stateJson: String) {
        val json = JSONObject(stateJson)
        projectData = json
        val objsArray = json.optJSONArray("objects")
        if (objsArray != null) {
            sceneObjects.clear()
            for (i in 0 until objsArray.length()) {
                val obj = objsArray.optJSONObject(i)
                if (obj != null) {
                    sceneObjects.add(obj)
                }
            }
            hierarchyAdapter.notifyDataSetChanged()

            if (currentSelectedObject != null) {
                val currentId = currentSelectedObject!!.optString("id")
                val restoredObj = sceneObjects.find { it.optString("id") == currentId }
                if (restoredObj != null) {
                    selectObject(restoredObj)
                } else if (sceneObjects.isNotEmpty()) {
                    selectObject(sceneObjects[0])
                }
            } else if (sceneObjects.isNotEmpty()) {
                selectObject(sceneObjects[0])
            }
        }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("BlenPrefs", Context.MODE_PRIVATE)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val seekUIScale = dialogView.findViewById<SeekBar>(R.id.seekUIScale)
        val switchAutoSave = dialogView.findViewById<Switch>(R.id.switchAutoSave)
        val btnClose = dialogView.findViewById<Button>(R.id.btnSettingsClose)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        seekUIScale.progress = prefs.getInt("ui_scale", 100)
        switchAutoSave.isChecked = prefs.getBoolean("auto_save", false)

        seekUIScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val rootView = this@EditorActivity.findViewById<ViewGroup>(android.R.id.content)
                    // Resetting to base size dynamically is complex without storing it,
                    // so we save and recreate the activity for a clean UI scale update.
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("ui_scale", seekBar?.progress ?: 100).apply()
                recreate() // Simple and robust way to re-apply the global scale factor
            }
        })

        switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_save", isChecked).apply()
            autoSaveEnabled = isChecked
            setupAutoSave()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupAutoSave() {
        scheduledExecutor?.shutdownNow()

        if (autoSaveEnabled) {
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
            scheduledExecutor?.scheduleAtFixedRate({
                saveProject()
            }, 5, 5, TimeUnit.SECONDS)
        }
    }

    private fun saveProject() {
        if (projectData != null) {
            // Update the JSON structure with current values
            val objsArray = JSONArray()
            sceneObjects.forEach { obj ->
                if (currentSelectedObject == obj) {
                    val transform = obj.optJSONObject("transform") ?: JSONObject()
                    transform.put("px", etPosX.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("py", etPosY.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("pz", etPosZ.text.toString().toDoubleOrNull() ?: 0.0)

                    transform.put("rx", etRotX.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("ry", etRotY.text.toString().toDoubleOrNull() ?: 0.0)
                    transform.put("rz", etRotZ.text.toString().toDoubleOrNull() ?: 0.0)

                    transform.put("sx", etScaleX.text.toString().toDoubleOrNull() ?: 1.0)
                    transform.put("sy", etScaleY.text.toString().toDoubleOrNull() ?: 1.0)
                    transform.put("sz", etScaleZ.text.toString().toDoubleOrNull() ?: 1.0)

                    obj.put("transform", transform)
                }
                objsArray.put(obj)
            }
            projectData!!.put("objects", objsArray)

            // Save in background to avoid lag
            Executors.newSingleThreadExecutor().execute {
                ProjectManager.saveProject(projectDir, projectData!!)
            }
        }
    }

    private fun loadProject() {
        projectData = ProjectManager.loadProject(projectDir)
        projectData?.let {
            val objsArray = it.optJSONArray("objects")
            if (objsArray != null) {
                sceneObjects.clear()
                for (i in 0 until objsArray.length()) {
                    val obj = objsArray.optJSONObject(i)
                    if (obj != null) {
                        sceneObjects.add(obj)
                    }
                }
                hierarchyAdapter.notifyDataSetChanged()

                if (sceneObjects.isNotEmpty()) {
                    selectObject(sceneObjects[0])
                }
            }
            historyManager.pushState(it.toString())
        }
    }

    private fun selectObject(obj: JSONObject) {
        currentSelectedObject = obj
        val transform = obj.optJSONObject("transform")
        if (transform != null) {
            isUpdatingUI = true
            etPosX.setText(transform.optDouble("px", 0.0).toString())
            etPosY.setText(transform.optDouble("py", 0.0).toString())
            etPosZ.setText(transform.optDouble("pz", 0.0).toString())

            etRotX.setText(transform.optDouble("rx", 0.0).toString())
            etRotY.setText(transform.optDouble("ry", 0.0).toString())
            etRotZ.setText(transform.optDouble("rz", 0.0).toString())

            etScaleX.setText(transform.optDouble("sx", 1.0).toString())
            etScaleY.setText(transform.optDouble("sy", 1.0).toString())
            etScaleZ.setText(transform.optDouble("sz", 1.0).toString())
            isUpdatingUI = false

            if (::renderer.isInitialized && this::renderer.isInitialized) {
                // If the object name is "Cube" or we just map current properties to the single cube renderer for now
                renderer.cubeRenderer.isSelected = true
                renderer.cubeRenderer.px = transform.optDouble("px", 0.0).toFloat()
                renderer.cubeRenderer.py = transform.optDouble("py", 0.0).toFloat()
                renderer.cubeRenderer.pz = transform.optDouble("pz", 0.0).toFloat()

                renderer.cubeRenderer.rx = transform.optDouble("rx", 0.0).toFloat()
                renderer.cubeRenderer.ry = transform.optDouble("ry", 0.0).toFloat()
                renderer.cubeRenderer.rz = transform.optDouble("rz", 0.0).toFloat()

                renderer.cubeRenderer.sx = transform.optDouble("sx", 1.0).toFloat()
                renderer.cubeRenderer.sy = transform.optDouble("sy", 1.0).toFloat()
                renderer.cubeRenderer.sz = transform.optDouble("sz", 1.0).toFloat()
            }
        }
    }
}