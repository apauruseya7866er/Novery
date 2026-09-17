package com.emptycastle.novery.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Slice-06.4: free off-site backup via a Telegram bot.
 *
 * One-way push (bot → chat document) + connection test. Restore reuses
 * the existing file-restore flow (download the file in any Telegram
 * client, open it with Novery). No extra dependencies (OkHttp only).
 */
object TelegramBackup {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Pure: Bot API base URL. Empty token yields an empty string. */
    fun apiBase(token: String): String {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return ""
        return "https://api.telegram.org/bot$trimmed"
    }

    /**
     * Pure: extract the bot username from a getMe response body.
     * Null when absent/invalid.
     */
    fun parseBotUsername(rawBody: String): String? {
        return try {
            val root = json.parseToJsonElement(rawBody).jsonObject
            if (root["ok"]?.jsonPrimitive?.contentOrNull != "true") return null
            root["result"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Pure: extract file_id from a sendDocument response body.
     * Null when absent/invalid.
     */
    fun parseFileId(rawBody: String): String? {
        return try {
            val root = json.parseToJsonElement(rawBody).jsonObject
            if (root["ok"]?.jsonPrimitive?.contentOrNull != "true") return null
            root["result"]?.jsonObject?.get("document")
                ?.jsonObject?.get("file_id")?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    fun errorMessage(code: Int, body: String): String {
        val description = try {
            json.parseToJsonElement(body).jsonObject["description"]
                ?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
        val reason = description ?: "HTTP $code"
        return when (code) {
            401 -> "Unauthorized — check the bot token ($reason)"
            400 -> "Bad request ($reason)"
            else -> reason
        }
    }

    /** Connection test: returns the bot username on success. */
    suspend fun getMe(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = apiBase(token)
            if (base.isEmpty()) return@withContext Result.failure(Exception("Bot token is empty"))
            val request = Request.Builder().url("$base/getMe").get().build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(errorMessage(response.code, body)))
                }
                val username = parseBotUsername(body)
                    ?: return@withContext Result.failure(Exception("Unexpected getMe response"))
                Result.success(username)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends the backup JSON as a document to [chatId].
     * @return Telegram file_id on success.
     */
    suspend fun sendBackup(
        token: String,
        chatId: String,
        backupJson: String,
        fileName: String,
        caption: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base = apiBase(token)
            if (base.isEmpty()) return@withContext Result.failure(Exception("Bot token is empty"))
            if (chatId.isBlank()) return@withContext Result.failure(Exception("Chat ID is empty"))

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.trim())
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "document", fileName,
                    backupJson.toByteArray(Charsets.UTF_8)
                        .toRequestBody("application/json".toMediaTypeOrNull())
                )
                .build()
            val request = Request.Builder().url("$base/sendDocument").post(body).build()
            http.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(errorMessage(response.code, responseBody))
                    )
                }
                val fileId = parseFileId(responseBody)
                    ?: return@withContext Result.failure(Exception("Unexpected sendDocument response"))
                Result.success(fileId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
