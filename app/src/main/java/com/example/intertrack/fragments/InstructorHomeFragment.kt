package com.example.intertrack.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreInternshipConnection
import com.example.intertrack.databinding.FragmentInstructorHomeBinding
import com.google.firebase.auth.FirebaseAuth

class InstructorHomeFragment : Fragment() {

    private var _binding: FragmentInstructorHomeBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructorHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvInstStudents.layoutManager = LinearLayoutManager(requireContext())
        loadDashboard()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "InstructorHomeFragment -> Loading")
        binding.progressInstHomeMain.visibility = View.VISIBLE
        binding.instHomeContent.visibility = View.GONE
        binding.tvInstHomeError.visibility = View.GONE

        // Clear counts
        binding.tvActiveCount.text = "0"
        binding.tvPendingCount.text = "0"
        binding.tvCompletedCount.text = "0"
        binding.tvRequestsCount.text = "0"
        binding.rvInstStudents.adapter = null
        binding.tvInstStudentsEmpty.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "InstructorHomeFragment -> Content")
        binding.progressInstHomeMain.visibility = View.GONE
        binding.instHomeContent.visibility = View.VISIBLE
        binding.tvInstHomeError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "InstructorHomeFragment -> Empty")
        showContentState()
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "InstructorHomeFragment -> Error: $message")
        binding.progressInstHomeMain.visibility = View.GONE
        binding.instHomeContent.visibility = View.GONE
        binding.tvInstHomeError.visibility = View.VISIBLE
        binding.tvInstHomeError.text = message
    }

    private fun loadDashboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        showLoadingState()

        authRepo.getInstructorActiveConnections(
            instructorUid = uid,
            onSuccess = { connections ->
                if (_binding == null) return@getInstructorActiveConnections
                binding.tvActiveCount.text = connections.size.toString()
                
                authRepo.getInstructorReports(
                    instructorUid = uid,
                    onSuccess = { reports ->
                        if (_binding == null) return@getInstructorReports
                        binding.tvPendingCount.text = reports.count { it.status == "SUBMITTED" }.toString()
                        binding.tvCompletedCount.text = reports.count { it.status == "REVIEWED" }.toString()
                        
                        val countByConn = reports.groupingBy {
                            it.internshipConnectionId.ifBlank { it.studentUid }
                        }.eachCount()
                        
                        renderStudents(connections) { conn ->
                            countByConn[conn.connectionId] ?: countByConn[conn.studentUid] ?: 0
                        }
                        showContentState()
                    },
                    onFailure = { 
                        if (_binding != null) {
                            renderStudents(connections) { 0 }
                            showContentState()
                        }
                    }
                )
            },
            onFailure = { message ->
                if (_binding == null) return@getInstructorActiveConnections
                showErrorState(message)
            }
        )

        authRepo.getInstructorRequests(
            instructorUid = uid,
            onSuccess = { requests ->
                if (_binding == null) return@getInstructorRequests
                binding.tvRequestsCount.text = requests.count { it.status == "PENDING" }.toString()
            },
            onFailure = {}
        )
    }

    private fun renderStudents(
        connections: List<FirestoreInternshipConnection>,
        reportCount: (FirestoreInternshipConnection) -> Int
    ) {
        if (_binding == null) return
        if (connections.isEmpty()) {
            binding.tvInstStudentsEmpty.visibility = View.VISIBLE
            binding.rvInstStudents.visibility = View.GONE
        } else {
            binding.tvInstStudentsEmpty.visibility = View.GONE
            binding.rvInstStudents.visibility = View.VISIBLE
            binding.rvInstStudents.adapter = InternshipReviewAdapter(connections, reportCount) { conn ->
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
            val name = AppSessionCache.currentUser?.fullName?.split(" ")?.firstOrNull() ?: "Instructor"
            updateHeader("Dashboard", "Hello, $name")
        }
        loadDashboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
