package com.emptycastle.novery.data.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Slice-07.2a: OpenAI-compatible translation engine (OpenAI, DeepSeek,
 * Qwen, Kimi, Gemini-openai-bridge, Ollama, LM Studio…).
 *
 * Batch format sent to the model:
 *   Translate the following numbered paragraphs from X to Y. Reply with the
 *   same numbering, nothing else.
 *   1. <p1>
 *   2. <p2>
 * Replies are re-aligned by leading number; unparseable lines fall back
 * to the original paragraph so nothing is ever lost.
 */
class ApiTranslationEngine(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val http: OkHttpClient = defaultClient()
) : TranslationEngine {

    override fun isConfigured(): Boolean {
        return endpoint.isNotBlank() && model.isNotBlank()
    }

    override suspend fun translate(
        paragraphs: List<String>,
        sourceLang: String,
        targetLang: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured()) {
                return@withContext Result.failure(Exception("Translation engine not configured"))
            }
            if (paragraphs.isEmpty()) return@withContext Result.success(emptyList())

            val numbered = paragraphs.mapIndexed { i, p -> "${i + 1}. $p" }
                .joinToString("\n")
            val payload = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = "Translate the following numbered paragraphs from " +
                            "$sourceLang to $targetLang. Reply with the same numbering " +
                            "and nothing else."
                    ),
                    ChatMessage(role = "user", content = numbered)
                )
            )
            val request = Request.Builder()
                .url(endpoint.trim())
                .post(
                    json.encodeToString(payload)
                        .toRequestBody("application/json".toMediaTypeOrNull())
                )
                .let { builder ->
                    if (apiKey.isNotBlank()) {
                        builder.header("Authorization", "Bearer $apiKey")
                    } else builder
                }
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Translation failed: HTTP ${response.code}")
                    )
                }
                val content = try {
                    json.decodeFromString<ChatResponse>(body)
                        .choices.firstOrNull()?.message?.content
                        ?: return@withContext Result.failure(
                            Exception("Empty translation response")
                        )
                } catch (e: Exception) {
                    return@withContext Result.failure(
                        Exception("Invalid translation response: ${e.message}")
                    )
                }
                Result.success(alignResults(content, paragraphs))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /**
         * Pure: re-aligns a numbered reply onto the source paragraphs.
         * Lines without a valid number fall back to positional order;
         * missing slots keep the original text.
         */
        fun alignResults(reply: String, sources: List<String>): List<String> {
            if (sources.isEmpty()) return emptyList()
            val numbered = mutableMapOf<Int, String>()
            val positional = mutableListOf<String>()
            for (line in reply.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val match = NUMBERED_LINE.matchEntire(trimmed)
                if (match != null) {
                    val index = match.groupValues[1].toIntOrNull()?.minus(1)
                    if (index != null && index in sources.indices) {
                        numbered[index] = match.groupValues[2].trim()
                        continue
                    }
                }
                positional.add(trimmed)
            }
            var fallback = 0
            return sources.indices.map { i ->
                numbered[i] ?: positional.getOrNull(fallback++).takeIf { numbered.isEmpty() }
                ?: sources[i]
            }
        }

        // Separator required: a bare leading number ("3 blind mice") is
        // content, not numbering.
        private val NUMBERED_LINE = Regex("""^(\d+)\s*[.):\-]\s+(.*)$""")
    }
}

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(val model: String, val messages: List<ChatMessage>)

@Serializable
private data class ChatChoice(val message: ChatMessage)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())
