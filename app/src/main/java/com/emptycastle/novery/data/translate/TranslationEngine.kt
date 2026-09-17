package com.emptycastle.novery.data.translate

/**
 * Slice-07.2a: translation engine abstraction.
 *
 * Engines translate batches of paragraphs (order preserved). Implementations
 * must never throw for transport/API errors — return [Result.failure]
 * instead so the reader can fall back to the original text.
 */
interface TranslationEngine {

    /** False when the engine lacks configuration (endpoint/model). */
    fun isConfigured(): Boolean

    /**
     * Translates [paragraphs] from [sourceLang] to [targetLang].
     * Language tags: "auto" or BCP-47 ("en", "zh", "ja", …).
     * Returns exactly [paragraphs.size] items on success.
     */
    suspend fun translate(
        paragraphs: List<String>,
        sourceLang: String,
        targetLang: String
    ): Result<List<String>>
}
