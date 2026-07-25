package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.databinding.FragmentHelpSupportBinding

class HelpSupportFragment : Fragment() {

    private var _binding: FragmentHelpSupportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // FAQ toggle behavior
        setupFaqToggle(binding.faqRow1, binding.faqAnswer1)
        setupFaqToggle(binding.faqRow2, binding.faqAnswer2)
        setupFaqToggle(binding.faqRow3, binding.faqAnswer3)
        setupFaqToggle(binding.faqRow4, binding.faqAnswer4)
        setupFaqToggle(binding.faqRow5, binding.faqAnswer5)

        binding.btnSendSupport.setOnClickListener {
            val subject = binding.etSupportSubject.text?.toString()?.trim() ?: ""
            val message = binding.etSupportMessage.text?.toString()?.trim() ?: ""
            if (subject.isEmpty() || message.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all support fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: POST /api/support — send message to support backend
            binding.etSupportSubject.text?.clear()
            binding.etSupportMessage.text?.clear()
            Toast.makeText(requireContext(), "Support message sent! We'll respond within 24h.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFaqToggle(row: View, answer: View) {
        row.setOnClickListener {
            answer.visibility = if (answer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Help & Support")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
