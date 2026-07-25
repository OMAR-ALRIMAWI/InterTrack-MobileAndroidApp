package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.isValidActive
import com.example.intertrack.databinding.FragmentReportsStudentBinding
import com.google.firebase.auth.FirebaseAuth

class ReportsStudent : Fragment() {

    private var _binding: FragmentReportsStudentBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var connectionStatus: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvStudentReports.layoutManager = LinearLayoutManager(requireContext())

        binding.swipeRefreshReports.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshReports.setOnRefreshListener { loadData() }

        binding.btnSubmitNewReport.setOnClickListener {
            if (connectionStatus == "ACTIVE") {
                (requireActivity() as? StudentDashBoard)?.openDetail(CreateReportFragment())
            } else {
                Toast.makeText(requireContext(),
                    "Reports are available once your internship becomes active.",
                    Toast.LENGTH_SHORT).show()
            }
        }

        loadData()
    }

    private fun loadData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        authRepo.getStudentActiveConnection(
            studentUid = uid,
            onSuccess = { conn ->
                if (_binding == null) return@getStudentActiveConnection
                // Only a validly-confirmed ACTIVE connection unlocks reports; an invalid
                // legacy ACTIVE is treated as waiting until the instructor re-confirms.
                connectionStatus = if (conn?.isValidActive() == true) "ACTIVE" else conn?.status
                val isActive = connectionStatus == "ACTIVE"
                binding.btnSubmitNewReport.alpha = if (isActive) 1f else 0.45f
            },
            onFailure = {}
        )

        binding.progressReports.visibility = View.VISIBLE
        binding.tvReportsError.visibility = View.GONE
        binding.tvReportsEmpty.visibility = View.GONE
        binding.rvStudentReports.visibility = View.GONE

        // Repair old reports missing routing fields, then load. The repair is idempotent and only
        // fills blanks, so once done it is a no-op on later opens.
        authRepo.backfillStudentReports(uid) {
            if (_binding != null) loadReportsList(uid)
        }
    }

    private fun loadReportsList(uid: String) {
        authRepo.getStudentReports(
            studentUid = uid,
            onSuccess = { reports ->
                if (_binding == null) return@getStudentReports
                binding.progressReports.visibility = View.GONE
                binding.swipeRefreshReports.isRefreshing = false

                if (reports.isEmpty()) {
                    binding.tvReportsEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvStudentReports.visibility = View.VISIBLE
                    binding.rvStudentReports.adapter = ReportAdapter(reports) { report ->
                        (requireActivity() as? StudentDashBoard)?.openDetail(
                            ReportDetailFragment.newInstance(report.reportId)
                        )
                    }
                }
            },
            onFailure = { _ ->
                if (_binding == null) return@getStudentReports
                binding.progressReports.visibility = View.GONE
                binding.swipeRefreshReports.isRefreshing = false
                binding.tvReportsError.visibility = View.VISIBLE
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("My Reports")
        }
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
