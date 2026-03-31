package com.blen.bludos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class HierarchyAdapter(
    private val objects: MutableList<JSONObject>,
    private val onClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<HierarchyAdapter.HierarchyViewHolder>() {

    class HierarchyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tvObjectName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HierarchyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hierarchy, parent, false)
        return HierarchyViewHolder(view)
    }

    override fun onBindViewHolder(holder: HierarchyViewHolder, position: Int) {
        val obj = objects[position]
        holder.nameText.text = obj.optString("id", "Unknown")
        holder.itemView.setOnClickListener { onClick(obj) }
    }

    override fun getItemCount() = objects.size
}