package com.blen.bludos

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ProjectManager {
    val rootDir: File by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Blen/Projects")
    }

    fun init() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    fun getProjects(): List<File> {
        val projects = mutableListOf<File>()
        if (rootDir.exists()) {
            rootDir.listFiles()?.forEach { file ->
                if (file.isDirectory && File(file, "project.blen").exists()) {
                    projects.add(file)
                }
            }
        }
        return projects
    }

    fun createProject(name: String): File? {
        val projectDir = File(rootDir, name)
        if (!projectDir.exists()) {
            if (projectDir.mkdirs()) {
                val projectFile = File(projectDir, "project.blen")
                val initialData = JSONObject().apply {
                    put("version", 1)
                    put("name", name)
                    val objects = JSONArray()
                    val cube = JSONObject().apply {
                        put("id", "Cube")
                        put("type", "cube")
                        val transform = JSONObject().apply {
                            put("px", 0.0)
                            put("py", 0.0)
                            put("pz", 0.0)
                            put("rx", 0.0)
                            put("ry", 0.0)
                            put("rz", 0.0)
                            put("sx", 1.0)
                            put("sy", 1.0)
                            put("sz", 1.0)
                        }
                        put("transform", transform)
                    }
                    objects.put(cube)
                    put("objects", objects)
                }
                FileOutputStream(projectFile).use { it.write(initialData.toString(4).toByteArray()) }
                return projectDir
            }
        }
        return null
    }

    fun loadProject(projectDir: File): JSONObject? {
        val projectFile = File(projectDir, "project.blen")
        if (projectFile.exists()) {
            val jsonString = projectFile.readText()
            return JSONObject(jsonString)
        }
        return null
    }

    fun saveProject(projectDir: File, data: JSONObject) {
        val projectFile = File(projectDir, "project.blen")
        FileOutputStream(projectFile).use { it.write(data.toString(4).toByteArray()) }
    }
}
