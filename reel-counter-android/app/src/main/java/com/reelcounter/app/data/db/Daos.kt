package com.reelcounter.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class DayPlatformCount(val localDay: String, val platform: String, val count: Int)
data class TransitionInstant(val platform: String, val occurredAtUtcMs: Long, val zoneIdAtEvent: String)
data class SessionTransitionCount(val sessionId: String, val count: Int)

/**
 * Every read takes a `cutoff` so queries never expose activity older than the rolling
 * 30-day window, even before the physical purge job runs (PRD §7, AC12).
 */
@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransition(t: ConfirmedTransitionEntity): Long

    @Query(
        "SELECT localDay, platform, COUNT(*) AS count FROM confirmed_transition " +
            "WHERE occurredAtUtcMs >= :cutoff GROUP BY localDay, platform",
    )
    fun observeDailyCounts(cutoff: Long): Flow<List<DayPlatformCount>>

    @Query(
        "SELECT localDay, platform, COUNT(*) AS count FROM confirmed_transition " +
            "WHERE occurredAtUtcMs >= :cutoff AND localDay = :day GROUP BY localDay, platform",
    )
    suspend fun countsForDay(day: String, cutoff: Long): List<DayPlatformCount>

    @Query(
        "SELECT localDay, platform, COUNT(*) AS count FROM confirmed_transition " +
            "WHERE occurredAtUtcMs >= :cutoff GROUP BY localDay, platform",
    )
    suspend fun dailyCounts(cutoff: Long): List<DayPlatformCount>

    @Query(
        "SELECT platform, occurredAtUtcMs, zoneIdAtEvent FROM confirmed_transition " +
            "WHERE occurredAtUtcMs >= :fromMs AND occurredAtUtcMs >= :cutoff",
    )
    fun observeTransitionsSince(fromMs: Long, cutoff: Long): Flow<List<TransitionInstant>>

    @Query(
        "SELECT sessionId, COUNT(*) AS count FROM confirmed_transition " +
            "WHERE occurredAtUtcMs >= :cutoff GROUP BY sessionId",
    )
    suspend fun countsPerSession(cutoff: Long): List<SessionTransitionCount>

    @Query("SELECT MAX(occurredAtUtcMs) FROM confirmed_transition WHERE platform = :platform AND occurredAtUtcMs >= :cutoff")
    fun observeLastEventAt(platform: String, cutoff: Long): Flow<Long?>

    // Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(s: TrackingSessionEntity)

    @Query("SELECT * FROM tracking_session WHERE sessionId = :id")
    suspend fun session(id: String): TrackingSessionEntity?

    @Query("SELECT * FROM tracking_session WHERE status = 'OPEN'")
    suspend fun openSessions(): List<TrackingSessionEntity>

    @Query("SELECT * FROM tracking_session WHERE startedAtUtcMs >= :cutoff ORDER BY startedAtUtcMs")
    fun observeSessions(cutoff: Long): Flow<List<TrackingSessionEntity>>

    @Query("SELECT * FROM tracking_session WHERE startedAtUtcMs >= :cutoff ORDER BY startedAtUtcMs")
    suspend fun sessions(cutoff: Long): List<TrackingSessionEntity>

    @Query(
        "SELECT * FROM tracking_session WHERE platform = :platform AND status = 'OBSERVED' " +
            "AND startedAtUtcMs >= :fromMs AND startedAtUtcMs >= :cutoff",
    )
    suspend fun observedSessions(platform: String, fromMs: Long, cutoff: Long): List<TrackingSessionEntity>

    // Estimates
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEstimate(e: EstimateBucketEntity)

    @Query("SELECT * FROM estimate_bucket WHERE intervalStartUtcMs >= :cutoff")
    fun observeEstimates(cutoff: Long): Flow<List<EstimateBucketEntity>>

    @Query("SELECT * FROM estimate_bucket WHERE intervalStartUtcMs >= :cutoff")
    suspend fun estimates(cutoff: Long): List<EstimateBucketEntity>

    @Query("SELECT * FROM estimate_bucket WHERE intervalStartUtcMs >= :cutoff AND localDay = :day")
    suspend fun estimatesForDay(day: String, cutoff: Long): List<EstimateBucketEntity>

    // Observation coverage
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWindow(w: ObservationWindowEntity)

    @Query("SELECT * FROM observation_window WHERE endedAtUtcMs IS NULL")
    suspend fun openWindows(): List<ObservationWindowEntity>

    @Query("SELECT * FROM observation_window WHERE lastSeenAtUtcMs >= :cutoff ORDER BY startedAtUtcMs")
    fun observeWindows(cutoff: Long): Flow<List<ObservationWindowEntity>>

    @Query("SELECT * FROM observation_window WHERE lastSeenAtUtcMs >= :cutoff ORDER BY startedAtUtcMs")
    suspend fun windows(cutoff: Long): List<ObservationWindowEntity>
}

@Dao
interface GoalDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(d: GoalThresholdDeliveryEntity): Long

    @Query("SELECT COUNT(*) FROM goal_threshold_delivery WHERE localDay = :day AND deliveryState = 'SENT'")
    suspend fun sentCountForDay(day: String): Int
}

@Dao
abstract class MaintenanceDao {
    @Query("DELETE FROM confirmed_transition") abstract suspend fun t()
    @Query("DELETE FROM tracking_session") abstract suspend fun s()
    @Query("DELETE FROM estimate_bucket") abstract suspend fun e()
    @Query("DELETE FROM observation_window") abstract suspend fun w()
    @Query("DELETE FROM goal_threshold_delivery") abstract suspend fun g()

    /** Immediate user-requested purge in one transaction (PRD §7). */
    @Transaction
    open suspend fun deleteAllActivity() {
        t(); s(); e(); w(); g()
    }

    @Query("DELETE FROM confirmed_transition WHERE occurredAtUtcMs < :cutoff") abstract suspend fun pt(cutoff: Long)
    @Query("DELETE FROM tracking_session WHERE COALESCE(endedAtUtcMs, lastActivityAtUtcMs) < :cutoff") abstract suspend fun ps(cutoff: Long)
    @Query("DELETE FROM estimate_bucket WHERE intervalEndUtcMs < :cutoff") abstract suspend fun pe(cutoff: Long)
    @Query("DELETE FROM observation_window WHERE lastSeenAtUtcMs < :cutoff") abstract suspend fun pw(cutoff: Long)
    @Query("DELETE FROM goal_threshold_delivery WHERE sentAtUtcMs < :cutoff") abstract suspend fun pg(cutoff: Long)

    @Transaction
    open suspend fun purgeOlderThan(cutoff: Long) {
        pt(cutoff); ps(cutoff); pe(cutoff); pw(cutoff); pg(cutoff)
    }
}
