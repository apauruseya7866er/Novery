package com.emptycastle.novery.data.update

/**
 * Slice-01.4: predicts when a novel will update next from past
 * new-chapter detection timestamps (Komikku-style fetch-interval
 * prediction, adapted to detection events instead of upload dates).
 *
 * Pure logic — covered by unit tests.
 */
object UpdatePredictor {

    const val MIN_POINTS = 2
    const val MAX_POINTS = 10
    const val MIN_GAP_MS = 24L * 60 * 60 * 1000 // 1 day
    const val MAX_GAP_MS = 28L * 24 * 60 * 60 * 1000 // 28 days
    const val STALE_MULTIPLE = 3.0 // prediction dies after 3x the cadence
    const val SOON_BEFORE_MS = 36L * 60 * 60 * 1000 // show badge up to 36h early
    const val SOON_AFTER_MS = 12L * 60 * 60 * 1000 // keep badge 12h when overdue

    /**
     * @param detectedAtMillis detection timestamps in any order.
     * @return predicted next-update epoch millis, or null when there is too
     * little data or the pattern went stale.
     */
    fun predictNextUpdate(
        detectedAtMillis: List<Long>,
        now: Long = System.currentTimeMillis()
    ): Long? {
        val points = detectedAtMillis.sortedDescending().take(MAX_POINTS)
        if (points.size < MIN_POINTS) return null

        val gaps = points.zipWithNext { newer, older -> newer - older }
            .filter { it > 0 }
        if (gaps.isEmpty()) return null

        val medianGap = median(gaps).coerceIn(MIN_GAP_MS.toDouble(), MAX_GAP_MS.toDouble())
        val last = points.first()

        // Stale: no update for 3x the usual cadence — pattern is dead.
        if (now - last > medianGap * STALE_MULTIPLE) return null

        return (last + medianGap).toLong()
    }

    /**
     * Whether the "expected soon" badge should show for a prediction.
     */
    fun isExpectedSoon(nextAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        return now >= nextAt - SOON_BEFORE_MS && now <= nextAt + SOON_AFTER_MS
    }

    private fun median(values: List<Long>): Double {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toDouble()
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
    }
}
