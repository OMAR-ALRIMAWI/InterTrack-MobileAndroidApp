package com.example.intertrack.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.FirestoreNotification
import com.example.intertrack.databinding.ItemPopupNotificationBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PopupNotificationAdapter(
    list: List<FirestoreNotification>,
    private val onClick: () -> Unit
) : RecyclerView.Adapter<PopupNotificationAdapter.VH>() {

    private val items: MutableList<FirestoreNotification> = list.toMutableList()

    inner class VH(val binding: ItemPopupNotificationBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPopupNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        holder.binding.tvPopupNotifTitle.text = n.title
        holder.binding.tvPopupNotifMessage.text = n.message

        val timeText = n.createdAt?.toDate()?.let { date ->
            val diffMs = System.currentTimeMillis() - date.time
            val mins   = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            val hrs    = TimeUnit.MILLISECONDS.toHours(diffMs)
            val days   = TimeUnit.MILLISECONDS.toDays(diffMs)
            when {
                mins  < 1  -> "Just now"
                mins  < 60 -> "${mins}m ago"
                hrs   < 24 -> "${hrs}h ago"
                days == 1L -> "Yesterday"
                days  < 7  -> "${days}d ago"
                else       -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        } ?: ""
        holder.binding.tvPopupNotifTime.text = timeText
        holder.binding.vPopupNotifDot.visibility = if (n.isRead) View.INVISIBLE else View.VISIBLE
        holder.binding.root.setOnClickListener { onClick() }
    }

    fun markAllRead() {
        val updated = items.map { it.copy(isRead = true) }
        items.clear()
        items.addAll(updated)
        notifyDataSetChanged()
    }
}
