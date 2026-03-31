package com.blen.bludos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ProjectAdapter(
    private val projects: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvProjectName)
        val pathText: TextView = view.findViewById(R.id.tvProjectPath)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]
        holder.nameText.text = project.name
        holder.pathText.text = project.absolutePath
        holder.itemView.setOnClickListener { onClick(project) }
    }

    override fun getItemCount() = projects.size
}