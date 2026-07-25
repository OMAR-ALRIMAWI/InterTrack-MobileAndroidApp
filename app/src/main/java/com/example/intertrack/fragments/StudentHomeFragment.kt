package com.example.intertrack.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentStudentHomeBinding
import com.google.firebase.auth.FirebaseAuth

class StudentHomeFragment : Fragment() {

    private var _binding: FragmentStudentHomeBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDashboard()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "StudentHomeFragment -> Loading")
        binding.progressStudentHome.visibility = View.VISIBLE
        binding.studentHomeContent.visibility = View.GONE
        binding.tvStudentHomeError.visibility = View.GONE

        // Clear visible text fields
        binding.tvCurrentInternshipTitle.text = ""
        binding.tvCurrentCompanyName.text = ""
        binding.tvStudentInstructorName.text = ""
        binding.tvProgressReportsSubmitted.text = "0"
        binding.tvProgressReportsReviewed.text = "0"
        binding.tvStudentInternshipBadge.visibility = View.GONE
        binding.internshipInfoLayout.visibility = View.GONE
        binding.tvNoActiveInternship.visibility = View.GONE
        binding.feedbackContentLayout.visibility = View.GONE
        binding.tvNoFeedback.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "StudentHomeFragment -> Content")
        binding.progressStudentHome.visibility = View.GONE
        binding.studentHomeContent.visibility = View.VISIBLE
        binding.tvStudentHomeError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "StudentHomeFragment -> Empty")
        // For StudentHome, "Empty" is handled within content (e.g. "No active internship")
        showContentState()
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "StudentHomeFragment -> Error: $message")
        binding.progressStudentHome.visibility = View.GONE
        binding.studentHomeContent.visibility = View.GONE
        binding.tvStudentHomeError.visibility = View.VISIBLE
        binding.tvStudentHomeError.text = message
    }

    private fun loadDashboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val cachedUser = AppSessionCache.currentUser

        showLoadingState()

        val displayName = cachedUser?.fullName?.let { "Hello, ${it.split(" ").first()} 👋" }
            ?: "Hello 👋"

        (requireActivity() as? StudentDashBoard)?.updateHeader("Home", displayName)

        // Show university + major from cache — only when the cache is for THIS signed-in user.
        if (cachedUser != null && cachedUser.uid == uid) {
            binding.tvStudentUniversity.text = cachedUser.university?.ifBlank { "Not set" } ?: "Not set"
            binding.tvStudentMajor.text = cachedUser.major?.ifBlank { "Not set" } ?: "Not set"
        }

        // Load applications to find accepted internship
        authRepo.getStudentApplications(
            studentUid = uid,
            onSuccess = { applications ->
                if (_binding == null) return@getStudentApplications
                val accepted = applications.firstOrNull { it.status == "ACCEPTED" }

                if (accepted != null) {
                    binding.tvCurrentInternshipTitle.text = accepted.offerTitle.ifBlank { "Active Internship" }
                    binding.tvCurrentCompanyName.text = accepted.companyName.ifBlank { "—" }
                    binding.tvStudentInternshipBadge.visibility = View.VISIBLE
                    binding.tvNoActiveInternship.visibility = View.GONE
                    binding.internshipInfoLayout.visibility = View.VISIBLE
                } else {
                    binding.tvStudentInternshipBadge.visibility = View.GONE
                    binding.tvNoActiveInternship.visibility = View.VISIBLE
                    binding.internshipInfoLayout.visibility = View.GONE
                }

                // Instructor from user doc
                val instructorName = cachedUser?.assignedInstructorName
                binding.tvStudentInstructorName.text =
                    if (!instructorName.isNullOrBlank()) instructorName else "Not assigned yet"

                // Progress tracker
                val hasAccepted = accepted != null
                val hasInstructor = !cachedUser?.assignedInstructorUid.isNullOrBlank()

                loadReportsAndProgress(uid, hasAccepted, hasInstructor, applications.size)
            },
            onFailure = { message ->
                if (_binding == null) return@getStudentApplications
                showErrorState(message)
            }
        )
    }

    private fun loadReportsAndProgress(
        uid: String,
        hasAccepted: Boolean,
        hasInstructor: Boolean,
        applicationCount: Int
    ) {
        authRepo.getStudentReports(
            studentUid = uid,
            onSuccess = { reports ->
                if (_binding == null) return@getStudentReports

                val totalReports = reports.size
                val reviewedReports = reports.count { it.status == "REVIEWED" }

                binding.tvProgressReportsSubmitted.text = totalReports.toString()
                binding.tvProgressReportsReviewed.text = reviewedReports.toString()

                // 4-step progress: Applied → Accepted → Instructor → Report Submitted
                val step = when {
                    totalReports > 0 -> 4
                    hasInstructor -> 3
                    hasAccepted -> 2
                    applicationCount > 0 -> 1
                    else -> 0
                }
                updateProgressSteps(step)

                // Recent feedback from latest reviewed report
                val latestReviewed = reports
                    .filter { it.status == "REVIEWED" && (it.instructorFeedback.isNotBlank() || it.supervisorFeedback.isNotBlank()) }
                    .maxByOrNull { it.reviewedAt?.seconds ?: 0L }

                if (latestReviewed != null) {
                    val feedback = latestReviewed.instructorFeedback.ifBlank { latestReviewed.supervisorFeedback }
                    val source = if (latestReviewed.instructorFeedback.isNotBlank()) "Instructor" else "Supervisor"
                    binding.tvFeedbackContent.text = feedback
                    binding.tvFeedbackSource.text = "— $source on \"${latestReviewed.reportTitle}\""
                    binding.feedbackContentLayout.visibility = View.VISIBLE
                    binding.tvNoFeedback.visibility = View.GONE
                } else {
                    binding.feedbackContentLayout.visibility = View.GONE
                    binding.tvNoFeedback.visibility = View.VISIBLE
                }
                
                showContentState()
            },
            onFailure = { message ->
                if (_binding == null) return@getStudentReports
                showErrorState(message)
            }
        )
    }

    private fun updateProgressSteps(completedStep: Int) {
        val steps = listOf(
            binding.stepApplied,
            binding.stepAccepted,
            binding.stepInstructor,
            binding.stepReportSubmitted
        )
        steps.forEachIndexed { idx, view ->
            val stepNum = idx + 1
            view.alpha = if (stepNum <= completedStep) 1.0f else 0.35f
        }
    }

    override fun onResume() {
        super.onResume()
        val cachedUser = AppSessionCache.currentUser
        val displayName = cachedUser?.fullName?.let { "Hello, ${it.split(" ").first()} 👋" } ?: "Hello 👋"
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Home", displayName)
        }
        loadDashboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
