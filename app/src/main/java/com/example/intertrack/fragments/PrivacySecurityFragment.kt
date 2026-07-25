package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.databinding.FragmentPrivacySecurityBinding

class PrivacySecurityFragment : Fragment() {

    private var _binding: FragmentPrivacySecurityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPrivacySecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowSecChangePassword.setOnClickListener {
            Toast.makeText(requireContext(), "Change password coming soon", Toast.LENGTH_SHORT).show()
        }
        binding.btnDeleteAccountPrivacy.setOnClickListener {
            Toast.makeText(requireContext(), "Delete account — not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Privacy & Security")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
