package com.emptycastle.novery.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Slice-06.1: integrity envelope for backups.
 *
 * v2 exports wrap the backup as {"format","checksum","backup"} where the
 * checksum is SHA-256 over a canonical (non-pretty, deterministic) encoding
 * of the inner backup. v1 bare backups and QuickNovel files still restore
 * as legacy (unverified). Pure logic — unit-tested.
 */
@Serializable
data class BackupEnvelope(
    val format: String = FORMAT,
    val checksum: String = "",
    val backup: BackupData = BackupData()
) {
    companion object {
        const val FORMAT = "novery-backup"
    }
}

object BackupIntegrity {

    private val canonicalJson = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun checksumOf(backup: BackupData): String {
        return sha256Hex(canonicalJson.encodeToString(backup).toByteArray(Charsets.UTF_8))
    }

    fun wrap(backup: BackupData): BackupEnvelope {
        return BackupEnvelope(checksum = checksumOf(backup), backup = backup)
    }

    sealed interface Unwrapped {
        /** Checksummed envelope, digest verified. */
        data class Valid(val backup: BackupData) : Unwrapped

        /** Legacy bare backup (v1) — restores, but unverified. */
        data class Legacy(val backup: BackupData) : Unwrapped

        data class Corrupt(val reason: String) : Unwrapped
    }

    fun unwrap(rawJson: String): Unwrapped {
        // v2 envelope first — but only when the envelope markers are really
        // present. All envelope fields have defaults, so a bare v1 backup
        // would otherwise decode as an envelope with a blank checksum.
        try {
            val element = lenientJson.parseToJsonElement(rawJson)
            if (element is kotlinx.serialization.json.JsonObject &&
                element["format"]?.let {
                    try {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content == BackupEnvelope.FORMAT
                    } catch (_: Exception) {
                        false
                    }
                } == true &&
                "backup" in element
            ) {
                val envelope = lenientJson.decodeFromString<BackupEnvelope>(rawJson)
                val checksum = envelope.checksum.lowercase()
                return if (checksum.isNotBlank() && checksumOf(envelope.backup) == checksum) {
                    Unwrapped.Valid(envelope.backup)
                } else {
                    Unwrapped.Corrupt("Backup checksum mismatch — file is corrupted or tampered")
                }
            }
        } catch (_: Exception) {
            // Not parseable as an envelope — try legacy below.
        }
        // v1 bare backup.
        return try {
            Unwrapped.Legacy(lenientJson.decodeFromString<BackupData>(rawJson))
        } catch (e: Exception) {
            Unwrapped.Corrupt("Unrecognized backup format: ${e.message}")
        }
    }
}
