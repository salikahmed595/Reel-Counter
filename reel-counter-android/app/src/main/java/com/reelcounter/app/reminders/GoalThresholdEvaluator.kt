package com.reelcounter.app.reminders

import com.reelcounter.app.core.Clock
import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.Retention
import com.reelcounter.app.data.db.AppDatabase
import com.reelcounter.app.data.db.DeliveryState
import com.reelcounter.app.data.db.GoalThresholdDeliveryEntity
import com.reelcounter.app.data.prefs.PreferencesRepository
import com.reelcounter.app.data.prefs.UserPrefs
import java.time.Instant
import java.util.UUID

data class Crossing(val platform: Platform?, val percent: Int, val count: Int, val goal: Int) {
    val scope: String get() = platform?.key ?: COMBINED
    companion object { const val COMBINED = "combined" }
}

/**
 * Fires 50/75/100% thresholds once per scope per local day per goal revision, coalesced into one
 * digest, respecting quiet hours, permission and a daily cap. Skipped alerts expire; they never
 * flood later (PRD §3 S05, §10, AC10). Goals use confirmed counts only — never estimates.
 */
class GoalThresholdEvaluator(
    private val db: AppDatabase,
    private val prefsRepo: PreferencesRepository,
    private val clock: Clock,
    private val notifier: ReminderNotifier,
    private val overlay: OverlayPresenter,
) {
    private val dao = db.activityDao()
    private val deliveries = db.goalDeliveryDao()

    suspend fun evaluate() {
        val prefs = prefsRepo.current()
        val crossings = currentCrossings(prefs)
        if (crossings.isEmpty()) return
        val now = clock.nowMs()
        val zone = clock.zone()
        val day = Days.key(now, zone)
        var sentToday = deliveries.sentCountForDay(day)
        val fresh = ArrayList<Crossing>()
        for (c in crossings) {
            val state = when {
                !prefs.remindersEnabled || !notifier.canPost() -> DeliveryState.SKIPPED_NO_PERMISSION
                inQuietHours(prefs, now, zone) -> DeliveryState.SKIPPED_QUIET
                sentToday >= prefs.dailyNotificationCap -> DeliveryState.SKIPPED_CAP
                else -> DeliveryState.SENT
            }
            val inserted = deliveries.insert(
                GoalThresholdDeliveryEntity(UUID.randomUUID().toString(), day, zone.id, c.scope, prefs.goalRevision, c.percent, now, state),
            ) != -1L
            if (inserted && state == DeliveryState.SENT) {
                fresh += c
                sentToday++
            }
        }
        if (fresh.isEmpty()) return
        notifier.notifyDigest(fresh)
        val cooldownMs = prefs.overlayCooldownMinutes * 60_000L
        if (prefs.overlayEnabled && !prefs.globalPaused && now - prefs.lastOverlayAtMs >= cooldownMs && overlay.canShow()) {
            overlay.show(fresh.maxBy { it.percent })
            prefsRepo.setLastOverlayAt(now)
        }
    }

    /**
     * After a goal edit only a *new* crossing should alert: thresholds already exceeded under the new
     * revision are recorded as suppressed.
     */
    suspend fun onGoalsEdited() {
        val prefs = prefsRepo.current()
        val now = clock.nowMs()
        val zone = clock.zone()
        val day = Days.key(now, zone)
        currentCrossings(prefs).forEach { c ->
            deliveries.insert(
                GoalThresholdDeliveryEntity(UUID.randomUUID().toString(), day, zone.id, c.scope, prefs.goalRevision, c.percent, now, DeliveryState.SUPPRESSED_ON_EDIT),
            )
        }
    }

    private suspend fun currentCrossings(prefs: UserPrefs): List<Crossing> {
        val now = clock.nowMs()
        val day = Days.key(now, clock.zone())
        val counts = dao.countsForDay(day, Retention.cutoff(now))
            .mapNotNull { c -> Platform.fromKey(c.platform)?.let { it to c.count } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        return crossings(prefs, counts)
    }

    companion object {
        fun enabledThresholds(prefs: UserPrefs): List<Int> = buildList {
            if (prefs.threshold50) add(50)
            if (prefs.threshold75) add(75)
            if (prefs.threshold100) add(100)
        }

        fun crossings(prefs: UserPrefs, counts: Map<Platform, Int>): List<Crossing> {
            val thresholds = enabledThresholds(prefs)
            val out = ArrayList<Crossing>()
            prefs.combinedGoalOrNull?.let { goal ->
                val total = counts.values.sum()
                thresholds.filter { total * 100 >= it * goal }.forEach { out += Crossing(null, it, total, goal) }
            }
            Platform.entries.forEach { p ->
                val goal = prefs.goalFor(p) ?: return@forEach
                val n = counts[p] ?: 0
                thresholds.filter { n * 100 >= it * goal }.forEach { out += Crossing(p, it, n, goal) }
            }
            return out
        }

        fun inQuietHours(prefs: UserPrefs, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
            if (!prefs.quietHoursEnabled) return false
            val t = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
            val m = t.hour * 60 + t.minute
            val s = prefs.quietStartMinutes
            val e = prefs.quietEndMinutes
            return if (s <= e) m in s until e else m >= s || m < e
        }
    }
}

interface ReminderNotifier {
    fun canPost(): Boolean
    fun notifyDigest(crossings: List<Crossing>)
}

interface OverlayPresenter {
    fun canShow(): Boolean
    fun show(crossing: Crossing)
}
