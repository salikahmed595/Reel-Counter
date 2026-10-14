package com.reelcounter.app

import android.content.Context
import com.reelcounter.app.billing.BillingManager
import com.reelcounter.app.billing.EntitlementRepository
import com.reelcounter.app.billing.RemoteEntitlementVerifier
import com.reelcounter.app.core.Clock
import com.reelcounter.app.core.Retention
import com.reelcounter.app.core.SystemClock
import com.reelcounter.app.data.StatsRepository
import com.reelcounter.app.data.TrackingRepository
import com.reelcounter.app.data.db.AppDatabase
import com.reelcounter.app.data.prefs.PreferencesRepository
import com.reelcounter.app.reminders.AndroidOverlayPresenter
import com.reelcounter.app.reminders.AndroidReminderNotifier
import com.reelcounter.app.reminders.GoalThresholdEvaluator
import com.reelcounter.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (single decision per PRD §6.1: no DI framework needed at this size). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = SystemClock
    val db: AppDatabase = AppDatabase.build(appContext)
    val prefsRepo = PreferencesRepository(appContext)
    val trackingRepo = TrackingRepository(db, clock)
    val statsRepo = StatsRepository(db, clock)
    val notifier = AndroidReminderNotifier(appContext)
    val overlay = AndroidOverlayPresenter(appContext)
    val goalEvaluator = GoalThresholdEvaluator(db, prefsRepo, clock, notifier, overlay)
    val widgetUpdater = WidgetUpdater(appContext, prefsRepo, appScope)
    val entitlements = EntitlementRepository(clock)
    val billing = BillingManager(appContext, appScope, prefsRepo, RemoteEntitlementVerifier(), clock)

    suspend fun purgeExpired() {
        db.maintenanceDao().purgeOlderThan(Retention.cutoff(clock.nowMs()))
    }

    /** User-requested "Delete all local activity": immediate, transactional, widgets cleared (PRD §7). */
    suspend fun deleteAllActivity() {
        db.maintenanceDao().deleteAllActivity()
        widgetUpdater.updateAllNow()
    }
}
