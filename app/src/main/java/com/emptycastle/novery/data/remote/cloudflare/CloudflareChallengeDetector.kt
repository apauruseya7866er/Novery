package com.emptycastle.novery.data.remote.cloudflare

/**
 * Slice-02.1: pure Cloudflare challenge classifier.
 *
 * Novery already stores cf_clearance cookies ([CloudflareManager]) and flags
 * blocked responses in [NetworkClient]. This detector is the shared brain
 * for the unified solver (2.2 headless clearance, 2.3 retry contract):
 * given a status code, body and headers it classifies the response WITHOUT
 * any Android dependencies, so it is fully unit-testable.
 *
 * Deliberately conservative: only 403/503/429 are ever classified, so a
 * novel chapter that literally contains "just a moment" can never trip it.
 */
enum class ChallengeKind {
    /** Normal response (or non-CF error page). */
    NONE,

    /** Classic managed challenge ("checking your browser", IUAM). */
    MANAGED_CHALLENGE,

    /** Interactive Turnstile / "verify you are human" widget. */
    INTERACTIVE_TURNSTILE,

    /** Rate limited — honor Retry-After, do NOT treat as a challenge. */
    RATE_LIMITED,

    /** Cloudflare block page without challenge markers (IP ban, WAF block). */
    HARD_BLOCK
}

object CloudflareChallengeDetector {

    private val MANAGED_MARKERS = listOf(
        "cf-browser-verification",
        "cf_chl_opt",
        "cf_chl_enter",
        "challenge-platform",
        "checking your browser",
        "just a moment",
        "cf-spinner",
        "cf-error-details"
    )

    private val TURNSTILE_MARKERS = listOf(
        "cf-turnstile",
        "challenges.cloudflare.com",
        "verify you are human",
        "cf_turnstile"
    )

    /**
     * @param code HTTP status code.
     * @param body response body (may be truncated; markers are early in CF pages).
     * @param headers response headers, case-insensitive names preferred
     * (looked up case-insensitively here).
     */
    fun detect(
        code: Int,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): ChallengeKind {
        // 429 is always rate limiting, challenge or not.
        if (code == 429) return ChallengeKind.RATE_LIMITED

        // Only challenge statuses are classified — never flag a 200 page,
        // even if its text mentions Cloudflare phrases.
        if (code != 403 && code != 503) return ChallengeKind.NONE
        if (body.isBlank()) return ChallengeKind.NONE

        val lower = body.lowercase()
        val hasTurnstile = TURNSTILE_MARKERS.any { lower.contains(it) }
        if (hasTurnstile) return ChallengeKind.INTERACTIVE_TURNSTILE

        val hasManaged = MANAGED_MARKERS.any { lower.contains(it) }
        if (hasManaged) return ChallengeKind.MANAGED_CHALLENGE

        // No markers: a Cloudflare-served block page (IP/WAF) vs a plain
        // origin 403. Distinguish via CF response headers.
        return if (isCloudflareServed(headers)) {
            ChallengeKind.HARD_BLOCK
        } else {
            ChallengeKind.NONE
        }
    }

    /**
     * True when the response demonstrably came from Cloudflare's edge:
     * cf-mitigated header, cf-ray, or Server: cloudflare.
     */
    fun isCloudflareServed(headers: Map<String, String>): Boolean {
        if (headers.isEmpty()) return false
        val byLower = headers.entries.associate { it.key.lowercase() to it.value }
        if (byLower.containsKey("cf-mitigated")) return true
        if (byLower.containsKey("cf-ray")) return true
        val server = byLower["server"] ?: return false
        return server.lowercase().contains("cloudflare")
    }

    /** Retry-After in seconds when present and parseable, else null. */
    fun retryAfterSeconds(headers: Map<String, String>): Long? {
        val byLower = headers.entries.associate { it.key.lowercase() to it.value }
        val raw = byLower["retry-after"]?.trim() ?: return null
        return raw.toLongOrNull()?.takeIf { it >= 0 }
    }
}
