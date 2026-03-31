package com.blen.bludos

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

public class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)

         WindowCompat.setDecorFitsSystemWindows(window, false)
         val insetsController = WindowCompat.getInsetsController(window, window.decorView)
         insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
         insetsController?.hide(WindowInsetsCompat.Type.systemBars())

         setContentView(R.layout.activity_main)

         recyclerView = findViewById(R.id.recyclerViewProjects)
         recyclerView.layoutManager = LinearLayoutManager(this)

         findViewById<Button>(R.id.btnNewProject).setOnClickListener {
             showNewProjectDialog()
         }

         findViewById<Button>(R.id.btnImportProject).setOnClickListener {
             importProject()
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
        projectAdapter = ProjectAdapter(projects) { projectFile ->
            openProject(projectFile)
        }
        recyclerView.adapter = projectAdapter
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