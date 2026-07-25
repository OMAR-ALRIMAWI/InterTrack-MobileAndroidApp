package com.example.intertrack.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.R
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.databinding.FragmentCompanyInternshipsReviewBinding
import com.example.intertrack.databinding.ItemCompanyInternshipRowBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated Company "Review Your Internships" list: every internship connection for this company
 * across ACTIVE / COMPLETED / ENDED_BY_COMPANY (legacy ENDED), filterable by status chips. Tapping a
 * row opens the shared Internship Hub as COMPANY — active internships keep normal hub actions,
 * completed ones show the green state, ended ones show the red banner with the reason.
 */
class CompanyInternshipsReviewFragment : Fragment() {

    private var _binding: FragmentCompanyInternshipsReviewBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    private var allConnections: List<FirestoreInternshipConnection> = emptyList()
    private var reportCountByConnection: Map<String, Int> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanyInternshipsReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCompInternships.layoutManager = LinearLayoutManager(requireContext())

        binding.swipeRefreshCompInternships.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshCompInternships.setOnRefreshListener { loadInternships() }

        binding.chipGroupCompInternships.setOnCheckedChangeListener { _, _ -> renderFiltered() }

        loadInternships()
    }

    // ── States ────────────────────────────────────────────────────────────────

    private fun showLoading() {
        binding.progressCompInternships.visibility =
            if (binding.swipeRefreshCompInternships.isRefreshing) View.GONE else View.VISIBLE
        binding.rvCompInternships.visibility = View.GONE
        binding.tvCompInternshipsEmpty.visibility = View.GONE
        binding.tvCompInternshipsError.visibility = View.GONE
        // Clear stale rows so a previous load can never flash.
        binding.rvCompInternships.adapter = null
    }

    private fun showError(message: String) {
        binding.progressCompInternships.visibility = View.GONE
        binding.swipeRefreshCompInternships.isRefreshing = false
        binding.rvCompInternships.visibility = View.GONE
        binding.tvCompInternshipsEmpty.visibility = View.GONE
        binding.tvCompInternshipsError.visibility = View.VISIBLE
        binding.tvCompInternshipsError.text = message
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private fun loadInternships() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) { showError("Not signed in."); return }

        showLoading()

        val cachedCompanyId = AppSessionCache.currentCompany?.companyId ?: ""
        if (cachedCompanyId.isNotBlank()) {
            loadForCompany(cachedCompanyId)
        } else {
            // Resolve the supervisor's companyId from their user doc when the cache is cold.
            authRepo.fetchUserDocument(
                uid = uid,
                onSuccess = { user ->
                    if (_binding == null) return@fetchUserDocument
                    val cid = user.companyId ?: ""
                    if (cid.isBlank()) showError("No company linked to this account.")
                    else loadForCompany(cid)
                },
                onFailure = {
                    if (_binding == null) return@fetchUserDocument
                    showError("Could not load your company. Please try again.")
                }
            )
        }
    }

    private fun loadForCompany(companyId: String) {
        authRepo.getCompanyAllConnections(
            companyId = companyId,
            onSuccess = { connections ->
                if (_binding == null) return@getCompanyAllConnections
                allConnections = connections
                // Load company reports once to compute per-internship submitted counts.
                authRepo.getCompanyReports(
                    companyId = companyId,
                    onSuccess = { reports ->
                        if (_binding == null) return@getCompanyReports
                        reportCountByConnection = reports
                            .filter { it.internshipConnectionId.isNotBlank() }
                            .groupingBy { it.internshipConnectionId }
                            .eachCount()
                        renderFiltered()
                    },
                    onFailure = {
                        if (_binding == null) return@getCompanyReports
                        reportCountByConnection = emptyMap()
                        renderFiltered()
                    }
                )
            },
            onFailure = { _ ->
                if (_binding == null) return@getCompanyAllConnections
                showError("Could not load internships. Please try again.")
            }
        )
    }

    private fun renderFiltered() {
        if (_binding == null) return
        binding.progressCompInternships.visibility = View.GONE
        binding.swipeRefreshCompInternships.isRefreshing = false
        binding.tvCompInternshipsError.visibility = View.GONE

        val filtered = when {
            binding.chipCompIntActive.isChecked -> allConnections.filter { it.status == "ACTIVE" }
            binding.chipCompIntCompleted.isChecked -> allConnections.filter { it.isCompleted() }
            binding.chipCompIntEnded.isChecked -> allConnections.filter { it.isEndedByCompany() }
            else -> allConnections
        }

        if (filtered.isEmpty()) {
            binding.rvCompInternships.visibility = View.GONE
            binding.tvCompInternshipsEmpty.visibility = View.VISIBLE
            binding.tvCompInternshipsEmpty.text = when {
                allConnections.isEmpty() -> "No internships yet."
                binding.chipCompIntActive.isChecked -> "No active internships."
                binding.chipCompIntCompleted.isChecked -> "No completed internships yet."
                binding.chipCompIntEnded.isChecked -> "No ended internships."
                else -> "No internships yet."
            }
        } else {
            binding.tvCompInternshipsEmpty.visibility = View.GONE
            binding.rvCompInternships.visibility = View.VISIBLE
            binding.rvCompInternships.adapter = CompanyInternshipRowAdapter(
                items = filtered,
                reportCount = { conn -> reportCountByConnection[conn.connectionId] ?: 0 },
                onClick = { conn -> openHub(conn) }
            )
        }
    }

    private fun openHub(conn: FirestoreInternshipConnection) {
        if (conn.connectionId.isBlank()) return
        (requireActivity() as? CompanyDashBoard)
            ?.openDetail(InternshipProgressHubFragment.newInstance(conn.connectionId, "COMPANY"))
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Internships")
        }
        loadInternships()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CompanyInternshipRowAdapter(
    private val items: List<FirestoreInternshipConnection>,
    private val reportCount: (FirestoreInternshipConnection) -> Int,
    private val onClick: (FirestoreInternshipConnection) -> Unit
) : RecyclerView.Adapter<CompanyInternshipRowAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    inner class VH(val binding: ItemCompanyInternshipRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCompanyInternshipRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conn = items[position]
        with(holder.binding) {
            tvCompInternStudent.text = conn.studentName.ifBlank { "Student" }
            tvCompInternTitle.text = conn.internshipTitle.ifBlank { "Internship" }

            // Always show the instructor row so a "waiting" state is visible at a glance instead of
            // silently missing.
            tvCompInternInstructor.visibility = View.VISIBLE
            tvCompInternInstructor.text = "Instructor: " +
                conn.instructorName.ifBlank { "Not assigned yet" }

            val start = conn.startDate?.toDate()?.let { dateFmt.format(it) }
            val end = conn.endDate?.toDate()?.let { dateFmt.format(it) }
            tvCompInternPeriod.text =
                if (start != null && end != null) "$start – $end" else "Period not set"

            // Status badge + extra line (completed date / end reason).
            when {
                conn.isCompleted() -> {
                    tvCompInternStatus.text = "COMPLETED"
                    tvCompInternStatus.setTextColor(Color.parseColor("#166534"))
                    tvCompInternStatus.setBackgroundResource(R.drawable.bg_status_approved)
                    val done = conn.completedAt?.toDate()?.let { dateFmt.format(it) }
                    tvCompInternExtra.visibility = View.VISIBLE
                    tvCompInternExtra.setTextColor(Color.parseColor("#166534"))
                    tvCompInternExtra.text = if (done != null) "Completed $done" else "Completed"
                }
                conn.isEndedByCompany() -> {
                    tvCompInternStatus.text = "ENDED"
                    tvCompInternStatus.setTextColor(Color.parseColor("#DC2626"))
                    tvCompInternStatus.setBackgroundResource(R.drawable.bg_status_rejected)
                    tvCompInternExtra.visibility = View.VISIBLE
                    tvCompInternExtra.setTextColor(Color.parseColor("#991B1B"))
                    tvCompInternExtra.text =
                        "Reason: ${conn.endReason.ifBlank { "No reason provided." }}"
                }
                conn.status == "ACTIVE" -> {
                    tvCompInternStatus.text = "ACTIVE"
                    tvCompInternStatus.setTextColor(Color.parseColor("#005FAF"))
                    tvCompInternStatus.setBackgroundResource(R.drawable.bg_filter_chip_inactive)
                    tvCompInternExtra.visibility = View.GONE
                }
                conn.status == "WAITING_INSTRUCTOR_CONNECTION" -> {
                    tvCompInternStatus.text = "WAITING FOR INSTRUCTOR"
                    tvCompInternStatus.setTextColor(Color.parseColor("#92400E"))
                    tvCompInternStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    tvCompInternExtra.visibility = View.VISIBLE
                    tvCompInternExtra.setTextColor(Color.parseColor("#92400E"))
                    tvCompInternExtra.text = "Waiting for instructor confirmation."
                }
                else -> {
                    tvCompInternStatus.text = conn.status
                    tvCompInternStatus.setTextColor(Color.parseColor("#92400E"))
                    tvCompInternStatus.setBackgroundResource(R.drawable.bg_status_pending)
                    tvCompInternExtra.visibility = View.GONE
                }
            }

            val submitted = reportCount(conn)
            tvCompInternReports.text = if (conn.requiredReportsCount > 0) {
                val remaining = (conn.requiredReportsCount - submitted).coerceAtLeast(0)
                "Reports: $submitted / ${conn.requiredReportsCount} · Remaining: $remaining"
            } else {
                "Reports: $submitted"
            }

            root.setOnClickListener { onClick(conn) }
        }
    }

    override fun getItemCount() = items.size
}
