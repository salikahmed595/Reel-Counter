package com.reelcounter.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One confirmed transition to a new short-video item. Stores only platform + timestamps +
 * local UUIDs. Never video IDs, captions, URLs, creators or content fingerprints (PRD §5.1, §7).
 */
@Entity(
    tableName = "confirmed_transition",
    indices = [
        Index(value = ["platform", "occurredAtUtcMs"]),
        Index(value = ["localDay", "platform"]),
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["sessionId"]),
    ],
)
data class ConfirmedTransitionEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val occurredAtUtcMs: Long,
    val zoneIdAtEvent: String,
    val localDay: String,
    val sessionId: String,
    val dedupeKey: String?,
    val detectorVersion: Int,
)

object SessionStatus {
    const val OPEN = "OPEN"
    const val OBSERVED = "OBSERVED"
    const val INCOMPLETE = "INCOMPLETE"
}

/** A continuous detectable period inside a short-form feed. No video content. */
@Entity(tableName = "tracking_session", indices = [Index(value = ["platform", "startedAtUtcMs"])])
data class TrackingSessionEntity(
    @PrimaryKey val sessionId: String,
    val platform: String,
    val startedAtUtcMs: Long,
    val endedAtUtcMs: Long?,
    val lastActivityAtUtcMs: Long,
    val endReason: String?,
    val status: String,
    /** Time inside the feed where transitions could not be resolved (no confirmed counts). */
    val uncertainMs: Long,
)

/** Separately stored estimate — never merged into confirmed totals (PRD §5.6). */
@Entity(tableName = "estimate_bucket", indices = [Index(value = ["intervalStartUtcMs", "platform"])])
data class EstimateBucketEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val localDay: String,
    val intervalStartUtcMs: Long,
    val intervalEndUtcMs: Long,
    val estimateValue: Double,
    val lowerBound: Double?,
    val upperBound: Double?,
    val method: String,
    val methodVersion: Int,
    val evidenceQuality: String,
)

object DeliveryState {
    const val SENT = "SENT"
    const val SKIPPED_QUIET = "SKIPPED_QUIET"
    const val SKIPPED_CAP = "SKIPPED_CAP"
    const val SKIPPED_NO_PERMISSION = "SKIPPED_NO_PERMISSION"
    const val SUPPRESSED_ON_EDIT = "SUPPRESSED_ON_EDIT"
}

/** Prevents duplicate threshold notifications per scope/day/goal revision (PRD §7, §10). */
@Entity(
    tableName = "goal_threshold_delivery",
    indices = [Index(value = ["scope", "localDay", "zoneId", "goalRevision", "thresholdPercent"], unique = true)],
)
data class GoalThresholdDeliveryEntity(
    @PrimaryKey val id: String,
    val localDay: String,
    val zoneId: String,
    val scope: String,
    val goalRevision: Int,
    val thresholdPercent: Int,
    val sentAtUtcMs: Long,
    val deliveryState: String,
)

/**
 * Period during which the tracking service was connected and not globally paused.
 * Used to distinguish "no activity" from "not observed" so charts never fabricate zeros.
 */
@Entity(tableName = "observation_window", indices = [Index(value = ["startedAtUtcMs"])])
data class ObservationWindowEntity(
    @PrimaryKey val id: String,
    val startedAtUtcMs: Long,
    val lastSeenAtUtcMs: Long,
    val endedAtUtcMs: Long?,
)
