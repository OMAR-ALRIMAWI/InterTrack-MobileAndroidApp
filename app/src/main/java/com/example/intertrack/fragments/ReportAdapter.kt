package com.example.intertrack.fragments

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.databinding.ItemReportRowBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ReportAdapter(
    private val items: List<FirestoreReport>,
    private val showStudentName: Boolean = false,
    private val onClick: (FirestoreReport) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemReportRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(report: FirestoreReport) {
            // Title line: for instructor/company show the STUDENT first; for the student's own list
            // show the report title. Everything falls back safely for old reports with missing fields.
            val reportTitle = report.reportTitle.ifBlank { "Student Report" }
            if (showStudentName) {
                binding.tvReportRowTitle.text = report.studentName.ifBlank { "Student" }
                binding.tvReportRowSubtitle.text = reportTitle
            } else {
                binding.tvReportRowTitle.text = reportTitle
                binding.tvReportRowSubtitle.text = report.reportPeriod.ifBlank { "No date specified" }
            }

            // Meta line: week · internship · company · deadline · submitted date.
            // Every piece falls back safely for old reports missing the weekly fields.
            val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val meta = listOf(
                report.reportWeekNumber.takeIf { it > 0 }?.let { "Week $it" },
                "Internship: ${report.internshipTitle.ifBlank { "—" }}",
                report.companyName.takeIf { it.isNotBlank() },
                report.deadlineDate?.toDate()?.let { "Deadline ${dateFmt.format(it)}" },
                report.createdAt?.toDate()?.let { "Submitted ${dateFmt.format(it)}" }
            ).filterNotNull().joinToString(" · ")
            binding.tvReportRowMeta.text = meta

            applyStatusBadge(report.status)
            binding.tvReportRowAction.text =
                if (report.status == "SUBMITTED" || report.status == "LATE") "Review Report ›" else "View Review ›"
            binding.root.setOnClickListener { onClick(report) }
        }

        private fun applyStatusBadge(status: String) {
            when (status) {
                "REVIEWED" -> {
                    binding.tvReportRowStatus.text = "Reviewed"
                    binding.tvReportRowStatus.setBackgroundResource(R.drawable.bg_status_approved)
                    binding.tvReportRowStatus.setTextColor(Color.parseColor("#166534"))
                }
                "REVISION_REQUESTED" -> {
                    binding.tvReportRowStatus.text = "Revision Requested"
                    binding.tvReportRowStatus.setBackgroundResource(R.drawable.bg_status_needs_mod)
                    binding.tvReportRowStatus.setTextColor(Color.parseColor("#DC2626"))
                }
                "LATE" -> {
                    binding.tvReportRowStatus.text = "Late"
                    binding.tvReportRowStatus.setBackgroundResource(R.drawable.bg_status_needs_mod)
                    binding.tvReportRowStatus.setTextColor(Color.parseColor("#DC2626"))
                }
                else -> {
                    binding.tvReportRowStatus.text = "Pending Review"
                    binding.tvReportRowStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    binding.tvReportRowStatus.setTextColor(Color.parseColor("#92400E"))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
