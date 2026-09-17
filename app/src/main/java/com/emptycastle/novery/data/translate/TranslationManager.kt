package com.emptycastle.novery.data.translate

/**
 * Slice-07.2a: chapter translation coordinator.
 *
 * Splits chapters into translatable paragraphs, batches engine calls,
 * streams partial results, and memoizes per (source, target, text-hash)
 * in a small LRU so re-opens and re-renders never re-translate.
 * Pure except for the injected engine — unit-tested with fakes.
 */
class TranslationManager(
    private val engine: TranslationEngine,
    private val maxBatchParagraphs: Int = 10,
    private val maxCacheEntries: Int = 200
) {

    private val cache = LinkedHashMap<String, String>(maxCacheEntries, 0.75f, true)

    fun cacheSize(): Int = synchronized(cache) { cache.size }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun cacheKey(text: String, sourceLang: String, targetLang: String): String {
        return "$sourceLang>$targetLang:${text.hashCode()}:${text.length}"
    }

    fun cached(text: String, sourceLang: String, targetLang: String): String? {
        synchronized(cache) {
            return cache[cacheKey(text, sourceLang, targetLang)]
        }
    }

    private fun store(text: String, translated: String, sourceLang: String, targetLang: String) {
        synchronized(cache) {
            cache[cacheKey(text, sourceLang, targetLang)] = translated
            while (cache.size > maxCacheEntries) {
                val eldest = cache.entries.iterator().next().key
                cache.remove(eldest)
            }
        }
    }

    /**
     * Translates [paragraphs], emitting each result via [onPartial] as soon
     * as its batch completes (streaming render). Untranslatable items keep
     * their original text — the returned list always matches input size.
     */
    suspend fun translateChapter(
        paragraphs: List<String>,
        sourceLang: String,
        targetLang: String,
        onPartial: (index: Int, translated: String) -> Unit = { _, _ -> }
    ): List<String> {
        if (paragraphs.isEmpty()) return emptyList()
        if (!engine.isConfigured()) return paragraphs.toList()

        val output = paragraphs.toMutableList()
        val pending = paragraphs.mapIndexedNotNull { index, text ->
            if (text.isBlank()) {
                null
            } else {
                val hit = cached(text, sourceLang, targetLang)
                if (hit != null) {
                    output[index] = hit
                    onPartial(index, hit)
                    null
                } else {
                    index
                }
            }
        }
        if (pending.isEmpty()) return output

        for (batch in pending.chunked(maxBatchParagraphs.coerceAtLeast(1))) {
            val result = engine.translate(
                batch.map { paragraphs[it] },
                sourceLang,
                targetLang
            )
            val translated = result.getOrElse { batch.map { paragraphs[it] } }
            batch.forEachIndexed { i, index ->
                // Guard against short engine replies.
                val text = translated.getOrNull(i) ?: paragraphs[index]
                output[index] = text
                store(paragraphs[index], text, sourceLang, targetLang)
                onPartial(index, text)
            }
        }
        return output
    }
}
