package com.reelcounter.app.data

import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.db.DayPlatformCount
import com.reelcounter.app.data.db.EstimateBucketEntity
import com.reelcounter.app.data.db.ObservationWindowEntity
import com.reelcounter.app.data.db.SessionStatus
import com.reelcounter.app.data.db.TrackingSessionEntity
import java.time.LocalDate
import java.time.ZoneId

enum class Coverage { FULL, PARTIAL, NONE }

data class EstimateRange(val value: Double, val low: Double?, val high: Double?) {
    operator fun plus(o: EstimateRange) = EstimateRange(
        value + o.value,
        if (low != null && o.low != null) low + o.low else null,
        if (high != null && o.high != null) high + o.high else null,
    )
}

data class DayStats(
    val date: LocalDate,
    val detected: Map<Platform, Int>,
    val coverage: Coverage,
    val coverageFraction: Float,
    val feedMs: Map<Platform, Long>,
    val feedIncomplete: Boolean,
    val estimates: Map<Platform, EstimateRange>,
    val sessions: Map<Platform, Int>,
) {
    val total: Int get() = detected.values.sum()
    fun detected(filter: Platform?): Int = if (filter == null) total else detected[filter] ?: 0
    fun feedMs(filter: Platform?): Long = if (filter == null) feedMs.values.sum() else feedMs[filter] ?: 0
    fun estimate(filter: Platform?): EstimateRange? =
        if (filter == null) estimates.values.reduceOrNull { a, b -> a + b } else estimates[filter]
}

sealed interface Comparison {
    /** Relative change, e.g. -0.12 for 12% fewer. */
    data class Delta(val ratio: Double) : Comparison
    data object NoComparableData : Comparison
    data object Unavailable : Comparison
}

sealed interface Streak {
    data class Days(val count: Int) : Streak
    data object Unverifiable : Streak
    data object NoGoal : Streak
}

object StatsCalculator {
    const val DAYS_IN_WINDOW = 30

    /** Builds one entry per local date for today and the 29 previous dates, oldest first. */
    fun buildDays(
        today: LocalDate,
        zone: ZoneId,
        nowMs: Long,
        counts: List<DayPlatformCount>,
        sessions: List<TrackingSessionEntity>,
        estimates: List<EstimateBucketEntity>,
        windows: List<ObservationWindowEntity>,
    ): List<DayStats> {
        val countsByDay = counts.groupBy { it.localDay }
        val estByDay = estimates.groupBy { it.localDay }
        return (DAYS_IN_WINDOW - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val key = Days.key(date)
            val dayStart = Days.startOfDayMs(date, zone)
            val dayEnd = minOf(Days.endOfDayMs(date, zone), nowMs)
            val detected = countsByDay[key].orEmpty()
                .mapNotNull { c -> Platform.fromKey(c.platform)?.let { it to c.count } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }

            val feed = HashMap<Platform, Long>()
            val sessionCount = HashMap<Platform, Int>()
            var incomplete = false
            for (s in sessions) {
                val p = Platform.fromKey(s.platform) ?: continue
                val end = s.endedAtUtcMs ?: s.lastActivityAtUtcMs
                val overlap = overlap(s.startedAtUtcMs, end, dayStart, dayEnd)
                if (overlap <= 0 && !(s.startedAtUtcMs in dayStart until dayEnd)) continue
                feed[p] = (feed[p] ?: 0) + overlap
                if (s.startedAtUtcMs in dayStart until dayEnd) sessionCount[p] = (sessionCount[p] ?: 0) + 1
                if (s.status != SessionStatus.OBSERVED) incomplete = true
            }

            val est = estByDay[key].orEmpty()
                .mapNotNull { e -> Platform.fromKey(e.platform)?.let { it to EstimateRange(e.estimateValue, e.lowerBound, e.upperBound) } }
                .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.reduce { a, b -> a + b } }

            val elapsed = (dayEnd - dayStart).coerceAtLeast(1)
            val covered = windows.sumOf { overlap(it.startedAtUtcMs, it.endedAtUtcMs ?: it.lastSeenAtUtcMs, dayStart, dayEnd) }
            val fraction = (covered.toDouble() / elapsed).toFloat().coerceIn(0f, 1f)
            val coverage = when {
                fraction >= 0.75f -> Coverage.FULL
                fraction > 0.01f || detected.isNotEmpty() -> Coverage.PARTIAL
                else -> Coverage.NONE
            }
            DayStats(date, detected, coverage, fraction, feed, incomplete, est, sessionCount)
        }
    }

    fun overlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long =
        (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0)

    /** deltaVsYesterday = (today − yesterday) / yesterday, with the PRD's zero/missing handling. */
    fun compare(current: Int, previous: Int?, previousCovered: Boolean): Comparison = when {
        previous == null || !previousCovered -> Comparison.Unavailable
        previous == 0 -> Comparison.NoComparableData
        else -> Comparison.Delta((current - previous).toDouble() / previous)
    }

    /**
     * Consecutive sufficiently observed days (before today) under goal, within retention.
     * Unobserved days end the streak and are never rewarded as zero (PRD §8).
     */
    fun underLimitStreak(days: List<DayStats>, goal: Int?, filter: Platform? = null): Streak {
        if (goal == null) return Streak.NoGoal
        val past = days.dropLast(1).asReversed()
        var n = 0
        for (d in past) {
            if (d.coverage != Coverage.FULL) return if (n == 0) Streak.Unverifiable else Streak.Days(n)
            if (d.detected(filter) >= goal) break
            n++
        }
        return Streak.Days(n)
    }

    /** Average transitions per observed feed minute; null when duration quality is inadequate. */
    fun sessionRate(transitions: Int, feedMs: Long): Double? =
        if (feedMs < 5 * 60_000L || transitions == 0) null else transitions / (feedMs / 60_000.0)

    fun goalProgress(count: Int, goal: Int?): Float? = goal?.let { if (it <= 0) null else count.toFloat() / it }
}
