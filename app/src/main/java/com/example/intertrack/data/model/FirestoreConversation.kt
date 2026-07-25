package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreConversation(
    val conversationId: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val unreadBy: List<String> = emptyList(),
    val type: String = "",
    val connectionId: String = "",
    // Stored on the conversation doc by connectInternship — used to name the group chat.
    val internshipTitle: String = "",
    val title: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun isProgressChat(): Boolean =
        type == "INTERNSHIP_PROGRESS" || conversationId.startsWith("progress_")

    fun otherParticipantUid(selfUid: String): String =
        participantUids.firstOrNull { it != selfUid } ?: ""

    fun otherParticipantName(selfUid: String): String {
        val name = participantNames[otherParticipantUid(selfUid)]
        return if (name.isNullOrBlank()) "User" else name
    }

    /** Group chat name based on the internship title, e.g. "back-end - Internship Chat". */
    fun internshipChatName(): String {
        val t = internshipTitle.ifBlank {
            // Fall back to a stored "<title> Progress" style title if internshipTitle is missing.
            title.removeSuffix(" Progress").trim()
        }
        return if (t.isNotBlank()) "$t - Internship Chat" else "Internship Chat"
    }

    fun displayTitle(selfUid: String): String = if (isProgressChat()) {
        internshipChatName()
    } else {
        val name = otherParticipantName(selfUid)
        if (name == "User") "Direct Message" else name
    }

    fun displaySubtitle(selfUid: String): String = if (isProgressChat()) {
        // Show the three participants under the internship name.
        participantNames.values.filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Internship group chat" }
    } else {
        // Direct chats: title already shows the name; keep subtitle clean
        ""
    }
}
