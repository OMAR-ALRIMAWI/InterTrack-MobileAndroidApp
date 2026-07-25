package com.example.intertrack.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.FirestoreConversation
import com.example.intertrack.databinding.ItemConversationRowBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ConversationAdapter(
    private val items: List<FirestoreConversation>,
    private val currentUid: String,
    private val onClick: (FirestoreConversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemConversationRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: FirestoreConversation) {
            val title = conversation.displayTitle(currentUid)
            val subtitle = conversation.displaySubtitle(currentUid)

            binding.tvConvRowName.text = title
            binding.tvConvRowInitials.text = initialsOf(title)
            binding.tvConvRowSubtitle.text = subtitle
            binding.tvConvRowSubtitle.visibility = if (subtitle.isNotBlank()) View.VISIBLE else View.GONE
            binding.tvConvRowLastMessage.text =
                conversation.lastMessage.ifBlank { "No messages yet" }
            binding.tvConvRowTime.text = formatTime(conversation.lastMessageAt?.toDate())
            binding.vConvRowUnreadDot.visibility =
                if (conversation.unreadBy.contains(currentUid)) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(conversation) }
        }

        private fun initialsOf(name: String): String {
            val parts = name.trim().split(" ").filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "?"
            }
        }

        private fun formatTime(date: Date?): String {
            if (date == null) return ""
            val diff = System.currentTimeMillis() - date.time
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
                diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
