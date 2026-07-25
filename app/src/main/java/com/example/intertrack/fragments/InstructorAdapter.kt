package com.example.intertrack.fragments

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.databinding.ItemInstructorBinding

class InstructorAdapter(
    private val fullList: List<Instructor>,
    private val onItemClick: (Instructor) -> Unit
) : RecyclerView.Adapter<InstructorAdapter.ViewHolder>() {

    private var items: List<Instructor> = fullList

    inner class ViewHolder(private val binding: ItemInstructorBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(instructor: Instructor) {
            binding.tvInstructorInitials.text = instructor.initials
            binding.tvInstructorName.text = instructor.name
            binding.tvInstructorDept.text = instructor.department
            binding.tvInstructorUniversity.text = instructor.university
            binding.tvInstructorEmail.text = instructor.email

            try {
                binding.instructorAvatarFrame.background.setTint(
                    Color.parseColor(instructor.accentColor)
                )
            } catch (_: Exception) { /* keep default */ }

            binding.root.setOnClickListener { onItemClick(instructor) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInstructorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun filter(query: String) {
        items = if (query.isBlank()) {
            fullList
        } else {
            val q = query.lowercase()
            fullList.filter { inst ->
                inst.name.lowercase().contains(q) ||
                    inst.university.lowercase().contains(q) ||
                    inst.email.lowercase().contains(q) ||
                    inst.department.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }
}
