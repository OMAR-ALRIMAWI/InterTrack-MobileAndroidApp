package com.example.intertrack.data.model

import com.google.firebase.Timestamp

data class FirestoreDocumentMeta(
    val documentId: String = "",
    val ownerUid: String = "",
    val relatedToType: String = "",
    val relatedToId: String = "",
    val documentName: String = "",
    val documentType: String = "",
    val selectedOnly: Boolean = true,
    val storageUrl: String? = null,
    val createdAt: Timestamp? = null
)
