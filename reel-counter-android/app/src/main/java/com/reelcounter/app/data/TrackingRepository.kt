package com.reelcounter.app.data

import com.reelcounter.app.core.Clock
import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.Retention
import com.reelcounter.app.data.db.AppDatabase
import com.reelcounter.app.data.db.ConfirmedTransitionEntity
import com.reelcounter.app.data.db.EstimateBucketEntity
import com.reelcounter.app.data.db.ObservationWindowEntity
import com.reelcounter.app.data.db.SessionStatus
import com.reelcounter.app.data.db.TrackingSessionEntity
import com.reelcounter.app.tracking.engine.TransitionEngine
import com.reelcounter.app.tracking.engine.TransitionEngine.Output
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Writes engine outputs durably. The widget and UI read from here; they never count. */
class TrackingRepository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.activityDao()

    /** @return number of newly stored confirmed transitions (duplicates ignored via dedupeKey). */
    suspend fun persist(outputs: List<Output>): Int {
        var inserted = 0
        for (o in outputs) {
            when (o) {
                is Output.SessionStarted -> dao.upsertSession(
                    TrackingSessionEntity(o.sessionId, o.platform.key, o.atMs, null, o.atMs, null, SessionStatus.OPEN, 0),
                )
                is Output.SessionHeartbeat -> dao.upsertSession(
                    TrackingSessionEntity(o.sessionId, o.platform.key, o.startedAtMs, null, o.lastActivityMs, null, SessionStatus.OPEN, o.uncertainMs),
                )
                is Output.SessionEnded -> {
                    val s = TrackingSessionEntity(
                        sessionId = o.sessionId,
                        platform = o.platform.key,
                        startedAtUtcMs = o.startedAtMs,
                        endedAtUtcMs = o.endedAtMs,
                        lastActivityAtUtcMs = o.endedAtMs,
                        endReason = o.reason,
                        status = if (o.incomplete) SessionStatus.INCOMPLETE else SessionStatus.OBSERVED,
                        uncertainMs = o.uncertainMs,
                    )
                    dao.upsertSession(s)
                    maybeEstimate(s)
                }
                is Output.Confirmed -> {
                    val zone = clock.zone()
                    val row = ConfirmedTransitionEntity(
                        id = UUID.randomUUID().toString(),
                        platform = o.platform.key,
                        occurredAtUtcMs = o.atMs,
                        zoneIdAtEvent = zone.id,
                        localDay = Days.key(o.atMs, zone),
                        sessionId = o.sessionId,
                        dedupeKey = o.dedupeKey,
                        detectorVersion = TransitionEngine.DETECTOR_VERSION,
                    )
                    if (dao.insertTransition(row) != -1L) inserted++
                }
            }
        }
        return inserted
    }

    /** Sessions left OPEN by a killed process are closed at their last observed activity, marked incomplete. */
    suspend fun closeDanglingSessions() {
        dao.openSessions().forEach {
            dao.upsertSession(it.copy(endedAtUtcMs = it.lastActivityAtUtcMs, endReason = "PROCESS_ENDED", status = SessionStatus.INCOMPLETE))
        }
    }

    /**
     * Bounded, honest estimate for feed time where transitions could not be resolved (PRD §5.6).
     * Calibrated only from recent *confirmed* sessions on the same platform; if evidence is
     * insufficient, nothing is stored ("estimate unavailable").
     */
    private suspend fun maybeEstimate(session: TrackingSessionEntity) {
        if (session.uncertainMs < MIN_UNCERTAIN_MS) return
        val now = clock.nowMs()
        val cutoff = Retention.cutoff(now)
        val calibration = dao.observedSessions(session.platform, now - TimeUnit.DAYS.toMillis(7), cutoff)
            .filter { it.sessionId != session.sessionId }
            .filter {
                val dur = (it.endedAtUtcMs ?: it.lastActivityAtUtcMs) - it.startedAtUtcMs
                dur >= 30_000 && it.uncertainMs < dur * 0.1
            }
        val counts = dao.countsPerSession(cutoff).associate { it.sessionId to it.count }
        val result = EstimateMath.estimate(
            uncertainMs = session.uncertainMs,
            calibration = calibration.map { (it.endedAtUtcMs ?: it.lastActivityAtUtcMs) - it.startedAtUtcMs to (counts[it.sessionId] ?: 0) },
        ) ?: return
        dao.insertEstimate(
            EstimateBucketEntity(
                id = UUID.randomUUID().toString(),
                platform = session.platform,
                localDay = Days.key(session.startedAtUtcMs, clock.zone()),
                intervalStartUtcMs = session.startedAtUtcMs,
                intervalEndUtcMs = session.endedAtUtcMs ?: session.lastActivityAtUtcMs,
                estimateValue = result.value,
                lowerBound = result.lower,
                upperBound = result.upper,
                method = "uncovered_feed_minutes_x_recent_confirmed_rate",
                methodVersion = 1,
                evidenceQuality = result.quality,
            ),
        )
    }

    // Observation coverage windows ------------------------------------------------------------

    suspend fun openCoverageWindow(): String {
        closeStaleWindows()
        val now = clock.nowMs()
        val id = UUID.randomUUID().toString()
        dao.upsertWindow(ObservationWindowEntity(id, now, now, null))
        return id
    }

    suspend fun heartbeatWindow(id: String) {
        val now = clock.nowMs()
        val w = dao.openWindows().firstOrNull { it.id == id } ?: return
        dao.upsertWindow(w.copy(lastSeenAtUtcMs = now))
    }

    suspend fun closeWindow(id: String) {
        val now = clock.nowMs()
        val w = dao.openWindows().firstOrNull { it.id == id } ?: return
        dao.upsertWindow(w.copy(lastSeenAtUtcMs = now, endedAtUtcMs = now))
    }

    private suspend fun closeStaleWindows() {
        dao.openWindows().forEach { dao.upsertWindow(it.copy(endedAtUtcMs = it.lastSeenAtUtcMs)) }
    }

    companion object {
        const val MIN_UNCERTAIN_MS = 60_000L
    }
}

object EstimateMath {
    data class Result(val value: Double, val lower: Double, val upper: Double, val quality: String)

    /**
     * @param calibration list of (observed feed duration ms, confirmed transitions) for recent sessions.
     * @return null when evidence is insufficient (< 3 sessions or < 10 observed minutes).
     */
    fun estimate(uncertainMs: Long, calibration: List<Pair<Long, Int>>): Result? {
        val usable = calibration.filter { it.first > 0 }
        val totalMin = usable.sumOf { it.first } / 60_000.0
        if (usable.size < 3 || totalMin < 10.0) return null
        val rate = usable.sumOf { it.second } / totalMin
        if (rate <= 0.0) return null
        val rates = usable.map { it.second / (it.first / 60_000.0) }
        val minutes = uncertainMs / 60_000.0
        val quality = if (usable.size >= 10 && totalMin >= 30) "MODERATE" else "LOW"
        return Result(minutes * rate, minutes * rates.min(), minutes * rates.max(), quality)
    }
}
