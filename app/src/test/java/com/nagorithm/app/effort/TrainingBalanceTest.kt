package com.nagorithm.app.effort

import com.nagorithm.app.data.LoadScore
import com.nagorithm.app.data.StoredActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TrainingBalanceTest {

    private val now: Instant = Instant.parse("2026-08-02T12:00:00Z")

    private fun session(daysAgo: Long, load: Double, measured: Boolean = true): StoredActivity {
        val start = now.minus(daysAgo, ChronoUnit.DAYS)
        return StoredActivity(
            recordId = "r-$daysAgo-$load",
            userId = "local",
            startTimeEpochMillis = start.toEpochMilli(),
            endTimeEpochMillis = start.plus(1, ChronoUnit.HOURS).toEpochMilli(),
            zoneOffsetSeconds = 0,
            title = null,
            exerciseType = 79,
            isIndoor = false,
            activeDurationSeconds = 3_600,
            distanceMeters = 20_000.0,
            averageHeartRateBpm = 140,
            sourcePackage = "test",
            firstSeenEpochMillis = start.toEpochMilli(),
            hrSampleCount = 100,
            load = LoadScore(
                hrss = load,
                model = if (measured) "HRSS" else "ESTIMATED",
                restingHr = 45,
                maxHr = 180,
                thresholdHr = 150,
            ),
        )
    }

    /** Both averages chase the same daily figure, so a machine-like routine ends up level. */
    @Test
    fun `a long unchanging routine settles with form near zero`() {
        val activities = (0L..200L).map { session(it, 50.0) }

        val balance = trainingBalance(activities, now)!!

        assertEquals(50.0, balance.fitness, 2.0)
        assertEquals(50.0, balance.fatigue, 2.0)
        assertTrue(kotlin.math.abs(balance.formPercent!!) < 5.0)
        assertEquals(LoadReading.STEADY, balance.reading)
    }

    /** Fatigue drains in days and fitness barely moves, which is the whole point of a taper. */
    @Test
    fun `resting after a long block turns form positive`() {
        val built = (10L..200L).map { session(it, 60.0) }

        val balance = trainingBalance(built, now)!!

        assertTrue("form was ${balance.formPercent}", balance.formPercent!! > 15.0)
        assertEquals(LoadReading.FADING, balance.reading)
    }

    /** The seven-day average reacts six times faster, so a sudden block buries form. */
    @Test
    fun `a hard block on a thin base reads as too much`() {
        val base = (30L..200L).map { session(it, 20.0) }
        val block = (0L..13L).map { session(it, 130.0) }

        val balance = trainingBalance(base + block, now)!!

        assertTrue("form was ${balance.formPercent}", balance.formPercent!! < -30.0)
        assertEquals(LoadReading.TOO_MUCH, balance.reading)
    }

    /**
     * The failure this guards against is the app calling a beginner overcooked because a 42-day
     * average has not had 42 days to stop being zero.
     */
    @Test
    fun `a short history is flagged as unsettled`() {
        val activities = (0L..20L).map { session(it, 50.0) }

        val balance = trainingBalance(activities, now)!!

        assertFalse(balance.isSettled)
        assertTrue(balance.daysOfHistory < TrainingBalance.SETTLING_DAYS)
    }

    /**
     * The regression this exists for: 23 days of history produced "form −296% of fitness" and a
     * verdict telling a man who had gone for a bike ride that he was overreaching. An uncalibrated
     * average must decline to answer rather than answer loudly.
     */
    @Test
    fun `an unsettled average refuses to produce a number`() {
        val activities = (0L..22L).map { session(it, 60.0) }

        val balance = trainingBalance(activities, now)!!

        assertFalse(balance.isUsable)
        assertEquals(null, balance.formPercent)
        assertEquals(null, balance.reading)
        assertFalse(balance.isOverreaching)
    }

    /** Dividing by a fitness of two makes an ordinary week look like a medical emergency. */
    @Test
    fun `a settled but tiny fitness still refuses`() {
        val activities = (0L..200L step 20).map { session(it, 25.0) }

        val balance = trainingBalance(activities, now)!!

        assertTrue(balance.isSettled)
        assertTrue("fitness was ${balance.fitness}", balance.fitness < TrainingBalance.MINIMUM_FITNESS)
        assertEquals(null, balance.reading)
    }

    /** Rest days have to decay fatigue. Skipping them would leave it permanently elevated. */
    @Test
    fun `gaps between sessions count as zeros`() {
        val everyDay = (0L..60L).map { session(it, 30.0) }
        val everyThirdDay = (0L..60L step 3).map { session(it, 30.0) }

        val dense = trainingBalance(everyDay, now)!!
        val sparse = trainingBalance(everyThirdDay, now)!!

        assertTrue(sparse.fatigue < dense.fatigue)
        assertTrue(sparse.fitness < dense.fitness)
    }

    @Test
    fun `estimated load is reported as a share of the recent total`() {
        val activities = (0L..27L).map { session(it, 40.0, measured = it % 2 == 0L) }

        val balance = trainingBalance(activities, now)!!

        assertEquals(0.5, balance.estimatedShare, 0.05)
    }

    @Test
    fun `nothing scored means no balance at all`() {
        assertEquals(null, trainingBalance(emptyList(), now))
    }

    @Test
    fun `acwr lands on the same scale so the two can be compared`() {
        assertEquals(LoadReading.FADING, acwrReading(0.6))
        assertEquals(LoadReading.STEADY, acwrReading(1.0))
        assertEquals(LoadReading.BUILDING, acwrReading(1.4))
        assertEquals(LoadReading.TOO_MUCH, acwrReading(1.8))
    }
}
