package com.reelcounter.app

import android.app.Application
import com.reelcounter.app.work.MaintenanceWorker
import kotlinx.coroutines.launch

class ReelCounterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Retention cleanup at launch (PRD §7); periodic purge when the scheduler allows.
        container.appScope.launch { container.purgeExpired() }
        MaintenanceWorker.schedule(this)
    }
}
