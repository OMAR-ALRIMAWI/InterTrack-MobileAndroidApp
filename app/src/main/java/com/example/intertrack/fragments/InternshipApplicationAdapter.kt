package com.example.intertrack.fragments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.data.model.FirestoreInternshipApplication
import com.example.intertrack.databinding.ItemInternshipApplicationBinding
import java.text.SimpleDateFormat
import java.util.Locale

class InternshipApplicationAdapter(
    private val items: List<FirestoreInternshipApplication>,
    private val onAccept: (FirestoreInternshipApplication) -> Unit,
    private val onReject: (FirestoreInternshipApplication) -> Unit,
    private val onItemClick: ((FirestoreInternshipApplication) -> Unit)? = null,
    private val onRemove: ((FirestoreInternshipApplication) -> Unit)? = null,
    private val onProgressHub: ((FirestoreInternshipApplication) -> Unit)? = null,
    private val onMessageStudent: ((FirestoreInternshipApplication) -> Unit)? = null,
    private val onProgressChat: ((FirestoreInternshipApplication) -> Unit)? = null,
    // applicationId -> connection state token: "ACTIVE" | "WAITING" (instructor attached, not yet
    // confirmed) | "NO_INSTRUCTOR" (no instructor / no connection). Drives the accepted-row status
    // line and primary-button label so it never promises a hub that can't open.
    private val connStateByApp: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<InternshipApplicationAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemInternshipApplicationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: FirestoreInternshipApplication) {
            val nameParts = app.studentName.trim().split(" ").filter { it.isNotEmpty() }
            val initials = when {
                nameParts.size >= 2 -> "${nameParts[0].first()}${nameParts[1].first()}".uppercase()
                nameParts.size == 1 -> nameParts[0].take(2).uppercase()
                else -> "?"
            }
            binding.tvAppItemInitials.text = initials

            binding.tvAppItemName.text = app.studentName.ifBlank { "Student" }

            val infoBuilder = StringBuilder()
            if (app.studentUniversity.isNotBlank()) infoBuilder.append(app.studentUniversity)
            if (app.studentUniversity.isNotBlank() && app.studentMajor.isNotBlank()) infoBuilder.append(" · ")
            if (app.studentMajor.isNotBlank()) infoBuilder.append(app.studentMajor)
            if (app.studentYearLevel.isNotBlank()) infoBuilder.append(" · ${app.studentYearLevel}")
            binding.tvAppItemInfo.text = infoBuilder.toString().ifBlank { "—" }

            if (app.studentSkills.isNotBlank()) {
                binding.tvAppItemSkills.text = "Skills: ${app.studentSkills}"
                binding.tvAppItemSkills.visibility = View.VISIBLE
            } else {
                binding.tvAppItemSkills.visibility = View.GONE
            }

            val dateStr = app.createdAt?.let {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(it.toDate())
            } ?: "—"
            binding.tvAppItemDate.text = "Applied: $dateStr"

            applyStatusBadge(app.status)

            if (app.status == "REJECTED" && app.rejectionReason.isNotBlank()) {
                binding.tvAppItemRejectionReason.text = "Reason: ${app.rejectionReason}"
                binding.tvAppItemRejectionReason.visibility = View.VISIBLE
            } else {
                binding.tvAppItemRejectionReason.visibility = View.GONE
            }

            when (app.status) {
                "PENDING" -> {
                    binding.llAppItemActions.visibility = View.VISIBLE
                    binding.llAcceptedActions.visibility = View.GONE
                    binding.tvAppItemConnStatus.visibility = View.GONE
                    binding.btnAcceptAppItem.setOnClickListener { onAccept(app) }
                    binding.btnRejectAppItem.setOnClickListener { onReject(app) }
                }
                "ACCEPTED" -> {
                    binding.llAppItemActions.visibility = View.GONE
                    // Instructor / connection status line.
                    val state = connStateByApp[app.applicationId] ?: "NO_INSTRUCTOR"
                    val isActive = state == "ACTIVE"
                    binding.tvAppItemConnStatus.visibility = View.VISIBLE
                    when (state) {
                        "ACTIVE" -> {
                            binding.tvAppItemConnStatus.text = "Active Internship"
                            binding.tvAppItemConnStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                        }
                        "WAITING" -> {
                            binding.tvAppItemConnStatus.text = "Waiting Instructor Confirmation"
                            binding.tvAppItemConnStatus.setTextColor(android.graphics.Color.parseColor("#C2410C"))
                        }
                        else -> {
                            binding.tvAppItemConnStatus.text = "Instructor not added yet"
                            binding.tvAppItemConnStatus.setTextColor(android.graphics.Color.parseColor("#64748B"))
                        }
                    }
                    if (onProgressHub != null || onMessageStudent != null || onProgressChat != null) {
                        binding.llAcceptedActions.visibility = View.VISIBLE
                        // Only an instructor-confirmed (ACTIVE) connection can open the hub. While it
                        // is still waiting, the button asks for the instructor connection instead of
                        // dead-ending on "You don't have access".
                        binding.btnAppProgressHub.text =
                            if (isActive) "Open Internship Hub" else "Request Instructor Connection"
                        binding.btnAppProgressHub.setOnClickListener { onProgressHub?.invoke(app) }
                        binding.btnAppMessageStudent.setOnClickListener { onMessageStudent?.invoke(app) }
                        binding.btnAppProgressChat.setOnClickListener { onProgressChat?.invoke(app) }
                    } else {
                        binding.llAcceptedActions.visibility = View.GONE
                    }
                }
                else -> {
                    binding.llAppItemActions.visibility = View.GONE
                    binding.llAcceptedActions.visibility = View.GONE
                    binding.tvAppItemConnStatus.visibility = View.GONE
                }
            }

            if (onRemove != null && app.status in listOf("PENDING", "ACCEPTED")) {
                binding.btnRemoveAppItem.visibility = View.VISIBLE
                val removeLabel = if (app.status == "ACCEPTED") "End Internship" else "Remove Application"
                binding.btnRemoveAppItem.text = removeLabel
                binding.btnRemoveAppItem.setOnClickListener { onRemove.invoke(app) }
            } else {
                binding.btnRemoveAppItem.visibility = View.GONE
            }

            binding.root.setOnClickListener { onItemClick?.invoke(app) }
        }

        private fun applyStatusBadge(status: String) {
            val ctx = binding.root.context
            when (status) {
                "PENDING" -> {
                    binding.tvAppItemStatus.text = "Pending"
                    binding.tvAppItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvAppItemStatus.setTextColor(android.graphics.Color.parseColor("#92400E"))
                }
                "ACCEPTED" -> {
                    binding.tvAppItemStatus.text = "Accepted"
                    binding.tvAppItemStatus.setBackgroundResource(R.drawable.bg_status_approved)
                    binding.tvAppItemStatus.setTextColor(android.graphics.Color.parseColor("#166534"))
                }
                "REJECTED" -> {
                    binding.tvAppItemStatus.text = "Rejected"
                    binding.tvAppItemStatus.setBackgroundResource(R.drawable.bg_status_blocked)
                    binding.tvAppItemStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                }
                "CANCELLED" -> {
                    binding.tvAppItemStatus.text = "Cancelled"
                    binding.tvAppItemStatus.setBackgroundResource(R.drawable.bg_status_blocked)
                    binding.tvAppItemStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                }
                else -> {
                    binding.tvAppItemStatus.text = status
                    binding.tvAppItemStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvAppItemStatus.setTextColor(android.graphics.Color.parseColor("#92400E"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInternshipApplicationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
