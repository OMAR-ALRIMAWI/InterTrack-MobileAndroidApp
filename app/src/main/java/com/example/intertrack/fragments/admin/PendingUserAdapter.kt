package com.example.intertrack.fragments.admin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.PendingUser
import com.example.intertrack.databinding.ItemPendingUserBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PendingUserAdapter(
    private val onApprove: (PendingUser) -> Unit,
    private val onReject: (PendingUser) -> Unit
) : RecyclerView.Adapter<PendingUserAdapter.ViewHolder>() {

    private val items = mutableListOf<PendingUser>()

    fun submitList(newList: List<PendingUser>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun removeItem(uid: String) {
        val index = items.indexOfFirst { it.uid == uid }
        if (index != -1) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPendingUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemPendingUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var actionInProgress = false

        fun bind(user: PendingUser) {
            actionInProgress = false
            setButtonsEnabled(true)

            val initials = user.fullName
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
            binding.tvInitials.text = if (initials.isNotEmpty()) initials else "?"

            binding.tvFullName.text = user.fullName
            binding.tvEmail.text = user.email
            binding.tvRolePill.text = user.roleDisplayName()

            val dateText = user.registeredAt?.toDate()?.let { date ->
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
            } ?: "—"
            binding.tvRegisteredAt.text = "Registered: $dateText"

            binding.btnApprove.setOnClickListener {
                if (actionInProgress) return@setOnClickListener
                actionInProgress = true
                setButtonsEnabled(false)
                onApprove(user)
            }

            binding.btnReject.setOnClickListener {
                if (actionInProgress) return@setOnClickListener
                actionInProgress = true
                setButtonsEnabled(false)
                onReject(user)
            }
        }

        private fun setButtonsEnabled(enabled: Boolean) {
            binding.btnApprove.isEnabled = enabled
            binding.btnReject.isEnabled = enabled
            binding.btnApprove.alpha = if (enabled) 1f else 0.5f
            binding.btnReject.alpha = if (enabled) 1f else 0.5f
        }
    }
}
