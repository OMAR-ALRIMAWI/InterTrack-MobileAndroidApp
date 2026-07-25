package com.example.intertrack.fragments.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.intertrack.activities.AdminDashboardActivity
import com.example.intertrack.databinding.FragmentAdminProfileBinding
import com.google.firebase.auth.FirebaseAuth

class AdminProfileFragment : Fragment() {

    private var _binding: FragmentAdminProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email ?: "Administrator"

        binding.tvProfileEmail.text = email
        binding.tvProfileInitials.text = email.take(1).uppercase()

        binding.btnLogout.setOnClickListener {
            (requireActivity() as? AdminDashboardActivity)?.logout()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
