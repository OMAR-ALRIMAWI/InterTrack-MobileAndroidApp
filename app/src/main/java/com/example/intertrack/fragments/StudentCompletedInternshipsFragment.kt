package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.databinding.FragmentStudentCompletedInternshipsBinding
import com.example.intertrack.databinding.ItemCompletedInternshipBinding
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Student's list of COMPLETED internships (opened from the Profile "Completed Internships" card).
 * Loading -> Content / Empty / Error. Each row opens the shared Internship Hub as the detail view.
 */
class StudentCompletedInternshipsFragment : Fragment() {

    private var _binding: FragmentStudentCompletedInternshipsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    /** A completed connection paired with its computed submitted-report count. */
    data class CompletedItem(val conn: FirestoreInternshipConnection, val submitted: Int)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentCompletedInternshipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvCompleted.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshCompleted.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshCompleted.setOnRefreshListener { loadData() }
        loadData()
    }

    private fun showLoading() {
        if (!binding.swipeRefreshCompleted.isRefreshing) binding.progressCompleted.visibility = View.VISIBLE
        binding.rvCompleted.visibility = View.GONE
        binding.tvCompletedEmpty.visibility = View.GONE
        binding.tvCompletedError.visibility = View.GONE
        // Clear stale data so a previous student's list can never flash.
        binding.rvCompleted.adapter = null
    }

    private fun showContent(items: List<CompletedItem>) {
        binding.progressCompleted.visibility = View.GONE
        binding.swipeRefreshCompleted.isRefreshing = false
        binding.tvCompletedError.visibility = View.GONE
        if (items.isEmpty()) {
            binding.rvCompleted.visibility = View.GONE
            binding.tvCompletedEmpty.visibility = View.VISIBLE
        } else {
            binding.tvCompletedEmpty.visibility = View.GONE
            binding.rvCompleted.visibility = View.VISIBLE
            binding.rvCompleted.adapter = CompletedInternshipAdapter(items) { openHub(it.conn) }
        }
    }

    private fun showError() {
        binding.progressCompleted.visibility = View.GONE
        binding.swipeRefreshCompleted.isRefreshing = false
        binding.rvCompleted.visibility = View.GONE
        binding.tvCompletedEmpty.visibility = View.GONE
        binding.tvCompletedError.visibility = View.VISIBLE
    }

    private fun loadData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) { showError(); return }

        showLoading()

        authRepo.getStudentCompletedConnections(
            studentUid = uid,
            onSuccess = { connections ->
                if (_binding == null) return@getStudentCompletedConnections
                // Load reports once to compute submitted-per-connection counts.
                authRepo.getStudentReports(
                    studentUid = uid,
                    onSuccess = { reports ->
                        if (_binding == null) return@getStudentReports
                        val countByConn = reports.groupingBy {
                            it.internshipConnectionId.ifBlank { it.studentUid }
                        }.eachCount()
                        val items = connections.map { c ->
                            CompletedItem(c, countByConn[c.connectionId] ?: countByConn[c.studentUid] ?: 0)
                        }
                        showContent(items)
                    },
                    onFailure = {
                        if (_binding == null) return@getStudentReports
                        showContent(connections.map { CompletedItem(it, 0) })
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@getStudentCompletedConnections
                showError()
            }
        )
    }

    private fun openHub(conn: FirestoreInternshipConnection) {
        if (conn.connectionId.isBlank()) return
        (requireActivity() as? StudentDashBoard)
            ?.openDetail(InternshipProgressHubFragment.newInstance(conn.connectionId, "STUDENT"))
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Completed Internships")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class CompletedInternshipAdapter(
    private val items: List<StudentCompletedInternshipsFragment.CompletedItem>,
    private val onClick: (StudentCompletedInternshipsFragment.CompletedItem) -> Unit
) : RecyclerView.Adapter<CompletedInternshipAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    inner class VH(val binding: ItemCompletedInternshipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCompletedInternshipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val conn = item.conn
        with(holder.binding) {
            tvCompletedItemTitle.text = conn.internshipTitle.ifBlank { "Internship" }

            tvCompletedItemCompany.text = buildString {
                append(conn.companyName.ifBlank { "Company" })
                if (conn.supervisorName.isNotBlank()) append(" · Supervisor: ${conn.supervisorName}")
            }
            if (conn.instructorName.isNotBlank()) {
                tvCompletedItemInstructor.visibility = View.VISIBLE
                tvCompletedItemInstructor.text = "Instructor: ${conn.instructorName}"
            } else {
                tvCompletedItemInstructor.visibility = View.GONE
            }

            val start = conn.startDate?.toDate()?.let { dateFmt.format(it) }
            val end = conn.endDate?.toDate()?.let { dateFmt.format(it) }
            val completed = conn.completedAt?.toDate()?.let { dateFmt.format(it) }
            tvCompletedItemPeriod.text = buildString {
                if (start != null && end != null) append("$start – $end")
                else append("Period not recorded")
                if (completed != null) append(" · Completed $completed")
            }

            tvCompletedItemReports.text = "Reports submitted: ${item.submitted}"
            val remaining = if (conn.requiredReportsCount > 0)
                (conn.requiredReportsCount - item.submitted).coerceAtLeast(0) else 0
            tvCompletedItemRemaining.text = "Remaining: $remaining"

            root.setOnClickListener { onClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
