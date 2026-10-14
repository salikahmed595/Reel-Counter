package com.reelcounter.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reelcounter.app.core.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Bump when the Accessibility disclosure text materially changes; forces re-consent. */
const val CURRENT_CONSENT_VERSION = 1

enum class UpdateMode { BALANCED, RESPONSIVE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class WidgetMetric { DETECTED_TODAY, REMAINING, FEED_TIME, LAST_7_DAYS }
enum class WidgetTheme(val proOnly: Boolean) { SAGE(false), LAVENDER(true), IVORY(true), NIGHT(true) }

data class WidgetConfig(
    val metric: WidgetMetric = WidgetMetric.DETECTED_TODAY,
    val platform: Platform? = null,
    val theme: WidgetTheme = WidgetTheme.SAGE,
    val showEstimates: Boolean = false,
) {
    fun encode(): String = listOf(metric.name, platform?.key ?: "all", theme.name, showEstimates.toString()).joinToString("|")

    companion object {
        fun decode(s: String?): WidgetConfig {
            val p = s?.split("|") ?: return WidgetConfig()
            return WidgetConfig(
                metric = runCatching { WidgetMetric.valueOf(p[0]) }.getOrDefault(WidgetMetric.DETECTED_TODAY),
                platform = p.getOrNull(1)?.let { Platform.fromKey(it) },
                theme = runCatching { WidgetTheme.valueOf(p[2]) }.getOrDefault(WidgetTheme.SAGE),
                showEstimates = p.getOrNull(3)?.toBooleanStrictOrNull() ?: false,
            )
        }
    }
}

enum class EntitlementState { NONE, PENDING, ACTIVE, GRACE, EXPIRED, UNVERIFIED }

data class EntitlementCache(
    val state: EntitlementState = EntitlementState.NONE,
    val productId: String? = null,
    val lastVerifiedAtMs: Long = 0,
    val expiresAtMs: Long = 0,
)

data class UserPrefs(
    val onboardingDone: Boolean = false,
    val consentVersion: Int = 0,
    val consentAtMs: Long = 0,
    val consentDeclined: Boolean = false,
    val combinedGoalEnabled: Boolean = true,
    val combinedGoal: Int = 200,
    val platformGoalEnabled: Map<Platform, Boolean> = Platform.entries.associateWith { true },
    val platformGoals: Map<Platform, Int> = mapOf(
        Platform.INSTAGRAM to 70, Platform.YOUTUBE to 60, Platform.FACEBOOK to 40, Platform.TIKTOK to 30,
    ),
    val goalRevision: Int = 1,
    val remindersEnabled: Boolean = false,
    val threshold50: Boolean = true,
    val threshold75: Boolean = true,
    val threshold100: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 8 * 60,
    val dailyNotificationCap: Int = 6,
    val overlayEnabled: Boolean = false,
    val overlayCooldownMinutes: Int = 30,
    val lastOverlayAtMs: Long = 0,
    val globalPaused: Boolean = false,
    val pausedPlatforms: Set<Platform> = emptySet(),
    val updateMode: UpdateMode = UpdateMode.BALANCED,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reducedMotion: Boolean = false,
    val showAppForegroundTime: Boolean = false,
    val entitlement: EntitlementCache = EntitlementCache(),
    /** First-run guide: hidden by the user, goal chosen at least once, per-screen tips already shown. */
    val guideDismissed: Boolean = false,
    val goalConfirmed: Boolean = false,
    val tipsSeen: Set<String> = emptySet(),
) {
    val hasValidConsent: Boolean get() = consentVersion >= CURRENT_CONSENT_VERSION && !consentDeclined
    fun isPlatformPaused(p: Platform) = globalPaused || p in pausedPlatforms
    fun goalFor(p: Platform): Int? = if (platformGoalEnabled[p] == true) platformGoals[p] else null
    val combinedGoalOrNull: Int? get() = if (combinedGoalEnabled) combinedGoal else null
}

class PreferencesRepository(private val context: Context) {
    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val consentVersion = intPreferencesKey("consent_version")
        val consentAt = longPreferencesKey("consent_at")
        val consentDeclined = booleanPreferencesKey("consent_declined")
        val combinedGoalEnabled = booleanPreferencesKey("combined_goal_enabled")
        val combinedGoal = intPreferencesKey("combined_goal")
        fun platformGoal(p: Platform) = intPreferencesKey("goal_${p.key}")
        fun platformGoalEnabled(p: Platform) = booleanPreferencesKey("goal_enabled_${p.key}")
        val goalRevision = intPreferencesKey("goal_revision")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val t50 = booleanPreferencesKey("threshold_50")
        val t75 = booleanPreferencesKey("threshold_75")
        val t100 = booleanPreferencesKey("threshold_100")
        val quiet = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val cap = intPreferencesKey("notification_cap")
        val overlay = booleanPreferencesKey("overlay_enabled")
        val overlayCooldown = intPreferencesKey("overlay_cooldown")
        val lastOverlay = longPreferencesKey("last_overlay_at")
        val globalPaused = booleanPreferencesKey("global_paused")
        val pausedPlatforms = stringSetPreferencesKey("paused_platforms")
        val updateMode = stringPreferencesKey("update_mode")
        val themeMode = stringPreferencesKey("theme_mode")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val appTime = booleanPreferencesKey("show_app_time")
        val entState = stringPreferencesKey("ent_state")
        val entProduct = stringPreferencesKey("ent_product")
        val entVerifiedAt = longPreferencesKey("ent_verified_at")
        val entExpires = longPreferencesKey("ent_expires")
        fun widget(id: Int) = stringPreferencesKey("widget_$id")
        val guideDismissed = booleanPreferencesKey("guide_dismissed")
        val goalConfirmed = booleanPreferencesKey("goal_confirmed")
        val tipsSeen = stringSetPreferencesKey("tips_seen")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p -> p.toUserPrefs() }

    suspend fun current(): UserPrefs = prefs.first()

    private fun Preferences.toUserPrefs(): UserPrefs {
        val d = UserPrefs()
        return UserPrefs(
            onboardingDone = this[K.onboardingDone] ?: d.onboardingDone,
            consentVersion = this[K.consentVersion] ?: d.consentVersion,
            consentAtMs = this[K.consentAt] ?: d.consentAtMs,
            consentDeclined = this[K.consentDeclined] ?: d.consentDeclined,
            combinedGoalEnabled = this[K.combinedGoalEnabled] ?: d.combinedGoalEnabled,
            combinedGoal = this[K.combinedGoal] ?: d.combinedGoal,
            platformGoalEnabled = Platform.entries.associateWith { this[K.platformGoalEnabled(it)] ?: true },
            platformGoals = Platform.entries.associateWith { this[K.platformGoal(it)] ?: d.platformGoals.getValue(it) },
            goalRevision = this[K.goalRevision] ?: d.goalRevision,
            remindersEnabled = this[K.remindersEnabled] ?: d.remindersEnabled,
            threshold50 = this[K.t50] ?: d.threshold50,
            threshold75 = this[K.t75] ?: d.threshold75,
            threshold100 = this[K.t100] ?: d.threshold100,
            quietHoursEnabled = this[K.quiet] ?: d.quietHoursEnabled,
            quietStartMinutes = this[K.quietStart] ?: d.quietStartMinutes,
            quietEndMinutes = this[K.quietEnd] ?: d.quietEndMinutes,
            dailyNotificationCap = this[K.cap] ?: d.dailyNotificationCap,
            overlayEnabled = this[K.overlay] ?: d.overlayEnabled,
            overlayCooldownMinutes = this[K.overlayCooldown] ?: d.overlayCooldownMinutes,
            lastOverlayAtMs = this[K.lastOverlay] ?: d.lastOverlayAtMs,
            globalPaused = this[K.globalPaused] ?: d.globalPaused,
            pausedPlatforms = (this[K.pausedPlatforms] ?: emptySet()).mapNotNull { Platform.fromKey(it) }.toSet(),
            updateMode = runCatching { UpdateMode.valueOf(this[K.updateMode]!!) }.getOrDefault(d.updateMode),
            themeMode = runCatching { ThemeMode.valueOf(this[K.themeMode]!!) }.getOrDefault(d.themeMode),
            reducedMotion = this[K.reducedMotion] ?: d.reducedMotion,
            showAppForegroundTime = this[K.appTime] ?: d.showAppForegroundTime,
            entitlement = EntitlementCache(
                state = runCatching { EntitlementState.valueOf(this[K.entState]!!) }.getOrDefault(EntitlementState.NONE),
                productId = this[K.entProduct],
                lastVerifiedAtMs = this[K.entVerifiedAt] ?: 0,
                expiresAtMs = this[K.entExpires] ?: 0,
            ),
            guideDismissed = this[K.guideDismissed] ?: false,
            goalConfirmed = this[K.goalConfirmed] ?: false,
            tipsSeen = this[K.tipsSeen] ?: emptySet(),
        )
    }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboardingDone] = true }

    suspend fun recordConsent(granted: Boolean, nowMs: Long) = context.dataStore.edit {
        if (granted) {
            it[K.consentVersion] = CURRENT_CONSENT_VERSION
            it[K.consentAt] = nowMs
            it[K.consentDeclined] = false
        } else {
            it[K.consentDeclined] = true
        }
    }

    /** Every goal edit bumps the revision so thresholds fire only on a new crossing (PRD §10). */
    suspend fun updateGoals(
        combinedEnabled: Boolean,
        combined: Int,
        platformEnabled: Map<Platform, Boolean>,
        platformGoals: Map<Platform, Int>,
    ): Int {
        var revision = 0
        context.dataStore.edit { p ->
            p[K.combinedGoalEnabled] = combinedEnabled
            p[K.combinedGoal] = combined.coerceIn(1, 9999)
            Platform.entries.forEach { pl ->
                p[K.platformGoalEnabled(pl)] = platformEnabled[pl] ?: true
                p[K.platformGoal(pl)] = (platformGoals[pl] ?: 1).coerceIn(1, 9999)
            }
            revision = (p[K.goalRevision] ?: 1) + 1
            p[K.goalRevision] = revision
            p[K.goalConfirmed] = true
        }
        return revision
    }

    suspend fun setGuideDismissed(v: Boolean) = context.dataStore.edit { it[K.guideDismissed] = v }
    suspend fun markTipSeen(tip: String) = context.dataStore.edit { it[K.tipsSeen] = (it[K.tipsSeen] ?: emptySet()) + tip }

    suspend fun setReminders(enabled: Boolean) = context.dataStore.edit { it[K.remindersEnabled] = enabled }
    suspend fun setThreshold(percent: Int, enabled: Boolean) = context.dataStore.edit {
        when (percent) {
            50 -> it[K.t50] = enabled
            75 -> it[K.t75] = enabled
            100 -> it[K.t100] = enabled
        }
    }
    suspend fun setQuietHours(enabled: Boolean, startMin: Int, endMin: Int) = context.dataStore.edit {
        it[K.quiet] = enabled; it[K.quietStart] = startMin; it[K.quietEnd] = endMin
    }
    suspend fun setOverlay(enabled: Boolean) = context.dataStore.edit { it[K.overlay] = enabled }
    suspend fun setOverlayCooldown(minutes: Int) = context.dataStore.edit { it[K.overlayCooldown] = minutes }
    suspend fun setLastOverlayAt(ms: Long) = context.dataStore.edit { it[K.lastOverlay] = ms }
    suspend fun setGlobalPaused(paused: Boolean) = context.dataStore.edit { it[K.globalPaused] = paused }
    suspend fun setPlatformPaused(platform: Platform, paused: Boolean) = context.dataStore.edit {
        val set = (it[K.pausedPlatforms] ?: emptySet()).toMutableSet()
        if (paused) set.add(platform.key) else set.remove(platform.key)
        it[K.pausedPlatforms] = set
    }
    suspend fun setUpdateMode(mode: UpdateMode) = context.dataStore.edit { it[K.updateMode] = mode.name }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[K.themeMode] = mode.name }
    suspend fun setReducedMotion(v: Boolean) = context.dataStore.edit { it[K.reducedMotion] = v }
    suspend fun setShowAppForegroundTime(v: Boolean) = context.dataStore.edit { it[K.appTime] = v }

    suspend fun setEntitlement(e: EntitlementCache) = context.dataStore.edit {
        it[K.entState] = e.state.name
        if (e.productId != null) it[K.entProduct] = e.productId else it.remove(K.entProduct)
        it[K.entVerifiedAt] = e.lastVerifiedAtMs
        it[K.entExpires] = e.expiresAtMs
    }

    // Per-widget-instance configuration keyed by appWidgetId (PRD §3 S06, §9).
    suspend fun widgetConfig(appWidgetId: Int): WidgetConfig =
        WidgetConfig.decode(context.dataStore.data.first()[K.widget(appWidgetId)])

    suspend fun setWidgetConfig(appWidgetId: Int, config: WidgetConfig) =
        context.dataStore.edit { it[K.widget(appWidgetId)] = config.encode() }

    suspend fun deleteWidgetConfig(appWidgetId: Int) = context.dataStore.edit { it.remove(K.widget(appWidgetId)) }

    /** "Reset app": clears settings too (separate from Delete all activity). */
    suspend fun resetAll() = context.dataStore.edit { it.clear() }
}
