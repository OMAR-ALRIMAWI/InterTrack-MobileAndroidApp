package com.example.intertrack.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentAccountSettingsBinding

class AccountSettingsFragment : Fragment() {

    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!!
    private val authRepo = FirebaseAuthRepository()

    companion object {
        private const val PREFS_NAME = "intertrack_prefs"
        private const val KEY_LANGUAGE = "selected_language"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateLanguageLabel()

        binding.rowChangeName.setOnClickListener {
            Toast.makeText(requireContext(), "Change name coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.rowChangeEmail.setOnClickListener {
            Toast.makeText(requireContext(), "Change email coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.rowChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        binding.rowLanguage.setOnClickListener {
            showLanguageDialog()
        }
        binding.btnDeleteAccount.setOnClickListener {
            Toast.makeText(requireContext(), "Delete account — are you sure? (Not implemented yet)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            android.R.layout.simple_list_item_1, null, false
        )
        // Build a custom dialog with three EditText fields
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etCurrent = EditText(requireContext()).apply {
            hint = "Current password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etNew = EditText(requireContext()).apply {
            hint = "New password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirm = EditText(requireContext()).apply {
            hint = "Confirm new password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etCurrent)
        layout.addView(etNew)
        layout.addView(etConfirm)

        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val current = etCurrent.text?.toString()?.trim() ?: ""
                val newPwd = etNew.text?.toString()?.trim() ?: ""
                val confirm = etConfirm.text?.toString()?.trim() ?: ""

                if (current.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter your current password.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPwd.length < 6) {
                    Toast.makeText(requireContext(), "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPwd != confirm) {
                    Toast.makeText(requireContext(), "New passwords do not match.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                authRepo.changePassword(
                    currentPassword = current,
                    newPassword = newPwd,
                    onSuccess = {
                        if (_binding == null) return@changePassword
                        Toast.makeText(requireContext(), "Password updated successfully.", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { msg ->
                        if (_binding == null) return@changePassword
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog() {
        val options = arrayOf("English", "Arabic (العربية)", "Turkish (Türkçe)")
        val keys = arrayOf("en", "ar", "tr")
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        val currentIndex = keys.indexOf(current).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(requireContext())
            .setTitle("Select Language")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.edit().putString(KEY_LANGUAGE, keys[which]).apply()
                updateLanguageLabel()
                Toast.makeText(requireContext(),
                    "${options[which]} selected. Restart the app to apply fully.",
                    Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateLanguageLabel() {
        // Language label updated via dialog selection — no extra UI update needed here
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Account Settings")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
