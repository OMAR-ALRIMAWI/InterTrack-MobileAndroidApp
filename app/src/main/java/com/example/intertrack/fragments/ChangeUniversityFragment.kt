package com.example.intertrack.fragments

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreUniversityChangeRequest
import com.example.intertrack.data.model.UniversityUtil
import com.example.intertrack.data.model.User
import com.example.intertrack.databinding.FragmentChangeUniversityBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Lets a Student/Instructor submit a request to change their verified university. The user's own
 * university is NEVER changed here — only a request document is created (admin approval applies it).
 * Attachment is metadata-only (no Firebase Storage).
 */
class ChangeUniversityFragment : Fragment() {

    private var _binding: FragmentChangeUniversityBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    private var currentUser: User? = null
    private var proofFileName: String? = null
    private var proofMimeType: String = ""
    private var submitting = false

    private val proofPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && _binding != null) {
            proofFileName = getFileName(uri) ?: "document_selected"
            proofMimeType = requireContext().contentResolver.getType(uri) ?: ""
            binding.tvChangeUniFileName.text = proofFileName
            binding.tvChangeUniFileNote.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeUniversityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideHostChrome()

        binding.btnChangeUniBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.etChangeUniRequested.setOnClickListener { showUniversityPicker() }
        binding.btnChangeUniAttach.setOnClickListener { proofPicker.launch("*/*") }
        binding.btnChangeUniSubmit.setOnClickListener { submit() }

        load()
    }

    private fun load() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) { showError("Not signed in."); return }

        binding.progressChangeUni.visibility = View.VISIBLE
        binding.layoutChangeUniForm.visibility = View.GONE
        binding.cardChangeUniPending.visibility = View.GONE
        binding.tvChangeUniError.visibility = View.GONE

        authRepo.fetchUserDocument(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@fetchUserDocument
                currentUser = user
                // Block duplicate requests: if one is already pending, show that instead of the form.
                authRepo.getPendingUniversityChangeRequest(
                    userUid = uid,
                    onSuccess = { pending ->
                        if (_binding == null) return@getPendingUniversityChangeRequest
                        binding.progressChangeUni.visibility = View.GONE
                        if (pending != null) {
                            binding.cardChangeUniPending.visibility = View.VISIBLE
                            binding.tvChangeUniPendingRequested.text =
                                "Requested university: ${pending.requestedUniversity}"
                        } else {
                            showForm(user)
                        }
                    },
                    onFailure = {
                        if (_binding == null) return@getPendingUniversityChangeRequest
                        binding.progressChangeUni.visibility = View.GONE
                        showForm(user) // best-effort: allow the form; server rules still apply
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@fetchUserDocument
                showError("Could not load your profile. Please try again.")
            }
        )
    }

    private fun showForm(user: User) {
        binding.layoutChangeUniForm.visibility = View.VISIBLE
        binding.tvChangeUniCurrent.text = currentUniversityDisplay(user)
    }

    private fun showError(message: String) {
        if (_binding == null) return
        binding.progressChangeUni.visibility = View.GONE
        binding.layoutChangeUniForm.visibility = View.GONE
        binding.cardChangeUniPending.visibility = View.GONE
        binding.tvChangeUniError.text = message
        binding.tvChangeUniError.visibility = View.VISIBLE
    }

    private fun currentUniversityDisplay(user: User): String =
        user.university?.takeIf { it.isNotBlank() }
            ?: UniversityUtil.displayNameForKey(user.universityKey).takeIf { it.isNotBlank() }
            ?: "Not set"

    private fun showUniversityPicker() {
        val options = UniversityUtil.displayNames()
        val current = binding.etChangeUniRequested.text?.toString()?.trim()
        val checked = options.indexOfFirst { it.equals(current, ignoreCase = true) }
        AlertDialog.Builder(requireContext())
            .setTitle("Requested University")
            .setSingleChoiceItems(options.toTypedArray(), checked) { dialog, which ->
                binding.etChangeUniRequested.setText(options[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submit() {
        if (submitting) return
        val user = currentUser ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val requested = binding.etChangeUniRequested.text?.toString()?.trim() ?: ""
        val reason = binding.etChangeUniReason.text?.toString()?.trim() ?: ""
        val requestedKey = UniversityUtil.keyForDisplayName(requested)
        val currentKey = user.universityKey?.takeIf { it.isNotBlank() }
            ?: UniversityUtil.normalizeUniversityName(user.university)

        if (requested.isEmpty()) { toast("Please select the requested university."); return }
        if (requestedKey == currentKey && currentKey.isNotBlank()) {
            toast("That is already your current university."); return
        }
        if (reason.isEmpty()) {
            binding.etChangeUniReason.error = "Reason is required"; toast("Please enter a reason."); return
        }
        if (proofFileName.isNullOrBlank()) { toast("Please attach a verification document."); return }

        submitting = true
        binding.btnChangeUniSubmit.isEnabled = false
        binding.btnChangeUniSubmit.text = "Submitting…"

        val request = FirestoreUniversityChangeRequest(
            userUid = uid,
            userRole = user.role,
            userName = user.fullName,
            userEmail = user.email,
            currentUniversity = user.university ?: UniversityUtil.displayNameForKey(user.universityKey),
            currentUniversityKey = currentKey,
            requestedUniversity = requested,
            requestedUniversityKey = requestedKey,
            reason = reason,
            proofFileName = proofFileName ?: "",
            proofMimeType = proofMimeType,
            proofUrl = null,
            status = "PENDING"
        )

        authRepo.createUniversityChangeRequest(
            request = request,
            onSuccess = {
                if (_binding == null) return@createUniversityChangeRequest
                Toast.makeText(requireContext(),
                    "Request submitted. An admin will review it.", Toast.LENGTH_LONG).show()
                // Switch to the pending state.
                binding.layoutChangeUniForm.visibility = View.GONE
                binding.cardChangeUniPending.visibility = View.VISIBLE
                binding.tvChangeUniPendingRequested.text = "Requested university: $requested"
            },
            onFailure = { msg ->
                if (_binding == null) return@createUniversityChangeRequest
                submitting = false
                binding.btnChangeUniSubmit.isEnabled = true
                binding.btnChangeUniSubmit.text = "Submit Request"
                Toast.makeText(requireContext(), "Could not submit: $msg", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()

    private fun getFileName(uri: Uri): String? = try {
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    } catch (_: Exception) { null }

    private fun hideHostChrome() {
        (requireActivity() as? StudentDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? InstructorDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
    }

    override fun onResume() {
        super.onResume()
        hideHostChrome()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Chrome is restored by the Profile fragment's onResume when navigating back.
        _binding = null
    }
}
