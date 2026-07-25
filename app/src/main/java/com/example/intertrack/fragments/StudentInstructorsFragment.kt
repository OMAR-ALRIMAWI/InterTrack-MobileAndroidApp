package com.example.intertrack.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentStudentInstructorsBinding
import com.google.firebase.auth.FirebaseAuth

class StudentInstructorsFragment : Fragment() {

    private var _binding: FragmentStudentInstructorsBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentInstructorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? StudentDashBoard)?.setHeaderVisible(false)

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.rvInstructors.layoutManager = LinearLayoutManager(requireContext())

        loadInstructors()
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "StudentInstructorsFragment -> Loading")
        binding.progressInstructors.visibility = View.VISIBLE
        binding.rvInstructors.visibility = View.GONE
        binding.tvInstructorsEmpty.visibility = View.GONE
        binding.tvInstructorsError.visibility = View.GONE
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "StudentInstructorsFragment -> Content")
        binding.progressInstructors.visibility = View.GONE
        binding.rvInstructors.visibility = View.VISIBLE
        binding.tvInstructorsEmpty.visibility = View.GONE
        binding.tvInstructorsError.visibility = View.GONE
    }

    private fun showEmptyState() {
        Log.d("INTERTRACK_STATE", "StudentInstructorsFragment -> Empty")
        binding.progressInstructors.visibility = View.GONE
        binding.rvInstructors.visibility = View.GONE
        binding.tvInstructorsEmpty.visibility = View.VISIBLE
        binding.tvInstructorsError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "StudentInstructorsFragment -> Error: $message")
        binding.progressInstructors.visibility = View.GONE
        binding.rvInstructors.visibility = View.GONE
        binding.tvInstructorsEmpty.visibility = View.GONE
        binding.tvInstructorsError.visibility = View.VISIBLE
        binding.tvInstructorsError.text = message
    }

    private fun loadInstructors() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        showLoadingState()

        authRepo.getStudentInstructorRequests(
            studentUid = uid,
            onSuccess = { requests ->
                if (_binding == null) return@getStudentInstructorRequests
                val accepted = requests.filter { it.status == "ACCEPTED" }
                if (accepted.isEmpty()) {
                    showEmptyState()
                } else {
                    val instructors = accepted.map { req ->
                        Instructor(
                            id = req.instructorUid,
                            initials = req.instructorName.trim().split(" ")
                                .filter { it.isNotEmpty() }
                                .let { parts ->
                                    when {
                                        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                                        parts.size == 1 -> parts[0].take(2).uppercase()
                                        else -> "?"
                                    }
                                },
                            name = req.instructorName.ifBlank { "Instructor" },
                            department = "",
                            university = "",
                            email = req.instructorEmail,
                            office = "",
                            bio = ""
                        )
                    }
                    val adapter = InstructorAdapter(instructors) { instructor ->
                        val fragment = ShowInstructorAccountFragment.newInstance(instructor)
                        requireActivity().supportFragmentManager.beginTransaction()
                            .replace(com.example.intertrack.R.id.fragmentContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    binding.rvInstructors.adapter = adapter
                    showContentState()
                }
            },
            onFailure = { msg ->
                if (_binding == null) return@getStudentInstructorRequests
                showErrorState(msg ?: "Could not load instructors.")
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(false)
            setNavVisible(false)
        }
        loadInstructors()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
