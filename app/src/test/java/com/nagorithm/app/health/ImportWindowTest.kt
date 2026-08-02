package com.nagorithm.app.health

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ImportWindowTest {

    private val now: Instant = Instant.parse("2026-08-02T12:00:00Z")

    private fun daysAgo(days: Long): Instant = now.minus(days, ChronoUnit.DAYS)

    /** Asking for a year without the permission returns the same 30 days, slowly. */
    @Test
    fun `without history access the window stays at a month`() {
        val from = importWindowStart(importedFrom = null, now = now, canReadHistory = false)

        assertEquals(daysAgo(ROUTINE_SYNC_DAYS), from)
    }

    @Test
    fun `the first sync after history is granted goes all the way back`() {
        val from = importWindowStart(importedFrom = null, now = now, canReadHistory = true)

        assertEquals(daysAgo(DEEP_IMPORT_DAYS), from)
    }

    /**
     * The one that matters for battery: four syncs a day must not each re-read a year of
     * heart-rate samples to discover there is nothing new down there.
     */
    @Test
    fun `once the deep read is done the rest are shallow`() {
        val from = importWindowStart(
            importedFrom = daysAgo(DEEP_IMPORT_DAYS),
            now = now,
            canReadHistory = true,
        )

        assertEquals(daysAgo(ROUTINE_SYNC_DAYS), from)
    }

    /** A watermark left by a shallow sync is not evidence that the deep one ever ran. */
    @Test
    fun `a shallow watermark still triggers the deep read`() {
        val from = importWindowStart(
            importedFrom = daysAgo(ROUTINE_SYNC_DAYS),
            now = now,
            canReadHistory = true,
        )

        assertEquals(daysAgo(DEEP_IMPORT_DAYS), from)
    }

    /**
     * Tomorrow's deep boundary is a day later than today's, and that must not read as a gap
     * needing another year-long fetch every single morning.
     */
    @Test
    fun `the window does not reopen as the boundary drifts forward`() {
        val importedFrom = daysAgo(DEEP_IMPORT_DAYS)
        val tomorrow = now.plus(1, ChronoUnit.DAYS)

        val from = importWindowStart(importedFrom, tomorrow, canReadHistory = true)

        assertEquals(tomorrow.minus(ROUTINE_SYNC_DAYS, ChronoUnit.DAYS), from)
    }
}
