package com.emptycastle.novery.data.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Slice-05.3: one persisted update failure.
 */
@Serializable
data class UpdateError(
    val novelUrl: String,
    val novelName: String,
    val providerName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Slice-05.3: file-backed store of the last library-refresh failures.
 * Each full refresh replaces the snapshot; entries older than a week or
 * beyond the cap are pruned. Never throws out of the public API.
 */
class UpdateErrorStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val file = File(context.filesDir, FILE_NAME)

    private val _errorsFlow = MutableStateFlow<List<UpdateError>>(emptyList())
    val errorsFlow: Flow<List<UpdateError>> = _errorsFlow.asStateFlow()

    init {
        loadFromFile()
    }

    fun observeErrors(): Flow<List<UpdateError>> = errorsFlow

    suspend fun replaceAll(errors: List<UpdateError>) {
        mutex.withLock {
            val pruned = prune(errors, System.currentTimeMillis())
            _errorsFlow.value = pruned.sortedByDescending { it.timestamp }
            saveToFile()
        }
    }

    suspend fun clearFor(novelUrl: String) {
        mutex.withLock {
            _errorsFlow.value = _errorsFlow.value.filter { it.novelUrl != novelUrl }
            saveToFile()
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            _errorsFlow.value = emptyList()
            saveToFile()
        }
    }

    private fun loadFromFile() {
        try {
            if (!file.exists()) return
            val data = json.decodeFromString<UpdateErrorData>(file.readText())
            _errorsFlow.value = prune(data.errors, System.currentTimeMillis())
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading update errors", e)
            _errorsFlow.value = emptyList()
        }
    }

    private suspend fun saveToFile() {
        try {
            file.writeText(json.encodeToString(UpdateErrorData(_errorsFlow.value)))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving update errors", e)
        }
    }

    companion object {
        private const val TAG = "UpdateErrorStore"
        private const val FILE_NAME = "update_errors.json"

        const val MAX_ENTRIES = 20
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * Pure prune: drop entries older than [MAX_AGE_MS], keep newest
         * [MAX_ENTRIES]. Unit-tested.
         */
        fun prune(errors: List<UpdateError>, now: Long): List<UpdateError> {
            return errors
                .filter { now - it.timestamp <= MAX_AGE_MS }
                .sortedByDescending { it.timestamp }
                .take(MAX_ENTRIES)
        }

        /**
         * Pure relative label ("just now", "5m ago", "3h ago", "2d ago").
         * Unit-tested.
         */
        fun timeAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
            val diff = (now - timestamp).coerceAtLeast(0)
            val minutes = diff / 60_000L
            if (minutes < 1) return "just now"
            if (minutes < 60) return "${minutes}m ago"
            val hours = minutes / 60
            if (hours < 24) return "${hours}h ago"
            return "${hours / 24}d ago"
        }
    }
}

@Serializable
private data class UpdateErrorData(
    val errors: List<UpdateError> = emptyList()
)
