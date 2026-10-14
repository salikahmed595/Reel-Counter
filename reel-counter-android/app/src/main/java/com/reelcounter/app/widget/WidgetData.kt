package com.reelcounter.app.widget

import android.content.Context
import com.reelcounter.app.ReelCounterApp
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.Coverage
import com.reelcounter.app.data.EstimateRange
import com.reelcounter.app.data.prefs.WidgetConfig
import com.reelcounter.app.data.prefs.WidgetTheme
import com.reelcounter.app.tracking.HealthEvaluator
import com.reelcounter.app.tracking.Permissions
import com.reelcounter.app.tracking.ServiceRuntime
import com.reelcounter.app.tracking.TrackingHealth

data class WidgetData(
    val config: WidgetConfig,
    val detectedToday: Int,
    val goal: Int?,
    val feedMs: Long,
    val last7Total: Int,
    val last7: List<Pair<Int, Coverage>>,
    val yesterday: Int?,
    val perPlatform: Map<Platform, Int>,
    val estimate: EstimateRange?,
    val health: TrackingHealth,
    val updatedAtMs: Long,
    val hasData: Boolean,
) {
    val progress: Float? get() = goal?.let { if (it > 0) detectedToday.toFloat() / it else null }
    val remaining: Int? get() = goal?.let { (it - detectedToday).coerceAtLeast(0) }
}

/** Reads durable repository state only; the widget is never a counter authority (PRD §6.3). */
object WidgetDataLoader {
    suspend fun load(context: Context, appWidgetId: Int?): WidgetData {
        val c = (context.applicationContext as ReelCounterApp).container
        val prefs = c.prefsRepo.current()
        var config = appWidgetId?.let { c.prefsRepo.widgetConfig(it) } ?: WidgetConfig()
        // Pro-only themes fall back gracefully when entitlement lapses; core data never locks.
        if (config.theme.proOnly && !c.entitlements.isPro(prefs)) config = config.copy(theme = WidgetTheme.SAGE)
        val days = c.statsRepo.daysSnapshot()
        val today = days.last()
        val filter = config.platform
        val goal = if (filter == null) prefs.combinedGoalOrNull else prefs.goalFor(filter)
        val health = HealthEvaluator.overall(
            Permissions.isAccessibilityServiceEnabled(context), ServiceRuntime.connected.value || Permissions.isAccessibilityServiceEnabled(context), prefs,
        )
        val last7 = days.takeLast(7)
        return WidgetData(
            config = config,
            detectedToday = today.detected(filter),
            goal = goal,
            feedMs = today.feedMs(filter),
            last7Total = last7.sumOf { it.detected(filter) },
            last7 = last7.map { it.detected(filter) to it.coverage },
            yesterday = days.getOrNull(days.size - 2)?.takeIf { it.coverage != Coverage.NONE }?.detected(filter),
            perPlatform = today.detected,
            estimate = if (config.showEstimates) today.estimate(filter) else null,
            health = health,
            updatedAtMs = System.currentTimeMillis(),
            hasData = days.any { it.total > 0 },
        )
    }
}
