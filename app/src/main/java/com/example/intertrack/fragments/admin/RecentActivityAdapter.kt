package com.example.intertrack.fragments.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.AdminLog
import com.example.intertrack.databinding.ItemRecentActivityBinding
import java.text.SimpleDateFormat
import java.util.Locale

class RecentActivityAdapter : RecyclerView.Adapter<RecentActivityAdapter.ViewHolder>() {

    private val items = mutableListOf<AdminLog>()
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun submitList(list: List<AdminLog>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentActivityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemRecentActivityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: AdminLog) {
            binding.tvActivityName.text = log.fullName.ifBlank { log.email }
            binding.tvActivityLabel.text = log.actionLabel()
            binding.tvActivityRole.text = log.roleDisplayName()
            binding.tvActivityDate.text = log.updatedAt?.let {
                dateFormat.format(it.toDate())
            } ?: ""

            val (iconRes, bgRes) = iconForStatus(log.accountStatus)
            binding.ivActivityIcon.setImageResource(iconRes)
            binding.activityIconBg.setBackgroundResource(bgRes)
        }

        private fun iconForStatus(status: String): Pair<Int, Int> = when (status) {
            AccountStatus.ACTIVE.value   -> R.drawable.ic_check_circle to R.drawable.bg_icon_circle_light_green
            AccountStatus.REJECTED.value -> R.drawable.ic_back_arrow to R.drawable.bg_icon_circle_light_red
            AccountStatus.BLOCKED.value  -> R.drawable.ic_lock to R.drawable.bg_icon_circle_light_orange
            AccountStatus.DELETED.value  -> R.drawable.ic_lock to R.drawable.bg_icon_circle_light_red
            else                         -> R.drawable.ic_add_person to R.drawable.bg_icon_circle_light_blue
        }
    }
}
