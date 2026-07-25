package com.example.intertrack.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.intertrack.activities.CompanyDashBoard
import com.example.intertrack.activities.InstructorDashBoard
import com.example.intertrack.activities.StudentDashBoard
import com.example.intertrack.data.firebase.FirebaseAuthRepository
import com.example.intertrack.data.model.FirestoreMessage
import com.example.intertrack.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val authRepo = FirebaseAuthRepository()
    private var conversationId: String = ""
    private var otherUid: String = ""

    companion object {
        private const val ARG_CONVERSATION_ID = "conversation_id"
        private const val ARG_OTHER_UID = "other_uid"
        private const val ARG_OTHER_NAME = "other_name"
        private const val ARG_SUBTITLE = "subtitle"

        fun newInstance(conversationId: String, otherUid: String, otherName: String): ChatFragment {
            return newInstance(conversationId, otherUid, otherName, "")
        }

        /** Overload for progress/group chats: otherName is used as the header title and
         *  subtitle shows participants/company. Pass otherUid = "GROUP" for group chats. */
        fun newInstance(conversationId: String, otherUid: String, otherName: String, subtitle: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONVERSATION_ID, conversationId)
                    putString(ARG_OTHER_UID, otherUid)
                    putString(ARG_OTHER_NAME, otherName)
                    putString(ARG_SUBTITLE, subtitle)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideHostUI()

        conversationId = arguments?.getString(ARG_CONVERSATION_ID) ?: ""
        otherUid = arguments?.getString(ARG_OTHER_UID) ?: ""
        val otherName = arguments?.getString(ARG_OTHER_NAME) ?: "Chat"
        val subtitle = arguments?.getString(ARG_SUBTITLE) ?: ""

        binding.tvChatName.text = otherName
        binding.tvChatInitials.text = initialsOf(otherName)
        // Show the real subtitle (group participants / role) instead of a fake "Online" status.
        if (subtitle.isNotBlank()) {
            binding.tvChatStatus.text = subtitle
            binding.chatStatusRow.visibility = View.VISIBLE
        } else {
            binding.chatStatusRow.visibility = View.GONE
        }

        // For an internship group chat opened from an old/generic conversation, resolve the real
        // internship title from its connection, fix the header, and normalize the stored doc so the
        // Messages list shows the proper name next time.
        if (otherUid == "GROUP" && conversationId.startsWith("progress_")) {
            normalizeGroupChatTitle()
        }

        // Anchor messages to the bottom like a real chat, and keep newest visible.
        binding.rvChatMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        binding.btnChatBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSend.setOnClickListener { sendMessage() }

        binding.etChatInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        if (conversationId.isNotBlank()) {
            loadMessages()
            markRead()
        }
    }

    private fun normalizeGroupChatTitle() {
        val connectionId = conversationId.removePrefix("progress_")
        authRepo.getConnectionById(
            connectionId = connectionId,
            onSuccess = { conn ->
                if (_binding == null || conn == null) return@getConnectionById
                val title = conn.internshipTitle
                if (title.isBlank()) return@getConnectionById
                val chatName = "$title - Internship Chat"
                binding.tvChatName.text = chatName
                binding.tvChatInitials.text = initialsOf(title)
                // Persist the proper name onto the conversation doc (no-op if already named).
                authRepo.normalizeProgressConversation(conversationId, title) {}
            },
            onFailure = { /* keep the passed-in title */ }
        )
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun loadMessages() {
        binding.progressChat.visibility = View.VISIBLE
        binding.chatEmptyContainer.visibility = View.GONE
        // Clear any previously-bound messages so a stale conversation can never flash before the
        // correct one for this conversationId arrives.
        binding.rvChatMessages.adapter = null
        binding.rvChatMessages.visibility = View.GONE
        authRepo.getMessages(
            conversationId = conversationId,
            onSuccess = { messages ->
                if (_binding == null) return@getMessages
                binding.progressChat.visibility = View.GONE
                renderMessages(messages)
            },
            onFailure = {
                if (_binding == null) return@getMessages
                binding.progressChat.visibility = View.GONE
            }
        )
    }

    private fun renderMessages(messages: List<FirestoreMessage>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (messages.isEmpty()) {
            binding.chatEmptyContainer.visibility = View.VISIBLE
            binding.rvChatMessages.visibility = View.GONE
        } else {
            binding.chatEmptyContainer.visibility = View.GONE
            binding.rvChatMessages.visibility = View.VISIBLE
            binding.rvChatMessages.adapter = ChatMessageAdapter(messages, uid)
            binding.rvChatMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun markRead() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        authRepo.markConversationRead(conversationId, uid, {}, {})
    }

    private fun sendMessage() {
        val text = binding.etChatInput.text?.toString()?.trim() ?: return
        if (text.isEmpty() || conversationId.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val senderName = FirebaseAuth.getInstance().currentUser?.displayName ?: ""

        binding.etChatInput.text?.clear()

        authRepo.sendMessage(
            conversationId = conversationId,
            senderUid = uid,
            senderName = senderName,
            receiverUid = otherUid,
            text = text,
            onSuccess = {
                if (_binding == null) return@sendMessage
                loadMessages()
            },
            onFailure = { /* fail silently — message stays unsent, user can retry */ }
        )
    }

    override fun onResume() {
        super.onResume()
        hideHostUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Do NOT restore the shared header here: with exit animations this runs AFTER the next
        // screen already hid it, which caused a duplicated header/background. Each destination
        // fragment re-asserts the header in its own onResume.
        _binding = null
    }

    private fun hideHostUI() {
        (requireActivity() as? StudentDashBoard)?.apply    { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? InstructorDashBoard)?.apply { setHeaderVisible(false); setNavVisible(false) }
        (requireActivity() as? CompanyDashBoard)?.apply    { setHeaderVisible(false); setNavVisible(false) }
    }

    private fun restoreHostUI() {
        (requireActivity() as? StudentDashBoard)?.apply    { setHeaderVisible(true); setNavVisible(true) }
        (requireActivity() as? InstructorDashBoard)?.apply { setHeaderVisible(true); setNavVisible(true) }
        (requireActivity() as? CompanyDashBoard)?.apply    { setHeaderVisible(true); setNavVisible(true) }
    }
}
