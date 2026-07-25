package com.example.intertrack.fragments.admin

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.AccountStatus
import com.example.intertrack.data.model.User
import com.example.intertrack.data.model.UserRole
import com.example.intertrack.databinding.FragmentAdminAccountDetailSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth

class AdminAccountDetailSheet : BottomSheetDialogFragment() {

    interface OnActionListener {
        fun onAccountActionPerformed()
    }

    companion object {
        private const val ARG_USER = "user"

        fun newInstance(user: User): AdminAccountDetailSheet =
            AdminAccountDetailSheet().apply {
                arguments = Bundle().apply { putParcelable(ARG_USER, user) }
            }
    }

    private var _binding: FragmentAdminAccountDetailSheetBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()
    private lateinit var user: User
    private var actionInProgress = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminAccountDetailSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_USER, User::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_USER)
        } ?: run { dismiss(); return }

        binding.tvSheetName.text = user.fullName
        binding.tvSheetEmail.text = user.email
        binding.tvSheetRole.text = when (user.role) {
            UserRole.STUDENT.value           -> "Student"
            UserRole.INSTRUCTOR.value        -> "Academic Supervisor"
            UserRole.COMPANY_SUPERVISOR.value -> "Company Supervisor"
            else                              -> user.role
        }
        binding.tvSheetStatus.text = user.accountStatus
        binding.tvSheetInitials.text = user.initials()

        configureActions()

        binding.btnSheetClose.setOnClickListener { dismiss() }
    }

    private fun configureActions() {
        val adminUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        when (user.accountStatus) {
            AccountStatus.BLOCKED.value -> {
                binding.btnSheetBlock.visibility = View.GONE
                binding.btnSheetUnblock.visibility = View.VISIBLE
                binding.btnSheetDelete.visibility = View.VISIBLE
            }
            AccountStatus.DELETED.value -> {
                binding.btnSheetBlock.visibility = View.GONE
                binding.btnSheetUnblock.visibility = View.GONE
                binding.btnSheetDelete.visibility = View.GONE
                binding.tvAlreadyDeleted.visibility = View.VISIBLE
            }
            AccountStatus.ACTIVE.value, AccountStatus.PENDING.value, AccountStatus.REJECTED.value -> {
                binding.btnSheetBlock.visibility = View.VISIBLE
                binding.btnSheetUnblock.visibility = View.GONE
                binding.btnSheetDelete.visibility = View.VISIBLE
            }
            else -> {
                binding.btnSheetBlock.visibility = View.VISIBLE
                binding.btnSheetUnblock.visibility = View.GONE
                binding.btnSheetDelete.visibility = View.VISIBLE
            }
        }

        binding.btnSheetBlock.setOnClickListener {
            if (actionInProgress) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Block Account")
                .setMessage("Block ${user.fullName}? They will lose access to the app.")
                .setPositiveButton("Block") { _, _ ->
                    actionInProgress = true
                    setButtonsEnabled(false)
                    authRepo.blockUser(
                        targetUid = user.uid,
                        adminUid = adminUid,
                        onSuccess = {
                            Toast.makeText(requireContext(), "${user.fullName} blocked.", Toast.LENGTH_SHORT).show()
                            notifyListener()
                            dismiss()
                        },
                        onFailure = { msg ->
                            actionInProgress = false
                            setButtonsEnabled(true)
                            Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSheetUnblock.setOnClickListener {
            if (actionInProgress) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Unblock Account")
                .setMessage("Unblock ${user.fullName}? They will regain access.")
                .setPositiveButton("Unblock") { _, _ ->
                    actionInProgress = true
                    setButtonsEnabled(false)
                    authRepo.unblockUser(
                        targetUid = user.uid,
                        onSuccess = {
                            Toast.makeText(requireContext(), "${user.fullName} unblocked.", Toast.LENGTH_SHORT).show()
                            notifyListener()
                            dismiss()
                        },
                        onFailure = { msg ->
                            actionInProgress = false
                            setButtonsEnabled(true)
                            Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSheetDelete.setOnClickListener {
            if (actionInProgress) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage(
                    "Permanently delete ${user.fullName}'s account?\n\n" +
                    "This action cannot be undone. The user will lose all access."
                )
                .setPositiveButton("Delete") { _, _ ->
                    actionInProgress = true
                    setButtonsEnabled(false)
                    authRepo.deleteUser(
                        targetUid = user.uid,
                        adminUid = adminUid,
                        onSuccess = {
                            Toast.makeText(requireContext(), "${user.fullName} deleted.", Toast.LENGTH_SHORT).show()
                            notifyListener()
                            dismiss()
                        },
                        onFailure = { msg ->
                            actionInProgress = false
                            setButtonsEnabled(true)
                            Toast.makeText(requireContext(), "Failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun notifyListener() {
        (parentFragment as? OnActionListener)?.onAccountActionPerformed()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        if (_binding == null) return
        binding.btnSheetBlock.isEnabled = enabled
        binding.btnSheetUnblock.isEnabled = enabled
        binding.btnSheetDelete.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
