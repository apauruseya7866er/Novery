package com.emptycastle.novery.data.backup

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-06.1: integrity envelope. Pure JVM test.
 */
class BackupIntegrityTest {

    private fun sample() = BackupData(
        version = 2,
        library = listOf(
            LibraryBackup(
                url = "https://x/n", name = "N", apiName = "P",
                addedAt = 1L, readingStatus = "READING"
            )
        ),
        works = listOf(
            WorkBackup(
                defaultNovelUrl = "https://x/n",
                projections = listOf(WorkProjectionBackup("https://x/n", "P"))
            )
        ),
        textFilters = listOf(TextFilterBackup("^foo$", true, "Foo", true))
    )

    @Test
    fun roundtrip_valid() {
        val raw = JsonForTest.encode(sample())
        when (val out = BackupIntegrity.unwrap(raw)) {
            is BackupIntegrity.Unwrapped.Valid -> {
                assertEquals(1, out.backup.library.size)
                assertEquals(1, out.backup.works.size)
                assertEquals(1, out.backup.textFilters.size)
            }
            else -> throw AssertionError("expected Valid, got $out")
        }
    }

    @Test
    fun tamperedBody_detectedAsCorrupt() {
        val raw = JsonForTest.encode(sample())
        // Flip a byte inside the backup payload (not the checksum).
        val tampered = raw.replace("Shadow", "Shadox").let {
            if (it == raw) raw.replace("https://x/n", "https://x/m") else it
        }
        val out = BackupIntegrity.unwrap(tampered)
        assertTrue("expected Corrupt, got $out", out is BackupIntegrity.Unwrapped.Corrupt)
    }

    @Test
    fun bareV1_decodesAsLegacy() {
        val bare = JsonForTest.encodeBare(sample().copy(version = 1))
        when (val out = BackupIntegrity.unwrap(bare)) {
            is BackupIntegrity.Unwrapped.Legacy ->
                assertEquals(1, out.backup.library.size)
            else -> throw AssertionError("expected Legacy, got $out")
        }
    }

    @Test
    fun garbage_isCorrupt() {
        val out = BackupIntegrity.unwrap("{not json")
        assertTrue(out is BackupIntegrity.Unwrapped.Corrupt)
    }

    @Test
    fun checksum_stable() {
        // Same instance twice: defaults like createdAt must not drift.
        val s = sample()
        val a = BackupIntegrity.checksumOf(s)
        val b = BackupIntegrity.checksumOf(s)
        assertEquals(a, b)
        assertEquals(64, a.length)
    }
}

private object JsonForTest {
    private val pretty = kotlinx.serialization.json.Json {
        prettyPrint = true
        encodeDefaults = true
    }
    private val bare = kotlinx.serialization.json.Json { encodeDefaults = true }

    fun encode(backup: BackupData): String =
        pretty.encodeToString(BackupEnvelope.serializer(), BackupIntegrity.wrap(backup))

    fun encodeBare(backup: BackupData): String =
        bare.encodeToString(BackupData.serializer(), backup)
}
