package com.emptycastle.novery.data.remote.cloudflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-02.1: challenge classifier. Pure JVM test with fixture bodies.
 */
class CloudflareChallengeDetectorTest {

    private val classicIuam = """
        <html><head><title>Just a moment...</title></head>
        <body><div id="cf-browser-verification">Checking your browser before you access the site.</div>
        <script src="/cdn-cgi/challenge-platform/h/g/scripts/jsd/main.js"></script></body></html>
    """.trimIndent()

    private val turnstilePage = """
        <html><body><form><div class="cf-turnstile" data-sitekey="abc"></div></form>
        <p>Verify you are human</p>
        <script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script></body></html>
    """.trimIndent()

    private val novelChapter = """
        <html><body><p>"Just a moment," she said, checking the door behind her.
        It would only take a moment before they could leave.</p></body></html>
    """.trimIndent()

    private val nginx403 = """
        <html><head><title>403 Forbidden</title></head>
        <body><center><h1>403 Forbidden</h1></center><hr><center>nginx</center></body></html>
    """.trimIndent()

    @Test
    fun classicChallenge_403_detectedAsManaged() {
        assertEquals(
            ChallengeKind.MANAGED_CHALLENGE,
            CloudflareChallengeDetector.detect(403, classicIuam)
        )
    }

    @Test
    fun classicChallenge_503_detectedAsManaged() {
        assertEquals(
            ChallengeKind.MANAGED_CHALLENGE,
            CloudflareChallengeDetector.detect(503, classicIuam)
        )
    }

    @Test
    fun turnstilePage_detectedAsInteractive() {
        assertEquals(
            ChallengeKind.INTERACTIVE_TURNSTILE,
            CloudflareChallengeDetector.detect(403, turnstilePage)
        )
        assertEquals(
            ChallengeKind.INTERACTIVE_TURNSTILE,
            CloudflareChallengeDetector.detect(503, turnstilePage)
        )
    }

    @Test
    fun novelTextContainingPhrases_200_neverFlagged() {
        assertEquals(
            ChallengeKind.NONE,
            CloudflareChallengeDetector.detect(200, novelChapter)
        )
        // Even a 404 chapter page with such text stays NONE.
        assertEquals(
            ChallengeKind.NONE,
            CloudflareChallengeDetector.detect(404, novelChapter)
        )
    }

    @Test
    fun plainNginx403_withoutCfHeaders_isNone() {
        assertEquals(
            ChallengeKind.NONE,
            CloudflareChallengeDetector.detect(403, nginx403)
        )
    }

    @Test
    fun bare403_withCfHeaders_isHardBlock() {
        val headers = mapOf("Server" to "cloudflare", "cf-ray" to "abc123-XYZ")
        assertEquals(
            ChallengeKind.HARD_BLOCK,
            CloudflareChallengeDetector.detect(403, nginx403, headers)
        )
    }

    @Test
    fun rateLimit_429_alwaysRateLimited() {
        assertEquals(
            ChallengeKind.RATE_LIMITED,
            CloudflareChallengeDetector.detect(429, "")
        )
        assertEquals(
            ChallengeKind.RATE_LIMITED,
            CloudflareChallengeDetector.detect(429, turnstilePage)
        )
    }

    @Test
    fun blankBody_neverFlagged() {
        assertEquals(ChallengeKind.NONE, CloudflareChallengeDetector.detect(403, ""))
        assertEquals(ChallengeKind.NONE, CloudflareChallengeDetector.detect(503, "   "))
    }

    @Test
    fun cloudflareServed_headerSignals() {
        assertTrue(
            CloudflareChallengeDetector.isCloudflareServed(mapOf("cf-mitigated" to "challenge"))
        )
        assertTrue(
            CloudflareChallengeDetector.isCloudflareServed(mapOf("CF-Ray" to "xyz"))
        )
        assertTrue(
            CloudflareChallengeDetector.isCloudflareServed(mapOf("server" to "CLOUDFLARE"))
        )
        assertFalse(
            CloudflareChallengeDetector.isCloudflareServed(mapOf("server" to "nginx"))
        )
        assertFalse(CloudflareChallengeDetector.isCloudflareServed(emptyMap()))
    }

    @Test
    fun retryAfter_parsedCaseInsensitively() {
        assertEquals(
            120L,
            CloudflareChallengeDetector.retryAfterSeconds(mapOf("Retry-After" to "120"))
        )
        assertNull(CloudflareChallengeDetector.retryAfterSeconds(emptyMap()))
        assertNull(CloudflareChallengeDetector.retryAfterSeconds(mapOf("Retry-After" to "soon")))
    }
}
