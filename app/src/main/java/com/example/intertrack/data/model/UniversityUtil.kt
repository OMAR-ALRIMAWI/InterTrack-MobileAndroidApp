package com.example.intertrack.data.model

/**
 * Small, dependency-free helper for comparing university names across spelling/diacritic variants.
 * The comparable key is derived from the existing `university` string on the user document — no new
 * stored field or migration is required.
 *
 * Examples that all map to "uskudar_university":
 *   "Üsküdar University", "Uskudar University", "uskudar", "Üsküdar Üniversitesi"
 */
object UniversityUtil {

    /**
     * The selectable universities (display name → stable key). This is the single source used by the
     * verification / instructor-profile dropdowns and by key↔display conversions. Add new entries
     * here only; the key is exactly what [normalizeUniversityName] produces for the display name.
     */
    val UNIVERSITIES: List<Pair<String, String>> = listOf(
        "Üsküdar University" to "uskudar_university",
        "Istanbul University" to "istanbul_university"
    )

    /** Display names for a dropdown/list. */
    fun displayNames(): List<String> = UNIVERSITIES.map { it.first }

    /** Stable key for a chosen display name (falls back to normalization for anything off-list). */
    fun keyForDisplayName(displayName: String?): String {
        if (displayName.isNullOrBlank()) return ""
        return UNIVERSITIES.firstOrNull { it.first.equals(displayName.trim(), ignoreCase = true) }?.second
            ?: normalizeUniversityName(displayName)
    }

    /** Human-readable name for a stored key (used as a profile-display fallback). "" if unknown. */
    fun displayNameForKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        return UNIVERSITIES.firstOrNull { it.second == key }?.first ?: ""
    }

    // Generic words dropped before building the key, so "X" and "X University" match.
    private val GENERIC_WORDS = setOf(
        "university", "universitesi", "üniversitesi", "uni", "college", "institute", "school"
    )

    /**
     * Normalizes a university name into a stable comparable key. Returns "" when the input is blank
     * or contains only generic words (treated as "no university").
     */
    fun normalizeUniversityName(value: String?): String {
        if (value.isNullOrBlank()) return ""

        // Lowercase, then transliterate Turkish diacritics to plain ASCII.
        val lowered = value.trim().lowercase()
            .replace('ü', 'u')
            .replace('ı', 'i')
            .replace('İ', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ç', 'c')
            .replace('ö', 'o')
            .replace('â', 'a')
            .replace('î', 'i')
            .replace('û', 'u')

        // Keep letters/digits/spaces only, collapse whitespace, split into tokens.
        val tokens = lowered
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in GENERIC_WORDS }

        if (tokens.isEmpty()) return ""

        // Stable key: distinguishing tokens joined by "_", plus a fixed "_university" suffix so that
        // "uskudar" and "Uskudar University" produce the same value.
        return tokens.joinToString("_") + "_university"
    }

    /** True when both names resolve to the same non-blank university key. */
    fun sameUniversity(a: String?, b: String?): Boolean {
        val ka = normalizeUniversityName(a)
        return ka.isNotBlank() && ka == normalizeUniversityName(b)
    }
}
