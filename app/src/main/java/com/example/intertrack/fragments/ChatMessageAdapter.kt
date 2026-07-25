package com.example.intertrack.fragments

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.data.model.FirestoreMessage
import com.example.intertrack.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ChatMessageAdapter(
    private val items: List<FirestoreMessage>,
    private val currentUid: String
) : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: FirestoreMessage) {
            binding.tvChatBubbleText.text = message.messageText
            val time = message.createdAt?.toDate()?.let {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
            } ?: ""
            binding.tvChatBubbleTime.text = time

            val isOutgoing = message.senderUid == currentUid
            if (isOutgoing) {
                binding.tvChatBubbleText.background =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.bg_message_sent)
                binding.tvChatBubbleText.setTextColor(Color.WHITE)
                binding.chatRowBubbleColumn.gravity = Gravity.END
                binding.chatRowSpacerStart.visibility = android.view.View.VISIBLE
                binding.chatRowSpacerEnd.visibility = android.view.View.GONE
            } else {
                binding.tvChatBubbleText.background =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.bg_message_received)
                binding.tvChatBubbleText.setTextColor(Color.parseColor("#0F172A"))
                binding.chatRowBubbleColumn.gravity = Gravity.START
                binding.chatRowSpacerStart.visibility = android.view.View.GONE
                binding.chatRowSpacerEnd.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
