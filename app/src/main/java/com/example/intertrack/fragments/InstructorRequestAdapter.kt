package com.example.intertrack.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.data.model.FirestoreInstructorRequest
import com.example.intertrack.databinding.ItemInstructorRequestBinding

class InstructorRequestAdapter(
    private val items: List<FirestoreInstructorRequest>,
    private val onAccept: (FirestoreInstructorRequest) -> Unit,
    private val onDecline: (FirestoreInstructorRequest) -> Unit,
    private val onMessage: (FirestoreInstructorRequest) -> Unit = {}
) : RecyclerView.Adapter<InstructorRequestAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemInstructorRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: FirestoreInstructorRequest) {
            val nameParts = request.studentName.trim().split(" ").filter { it.isNotEmpty() }
            val initials = when {
                nameParts.size >= 2 -> "${nameParts[0].first()}${nameParts[1].first()}".uppercase()
                nameParts.size == 1 -> nameParts[0].take(2).uppercase()
                else -> "?"
            }
            binding.tvReqItemInitials.text = initials
            binding.tvReqItemName.text = request.studentName.ifBlank { "Student" }

            val infoBuilder = StringBuilder()
            if (request.studentMajor.isNotBlank()) infoBuilder.append(request.studentMajor)
            binding.tvReqItemInfo.text = infoBuilder.toString().ifBlank { "—" }
            binding.tvReqItemUniversity.text = request.studentUniversity.ifBlank { "—" }

            if (request.studentCompanyName.isNotBlank()) {
                binding.tvReqItemCompany.text = request.studentCompanyName
                binding.llReqItemCompanyRow.visibility = View.VISIBLE
            } else {
                binding.llReqItemCompanyRow.visibility = View.GONE
            }

            applyStatusBadge(request.status)

            if (request.status == "REJECTED" && request.rejectionReason.isNotBlank()) {
                binding.tvReqItemRejectionReason.text = "Reason: ${request.rejectionReason}"
                binding.tvReqItemRejectionReason.visibility = View.VISIBLE
            } else {
                binding.tvReqItemRejectionReason.visibility = View.GONE
            }

            if (request.status == "PENDING") {
                binding.llReqItemActions.visibility = View.VISIBLE
                binding.btnAcceptReqItem.setOnClickListener { onAccept(request) }
                binding.btnDeclineReqItem.setOnClickListener { onDecline(request) }
            } else {
                binding.llReqItemActions.visibility = View.GONE
            }

            if (request.status == "ACCEPTED") {
                binding.btnMessageReqItem.visibility = View.VISIBLE
                binding.btnMessageReqItem.setOnClickListener { onMessage(request) }
            } else {
                binding.btnMessageReqItem.visibility = View.GONE
            }
        }

        private fun applyStatusBadge(status: String) {
            when (status) {
                "PENDING" -> {
                    binding.tvReqItemStatus.text = "New"
                    binding.tvReqItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvReqItemStatus.setTextColor(android.graphics.Color.parseColor("#92400E"))
                }
                "ACCEPTED" -> {
                    binding.tvReqItemStatus.text = "Active"
                    binding.tvReqItemStatus.setBackgroundResource(R.drawable.bg_status_approved)
                    binding.tvReqItemStatus.setTextColor(android.graphics.Color.parseColor("#166534"))
                }
                "REJECTED" -> {
                    binding.tvReqItemStatus.text = "Declined"
                    binding.tvReqItemStatus.setBackgroundResource(R.drawable.bg_status_blocked)
                    binding.tvReqItemStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                }
                else -> {
                    binding.tvReqItemStatus.text = status
                    binding.tvReqItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvReqItemStatus.setTextColor(android.graphics.Color.parseColor("#92400E"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInstructorRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
