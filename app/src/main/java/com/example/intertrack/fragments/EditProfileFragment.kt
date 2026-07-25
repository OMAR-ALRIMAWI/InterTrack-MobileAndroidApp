package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentEditProfileBinding
import com.google.firebase.auth.FirebaseAuth

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentProfile()

        binding.btnSaveProfile.setOnClickListener { saveProfile() }

        binding.btnCancelEdit.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.editProfileAvatarHolder.setOnClickListener {
            Toast.makeText(requireContext(), "Photo picker coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCurrentProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                binding.etEditFullName.setText(user.fullName)
                binding.etEditEmail.setText(user.email)
                binding.etEditUniversity.setText(user.university ?: "")
                binding.etEditDepartment.setText(user.major ?: "")
                binding.etEditStudentId.setText("")
                binding.etEditBio.setText(user.bio ?: "")
            },
            onFailure = { /* fail silently */ }
        )
    }

    private fun saveProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val name = binding.etEditFullName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.etEditFullName.error = "Name cannot be empty"
            return
        }

        binding.btnSaveProfile.isEnabled = false
        binding.btnSaveProfile.text = "Saving…"

        val updates = mapOf<String, Any?>(
            "fullName" to name,
            "university" to (binding.etEditUniversity.text?.toString()?.trim() ?: ""),
            "major" to (binding.etEditDepartment.text?.toString()?.trim() ?: ""),
            "bio" to (binding.etEditBio.text?.toString()?.trim() ?: "")
        )

        authRepo.updateUserProfile(
            uid = uid,
            updates = updates,
            onSuccess = {
                if (_binding == null) return@updateUserProfile
                // Patch cache so profile page shows updated data instantly on back-navigation
                AppSessionCache.currentUser = AppSessionCache.currentUser?.copy(
                    fullName = updates["fullName"] as? String ?: AppSessionCache.currentUser?.fullName ?: "",
                    university = updates["university"] as? String,
                    major = updates["major"] as? String,
                    bio = updates["bio"] as? String
                )
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = "Save Changes"
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            },
            onFailure = { msg ->
                if (_binding == null) return@updateUserProfile
                binding.btnSaveProfile.isEnabled = true
                binding.btnSaveProfile.text = "Save Changes"
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Edit Profile")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
