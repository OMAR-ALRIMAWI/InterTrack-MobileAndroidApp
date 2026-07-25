package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreConversation
import com.example.intertrack.databinding.FragmentMessagesStudentBinding
import com.google.firebase.auth.FirebaseAuth

class MessegesStudent : Fragment() {

    private var _binding: FragmentMessagesStudentBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvConversations.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefreshMessages.setColorSchemeColors(0xFF005FAF.toInt())
        binding.swipeRefreshMessages.setOnRefreshListener { loadConversations() }

        loadConversations()
    }

    private fun loadConversations() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressMessages.visibility = View.VISIBLE
        binding.tvMessagesError.visibility = View.GONE
        binding.tvMessagesEmpty.visibility = View.GONE
        binding.rvConversations.visibility = View.GONE

        authRepo.getUserConversations(
            uid = uid,
            onSuccess = { conversations ->
                if (_binding == null) return@getUserConversations
                binding.progressMessages.visibility = View.GONE
                binding.swipeRefreshMessages.isRefreshing = false

                if (conversations.isEmpty()) {
                    binding.tvMessagesEmpty.visibility = View.VISIBLE
                } else {
                    binding.rvConversations.visibility = View.VISIBLE
                    showAdapter(conversations, uid)
                    resolveAndRefreshNames(conversations, uid)
                }
            },
            onFailure = { _ ->
                if (_binding == null) return@getUserConversations
                binding.progressMessages.visibility = View.GONE
                binding.swipeRefreshMessages.isRefreshing = false
                binding.tvMessagesError.visibility = View.VISIBLE
            }
        )
    }

    private fun showAdapter(conversations: List<FirestoreConversation>, selfUid: String) {
        if (_binding == null) return
        binding.rvConversations.adapter = ConversationAdapter(conversations, selfUid) { conv ->
            val chat = if (conv.isProgressChat()) {
                ChatFragment.newInstance(conv.conversationId, "GROUP",
                    conv.displayTitle(selfUid), conv.displaySubtitle(selfUid))
            } else {
                ChatFragment.newInstance(conv.conversationId,
                    conv.otherParticipantUid(selfUid), conv.otherParticipantName(selfUid))
            }
            (requireActivity() as? StudentDashBoard)?.openDetail(chat)
        }
    }

    private fun resolveAndRefreshNames(
        conversations: List<FirestoreConversation>,
        selfUid: String
    ) {
        // Collect UIDs whose names are missing or blank
        val unknownUids = conversations
            .flatMap { it.participantUids }
            .filter { uid -> uid != selfUid && uid.isNotBlank() && uid != "GROUP" }
            .filter { uid ->
                conversations.any { conv ->
                    uid in conv.participantUids && conv.participantNames[uid].isNullOrBlank()
                }
            }
            .distinct()

        if (unknownUids.isEmpty()) return

        val resolvedNames = mutableMapOf<String, String>()
        var pending = unknownUids.size

        fun onResolved() {
            pending--
            if (pending > 0) return
            if (_binding == null) return
            val updated = conversations.map { conv ->
                val names = conv.participantNames.toMutableMap()
                resolvedNames.forEach { (uid, name) ->
                    if (uid in conv.participantUids && conv.participantNames[uid].isNullOrBlank()) {
                        names[uid] = name
                    }
                }
                conv.copy(participantNames = names)
            }
            showAdapter(updated, selfUid)
        }

        unknownUids.forEach { uid ->
            authRepo.fetchUserDocument(
                uid = uid,
                onSuccess = { user ->
                    if (user.fullName.isNotBlank()) resolvedNames[uid] = user.fullName
                    onResolved()
                },
                onFailure = { onResolved() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? StudentDashBoard)?.apply {
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
