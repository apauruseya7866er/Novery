package com.emptycastle.novery.data.remote.cloudflare

/**
 * Slice-02.2: per-host failure cooldown for the headless solver.
 *
 * Pure logic (injectable clock) — unit-tested. Mirrors Kototoro's rule:
 * a failed/timeout solve cools the host down briefly so repeated
 * challenges can't spin hot loops or stack verification windows.
 */
class SolveCooldown(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lockedUntil = mutableMapOf<String, Long>()

    /** True when [host] (already normalized) is still cooling down. */
    fun isCooling(host: String): Boolean {
        val until = lockedUntil[host] ?: return false
        if (clock() >= until) {
            lockedUntil.remove(host)
            return false
        }
        return true
    }

    fun remainingMs(host: String): Long {
        val until = lockedUntil[host] ?: return 0L
        return (until - clock()).coerceAtLeast(0L)
    }

    fun recordFailure(host: String) {
        lockedUntil[host] = clock() + cooldownMs
    }

    fun recordSuccess(host: String) {
        lockedUntil.remove(host)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 2 * 60 * 1000L
    }
}
