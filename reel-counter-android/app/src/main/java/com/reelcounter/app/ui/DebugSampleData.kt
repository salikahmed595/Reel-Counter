package com.reelcounter.app.ui

import com.reelcounter.app.AppContainer
import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.db.ConfirmedTransitionEntity
import com.reelcounter.app.data.db.ObservationWindowEntity
import com.reelcounter.app.data.db.SessionStatus
import com.reelcounter.app.data.db.TrackingSessionEntity
import java.util.UUID
import kotlin.random.Random

/**
 * DEBUG BUILDS ONLY: fills the local database with synthetic activity so designers/QA can review
 * populated screens without social apps installed. Never compiled into release behavior
 * (guarded by BuildConfig.DEBUG at the call site).
 */
object DebugSampleData {
    suspend fun insert(c: AppContainer) {
        val dao = c.db.activityDao()
        val zone = c.clock.zone()
        val now = c.clock.nowMs()
        val rnd = Random(42)
        val weights = mapOf(Platform.INSTAGRAM to 0.42, Platform.YOUTUBE to 0.29, Platform.FACEBOOK to 0.16, Platform.TIKTOK to 0.13)
        for (back in 13 downTo 0) {
            val date = Days.today(c.clock).minusDays(back.toLong())
            val dayStart = Days.startOfDayMs(date, zone)
            if (back == 9) continue // leave one unobserved day to show the "no data" state
            dao.upsertWindow(ObservationWindowEntity(UUID.randomUUID().toString(), dayStart, minOf(dayStart + 86_400_000L, now), minOf(dayStart + 86_400_000L, now)))
            val dayTotal = if (back == 0) 128 else rnd.nextInt(90, 230)
            weights.forEach { (p, w) ->
                val n = (dayTotal * w).toInt()
                var t = dayStart + 9 * 3_600_000L + rnd.nextLong(0, 3_600_000L)
                var remaining = n
                while (remaining > 0) {
                    val inSession = minOf(remaining, rnd.nextInt(5, 25))
                    val sid = UUID.randomUUID().toString()
                    val start = t
                    repeat(inSession) { i ->
                        t += rnd.nextLong(8_000, 40_000)
                        if (t > now) return@repeat
                        dao.insertTransition(ConfirmedTransitionEntity(UUID.randomUUID().toString(), p.key, t, zone.id, Days.key(t, zone), sid, "$sid:$i", 1))
                    }
                    if (start < now) {
                        dao.upsertSession(TrackingSessionEntity(sid, p.key, start, minOf(t, now), minOf(t, now), "FEED_EXIT", SessionStatus.OBSERVED, 0))
                    }
                    remaining -= inSession
                    t += rnd.nextLong(20 * 60_000L, 120 * 60_000L)
                }
            }
        }
        c.widgetUpdater.updateAllNow()
    }
}
