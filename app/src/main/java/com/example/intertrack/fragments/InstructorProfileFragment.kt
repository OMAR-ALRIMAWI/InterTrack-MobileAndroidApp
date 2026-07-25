package com.example.intertrack.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.LoginActivity
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.User
import com.example.intertrack.databinding.FragmentInstructorProfileBinding
import com.google.firebase.auth.FirebaseAuth

class InstructorProfileFragment : Fragment() {

    private var _binding: FragmentInstructorProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfile()

        binding.btnInstEditProfile.setOnClickListener {
            (requireActivity() as? InstructorDashBoard)?.openDetail(EditInstructorProfileFragment())
        }

        binding.btnInstChangeUniversity.setOnClickListener {
            (requireActivity() as? InstructorDashBoard)?.openDetail(ChangeUniversityFragment())
        }

        binding.btnInstSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Settings — coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnInstLogout.setOnClickListener {
            AppSessionCache.clear()
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun showLoadingState() {
        Log.d("INTERTRACK_STATE", "InstructorProfileFragment -> Loading")
        binding.progressInstProfileMain.visibility = View.VISIBLE
        binding.instProfileContent.visibility = View.GONE
        binding.tvInstProfileError.visibility = View.GONE

        binding.instProfName.text = ""
        binding.instProfDept.text = ""
        binding.instProfUniversity.text = ""
        binding.instProfEmail.text = ""
        binding.instProfOffice.text = ""
        binding.instProfBio.text = ""
    }

    private fun showContentState() {
        Log.d("INTERTRACK_STATE", "InstructorProfileFragment -> Content")
        binding.progressInstProfileMain.visibility = View.GONE
        binding.instProfileContent.visibility = View.VISIBLE
        binding.tvInstProfileError.visibility = View.GONE
    }

    private fun showErrorState(message: String) {
        Log.d("INTERTRACK_STATE", "InstructorProfileFragment -> Error: $message")
        binding.progressInstProfileMain.visibility = View.GONE
        binding.instProfileContent.visibility = View.GONE
        binding.tvInstProfileError.visibility = View.VISIBLE
        binding.tvInstProfileError.text = message
    }

    private fun bindProfile(user: User) {
        binding.instProfInitials.text = user.initials()
        binding.instProfName.text = user.fullName.ifBlank { "Instructor" }
        binding.instProfDept.text = user.department?.takeIf { it.isNotBlank() } ?: "Department not set"
        binding.instProfUniversity.text = user.university?.takeIf { it.isNotBlank() }
            ?: com.example.intertrack.data.model.UniversityUtil.displayNameForKey(user.universityKey)
                .takeIf { it.isNotBlank() }
            ?: "University not set"
        binding.instProfEmail.text = user.email
        binding.instProfOffice.text = if (!user.office.isNullOrBlank()) "Office: ${user.office}" else "Office not set"
        binding.instProfBio.text = user.bio?.takeIf { it.isNotBlank() } ?: "No bio provided."
    }

    private fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        showLoadingState()

        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                AppSessionCache.currentUser = user
                bindProfile(user)
                showContentState()
            },
            onFailure = { message ->
                if (_binding == null) return@fetchUserDocument
                showErrorState(message)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? InstructorDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Profile")
        }
        loadProfile()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
