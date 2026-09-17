package com.emptycastle.novery.data.remote

import com.emptycastle.novery.data.remote.cloudflare.ChallengeKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-02.3: auto-solve retry gate. Pure JVM test.
 */
class NetworkClientSolveGateTest {

    @Test
    fun managedChallenge_firstAttempt_enabledForeground_solves() {
        assertTrue(
            NetworkClient.shouldAttemptSolve(
                kind = ChallengeKind.MANAGED_CHALLENGE,
                depth = 0,
                flagEnabled = true,
                foreground = true
            )
        )
    }

    @Test
    fun retryDepth_neverSolvesAgain() {
        assertFalse(
            NetworkClient.shouldAttemptSolve(
                kind = ChallengeKind.MANAGED_CHALLENGE,
                depth = 1,
                flagEnabled = true,
                foreground = true
            )
        )
    }

    @Test
    fun flagOff_neverSolves() {
        for (kind in ChallengeKind.entries) {
            assertFalse(
                "kind=$kind",
                NetworkClient.shouldAttemptSolve(kind, 0, false, true)
            )
        }
    }

    @Test
    fun background_neverSolves() {
        assertFalse(
            NetworkClient.shouldAttemptSolve(
                ChallengeKind.MANAGED_CHALLENGE, 0, true, false
            )
        )
    }

    @Test
    fun onlyManagedChallenges_solve() {
        assertFalse(
            NetworkClient.shouldAttemptSolve(ChallengeKind.INTERACTIVE_TURNSTILE, 0, true, true)
        )
        assertFalse(
            NetworkClient.shouldAttemptSolve(ChallengeKind.HARD_BLOCK, 0, true, true)
        )
        assertFalse(
            NetworkClient.shouldAttemptSolve(ChallengeKind.RATE_LIMITED, 0, true, true)
        )
        assertFalse(
            NetworkClient.shouldAttemptSolve(ChallengeKind.NONE, 0, true, true)
        )
    }
}
