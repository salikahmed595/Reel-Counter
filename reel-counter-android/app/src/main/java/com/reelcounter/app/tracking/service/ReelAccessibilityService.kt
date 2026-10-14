package com.reelcounter.app.tracking.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import com.reelcounter.app.BuildConfig
import android.view.accessibility.AccessibilityEvent
import com.reelcounter.app.ReelCounterApp
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.prefs.UserPrefs
import com.reelcounter.app.tracking.ServiceRuntime
import com.reelcounter.app.tracking.adapters.Adapters
import com.reelcounter.app.tracking.engine.Observation
import com.reelcounter.app.tracking.engine.TransitionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * User-enabled AccessibilityService that recognizes short-video feed transitions in the four
 * allowlisted apps (PRD §5, §6.2).
 *
 * Privacy contract (PRD §5.2, §13, AC17):
 *  - the service config restricts events to the allowlisted packages; anything else is dropped;
 *  - only structural properties are read (event type, scroll indices/deltas, container bounds);
 *  - no text, content descriptions, captions, comments, messages or screenshots are read or stored;
 *  - nothing is processed until the in-app disclosure has been affirmatively accepted;
 *  - the service never performs gestures, blocks apps or changes settings.
 */
class ReelAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Observation>(Channel.UNLIMITED)
    private val engineLock = Mutex()
    private var consumer: Job? = null

    @Volatile private var prefs: UserPrefs = UserPrefs()
    private lateinit var engine: TransitionEngine
    private var coverageWindowId: String? = null
    private val coverageLock = Mutex()
    private val rect = Rect()

    private val app get() = application as ReelCounterApp

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) enqueue(Observation.Kind.SCREEN_OFF, null)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val c = app.container
        engine = TransitionEngine(Adapters.all(), isPaused = { prefs.isPlatformPaused(it) || !prefs.hasValidConsent })
        // prefs start at defaults (no consent) so nothing is processed until the real values load.

        consumer = scope.launch {
            c.trackingRepo.closeDanglingSessions()
            for (obs in queue) process(obs)
        }
        scope.launch {
            c.prefsRepo.prefs.collect {
                prefs = it
                reconcileCoverage()
                if (it.globalPaused) c.overlay.hide()
            }
        }
        scope.launch {
            while (isActive) {
                delay(30_000)
                enqueue(Observation.Kind.TICK, null)
                coverageLock.withLock { coverageWindowId?.let { id -> c.trackingRepo.heartbeatWindow(id) } }
            }
        }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        enqueue(Observation.Kind.SERVICE_CONNECTED, null)
        ServiceRuntime.setConnected(true)
        if (BuildConfig.DIAGNOSTICS) Log.i(TAG, "service connected")
        scope.launch { reconcileCoverage() }
    }

    private suspend fun process(obs: Observation) {
        val outputs = engineLock.withLock { engine.process(obs) }
        if (outputs.isEmpty()) return
        val c = app.container
        val inserted = c.trackingRepo.persist(outputs)
        if (inserted > 0) {
            outputs.filterIsInstance<TransitionEngine.Output.Confirmed>().forEach { ServiceRuntime.markEvent(it.platform, it.atMs) }
            c.goalEvaluator.evaluate()
            c.widgetUpdater.request(significant = true)
        }
    }

    /** Coverage is recorded only while connected, consented and not globally paused. */
    private suspend fun reconcileCoverage() = coverageLock.withLock {
        val repo = app.container.trackingRepo
        val shouldObserve = ServiceRuntime.connected.value && prefs.hasValidConsent && !prefs.globalPaused
        val open = coverageWindowId
        if (shouldObserve && open == null) coverageWindowId = repo.openCoverageWindow()
        if (!shouldObserve && open != null) {
            repo.closeWindow(open)
            coverageWindowId = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !prefs.hasValidConsent) return
        val platform = Platform.forPackage(event.packageName?.toString()) ?: return
        if (prefs.isPlatformPaused(platform)) return
        if (BuildConfig.DIAGNOSTICS && event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val src = event.source
            if (src != null) src.getBoundsInScreen(rect)
            Log.i(TAG, "EVT ${platform.key} ${AccessibilityEvent.eventTypeToString(event.eventType)} cls=${event.className} ch=${event.contentChangeTypes} " +
                "from=${event.fromIndex} to=${event.toIndex} n=${event.itemCount} dy=${event.scrollDeltaY} src=${if (src == null) "-" else "${rect.width()}x${rect.height()}@${rect.top}"}")
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val source = event.source ?: return
                source.getBoundsInScreen(rect)
                val (sw, sh) = screenSize()
                val container = Observation.Container(
                    key = "${event.windowId}:${source.hashCode()}",
                    widthFraction = rect.width().toFloat() / sw,
                    heightFraction = rect.height().toFloat() / sh,
                    heightPx = rect.height(),
                )
                if (BuildConfig.DIAGNOSTICS) {
                    // Adapter discovery (PRD M0): structural fields only — never text or descriptions.
                    Log.i(TAG, "SCROLL ${platform.key} cls=${source.className} from=${event.fromIndex} to=${event.toIndex} n=${event.itemCount} " +
                        "dx=${event.scrollDeltaX} dy=${event.scrollDeltaY} w=${"%.2f".format(container.widthFraction)} h=${"%.2f".format(container.heightFraction)}")
                }
                queue.trySend(
                    Observation(
                        kind = Observation.Kind.SCROLLED,
                        platform = platform,
                        wallMs = System.currentTimeMillis(),
                        elapsedMs = SystemClock.elapsedRealtime(),
                        container = container,
                        fromIndex = event.fromIndex,
                        toIndex = event.toIndex,
                        itemCount = event.itemCount,
                        scrollDeltaX = event.scrollDeltaX,
                        scrollDeltaY = event.scrollDeltaY,
                    ),
                )
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (BuildConfig.DIAGNOSTICS) Log.i(TAG, "WINDOW ${platform.key} cls=${event.className}")
                enqueue(Observation.Kind.WINDOW_CHANGED, platform)
            }
        }
    }

    private fun enqueue(kind: Observation.Kind, platform: Platform?) {
        queue.trySend(Observation(kind, platform, System.currentTimeMillis(), SystemClock.elapsedRealtime()))
    }

    private fun screenSize(): Pair<Float, Float> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width().coerceAtLeast(1).toFloat() to b.height().coerceAtLeast(1).toFloat()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels.coerceAtLeast(1).toFloat() to dm.heightPixels.coerceAtLeast(1).toFloat()
        }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "ReelCounterAdapter"
    }

    override fun onDestroy() {
        ServiceRuntime.setConnected(false)
        runCatching { unregisterReceiver(screenReceiver) }
        enqueue(Observation.Kind.SERVICE_DISCONNECTED, null)
        queue.close()
        // Best effort: flush the queue and close coverage. Missed events are never backfilled.
        runBlocking {
            withTimeoutOrNull(1_500) {
                consumer?.join()
                reconcileCoverage()
            }
        }
        app.container.overlay.hide()
        scope.cancel()
        super.onDestroy()
    }
}
