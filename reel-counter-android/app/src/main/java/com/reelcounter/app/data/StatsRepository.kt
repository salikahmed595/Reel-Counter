package com.reelcounter.app.data

import com.reelcounter.app.core.Clock
import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Retention
import com.reelcounter.app.data.db.AppDatabase
import com.reelcounter.app.data.db.TransitionInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class StatsRepository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.activityDao()

    /** Emits once a minute so the day rollover and rolling cutoff advance without user action. */
    private val minuteTicker: Flow<Long> = flow {
        while (true) {
            emit(clock.nowMs() / 60_000L)
            delay(60_000L - clock.nowMs() % 60_000L)
        }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeDays(): Flow<List<DayStats>> = minuteTicker.flatMapLatest { minute ->
        val now = minute * 60_000L
        val cutoff = Retention.cutoff(now)
        combine(
            dao.observeDailyCounts(cutoff),
            dao.observeSessions(cutoff),
            dao.observeEstimates(cutoff),
            dao.observeWindows(cutoff),
        ) { counts, sessions, estimates, windows ->
            val n = clock.nowMs()
            StatsCalculator.buildDays(Days.today(clock), clock.zone(), n, counts, sessions, estimates, windows)
        }
    }.flowOn(Dispatchers.Default)

    /** Raw transition instants (platform + time only) for hourly histograms. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeTransitionsSince(daysBack: Int): Flow<List<TransitionInstant>> = minuteTicker
        .map { Days.today(clock).minusDays(daysBack.toLong()) }
        .distinctUntilChanged()
        .flatMapLatest { from ->
            val now = clock.nowMs()
            dao.observeTransitionsSince(Days.startOfDayMs(from, clock.zone()), Retention.cutoff(now))
        }

    /** One-shot snapshot used by widgets and notification evaluation. */
    suspend fun daysSnapshot(): List<DayStats> {
        val now = clock.nowMs()
        val cutoff = Retention.cutoff(now)
        return StatsCalculator.buildDays(
            Days.today(clock), clock.zone(), now,
            dao.dailyCounts(cutoff),
            dao.sessions(cutoff),
            dao.estimates(cutoff),
            dao.windows(cutoff),
        )
    }
}
