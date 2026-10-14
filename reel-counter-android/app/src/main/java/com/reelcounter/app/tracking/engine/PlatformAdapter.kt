package com.reelcounter.app.tracking.engine

import com.reelcounter.app.core.Platform

/** Visible short-video item identified only by its position in an ephemeral container. */
data class VisibleItem(val containerKey: String, val index: Int)

enum class FeedSignal {
    /** Observation matches the adapter's short-form feed criteria. */
    FEED,

    /** Clearly a non-feed surface (e.g. a multi-item list) — closes the feed session. */
    NOT_FEED,

    /** Full-screen vertical container, but item position cannot be resolved. */
    FEED_UNRESOLVED,

    /** Not enough information either way (e.g. mid-swipe frame). Ignore. */
    UNKNOWN,
}

enum class TransitionVerdict { NEW_ITEM, DUPLICATE_UI_EVENT, INSUFFICIENT_EVIDENCE }

/**
 * Validation status of an adapter's ruleset against real app builds (PRD §5.3, §12).
 * Nothing ships as VERIFIED until it passes the ≥95% precision/recall gate.
 */
enum class AdapterReliability { VERIFIED, EXPERIMENTAL, UNAVAILABLE }

interface PlatformAdapter {
    val platform: Platform
    val rulesetVersion: Int
    val reliability: AdapterReliability

    fun recognizeFeed(observation: Observation): FeedSignal

    fun recognizeTransition(previous: VisibleItem?, current: VisibleItem, observation: Observation): TransitionVerdict

    fun currentItem(observation: Observation): VisibleItem?

    /**
     * The item the user was resting on before the first observed movement of a container, when the
     * adapter can infer it structurally (e.g. offset 0). Used only as a baseline — never counted.
     */
    fun originItem(observation: Observation): VisibleItem? = null

    /** Human-readable reason for the most recent decision (diagnostics only, never content). */
    fun confidenceAndReason(): String

    fun reset()
}
