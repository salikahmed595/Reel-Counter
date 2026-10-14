package com.reelcounter.app.tracking

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.prefs.UserPrefs
import com.reelcounter.app.tracking.adapters.Adapters
import com.reelcounter.app.tracking.engine.AdapterReliability
import com.reelcounter.app.tracking.service.ReelAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-process runtime status published by the accessibility service. Never persisted content. */
object ServiceRuntime {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastEventAt = MutableStateFlow<Map<Platform, Long>>(emptyMap())
    /** Last *confirmed* transition time per platform during this process lifetime. */
    val lastEventAt: StateFlow<Map<Platform, Long>> = _lastEventAt.asStateFlow()

    fun setConnected(v: Boolean) { _connected.value = v }
    fun markEvent(p: Platform, at: Long) { _lastEventAt.value = _lastEventAt.value + (p to at) }
}

enum class TrackingHealth { ACTIVE, PAUSED, LIMITED, PERMISSION_OFF, DETECTION_ISSUE, CONSENT_NEEDED }

enum class PlatformStatus { ACTIVE_EXPERIMENTAL, ACTIVE_VERIFIED, USER_PAUSED, SERVICE_DISABLED, NOT_INSTALLED, UNAVAILABLE }

data class PlatformHealth(
    val platform: Platform,
    val status: PlatformStatus,
    val installed: Boolean,
    val appVersion: String?,
    val reliability: AdapterReliability,
)

object Permissions {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ReelAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
}

object HealthEvaluator {
    fun overall(serviceEnabled: Boolean, serviceConnected: Boolean, prefs: UserPrefs): TrackingHealth = when {
        !prefs.hasValidConsent -> TrackingHealth.CONSENT_NEEDED
        !serviceEnabled -> TrackingHealth.PERMISSION_OFF
        prefs.globalPaused -> TrackingHealth.PAUSED
        !serviceConnected -> TrackingHealth.DETECTION_ISSUE
        prefs.pausedPlatforms.isNotEmpty() -> TrackingHealth.LIMITED
        else -> TrackingHealth.ACTIVE
    }

    fun perPlatform(context: Context, serviceEnabled: Boolean, prefs: UserPrefs): List<PlatformHealth> {
        val adapters = Adapters.all()
        return Platform.entries.map { p ->
            val version = installedVersion(context, p)
            val reliability = adapters[p]?.reliability ?: AdapterReliability.UNAVAILABLE
            val status = when {
                version == null -> PlatformStatus.NOT_INSTALLED
                prefs.isPlatformPaused(p) -> PlatformStatus.USER_PAUSED
                !serviceEnabled || !prefs.hasValidConsent -> PlatformStatus.SERVICE_DISABLED
                reliability == AdapterReliability.UNAVAILABLE -> PlatformStatus.UNAVAILABLE
                reliability == AdapterReliability.VERIFIED -> PlatformStatus.ACTIVE_VERIFIED
                else -> PlatformStatus.ACTIVE_EXPERIMENTAL
            }
            PlatformHealth(p, status, version != null, version, reliability)
        }
    }

    /** Requires the narrow <queries> package list in the manifest — no broad package visibility. */
    private fun installedVersion(context: Context, p: Platform): String? = p.packages.firstNotNullOfOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

/**
 * Whole-app foreground time from Usage Access (PRD §5.7 #2). This is NOT short-video time:
 * it includes chats, stories and every other screen in those apps.
 */
object AppForegroundTime {
    fun measure(context: Context, fromMs: Long, toMs: Long): Map<Platform, Long>? {
        if (!Permissions.hasUsageAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = usm.queryEvents(fromMs, toMs)
        val resumedAt = HashMap<String, Long>()
        val totals = HashMap<Platform, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val platform = Platform.forPackage(e.packageName) ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt.putIfAbsent(e.packageName, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = resumedAt.remove(e.packageName) ?: continue
                    totals[platform] = (totals[platform] ?: 0) + (e.timeStamp - start)
                }
            }
        }
        resumedAt.forEach { (pkg, start) ->
            val p = Platform.forPackage(pkg) ?: return@forEach
            totals[p] = (totals[p] ?: 0) + (toMs - start)
        }
        return totals
    }
}
