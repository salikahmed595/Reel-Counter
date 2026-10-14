package com.reelcounter.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.app.BuildConfig
import com.reelcounter.app.ReelCounterApp
import com.reelcounter.app.core.Days
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.DayStats
import com.reelcounter.app.data.db.TransitionInstant
import com.reelcounter.app.data.prefs.ThemeMode
import com.reelcounter.app.data.prefs.UpdateMode
import com.reelcounter.app.data.prefs.UserPrefs
import com.reelcounter.app.tracking.AppForegroundTime
import com.reelcounter.app.tracking.HealthEvaluator
import com.reelcounter.app.tracking.Permissions
import com.reelcounter.app.tracking.PlatformHealth
import com.reelcounter.app.tracking.ServiceRuntime
import com.reelcounter.app.tracking.TrackingHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SystemState(
    val serviceEnabled: Boolean = false,
    val usageAccess: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val overlayAllowed: Boolean = false,
    val appForegroundToday: Map<Platform, Long>? = null,
    val appForeground7d: Long? = null,
    val widgetCount: Int = 0,
)

data class UiState(
    val loaded: Boolean = false,
    val prefs: UserPrefs = UserPrefs(),
    val days: List<DayStats> = emptyList(),
    val system: SystemState = SystemState(),
    val serviceConnected: Boolean = false,
    val health: TrackingHealth = TrackingHealth.PERMISSION_OFF,
    val platformHealth: List<PlatformHealth> = emptyList(),
    val lastEventAt: Map<Platform, Long> = emptyMap(),
    val isPro: Boolean = false,
) {
    val today: DayStats? get() = days.lastOrNull()
    val yesterday: DayStats? get() = days.getOrNull(days.size - 2)
    val hasAnyData: Boolean get() = days.any { it.total > 0 || it.feedMs.isNotEmpty() }
    val trackingOn: Boolean get() = prefs.hasValidConsent && system.serviceEnabled
    val hasDetectedEver: Boolean get() = lastEventAt.isNotEmpty() || days.any { it.total > 0 }
    /** Required first-run steps: tracking on, goal chosen, first detection seen. */
    val guideStepsDone: Int get() = listOf(trackingOn, prefs.goalConfirmed, hasDetectedEver).count { it }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as ReelCounterApp).container
    private val system = MutableStateFlow(SystemState())

    val state: StateFlow<UiState> = combine(
        c.prefsRepo.prefs,
        c.statsRepo.observeDays(),
        system,
        ServiceRuntime.connected,
        ServiceRuntime.lastEventAt,
    ) { prefs, days, sys, connected, last ->
        UiState(
            loaded = true,
            prefs = prefs,
            days = days,
            system = sys,
            serviceConnected = connected,
            health = HealthEvaluator.overall(sys.serviceEnabled, connected, prefs),
            platformHealth = HealthEvaluator.perPlatform(getApplication(), sys.serviceEnabled, prefs),
            lastEventAt = last,
            isPro = c.entitlements.isPro(prefs),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    val billingPlans = c.billing.plans
    val billingStatus = c.billing.status

    val transitionsToday: StateFlow<List<TransitionInstant>> =
        c.statsRepo.observeTransitionsSince(0).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transitions30d: StateFlow<List<TransitionInstant>> =
        c.statsRepo.observeTransitionsSince(29).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called on every resume: permissions can change in system settings at any time. */
    fun refreshSystemState() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val prefs = c.prefsRepo.current()
            val usage = Permissions.hasUsageAccess(ctx)
            val (todayFg, weekFg) = if (usage && prefs.showAppForegroundTime) {
                withContext(Dispatchers.IO) {
                    val now = c.clock.nowMs()
                    val start = Days.startOfDayMs(Days.today(c.clock), c.clock.zone())
                    val weekStart = Days.startOfDayMs(Days.today(c.clock).minusDays(6), c.clock.zone())
                    AppForegroundTime.measure(ctx, start, now) to AppForegroundTime.measure(ctx, weekStart, now)?.values?.sum()
                }
            } else {
                null to null
            }
            system.value = SystemState(
                serviceEnabled = Permissions.isAccessibilityServiceEnabled(ctx),
                usageAccess = usage,
                notificationsAllowed = c.notifier.canPost(),
                overlayAllowed = Permissions.canDrawOverlays(ctx),
                appForegroundToday = todayFg,
                appForeground7d = weekFg,
                widgetCount = countWidgets(ctx),
            )
            // Revoked permissions immediately stop dependent features (PRD §3 S08).
            if (!system.value.overlayAllowed && prefs.overlayEnabled) c.prefsRepo.setOverlay(false)
            if (!system.value.overlayAllowed) c.overlay.hide()
        }
    }

    private fun countWidgets(ctx: Application): Int {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        return listOf(
            com.reelcounter.app.widget.SmallWidgetReceiver::class.java, com.reelcounter.app.widget.MediumWidgetReceiver::class.java,
            com.reelcounter.app.widget.LargeWidgetReceiver::class.java, com.reelcounter.app.widget.CustomWidgetReceiver::class.java,
        ).sumOf { runCatching { mgr.getAppWidgetIds(android.content.ComponentName(ctx, it)).size }.getOrDefault(0) }
    }

    /** One-tap daily limit from the guide; keeps per-app goals as they are. */
    fun setQuickGoal(goal: Int) = launch {
        val p = c.prefsRepo.current()
        c.prefsRepo.updateGoals(true, goal, p.platformGoalEnabled, p.platformGoals)
        c.goalEvaluator.onGoalsEdited()
        c.widgetUpdater.request(significant = true)
    }

    fun dismissGuide() = launch { c.prefsRepo.setGuideDismissed(true) }
    fun markTipSeen(tip: String) = launch { c.prefsRepo.markTipSeen(tip) }

    fun connectBilling() = c.billing.connect()
    fun restorePurchases() = c.billing.restore()
    fun purchase(activity: android.app.Activity, productId: String) = c.billing.launchPurchase(activity, productId)

    fun recordConsent(granted: Boolean) = launch { c.prefsRepo.recordConsent(granted, c.clock.nowMs()) }
    fun finishOnboarding() = launch { c.prefsRepo.setOnboardingDone() }

    fun saveGoals(combinedEnabled: Boolean, combined: Int, enabled: Map<Platform, Boolean>, goals: Map<Platform, Int>) = launch {
        c.prefsRepo.updateGoals(combinedEnabled, combined, enabled, goals)
        c.goalEvaluator.onGoalsEdited()
        c.widgetUpdater.request(significant = true)
    }

    fun setReminders(v: Boolean) = launch { c.prefsRepo.setReminders(v) }
    fun setThreshold(pct: Int, v: Boolean) = launch { c.prefsRepo.setThreshold(pct, v) }
    fun setQuietHours(v: Boolean, start: Int, end: Int) = launch { c.prefsRepo.setQuietHours(v, start, end) }
    fun setOverlay(v: Boolean) = launch {
        c.prefsRepo.setOverlay(v)
        if (!v) c.overlay.hide()
    }
    fun setOverlayCooldown(min: Int) = launch { c.prefsRepo.setOverlayCooldown(min) }
    fun previewOverlay() = c.overlay.preview()
    fun setGlobalPaused(v: Boolean) = launch {
        c.prefsRepo.setGlobalPaused(v)
        c.widgetUpdater.request(significant = true)
    }
    fun setPlatformPaused(p: Platform, v: Boolean) = launch { c.prefsRepo.setPlatformPaused(p, v) }
    fun setUpdateMode(m: UpdateMode) = launch { c.prefsRepo.setUpdateMode(m) }
    fun setThemeMode(m: ThemeMode) = launch { c.prefsRepo.setThemeMode(m) }
    fun setReducedMotion(v: Boolean) = launch { c.prefsRepo.setReducedMotion(v) }
    fun setShowAppTime(v: Boolean) = launch {
        c.prefsRepo.setShowAppForegroundTime(v)
        refreshSystemState()
    }

    fun deleteAllActivity() = launch { c.deleteAllActivity() }

    fun resetApp() = launch {
        c.deleteAllActivity()
        c.prefsRepo.resetAll()
        setLanguage("")
    }

    /** "" = follow device language (default); manual override persists and takes precedence (PRD §4). */
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }

    fun currentLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    fun loadDebugSample() {
        if (!BuildConfig.DEBUG) return
        launch { DebugSampleData.insert(c) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
