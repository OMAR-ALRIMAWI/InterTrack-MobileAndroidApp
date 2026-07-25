package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.data.cache.AppSessionCache
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.UniversityUtil
import com.example.intertrack.databinding.FragmentEditInstructorProfileBinding
import com.google.firebase.auth.FirebaseAuth

class EditInstructorProfileFragment : Fragment() {

    private var _binding: FragmentEditInstructorProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditInstructorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentProfile()

        // University is chosen from the allowed list (no free text).
        binding.etInstEditUniversity.apply {
            isFocusable = false
            isClickable = true
            keyListener = null
            setOnClickListener { showUniversityPicker() }
        }

        binding.btnChangeAvatar.setOnClickListener {
            Toast.makeText(requireContext(), "Photo upload — coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnInstSaveProfile.setOnClickListener { saveProfile() }
    }

    private fun showUniversityPicker() {
        val options = UniversityUtil.displayNames()
        val current = binding.etInstEditUniversity.text?.toString()?.trim()
        val checked = options.indexOfFirst { it.equals(current, ignoreCase = true) }
        AlertDialog.Builder(requireContext())
            .setTitle("Select University")
            .setSingleChoiceItems(options.toTypedArray(), checked) { dialog, which ->
                binding.etInstEditUniversity.setText(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCurrentProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                binding.editInstProfInitials.text = user.initials()
                binding.etInstEditName.setText(user.fullName)
                binding.etInstEditDept.setText(user.department ?: "")
                // Prefer the stored display name; fall back to the key's display name for older docs.
                binding.etInstEditUniversity.setText(
                    user.university?.takeIf { it.isNotBlank() }
                        ?: UniversityUtil.displayNameForKey(user.universityKey)
                )
                binding.etInstEditOffice.setText(user.office ?: "")
                binding.etInstEditBio.setText(user.bio ?: "")
            },
            onFailure = { /* fail silently */ }
        )
    }

    private fun saveProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val name = binding.etInstEditName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.etInstEditName.error = "Name cannot be empty"
            return
        }

        binding.btnInstSaveProfile.isEnabled = false
        binding.btnInstSaveProfile.text = "Saving…"

        val university = binding.etInstEditUniversity.text?.toString()?.trim() ?: ""
        val updates = mapOf<String, Any?>(
            "fullName" to name,
            "department" to (binding.etInstEditDept.text?.toString()?.trim() ?: ""),
            "university" to university,
            "universityKey" to UniversityUtil.keyForDisplayName(university),
            "office" to (binding.etInstEditOffice.text?.toString()?.trim() ?: ""),
            "bio" to (binding.etInstEditBio.text?.toString()?.trim() ?: "")
        )

        authRepo.updateUserProfile(
            uid = uid,
            updates = updates,
            onSuccess = {
                if (_binding == null) return@updateUserProfile
                // Patch cache so profile page shows updated data instantly on back-navigation
                AppSessionCache.currentUser = AppSessionCache.currentUser?.copy(
                    fullName = updates["fullName"] as? String ?: AppSessionCache.currentUser?.fullName ?: "",
                    department = updates["department"] as? String,
                    university = updates["university"] as? String,
                    universityKey = updates["universityKey"] as? String,
                    office = updates["office"] as? String,
                    bio = updates["bio"] as? String
                )
                binding.btnInstSaveProfile.isEnabled = true
                binding.btnInstSaveProfile.text = "Save Changes"
                Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            },
            onFailure = { msg ->
                if (_binding == null) return@updateUserProfile
                binding.btnInstSaveProfile.isEnabled = true
                binding.btnInstSaveProfile.text = "Save Changes"
                Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? InstructorDashBoard)?.apply {
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
