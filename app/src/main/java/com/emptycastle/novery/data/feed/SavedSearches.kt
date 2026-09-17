package com.emptycastle.novery.data.feed

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Slice-07.1b: a saved feed search. Pure helpers below are unit-tested;
 * persistence lives in PreferencesManager (JSON list + flow).
 */
@Serializable
data class SavedSearch(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val createdAt: Long = System.currentTimeMillis()
)

object SavedSearches {

    /** Normalized form for dedupe/compare. Null when blank. */
    fun normalize(query: String): String? {
        val trimmed = query.trim().replace(Regex("\\s+"), " ")
        return trimmed.takeIf { it.isNotBlank() }
    }

    /**
     * Adds a query (case-insensitive dedupe, newest first, capped).
     * Returns the new list.
     */
    fun add(
        current: List<SavedSearch>,
        query: String,
        max: Int = 20
    ): List<SavedSearch> {
        val normalized = normalize(query) ?: return current
        val withoutDupes = current.filter {
            normalize(it.query)?.lowercase() != normalized.lowercase()
        }
        return (listOf(SavedSearch(query = normalized)) + withoutDupes).take(max)
    }

    fun remove(current: List<SavedSearch>, id: String): List<SavedSearch> {
        return current.filter { it.id != id }
    }
}
