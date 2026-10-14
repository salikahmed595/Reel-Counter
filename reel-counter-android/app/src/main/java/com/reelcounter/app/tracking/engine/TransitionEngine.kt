package com.reelcounter.app.tracking.engine

import com.reelcounter.app.core.Platform
import java.util.UUID

/**
 * Single-threaded counting state machine (PRD §5.4):
 *
 * ```
 * WAITING_FOR_ALLOWED_APP -> ALLOWED_APP_FOREGROUND -> FEED_BASELINE (no count)
 *   -> transition candidate -> NEW_ITEM -> emit CONFIRMED -> FEED_BASELINE
 *                           -> DUPLICATE_UI_EVENT -> discard
 *                           -> INSUFFICIENT_EVIDENCE -> rebaseline / UNCERTAIN (no count)
 *   -> FEED_EXIT / SCREEN_OFF / PAUSED / SERVICE_DISCONNECTED -> close session
 * ```
 *
 * Not thread-safe by design: callers must feed observations from one sequential queue.
 * Conservative: when evidence is uncertain, it never counts.
 */
class TransitionEngine(
    private val adapters: Map<Platform, PlatformAdapter>,
    private val isPaused: (Platform) -> Boolean,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) {
    enum class State { WAITING_FOR_ALLOWED_APP, ALLOWED_APP_FOREGROUND, FEED_BASELINE, UNCERTAIN }

    object EndReason {
        const val FEED_EXIT = "FEED_EXIT"
        const val IDLE = "IDLE"
        const val SCREEN_OFF = "SCREEN_OFF"
        const val PAUSED = "PAUSED"
        const val SERVICE_DISCONNECTED = "SERVICE_DISCONNECTED"
    }

    sealed interface Output {
        data class SessionStarted(val sessionId: String, val platform: Platform, val atMs: Long) : Output
        data class SessionHeartbeat(val sessionId: String, val platform: Platform, val startedAtMs: Long, val lastActivityMs: Long, val uncertainMs: Long) : Output
        data class SessionEnded(
            val sessionId: String,
            val platform: Platform,
            val startedAtMs: Long,
            val endedAtMs: Long,
            val reason: String,
            /** True when the exit was inferred (idle/screen-off/disconnect) rather than observed. */
            val incomplete: Boolean,
            val uncertainMs: Long,
        ) : Output
        data class Confirmed(val platform: Platform, val sessionId: String, val atMs: Long, val dedupeKey: String) : Output
    }

    private class PlatformState {
        var state = State.WAITING_FOR_ALLOWED_APP
        var sessionId: String? = null
        var sessionStartWall = 0L
        var lastActivityWall = 0L
        var lastActivityElapsed = 0L
        var lastHeartbeatElapsed = 0L
        var baseline: VisibleItem? = null
        var seq = 0
        var uncertainMs = 0L
        var uncertainSinceElapsed: Long? = null
    }

    private val states = HashMap<Platform, PlatformState>()

    fun stateOf(platform: Platform): State = states[platform]?.state ?: State.WAITING_FOR_ALLOWED_APP

    fun process(o: Observation): List<Output> {
        val out = ArrayList<Output>(2)
        when (o.kind) {
            Observation.Kind.SERVICE_CONNECTED -> {
                states.keys.toList().forEach { endSession(it, o, EndReason.SERVICE_DISCONNECTED, true, out) }
                states.clear()
                adapters.values.forEach { it.reset() }
            }
            Observation.Kind.SERVICE_DISCONNECTED -> endAll(o, EndReason.SERVICE_DISCONNECTED, out, clearBaseline = true)
            Observation.Kind.SCREEN_OFF -> endAll(o, EndReason.SCREEN_OFF, out, clearBaseline = true)
            Observation.Kind.TICK -> {
                states.forEach { (p, s) ->
                    if (s.sessionId != null && o.elapsedMs - s.lastActivityElapsed > idleTimeoutMs) {
                        // Keep the baseline: the same ephemeral container + index means the same item.
                        endSession(p, o, EndReason.IDLE, true, out)
                    }
                }
            }
            Observation.Kind.WINDOW_CHANGED -> {
                val p = o.platform ?: return out
                if (handlePaused(p, o, out)) return out
                val s = states.getOrPut(p) { PlatformState() }
                // Modal, comments sheet, new screen: a fresh baseline is required before any count.
                closeUncertain(s, o.elapsedMs)
                s.baseline = null
                if (s.state == State.WAITING_FOR_ALLOWED_APP || s.sessionId == null) s.state = State.ALLOWED_APP_FOREGROUND
                touch(s, o)
            }
            Observation.Kind.SCROLLED -> {
                val p = o.platform ?: return out
                if (handlePaused(p, o, out)) return out
                val adapter = adapters[p] ?: return out
                val s = states.getOrPut(p) { PlatformState() }
                if (s.state == State.WAITING_FOR_ALLOWED_APP) s.state = State.ALLOWED_APP_FOREGROUND
                when (adapter.recognizeFeed(o)) {
                    FeedSignal.NOT_FEED -> {
                        endSession(p, o, EndReason.FEED_EXIT, false, out)
                        s.baseline = null
                        s.state = State.ALLOWED_APP_FOREGROUND
                    }
                    FeedSignal.UNKNOWN -> if (s.sessionId != null) touch(s, o)
                    FeedSignal.FEED_UNRESOLVED -> {
                        ensureSession(p, s, o, out)
                        if (s.uncertainSinceElapsed == null) s.uncertainSinceElapsed = o.elapsedMs
                        s.baseline = null
                        s.state = State.UNCERTAIN
                        touch(s, o)
                    }
                    FeedSignal.FEED -> {
                        ensureSession(p, s, o, out)
                        closeUncertain(s, o.elapsedMs)
                        val item = adapter.currentItem(o)
                        if (item == null) {
                            s.state = State.UNCERTAIN
                            if (s.uncertainSinceElapsed == null) s.uncertainSinceElapsed = o.elapsedMs
                        } else {
                            val prev = s.baseline ?: adapter.originItem(o)
                            if (prev == null || prev == item) {
                                s.baseline = item // initial visible video: zero (AC01)
                            } else {
                                when (adapter.recognizeTransition(prev, item, o)) {
                                    TransitionVerdict.NEW_ITEM -> {
                                        s.seq += 1
                                        out += Output.Confirmed(p, s.sessionId!!, o.wallMs, "${s.sessionId}:${s.seq}")
                                        s.baseline = item
                                    }
                                    TransitionVerdict.DUPLICATE_UI_EVENT -> Unit // AC03
                                    TransitionVerdict.INSUFFICIENT_EVIDENCE -> s.baseline = item
                                }
                            }
                            s.state = State.FEED_BASELINE
                        }
                        touch(s, o)
                    }
                }
                maybeHeartbeat(p, s, o, out)
            }
        }
        return out
    }

    private fun handlePaused(p: Platform, o: Observation, out: MutableList<Output>): Boolean {
        if (!isPaused(p)) return false
        endSession(p, o, EndReason.PAUSED, false, out)
        states[p]?.apply { baseline = null; state = State.WAITING_FOR_ALLOWED_APP }
        return true // AC08: paused platform events are dropped
    }

    private fun ensureSession(p: Platform, s: PlatformState, o: Observation, out: MutableList<Output>) {
        if (s.sessionId != null) return
        s.sessionId = newId()
        s.sessionStartWall = o.wallMs
        s.lastActivityWall = o.wallMs
        s.lastActivityElapsed = o.elapsedMs
        s.lastHeartbeatElapsed = o.elapsedMs
        s.seq = 0
        s.uncertainMs = 0
        s.uncertainSinceElapsed = null
        out += Output.SessionStarted(s.sessionId!!, p, o.wallMs)
    }

    private fun touch(s: PlatformState, o: Observation) {
        s.lastActivityWall = o.wallMs
        s.lastActivityElapsed = o.elapsedMs
    }

    private fun closeUncertain(s: PlatformState, nowElapsed: Long) {
        s.uncertainSinceElapsed?.let { s.uncertainMs += (nowElapsed - it).coerceAtLeast(0) }
        s.uncertainSinceElapsed = null
    }

    private fun maybeHeartbeat(p: Platform, s: PlatformState, o: Observation, out: MutableList<Output>) {
        val id = s.sessionId ?: return
        if (o.elapsedMs - s.lastHeartbeatElapsed >= HEARTBEAT_MS) {
            s.lastHeartbeatElapsed = o.elapsedMs
            out += Output.SessionHeartbeat(id, p, s.sessionStartWall, s.lastActivityWall, s.uncertainMs)
        }
    }

    private fun endAll(o: Observation, reason: String, out: MutableList<Output>, clearBaseline: Boolean) {
        states.forEach { (p, s) ->
            endSession(p, o, reason, true, out)
            if (clearBaseline) s.baseline = null
            s.state = State.WAITING_FOR_ALLOWED_APP
        }
    }

    private fun endSession(p: Platform, o: Observation, reason: String, inferred: Boolean, out: MutableList<Output>) {
        val s = states[p] ?: return
        val id = s.sessionId ?: return
        // Uncertain time only up to the last observed activity — never extrapolated.
        closeUncertain(s, if (inferred) s.lastActivityElapsed else o.elapsedMs)
        val end = if (inferred) s.lastActivityWall else o.wallMs
        out += Output.SessionEnded(id, p, s.sessionStartWall, maxOf(end, s.sessionStartWall), reason, inferred, s.uncertainMs)
        s.sessionId = null
        s.uncertainMs = 0
        if (s.state == State.UNCERTAIN || s.state == State.FEED_BASELINE) s.state = State.ALLOWED_APP_FOREGROUND
    }

    companion object {
        const val DETECTOR_VERSION = 1
        const val DEFAULT_IDLE_TIMEOUT_MS = 120_000L
        const val HEARTBEAT_MS = 30_000L
    }
}
