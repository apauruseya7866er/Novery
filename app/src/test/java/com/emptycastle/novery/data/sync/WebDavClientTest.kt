package com.emptycastle.novery.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-06.3: WebDAV pure helpers. JVM test (no network).
 */
class WebDavClientTest {

    @Test
    fun joinUrl_exactlyOneSlash() {
        assertEquals(
            "https://x/dav/novery-sync.novery",
            WebDavClient.joinUrl("https://x/dav/", "novery-sync.novery")
        )
        assertEquals(
            "https://x/dav/novery-sync.novery",
            WebDavClient.joinUrl("https://x/dav", "/novery-sync.novery")
        )
        assertEquals(
            "https://x/dav/novery-sync.novery",
            WebDavClient.joinUrl("  https://x/dav/  ", "novery-sync.novery")
        )
    }

    @Test
    fun basicAuth_startsWithBasic() {
        val header = WebDavClient.basicAuth("user", "pass")
        assertTrue(header.startsWith("Basic "))
    }

    @Test
    fun meta_roundtrip() {
        val meta = SyncMeta(deviceId = "d1", updatedAt = 123L, appVersion = "1.0")
        assertEquals(meta, WebDavClient.decodeMeta(WebDavClient.encodeMeta(meta)))
    }

    @Test
    fun config_completeness() {
        assertTrue(
            WebDavClient.Config("https://x/d", "u", "").isComplete()
        )
        assertTrue(
            !WebDavClient.Config("", "u", "p").isComplete()
        )
        assertTrue(
            !WebDavClient.Config("https://x/d", "", "").isComplete()
        )
    }
}
