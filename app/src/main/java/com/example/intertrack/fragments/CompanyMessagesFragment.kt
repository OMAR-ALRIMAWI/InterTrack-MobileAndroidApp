package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentCompanyMessagesBinding
import com.google.firebase.auth.FirebaseAuth

class CompanyMessagesFragment : Fragment() {

    private var _binding: FragmentCompanyMessagesBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanyMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCompanyConversations.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshCompanyMessages.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshCompanyMessages.setOnRefreshListener { loadConversations() }

        loadConversations()
    }

    private fun loadConversations() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressCompanyMessages.visibility = View.VISIBLE
        binding.tvCompanyMessagesError.visibility = View.GONE
        binding.tvCompanyMessagesEmpty.visibility = View.GONE
        binding.rvCompanyConversations.visibility = View.GONE

        authRepo.getUserConversations(
            uid = uid,
            onSuccess = { conversations ->
                if (_binding == null) return@getUserConversations
                binding.progressCompanyMessages.visibility = View.GONE
                binding.swipeRefreshCompanyMessages.isRefreshing = false

                if (conversations.isEmpty()) {
                    binding.tvCompanyMessagesEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvCompanyConversations.visibility = View.VISIBLE
                    binding.rvCompanyConversations.adapter = ConversationAdapter(conversations, uid) { conv ->
                        val chat = if (conv.isProgressChat()) {
                            ChatFragment.newInstance(conv.conversationId, "GROUP",
                                conv.displayTitle(uid), conv.displaySubtitle(uid))
                        } else {
                            ChatFragment.newInstance(conv.conversationId,
                                conv.otherParticipantUid(uid), conv.otherParticipantName(uid))
                        }
                        (requireActivity() as? CompanyDashBoard)?.openDetail(chat)
                    }
                }
            },
            onFailure = { _ ->
                if (_binding == null) return@getUserConversations
                binding.progressCompanyMessages.visibility = View.GONE
                binding.swipeRefreshCompanyMessages.isRefreshing = false
                binding.tvCompanyMessagesError.visibility = View.VISIBLE
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? CompanyDashBoard)?.apply {
            setHeaderVisible(true)
            setNavVisible(true)
            updateHeader("Messages")
        }
        loadConversations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
