package com.example.intertrack.fragments.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.PendingUser
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ItemPendingRequestBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PendingRequestAdapter(
    private val onClick: (PendingUser) -> Unit
) : RecyclerView.Adapter<PendingRequestAdapter.ViewHolder>() {

    private val items = mutableListOf<PendingUser>()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun submitList(list: List<PendingUser>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemPendingRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: PendingUser) {
            binding.tvRequestName.text = user.fullName
            binding.tvRequestEmail.text = user.email
            binding.tvRequestRole.text = user.roleDisplayName()

            val initials = user.fullName.trim().split(" ").filter { it.isNotEmpty() }
                .take(2).joinToString("") { it.first().uppercaseChar().toString() }
            binding.tvRequestInitials.text = initials.ifBlank { "?" }

            binding.tvRequestDate.text = user.registeredAt?.let {
                dateFormat.format(it.toDate())
            } ?: "Unknown date"

            binding.root.setOnClickListener { onClick(user) }
        }
    }
}
