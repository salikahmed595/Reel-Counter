package com.reelcounter.app.tracking.engine

import com.reelcounter.app.core.Platform

/**
 * Ephemeral, structural-only view of one accessibility event (PRD §5.2).
 * Holds no text, captions, descriptions, creator names, URLs or video identifiers,
 * and is never persisted.
 */
data class Observation(
    val kind: Kind,
    val platform: Platform?,
    val wallMs: Long,
    val elapsedMs: Long,
    val container: Container? = null,
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val itemCount: Int = -1,
    val scrollDeltaX: Int = 0,
    val scrollDeltaY: Int = 0,
) {
    enum class Kind {
        /** A scrollable container in an allowlisted app scrolled. */
        SCROLLED,

        /** A new window/activity/dialog was shown inside an allowlisted app (modal, comments, etc). */
        WINDOW_CHANGED,

        /** Screen turned off / device locked. */
        SCREEN_OFF,

        /** Service (re)connected — establish fresh baselines, never replay. */
        SERVICE_CONNECTED,

        /** Service interrupted or unbinding. */
        SERVICE_DISCONNECTED,

        /** Periodic tick used to close idle sessions; carries no UI information. */
        TICK,
    }

    /**
     * @param key ephemeral identity of the scrolling view inside its window (window id + node id).
     *            Only valid during the window's lifetime; never stored.
     * @param widthFraction container width / screen width
     * @param heightFraction container height / screen height
     */
    data class Container(
        val key: String,
        val widthFraction: Float,
        val heightFraction: Float,
        /** Container height in pixels; used to derive pages from scroll offsets. 0 = unknown. */
        val heightPx: Int = 0,
    )
}
