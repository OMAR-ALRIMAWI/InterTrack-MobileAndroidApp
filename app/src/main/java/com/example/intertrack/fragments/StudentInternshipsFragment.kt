package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.R
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentStudentInternshipsBinding
import com.google.firebase.auth.FirebaseAuth

class StudentInternshipsFragment : Fragment() {

    private var _binding: FragmentStudentInternshipsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStudentInternshipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.chipGroupInternships.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipExplore -> swapContent(ExploreInternshipsFragment())
                R.id.chipMyInternship -> swapContent(MyInternshipFragment())
            }
        }

        if (savedInstanceState == null) {
            // Decide the correct default tab BEFORE showing any child fragment. Previously Explore
            // was shown immediately and an async connection check then swapped to My Internship,
            // which briefly flashed the wrong screen. Now nothing is shown until the check returns.
            resolveDefaultTab()
        }
    }

    private fun resolveDefaultTab() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run { showExplore(); return }
        authRepo.getStudentActiveConnection(
            studentUid = uid,
            onSuccess = { conn ->
                if (_binding == null || !isAdded) return@getStudentActiveConnection
                if (conn != null) showMyInternship() else showExplore()
            },
            onFailure = {
                if (_binding == null || !isAdded) return@getStudentActiveConnection
                showExplore()
            }
        )
    }

    private fun showExplore() {
        // If the chip is already checked the listener won't fire, so swap explicitly.
        if (binding.chipExplore.isChecked) swapContent(ExploreInternshipsFragment())
        else binding.chipExplore.isChecked = true
    }

    private fun showMyInternship() {
        if (binding.chipMyInternship.isChecked) swapContent(MyInternshipFragment())
        else binding.chipMyInternship.isChecked = true
    }

    private fun swapContent(fragment: Fragment) {
        if (!isAdded || _binding == null) return
        childFragmentManager.beginTransaction()
            .replace(binding.internshipsContentFrame.id, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Internships")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
