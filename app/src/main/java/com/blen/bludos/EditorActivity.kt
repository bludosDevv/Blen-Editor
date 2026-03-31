package com.blen.bludos

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.SeekBar
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class EditorActivity : ComponentActivity() {

    private lateinit var projectDir: File
    private var projectData: JSONObject? = null
    private val sceneObjects = mutableStateListOf<JSONObject>()

    private var currentSelectedObject by mutableStateOf<JSONObject?>(null)

    private var autoSaveEnabled = false
    private var scheduledExecutor: ScheduledExecutorService? = null

    private val historyManager = HistoryManager()

    private lateinit var glSurfaceView: BlenGLSurfaceView
    private lateinit var renderer: BlenRenderer

    // Transform State (G, R, S)
    private var activeTransformMode: Char? = null // 'g', 'r', 's', null
    private var activeAxisLock: Char? = null // 'x', 'y', 'z', null
    private var prevMouseX = -1f
    private var prevMouseY = -1f

    // UI State variables for text inputs
    private var px by mutableStateOf("0.0")
    private var py by mutableStateOf("0.0")
    private var pz by mutableStateOf("0.0")
    private var rx by mutableStateOf("0.0")
    private var ry by mutableStateOf("0.0")
    private var rz by mutableStateOf("0.0")
    private var sx by mutableStateOf("1.0")
    private var sy by mutableStateOf("1.0")
    private var sz by mutableStateOf("1.0")

    private var isUpdatingUI = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        val path = intent.getStringExtra("PROJECT_PATH")
        if (path == null) {
            finish()
            return
        }
        projectDir = File(path)

        loadProject()

        setContent {
            MaterialTheme(
                 colorScheme = darkColorScheme(
                     background = BlenViewportBg,
                     surface = BlenPanelBg,
                     primary = BlenAccent,
                     onBackground = BlenTextNormal,
                     onSurface = BlenTextNormal
                 )
             ) {
                 EditorScreen()
             }
        }
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

    @Composable
    fun EditorScreen() {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = projectDir.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                BlenButton(text = "Undo", onClick = { undo() }, modifier = Modifier.padding(end = 4.dp))
                BlenButton(text = "Redo", onClick = { redo() }, modifier = Modifier.padding(end = 4.dp))
                BlenButton(text = "Save", onClick = { saveProject() }, modifier = Modifier.padding(end = 4.dp))
                BlenButton(text = "Settings", onClick = { /* TODO show settings using compose later */ })
            }

            // Main Editor Area
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Left Panel: Hierarchy
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.2f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text("Hierarchy", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn {
                        items(sceneObjects) { obj ->
                            val isSelected = currentSelectedObject == obj
                            Text(
                                text = obj.optString("id", "Unknown"),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectObject(obj) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // Center Panel: 3D Viewport
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.6f)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AndroidView(
                        factory = { context ->
                            BlenGLSurfaceView(context).apply {
                                glSurfaceView = this
                                renderer = BlenRenderer()
                                setRenderer(renderer)
                                renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            }
                        },
                        update = { view ->
                            // When the view updates (which means compose state changed like px, py)
                            // Let's ensure the renderer is synced if it is initialized.
                            // The cubeRenderer might not be immediately initialized by GL thread,
                            // so we queue a runnable to check and sync.
                            view.queueEvent {
                                if (::renderer.isInitialized) {
                                    try {
                                        val cr = renderer.cubeRenderer
                                        cr.px = px.toFloatOrNull() ?: 0f
                                    cr.py = py.toFloatOrNull() ?: 0f
                                    cr.pz = pz.toFloatOrNull() ?: 0f
                                    cr.rx = rx.toFloatOrNull() ?: 0f
                                    cr.ry = ry.toFloatOrNull() ?: 0f
                                    cr.rz = rz.toFloatOrNull() ?: 0f
                                    cr.sx = sx.toFloatOrNull() ?: 1f
                                    cr.sy = sy.toFloatOrNull() ?: 1f
                                        cr.sz = sz.toFloatOrNull() ?: 1f
                                        if (currentSelectedObject != null) {
                                            cr.isSelected = true
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right Panel: Properties
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.2f)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text("Properties", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))

                    PropertySection("Position", px, py, pz, { px = it; updateTransform() }, { py = it; updateTransform() }, { pz = it; updateTransform() })
                    PropertySection("Rotation", rx, ry, rz, { rx = it; updateTransform() }, { ry = it; updateTransform() }, { rz = it; updateTransform() })
                    PropertySection("Scale", sx, sy, sz, { sx = it; updateTransform() }, { sy = it; updateTransform() }, { sz = it; updateTransform() })
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PropertySection(
        title: String,
        xVal: String, yVal: String, zVal: String,
        onXChange: (String) -> Unit, onYChange: (String) -> Unit, onZChange: (String) -> Unit
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = xVal,
                    onValueChange = onXChange,
                    modifier = Modifier.weight(1f).padding(end = 2.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(unfocusedBorderColor = BlenBtnStroke, focusedBorderColor = BlenAccent)
                )
                OutlinedTextField(
                    value = yVal,
                    onValueChange = onYChange,
                    modifier = Modifier.weight(1f).padding(end = 2.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(unfocusedBorderColor = BlenBtnStroke, focusedBorderColor = BlenAccent)
                )
                OutlinedTextField(
                    value = zVal,
                    onValueChange = onZChange,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(unfocusedBorderColor = BlenBtnStroke, focusedBorderColor = BlenAccent)
                )
            }
        }
    }

    private fun updateTransform() {
        if (!isUpdatingUI) {
            recordState()

            // Sync typed UI values back to the renderer directly just to be safe
            if (::glSurfaceView.isInitialized && ::renderer.isInitialized) {
                glSurfaceView.queueEvent {
                    try {
                        renderer.cubeRenderer.px = px.toFloatOrNull() ?: 0f
                        renderer.cubeRenderer.py = py.toFloatOrNull() ?: 0f
                        renderer.cubeRenderer.pz = pz.toFloatOrNull() ?: 0f
                        renderer.cubeRenderer.rx = rx.toFloatOrNull() ?: 0f
                        renderer.cubeRenderer.ry = ry.toFloatOrNull() ?: 0f
                        renderer.cubeRenderer.rz = rz.toFloatOrNull() ?: 0f
                        renderer.cubeRenderer.sx = sx.toFloatOrNull() ?: 1f
                        renderer.cubeRenderer.sy = sy.toFloatOrNull() ?: 1f
                        renderer.cubeRenderer.sz = sz.toFloatOrNull() ?: 1f
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }

    private fun undo() {
        val prevState = historyManager.undo()
        if (prevState != null) {
            applyState(prevState)
        }
    }

    private fun redo() {
        val nextState = historyManager.redo()
        if (nextState != null) {
            applyState(nextState)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Skip keybinds if we don't have a selection
        if (currentSelectedObject == null) return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_G -> { activeTransformMode = 'g'; activeAxisLock = null; return true }
            KeyEvent.KEYCODE_R -> { activeTransformMode = 'r'; activeAxisLock = null; return true }
            KeyEvent.KEYCODE_S -> { activeTransformMode = 's'; activeAxisLock = null; return true }
            KeyEvent.KEYCODE_X -> { if (activeTransformMode != null) { activeAxisLock = 'x'; return true } }
            KeyEvent.KEYCODE_Y -> { if (activeTransformMode != null) { activeAxisLock = 'y'; return true } }
            KeyEvent.KEYCODE_Z -> { if (activeTransformMode != null) { activeAxisLock = 'z'; return true } }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_ENTER -> { activeTransformMode = null; activeAxisLock = null; return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (activeTransformMode != null && ev.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val currentX = ev.x
            val currentY = ev.y

            if (prevMouseX != -1f && prevMouseY != -1f) {
                val dx = currentX - prevMouseX
                val dy = currentY - prevMouseY

                val factor = 0.05f
                val amount = (dx - dy) * factor

                if (::renderer.isInitialized) {
                    try {
                        val cr = renderer.cubeRenderer
                        when (activeTransformMode) {
                            'g' -> {
                            when (activeAxisLock) {
                                'x' -> cr.px += amount
                                'y' -> cr.py += amount
                                'z' -> cr.pz += amount
                                else -> { cr.px += dx * factor; cr.pz += dy * factor }
                            }
                            px = cr.px.toString()
                            py = cr.py.toString()
                            pz = cr.pz.toString()
                        }
                        'r' -> {
                            val rAmount = amount * 10f
                            when (activeAxisLock) {
                                'x' -> cr.rx += rAmount
                                'y' -> cr.ry += rAmount
                                'z' -> cr.rz += rAmount
                                else -> { cr.ry += dx * factor * 10f; cr.rx += dy * factor * 10f }
                            }
                            rx = cr.rx.toString()
                            ry = cr.ry.toString()
                            rz = cr.rz.toString()
                        }
                        's' -> {
                            val sAmount = amount * 0.1f
                            when (activeAxisLock) {
                                'x' -> cr.sx += sAmount
                                'y' -> cr.sy += sAmount
                                'z' -> cr.sz += sAmount
                                else -> { cr.sx += sAmount; cr.sy += sAmount; cr.sz += sAmount }
                            }
                                sx = cr.sx.toString()
                                sy = cr.sy.toString()
                                sz = cr.sz.toString()
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore uninitialized renderer crash
                    }
                }
            }
            prevMouseX = currentX
            prevMouseY = currentY
            return true
        }

        if (activeTransformMode == null) {
            prevMouseX = -1f
            prevMouseY = -1f
        }

        return super.dispatchGenericMotionEvent(ev)
    }

    private fun recordState() {
        if (projectData != null) {
            val objsArray = JSONArray()
            sceneObjects.forEach { obj ->
                if (currentSelectedObject == obj) {
                    val transform = obj.optJSONObject("transform") ?: JSONObject()
                    transform.put("px", px.toDoubleOrNull() ?: 0.0)
                    transform.put("py", py.toDoubleOrNull() ?: 0.0)
                    transform.put("pz", pz.toDoubleOrNull() ?: 0.0)

                    transform.put("rx", rx.toDoubleOrNull() ?: 0.0)
                    transform.put("ry", ry.toDoubleOrNull() ?: 0.0)
                    transform.put("rz", rz.toDoubleOrNull() ?: 0.0)

                    transform.put("sx", sx.toDoubleOrNull() ?: 1.0)
                    transform.put("sy", sy.toDoubleOrNull() ?: 1.0)
                    transform.put("sz", sz.toDoubleOrNull() ?: 1.0)

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
            val objsArray = JSONArray()
            sceneObjects.forEach { obj ->
                if (currentSelectedObject == obj) {
                    val transform = obj.optJSONObject("transform") ?: JSONObject()
                    transform.put("px", px.toDoubleOrNull() ?: 0.0)
                    transform.put("py", py.toDoubleOrNull() ?: 0.0)
                    transform.put("pz", pz.toDoubleOrNull() ?: 0.0)

                    transform.put("rx", rx.toDoubleOrNull() ?: 0.0)
                    transform.put("ry", ry.toDoubleOrNull() ?: 0.0)
                    transform.put("rz", rz.toDoubleOrNull() ?: 0.0)

                    transform.put("sx", sx.toDoubleOrNull() ?: 1.0)
                    transform.put("sy", sy.toDoubleOrNull() ?: 1.0)
                    transform.put("sz", sz.toDoubleOrNull() ?: 1.0)

                    obj.put("transform", transform)
                }
                objsArray.put(obj)
            }
            projectData!!.put("objects", objsArray)

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
            px = transform.optDouble("px", 0.0).toString()
            py = transform.optDouble("py", 0.0).toString()
            pz = transform.optDouble("pz", 0.0).toString()

            rx = transform.optDouble("rx", 0.0).toString()
            ry = transform.optDouble("ry", 0.0).toString()
            rz = transform.optDouble("rz", 0.0).toString()

            sx = transform.optDouble("sx", 1.0).toString()
            sy = transform.optDouble("sy", 1.0).toString()
            sz = transform.optDouble("sz", 1.0).toString()
            isUpdatingUI = false

            if (::glSurfaceView.isInitialized && ::renderer.isInitialized) {
                // If the object name is "Cube" or we just map current properties to the single cube renderer for now
                glSurfaceView.queueEvent {
                    try {
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
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }
}
