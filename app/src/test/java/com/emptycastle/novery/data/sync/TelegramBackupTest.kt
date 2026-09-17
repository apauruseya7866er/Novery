package com.emptycastle.novery.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-06.4: Telegram helpers. Pure JVM test (no network).
 */
class TelegramBackupTest {

    @Test
    fun apiBase_buildsAndGuardsBlank() {
        assertEquals(
            "https://api.telegram.org/bot123:ABC",
            TelegramBackup.apiBase("  123:ABC  ")
        )
        assertEquals("", TelegramBackup.apiBase("   "))
    }

    @Test
    fun parseBotUsername_okAndBad() {
        assertEquals(
            "novery_bot",
            TelegramBackup.parseBotUsername(
                """{"ok":true,"result":{"id":1,"username":"novery_bot"}}"""
            )
        )
        assertNull(
            TelegramBackup.parseBotUsername("""{"ok":false,"description":"Unauthorized"}""")
        )
        assertNull(TelegramBackup.parseBotUsername("not json"))
    }

    @Test
    fun parseFileId_okAndBad() {
        assertEquals(
            "FILE123",
            TelegramBackup.parseFileId(
                """{"ok":true,"result":{"document":{"file_id":"FILE123"}}}"""
            )
        )
        assertNull(
            TelegramBackup.parseFileId("""{"ok":true,"result":{}}""")
        )
        assertNull(TelegramBackup.parseFileId("{{{"))
    }

    @Test
    fun errorMessage_maps401() {
        val msg = TelegramBackup.errorMessage(
            401, """{"ok":false,"description":"Unauthorized"}"""
        )
        assertTrue(msg.contains("Unauthorized"))
        assertTrue(msg.contains("token"))
    }
}
