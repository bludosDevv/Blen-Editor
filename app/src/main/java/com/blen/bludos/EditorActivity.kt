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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blen.bludos.ui.theme.AppTypography
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

    // Panel Expansion State
    private var isTransformExpanded by mutableStateOf(true)
    private var isMaterialExpanded by mutableStateOf(true)

    // Material State
    private var matColorR by mutableStateOf(0.8f)
    private var matColorG by mutableStateOf(0.8f)
    private var matColorB by mutableStateOf(0.8f)
    private var matMetallic by mutableStateOf(0.0f)
    private var matRoughness by mutableStateOf(0.5f)
    private var matEmissionR by mutableStateOf(0.0f)
    private var matEmissionG by mutableStateOf(0.0f)
    private var matEmissionB by mutableStateOf(0.0f)
    private var matEmissionIntensity by mutableStateOf(0.0f)

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
                 ),
                 typography = AppTypography
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

                                        // Update gizmo renderer mode
                                        val gizmoMode = activeTransformMode ?: 'g'
                                        renderer.gizmoRenderer.activeMode = gizmoMode
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gizmo State Toolbar (Overlay)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .background(Color(0x88000000), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        GizmoButton("P", activeTransformMode == 'g' || activeTransformMode == null) { activeTransformMode = 'g' }
                        Spacer(modifier = Modifier.height(8.dp))
                        GizmoButton("R", activeTransformMode == 'r') { activeTransformMode = 'r' }
                        Spacer(modifier = Modifier.height(8.dp))
                        GizmoButton("S", activeTransformMode == 's') { activeTransformMode = 's' }
                    }
                }

                // Right Panel: Properties
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.2f)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Text("Properties", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(12.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            CollapsibleSection("Transform", isTransformExpanded, { isTransformExpanded = !isTransformExpanded }) {
                                PropertySection("Position", px, py, pz, { px = it; updateTransform() }, { py = it; updateTransform() }, { pz = it; updateTransform() })
                                PropertySection("Rotation", rx, ry, rz, { rx = it; updateTransform() }, { ry = it; updateTransform() }, { rz = it; updateTransform() })
                                PropertySection("Scale", sx, sy, sz, { sx = it; updateTransform() }, { sy = it; updateTransform() }, { sz = it; updateTransform() })
                            }
                        }
                        item {
                            CollapsibleSection("Material", isMaterialExpanded, { isMaterialExpanded = !isMaterialExpanded }) {
                                MaterialColorSlider("Albedo R", matColorR) { matColorR = it; updateMaterial() }
                                MaterialColorSlider("Albedo G", matColorG) { matColorG = it; updateMaterial() }
                                MaterialColorSlider("Albedo B", matColorB) { matColorB = it; updateMaterial() }

                                Spacer(modifier = Modifier.height(8.dp))
                                MaterialSlider("Metallic", matMetallic) { matMetallic = it; updateMaterial() }
                                MaterialSlider("Roughness", matRoughness) { matRoughness = it; updateMaterial() }

                                Spacer(modifier = Modifier.height(8.dp))
                                MaterialColorSlider("Emission R", matEmissionR) { matEmissionR = it; updateMaterial() }
                                MaterialColorSlider("Emission G", matEmissionG) { matEmissionG = it; updateMaterial() }
                                MaterialColorSlider("Emission B", matEmissionB) { matEmissionB = it; updateMaterial() }
                                MaterialSlider("Emission Intensity", matEmissionIntensity, 0f, 10f) { matEmissionIntensity = it; updateMaterial() }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MaterialSlider(title: String, value: Float, rangeMin: Float = 0f, rangeMax: Float = 1f, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.Gray, fontSize = 12.sp)
                Text(String.format("%.2f", value), color = Color.White, fontSize = 12.sp)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = rangeMin..rangeMax,
                colors = SliderDefaults.colors(thumbColor = BlenAccent, activeTrackColor = BlenAccent, inactiveTrackColor = BlenBtnStroke)
            )
        }
    }

    @Composable
    fun MaterialColorSlider(title: String, value: Float, onValueChange: (Float) -> Unit) {
        MaterialSlider(title, value, 0f, 1f, onValueChange)
    }

    @Composable
    fun CollapsibleSection(title: String, isExpanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .background(Color(0xFF383838))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isExpanded) "▼" else "▶", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(12.dp)) {
                    content()
                }
            }
        }
    }

    @Composable
    fun GizmoButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
        val bgColor = if (isSelected) BlenAccent else Color.Transparent
        val textColor = if (isSelected) Color.White else BlenTextNormal
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(bgColor, RoundedCornerShape(4.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

    private fun updateMaterial() {
        if (!isUpdatingUI) {
            recordState()

            if (::glSurfaceView.isInitialized && ::renderer.isInitialized) {
                glSurfaceView.queueEvent {
                    try {
                        renderer.cubeRenderer.albedo = floatArrayOf(matColorR, matColorG, matColorB, 1f)
                        renderer.cubeRenderer.metallic = matMetallic
                        renderer.cubeRenderer.roughness = matRoughness
                        renderer.cubeRenderer.emission = floatArrayOf(matEmissionR, matEmissionG, matEmissionB, 1f)
                        renderer.cubeRenderer.emissionIntensity = matEmissionIntensity
                    } catch (e: Exception) {}
                }
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

                    val mat = obj.optJSONObject("material") ?: JSONObject()
                    mat.put("albedoR", matColorR.toDouble())
                    mat.put("albedoG", matColorG.toDouble())
                    mat.put("albedoB", matColorB.toDouble())
                    mat.put("metallic", matMetallic.toDouble())
                    mat.put("roughness", matRoughness.toDouble())
                    mat.put("emissionR", matEmissionR.toDouble())
                    mat.put("emissionG", matEmissionG.toDouble())
                    mat.put("emissionB", matEmissionB.toDouble())
                    mat.put("emissionIntensity", matEmissionIntensity.toDouble())

                    obj.put("material", mat)
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

                    val mat = obj.optJSONObject("material") ?: JSONObject()
                    mat.put("albedoR", matColorR.toDouble())
                    mat.put("albedoG", matColorG.toDouble())
                    mat.put("albedoB", matColorB.toDouble())
                    mat.put("metallic", matMetallic.toDouble())
                    mat.put("roughness", matRoughness.toDouble())
                    mat.put("emissionR", matEmissionR.toDouble())
                    mat.put("emissionG", matEmissionG.toDouble())
                    mat.put("emissionB", matEmissionB.toDouble())
                    mat.put("emissionIntensity", matEmissionIntensity.toDouble())

                    obj.put("material", mat)
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

                // Set the default state to no selection. Cube is drawn matte grey until selected.
                currentSelectedObject = null
                if (::glSurfaceView.isInitialized && ::renderer.isInitialized) {
                    glSurfaceView.queueEvent {
                        try {
                            renderer.cubeRenderer.isSelected = false
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
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

            val mat = obj.optJSONObject("material")
            if (mat != null) {
                matColorR = mat.optDouble("albedoR", 0.8).toFloat()
                matColorG = mat.optDouble("albedoG", 0.8).toFloat()
                matColorB = mat.optDouble("albedoB", 0.8).toFloat()
                matMetallic = mat.optDouble("metallic", 0.0).toFloat()
                matRoughness = mat.optDouble("roughness", 0.5).toFloat()
                matEmissionR = mat.optDouble("emissionR", 0.0).toFloat()
                matEmissionG = mat.optDouble("emissionG", 0.0).toFloat()
                matEmissionB = mat.optDouble("emissionB", 0.0).toFloat()
                matEmissionIntensity = mat.optDouble("emissionIntensity", 0.0).toFloat()
            }

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

                        val mat = obj.optJSONObject("material")
                        if (mat != null) {
                            renderer.cubeRenderer.albedo = floatArrayOf(
                                mat.optDouble("albedoR", 0.8).toFloat(),
                                mat.optDouble("albedoG", 0.8).toFloat(),
                                mat.optDouble("albedoB", 0.8).toFloat(),
                                1f
                            )
                            renderer.cubeRenderer.metallic = mat.optDouble("metallic", 0.0).toFloat()
                            renderer.cubeRenderer.roughness = mat.optDouble("roughness", 0.5).toFloat()
                            renderer.cubeRenderer.emission = floatArrayOf(
                                mat.optDouble("emissionR", 0.0).toFloat(),
                                mat.optDouble("emissionG", 0.0).toFloat(),
                                mat.optDouble("emissionB", 0.0).toFloat(),
                                1f
                            )
                            renderer.cubeRenderer.emissionIntensity = mat.optDouble("emissionIntensity", 0.0).toFloat()
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
    }
}
