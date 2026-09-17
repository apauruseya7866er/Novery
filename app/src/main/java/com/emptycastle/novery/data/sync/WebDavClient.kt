package com.emptycastle.novery.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Slice-06.3: minimal WebDAV client for server-free sync.
 *
 * No extra dependencies (OkHttp only). Layout on the server:
 *   <base>/novery-sync.novery      — latest backup (v2 envelope)
 *   <base>/novery-sync.meta.json   — {deviceId, updatedAt, appVersion}
 *
 * Pure URL/auth helpers are unit-tested; network calls run on IO.
 */
@Serializable
data class SyncMeta(
    val deviceId: String,
    val updatedAt: Long,
    val appVersion: String = ""
)

object WebDavClient {

    const val BACKUP_NAME = "novery-sync.novery"
    const val META_NAME = "novery-sync.meta.json"

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Config(
        val baseUrl: String,
        val username: String,
        val password: String
    ) {
        fun isComplete(): Boolean =
            baseUrl.isNotBlank() && username.isNotBlank()
    }

    /** Pure: join base + name with exactly one slash. */
    fun joinUrl(baseUrl: String, name: String): String {
        return baseUrl.trim().trimEnd('/') + "/" + name.trimStart('/')
    }

    /** Pure: Basic auth header value (empty password allowed). */
    fun basicAuth(username: String, password: String): String {
        return Credentials.basic(username, password)
    }

    fun encodeMeta(meta: SyncMeta): String = json.encodeToString(meta)

    fun decodeMeta(raw: String): SyncMeta = json.decodeFromString(raw)

    private fun authHeader(config: Config): String =
        basicAuth(config.username, config.password)

    suspend fun putBytes(
        config: Config,
        name: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(joinUrl(config.baseUrl, name))
                .header("Authorization", authHeader(config))
                .put(bytes.toRequestBody(contentType.toMediaTypeOrNull()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Upload failed: HTTP ${response.code}")
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBytes(config: Config, name: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(joinUrl(config.baseUrl, name))
                    .header("Authorization", authHeader(config))
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        return@withContext Result.failure(
                            Exception("Not found on server (404)")
                        )
                    }
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("Download failed: HTTP ${response.code}")
                        )
                    }
                    val bytes = response.body?.bytes()
                        ?: return@withContext Result.failure(Exception("Empty response"))
                    Result.success(bytes)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Push backup + metadata. Returns the pushed meta on success. */
    suspend fun pushBackup(
        config: Config,
        backupJson: String,
        deviceId: String,
        appVersion: String = ""
    ): Result<SyncMeta> {
        val meta = SyncMeta(
            deviceId = deviceId,
            updatedAt = System.currentTimeMillis(),
            appVersion = appVersion
        )
        putBytes(config, BACKUP_NAME, backupJson.toByteArray(Charsets.UTF_8)).onFailure {
            return Result.failure(it)
        }
        putBytes(config, META_NAME, encodeMeta(meta).toByteArray(Charsets.UTF_8)).onFailure {
            return Result.failure(it)
        }
        return Result.success(meta)
    }

    /** Fetch remote metadata (null meta + failure when absent). */
    suspend fun fetchMeta(config: Config): Result<SyncMeta> {
        return getBytes(config, META_NAME).fold(
            onSuccess = { bytes ->
                try {
                    Result.success(decodeMeta(bytes.toString(Charsets.UTF_8)))
                } catch (e: Exception) {
                    Result.failure(Exception("Invalid sync metadata: ${e.message}"))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** Download the remote backup as a JSON string. */
    suspend fun pullBackup(config: Config): Result<String> {
        return getBytes(config, BACKUP_NAME).fold(
            onSuccess = { Result.success(it.toString(Charsets.UTF_8)) },
            onFailure = { Result.failure(it) }
        )
    }
}
