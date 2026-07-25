package com.example.intertrack.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreReport
import com.example.intertrack.databinding.FragmentInternshipReportsBinding
import com.google.firebase.auth.FirebaseAuth

class InternshipReportsFragment : Fragment() {

    private var _binding: FragmentInternshipReportsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    companion object {
        private const val A_CONNECTION_ID = "connectionId"
        private const val A_STUDENT_UID = "studentUid"
        private const val A_TITLE = "title"
        private const val A_ROLE = "role"

        fun newInstance(connectionId: String, studentUid: String, internshipTitle: String, role: String) =
            InternshipReportsFragment().apply {
                arguments = Bundle().apply {
                    putString(A_CONNECTION_ID, connectionId)
                    putString(A_STUDENT_UID, studentUid)
                    putString(A_TITLE, internshipTitle)
                    putString(A_ROLE, role)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInternshipReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideHostUI()
        binding.rvInternReports.layoutManager = LinearLayoutManager(requireContext())
        binding.tvInternReportsTitle.text = arguments?.getString(A_TITLE).orEmpty().ifBlank { "Internship Reports" }
        binding.btnInternReportsBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        loadReports()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "InternshipReportsFragment -> Loading")
        binding.progressInternReports.visibility = View.VISIBLE
        binding.rvInternReports.visibility = View.GONE
        binding.tvInternReportsEmpty.visibility = View.GONE
        binding.tvInternReportsError.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "InternshipReportsFragment -> Content")
        binding.progressInternReports.visibility = View.GONE
        binding.rvInternReports.visibility = View.VISIBLE
        binding.tvInternReportsEmpty.visibility = View.GONE
        binding.tvInternReportsError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "InternshipReportsFragment -> Empty")
        binding.progressInternReports.visibility = View.GONE
        binding.rvInternReports.visibility = View.GONE
        binding.tvInternReportsEmpty.visibility = View.VISIBLE
        binding.tvInternReportsError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "InternshipReportsFragment -> Error: $message")
        binding.progressInternReports.visibility = View.GONE
        binding.rvInternReports.visibility = View.GONE
        binding.tvInternReportsEmpty.visibility = View.GONE
        binding.tvInternReportsError.visibility = View.VISIBLE
        binding.tvInternReportsError.text = message
    }

    private fun loadReports() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val role = arguments?.getString(A_ROLE) ?: "STUDENT"
        val connectionId = arguments?.getString(A_CONNECTION_ID).orEmpty()
        val studentUid = arguments?.getString(A_STUDENT_UID).orEmpty()

        showLoadingState()

        val onLoaded: (List<FirestoreReport>) -> Unit = { all ->
            if (_binding != null) {
                val scoped = all.filter {
                    (connectionId.isNotBlank() && it.internshipConnectionId == connectionId) ||
                        (studentUid.isNotBlank() && it.studentUid == studentUid)
                }
                
                if (scoped.isEmpty()) {
                    showEmptyState()
                } else {
                    render(scoped, role)
                    showContentState()
                }
            }
        }
        val onErr: (String) -> Unit = { msg ->
            if (_binding != null) {
                showErrorState(msg ?: "Could not load reports.")
            }
        }

        when (role.uppercase()) {
            "INSTRUCTOR" -> authRepo.getInstructorReports(uid, onLoaded, onErr)
            "COMPANY" -> {
                val companyId = AppSessionCache.currentCompany?.companyId ?: ""
                authRepo.getCompanyReports(companyId, onLoaded, onErr)
            }
            else -> authRepo.getStudentReports(uid, onLoaded, onErr)
        }
    }

    private fun render(reports: List<FirestoreReport>, role: String) {
        if (_binding == null) return
        binding.rvInternReports.adapter = ReportAdapter(reports, showStudentName = true) { report ->
            openDetail(ReportDetailFragment.newInstance(report.reportId))
        }
    }

    private fun openDetail(fragment: Fragment) {
        when (val act = requireActivity()) {
            is InstructorDashBoard -> act.openDetail(fragment)
            is CompanyDashBoard -> act.openDetail(fragment)
            is StudentDashBoard -> act.openDetail(fragment)
        }
    }

    private fun hideHostUI() {
        (requireActivity() as? StudentDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? InstructorDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? CompanyDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
    }

    override fun onResume() {
        super.onResume()
        hideHostUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
