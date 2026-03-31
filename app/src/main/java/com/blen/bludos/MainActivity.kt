package com.blen.bludos

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.blen.bludos.ui.theme.AppTypography
import java.io.File
import java.io.FileOutputStream

// Blen Dark Theme Colors for Compose
val BlenViewportBg = Color(0xFF1C1C1C)
val BlenPanelBg = Color(0xFF282828)
val BlenAccent = Color(0xFFEA7600)
val BlenTextNormal = Color(0xFFCCCCCC)
val BlenBtnBg = Color(0xFF333333)
val BlenBtnStroke = Color(0xFF444444)

public class MainActivity : ComponentActivity() {

    private var projectListState = mutableStateListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)

         WindowCompat.setDecorFitsSystemWindows(window, false)
         val insetsController = WindowCompat.getInsetsController(window, window.decorView)
         insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
         insetsController?.hide(WindowInsetsCompat.Type.systemBars())

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
                 MainScreen(
                     projects = projectListState,
                     onNewProject = { showNewProjectDialog() },
                     onImportProject = { importProject() },
                     onOpenProject = { openProject(it) }
                 )
             }
         }

         checkPermissions()
    }

    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } else {
                ProjectManager.init()
                updateProjectList()
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), 1002)
            } else {
                ProjectManager.init()
                updateProjectList()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ProjectManager.init()
                updateProjectList()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                ProjectManager.init()
                updateProjectList()
            }
        } else {
            ProjectManager.init()
            updateProjectList()
        }
    }

    private fun updateProjectList() {
        val projects = ProjectManager.getProjects()
        projectListState.clear()
        projectListState.addAll(projects)
    }

    private fun showNewProjectDialog() {
        val input = EditText(this)
        input.hint = "Project Name"

        AlertDialog.Builder(this)
            .setTitle("New Project")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    val projectDir = ProjectManager.createProject(name)
                    if (projectDir != null) {
                        openProject(projectDir)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openProject(projectDir: File) {
        val intent = Intent(this, EditorActivity::class.java)
        intent.putExtra("PROJECT_PATH", projectDir.absolutePath)
        startActivity(intent)
    }

    private fun importProject() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                if (documentFile != null && documentFile.isDirectory) {
                    val blenFile = documentFile.findFile("project.blen")
                    if (blenFile != null) {
                        val projectName = documentFile.name ?: "ImportedProject"
                        val projectDir = File(ProjectManager.rootDir, projectName)

                        if (!projectDir.exists()) {
                            projectDir.mkdirs()
                        }

                        val newBlenFile = File(projectDir, "project.blen")
                        try {
                            contentResolver.openInputStream(blenFile.uri)?.use { input ->
                                FileOutputStream(newBlenFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            updateProjectList()
                            Toast.makeText(this, "Project imported successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to import project data", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Selected folder is not a Blen project", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    projects: List<File>,
    onNewProject: () -> Unit,
    onImportProject: () -> Unit,
    onOpenProject: (File) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Projects",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            BlenButton(text = "Import", onClick = onImportProject, modifier = Modifier.padding(end = 8.dp))
            BlenButton(text = "New Project", onClick = onNewProject)
        }

        // Project List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(projects) { project ->
                ProjectItem(project = project, onClick = { onOpenProject(project) })
            }
        }
    }
}

@Composable
fun ProjectItem(project: File, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = project.name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = project.absolutePath,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun BlenButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = BlenBtnBg,
            contentColor = BlenTextNormal
        ),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}