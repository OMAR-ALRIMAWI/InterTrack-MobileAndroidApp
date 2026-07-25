package com.example.intertrack.data.model

/**
 * An OPEN internship offer paired with its (optionally loaded) company document.
 * Used by the student Explore screen so each offer renders as its own card without
 * forcing the company profile to load first. [company] may be null if the referenced
 * company document is missing — the offer still carries companyId/companyName.
 */
data class ExploreOffer(
    val offer: FirestoreInternshipOffer,
    val company: FirestoreCompany?
)
