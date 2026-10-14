package com.reelcounter.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.reelcounter.app.data.prefs.PreferencesRepository
import com.reelcounter.app.data.prefs.UpdateMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces widget refreshes (PRD §9). Balanced batches bursts for longer; Responsive refreshes
 * soon after significant changes while the service is already awake. Neither is real-time:
 * Android and the launcher decide when a widget actually redraws.
 */
class WidgetUpdater(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
) {
    private var pending: Job? = null

    @Synchronized
    fun request(significant: Boolean) {
        if (pending?.isActive == true) return
        pending = scope.launch {
            val mode = prefs.current().updateMode
            delay(
                when {
                    mode == UpdateMode.RESPONSIVE && significant -> 10_000L
                    mode == UpdateMode.RESPONSIVE -> 30_000L
                    else -> 90_000L
                },
            )
            updateAllNow()
        }
    }

    suspend fun updateAllNow() {
        runCatching {
            SmallWidget().updateAll(context)
            MediumWidget().updateAll(context)
            LargeWidget().updateAll(context)
            CustomWidget().updateAll(context)
        }
    }
}
