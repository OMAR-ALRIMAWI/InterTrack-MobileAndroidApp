package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.databinding.FragmentInstructorMessagesBinding
import com.google.firebase.auth.FirebaseAuth

class InstructorMessagesFragment : Fragment() {

    private var _binding: FragmentInstructorMessagesBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstructorMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvInstructorConversations.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshInstructorMessages.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshInstructorMessages.setOnRefreshListener { loadConversations() }

        loadConversations()
    }

    private fun loadConversations() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressInstructorMessages.visibility = View.VISIBLE
        binding.tvInstructorMessagesError.visibility = View.GONE
        binding.tvInstructorMessagesEmpty.visibility = View.GONE
        binding.rvInstructorConversations.visibility = View.GONE

        authRepo.getUserConversations(
            uid = uid,
            onSuccess = { conversations ->
                if (_binding == null) return@getUserConversations
                binding.progressInstructorMessages.visibility = View.GONE
                binding.swipeRefreshInstructorMessages.isRefreshing = false

                if (conversations.isEmpty()) {
                    binding.tvInstructorMessagesEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvInstructorConversations.visibility = View.VISIBLE
                    binding.rvInstructorConversations.adapter = ConversationAdapter(conversations, uid) { conv ->
                        val chat = if (conv.isProgressChat()) {
                            ChatFragment.newInstance(conv.conversationId, "GROUP",
                                conv.displayTitle(uid), conv.displaySubtitle(uid))
                        } else {
                            ChatFragment.newInstance(conv.conversationId,
                                conv.otherParticipantUid(uid), conv.otherParticipantName(uid))
                        }
                        (requireActivity() as? InstructorDashBoard)?.openDetail(chat)
                    }
                }
            },
            onFailure = { _ ->
                if (_binding == null) return@getUserConversations
                binding.progressInstructorMessages.visibility = View.GONE
                binding.swipeRefreshInstructorMessages.isRefreshing = false
                binding.tvInstructorMessagesError.visibility = View.VISIBLE
            }
        )
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? InstructorDashBoard)?.apply {
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
