package com.example.intertrack.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.FirestoreNotification
import com.example.intertrack.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationAdapter(
    private val items: List<FirestoreNotification>,
    private val onClick: (FirestoreNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notif: FirestoreNotification) {
            binding.tvNotifTitle.text = notif.title
            binding.tvNotifMessage.text = notif.message

            val timeText = notif.createdAt?.toDate()?.let { date ->
                val now = System.currentTimeMillis()
                val diffMs = now - date.time
                val diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs)
                val diffHrs = TimeUnit.MILLISECONDS.toHours(diffMs)
                val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
                when {
                    diffMin < 1 -> "Just now"
                    diffMin < 60 -> "${diffMin}m ago"
                    diffHrs < 24 -> "${diffHrs}h ago"
                    diffDays == 1L -> "Yesterday"
                    diffDays < 7 -> "${diffDays}d ago"
                    else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
                }
            } ?: ""
            binding.tvNotifTime.text = timeText

            if (notif.isRead) {
                binding.vNotifUnreadBar.visibility = View.INVISIBLE
                binding.vNotifDot.visibility = View.INVISIBLE
            } else {
                binding.vNotifUnreadBar.visibility = View.VISIBLE
                binding.vNotifDot.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener { onClick(notif) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
