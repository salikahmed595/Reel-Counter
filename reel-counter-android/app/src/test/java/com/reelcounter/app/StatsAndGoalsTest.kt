package com.reelcounter.app

import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.Retention
import com.reelcounter.app.data.Comparison
import com.reelcounter.app.data.Coverage
import com.reelcounter.app.data.EstimateMath
import com.reelcounter.app.data.StatsCalculator
import com.reelcounter.app.data.Streak
import com.reelcounter.app.data.db.DayPlatformCount
import com.reelcounter.app.data.db.ObservationWindowEntity
import com.reelcounter.app.data.prefs.UserPrefs
import com.reelcounter.app.reminders.GoalThresholdEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsAndGoalsTest {
    private val zone = ZoneId.of("Asia/Karachi")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = ZonedDateTime.of(2026, 10, 14, 18, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun fullWindow(date: LocalDate) = ObservationWindowEntity(
        date.toString(), Days.startOfDayMs(date, zone), minOf(Days.endOfDayMs(date, zone), now), minOf(Days.endOfDayMs(date, zone), now),
    )

    @Test
    fun ac11_midnightRolloverUsesEventZoneDay() {
        val beforeMidnight = ZonedDateTime.of(2026, 10, 13, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val afterMidnight = ZonedDateTime.of(2026, 10, 14, 0, 1, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2026-10-13", Days.key(beforeMidnight, zone))
        assertEquals("2026-10-14", Days.key(afterMidnight, zone))
    }

    @Test
    fun ac12_retentionCutoffIsRolling30x24h() {
        assertEquals(now - 30L * 24 * 3600 * 1000, Retention.cutoff(now))
    }

    @Test
    fun unobservedDaysAreNotFabricatedAsZero() {
        val days = StatsCalculator.buildDays(today, zone, now, emptyList(), emptyList(), emptyList(), listOf(fullWindow(today)))
        assertEquals(30, days.size)
        assertEquals(Coverage.FULL, days.last().coverage)
        assertEquals(Coverage.NONE, days[days.size - 2].coverage)
    }

    @Test
    fun deltaVsYesterdayHandlesZeroAndMissing() {
        assertEquals(Comparison.Unavailable, StatsCalculator.compare(10, null, false))
        assertEquals(Comparison.Unavailable, StatsCalculator.compare(10, 5, false))
        assertEquals(Comparison.NoComparableData, StatsCalculator.compare(10, 0, true))
        val d = StatsCalculator.compare(80, 100, true) as Comparison.Delta
        assertEquals(-0.2, d.ratio, 1e-9)
    }

    @Test
    fun streakCountsOnlyObservedUnderGoalDays() {
        val windows = (0..5).map { fullWindow(today.minusDays(it.toLong())) }
        val counts = (1..5).map { DayPlatformCount(today.minusDays(it.toLong()).toString(), "instagram", if (it == 4) 300 else 50) }
        val days = StatsCalculator.buildDays(today, zone, now, counts, emptyList(), emptyList(), windows)
        assertEquals(Streak.Days(3), StatsCalculator.underLimitStreak(days, 200))
    }

    @Test
    fun streakIsUnverifiableWhenYesterdayUnobserved() {
        val days = StatsCalculator.buildDays(today, zone, now, emptyList(), emptyList(), emptyList(), listOf(fullWindow(today)))
        assertEquals(Streak.Unverifiable, StatsCalculator.underLimitStreak(days, 200))
        assertEquals(Streak.NoGoal, StatsCalculator.underLimitStreak(days, null))
    }

    @Test
    fun ac10_thresholdsCrossAtExactPercentages() {
        val prefs = UserPrefs(combinedGoal = 100, platformGoalEnabled = Platform.entries.associateWith { false })
        assertTrue(GoalThresholdEvaluator.crossings(prefs, mapOf(Platform.INSTAGRAM to 49)).isEmpty())
        val at50 = GoalThresholdEvaluator.crossings(prefs, mapOf(Platform.INSTAGRAM to 50))
        assertEquals(listOf(50), at50.map { it.percent })
        val at100 = GoalThresholdEvaluator.crossings(prefs, mapOf(Platform.INSTAGRAM to 60, Platform.TIKTOK to 40))
        assertEquals(listOf(50, 75, 100), at100.map { it.percent })
    }

    @Test
    fun combinedAndPlatformGoalsTriggerIndependently() {
        val prefs = UserPrefs(combinedGoal = 200)
        val c = GoalThresholdEvaluator.crossings(prefs, mapOf(Platform.TIKTOK to 30))
        assertTrue(c.all { it.platform == Platform.TIKTOK })
        assertEquals(listOf(50, 75, 100), c.map { it.percent })
    }

    @Test
    fun disabledThresholdsDoNotFire() {
        val prefs = UserPrefs(combinedGoal = 100, threshold50 = false, threshold75 = false, platformGoalEnabled = Platform.entries.associateWith { false })
        assertEquals(listOf(100), GoalThresholdEvaluator.crossings(prefs, mapOf(Platform.YOUTUBE to 120)).map { it.percent })
    }

    @Test
    fun quietHoursWrapAroundMidnight() {
        val prefs = UserPrefs(quietHoursEnabled = true, quietStartMinutes = 22 * 60, quietEndMinutes = 8 * 60)
        fun at(h: Int) = ZonedDateTime.of(2026, 10, 14, h, 30, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(GoalThresholdEvaluator.inQuietHours(prefs, at(23), zone))
        assertTrue(GoalThresholdEvaluator.inQuietHours(prefs, at(3), zone))
        assertTrue(!GoalThresholdEvaluator.inQuietHours(prefs, at(12), zone))
        assertTrue(!GoalThresholdEvaluator.inQuietHours(prefs.copy(quietHoursEnabled = false), at(23), zone))
    }

    @Test
    fun ac09_estimateUnavailableWithoutEvidence() {
        assertNull(EstimateMath.estimate(10 * 60_000L, emptyList()))
        assertNull(EstimateMath.estimate(10 * 60_000L, listOf(60_000L to 5, 60_000L to 5, 60_000L to 5)))
    }

    @Test
    fun estimateUsesRecentConfirmedRateWithRange() {
        val calib = listOf(5 * 60_000L to 20, 5 * 60_000L to 30, 5 * 60_000L to 25)
        val r = EstimateMath.estimate(4 * 60_000L, calib)
        assertNotNull(r)
        assertEquals(20.0, r!!.value, 1e-9)
        assertEquals(16.0, r.lower, 1e-9)
        assertEquals(24.0, r.upper, 1e-9)
    }

    @Test
    fun goalProgressNeverIncludesEstimates() {
        assertEquals(0.5f, StatsCalculator.goalProgress(100, 200)!!, 1e-6f)
        assertNull(StatsCalculator.goalProgress(100, null))
    }
}
