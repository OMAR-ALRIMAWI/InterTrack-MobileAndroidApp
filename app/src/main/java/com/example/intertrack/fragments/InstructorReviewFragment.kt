package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.databinding.FragmentInstructorReviewBinding
import com.example.intertrack.databinding.ItemInternshipReviewBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Instructor "Internships" tab: lists the ACTIVE internships assigned to this instructor with a
 * report count, and opens the internship review hub on tap. (Replaces the old flat report list.)
 */
class InstructorReviewFragment : Fragment() {

    private var _binding: FragmentInstructorReviewBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructorReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvInstructorReports.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshInstructorReview.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshInstructorReview.setOnRefreshListener { loadInternships() }
        binding.tvInstructorReviewEmpty.text = "No internships yet."
        loadInternships()
    }

    private fun loadInternships() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressInstructorReview.visibility =
            if (binding.swipeRefreshInstructorReview.isRefreshing) View.GONE else View.VISIBLE
        binding.tvInstructorReviewError.visibility = View.GONE
        binding.tvInstructorReviewEmpty.visibility = View.GONE
        binding.rvInstructorReports.visibility = View.GONE

        authRepo.getInstructorActiveConnections(
            instructorUid = uid,
            onSuccess = { active ->
                if (_binding == null) return@getInstructorActiveConnections
                // Also surface COMPLETED internships (green history cards)…
                authRepo.getInstructorCompletedConnections(
                    instructorUid = uid,
                    onSuccess = { completed ->
                        if (_binding == null) return@getInstructorCompletedConnections
                        loadEndedThenFinish(uid, active + completed)
                    },
                    onFailure = {
                        if (_binding == null) return@getInstructorCompletedConnections
                        loadEndedThenFinish(uid, active)
                    }
                )
            },
            onFailure = { _ ->
                if (_binding == null) return@getInstructorActiveConnections
                binding.progressInstructorReview.visibility = View.GONE
                binding.swipeRefreshInstructorReview.isRefreshing = false
                binding.tvInstructorReviewError.visibility = View.VISIBLE
            }
        )
    }

    /** …then company-ended internships (red cards), then render everything together. */
    private fun loadEndedThenFinish(uid: String, soFar: List<FirestoreInternshipConnection>) {
        authRepo.getInstructorEndedConnections(
            instructorUid = uid,
            onSuccess = { ended ->
                if (_binding == null) return@getInstructorEndedConnections
                finishLoad(uid, (soFar + ended).distinctBy { it.connectionId })
            },
            onFailure = {
                if (_binding == null) return@getInstructorEndedConnections
                finishLoad(uid, soFar)
            }
        )
    }

    private fun finishLoad(uid: String, connections: List<FirestoreInternshipConnection>) {
        // Load this instructor's reports once to compute per-internship counts.
        authRepo.getInstructorReports(
            instructorUid = uid,
            onSuccess = { reports ->
                if (_binding == null) return@getInstructorReports
                val countByConnection = reports.groupingBy {
                    it.internshipConnectionId.ifBlank { it.studentUid }
                }.eachCount()
                render(connections) { conn ->
                    countByConnection[conn.connectionId]
                        ?: countByConnection[conn.studentUid] ?: 0
                }
            },
            onFailure = {
                if (_binding == null) return@getInstructorReports
                render(connections) { 0 }
            }
        )
    }

    private fun render(
        connections: List<FirestoreInternshipConnection>,
        reportCount: (FirestoreInternshipConnection) -> Int
    ) {
        binding.progressInstructorReview.visibility = View.GONE
        binding.swipeRefreshInstructorReview.isRefreshing = false
        if (connections.isEmpty()) {
            binding.tvInstructorReviewEmpty.visibility = View.VISIBLE
            binding.rvInstructorReports.visibility = View.GONE
        } else {
            binding.rvInstructorReports.visibility = View.VISIBLE
            binding.rvInstructorReports.adapter = InternshipReviewAdapter(connections, reportCount) { conn ->
                (requireActivity() as? InstructorDashBoard)?.openDetail(
                    InternshipProgressHubFragment.newInstance(conn.connectionId, "INSTRUCTOR")
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? InstructorDashBoard)?.apply {
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

class InternshipReviewAdapter(
    private val items: List<FirestoreInternshipConnection>,
    private val reportCount: (FirestoreInternshipConnection) -> Int,
    private val onReview: (FirestoreInternshipConnection) -> Unit
) : RecyclerView.Adapter<InternshipReviewAdapter.VH>() {

    inner class VH(val binding: ItemInternshipReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemInternshipReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conn = items[position]
        with(holder.binding) {
            tvRevItemTitle.text = conn.internshipTitle.ifBlank { "Internship" }
            tvRevItemStudent.text = conn.studentName.ifBlank { "Student" }
            tvRevItemCompany.text = listOfNotNull(
                conn.companyName.takeIf { it.isNotBlank() },
                conn.supervisorName.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "—" }
            val submitted = reportCount(conn)
            tvRevItemReports.text = if (conn.requiredReportsCount > 0) {
                val remaining = (conn.requiredReportsCount - submitted).coerceAtLeast(0)
                "Reports: $submitted / ${conn.requiredReportsCount} · Remaining: $remaining"
            } else {
                "Reports: $submitted"
            }

            val card = root as com.google.android.material.card.MaterialCardView
            when {
                conn.isEndedByCompany() -> {
                    tvRevItemStatus.visibility = View.VISIBLE
                    tvRevItemStatus.text = if (conn.endReason.isNotBlank())
                        "Ended by Company · ${conn.endReason}" else "Ended by Company"
                    tvRevItemStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    card.strokeColor = android.graphics.Color.parseColor("#FECACA")
                    btnRevItemReview.text = "View Ended Internship"
                    btnRevItemReview.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DC2626"))
                }
                conn.isCompleted() -> {
                    tvRevItemStatus.visibility = View.VISIBLE
                    val done = conn.completedAt?.toDate()?.let {
                        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(it)
                    }
                    tvRevItemStatus.text = if (done != null) "Completed · $done" else "Completed"
                    tvRevItemStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                    card.strokeColor = android.graphics.Color.parseColor("#BBF7D0")
                    btnRevItemReview.text = "View Completed Internship"
                    btnRevItemReview.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#16A34A"))
                }
                else -> {
                    tvRevItemStatus.visibility = View.GONE
                    card.strokeColor = android.graphics.Color.parseColor("#E2E8F0")
                    btnRevItemReview.text = "Review Internship"
                    btnRevItemReview.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#005FAF"))
                }
            }

            btnRevItemReview.setOnClickListener { onReview(conn) }
            root.setOnClickListener { onReview(conn) }
        }
    }

    override fun getItemCount() = items.size
}
