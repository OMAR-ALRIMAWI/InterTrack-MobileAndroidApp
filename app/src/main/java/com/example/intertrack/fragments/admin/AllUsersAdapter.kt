package com.example.intertrack.fragments.admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.ItemUserAccountBinding

class AllUsersAdapter(
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<AllUsersAdapter.ViewHolder>() {

    private val items = mutableListOf<User>()

    fun submitList(list: List<User>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUserAccountBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemUserAccountBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvUserName.text = user.fullName
            binding.tvUserEmail.text = user.email
            binding.tvUserInitials.text = user.initials()

            binding.tvUserRole.text = when (user.role) {
                UserRole.STUDENT.value           -> "Student"
                UserRole.INSTRUCTOR.value        -> "Academic Supervisor"
                UserRole.COMPANY_SUPERVISOR.value -> "Company Supervisor"
                else                              -> user.role
            }

            val (statusLabel, bgColor, textColor) = statusStyle(user.accountStatus)
            binding.tvUserStatus.text = statusLabel
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(Color.parseColor(bgColor))
            }
            binding.tvUserStatus.background = bg
            binding.tvUserStatus.setTextColor(Color.parseColor(textColor))

            binding.root.setOnClickListener { onClick(user) }
        }

        private fun statusStyle(status: String): Triple<String, String, String> = when (status) {
            AccountStatus.ACTIVE.value   -> Triple("Active",   "#DCFCE7", "#166534")
            AccountStatus.PENDING.value  -> Triple("Pending",  "#FEF3C7", "#92400E")
            AccountStatus.REJECTED.value -> Triple("Rejected", "#FEE2E2", "#991B1B")
            AccountStatus.BLOCKED.value  -> Triple("Blocked",  "#374151", "#FFFFFF")
            AccountStatus.DELETED.value  -> Triple("Deleted",  "#E5E7EB", "#6B7280")
            else                         -> Triple(status,     "#E5E7EB", "#374151")
        }
    }
}
