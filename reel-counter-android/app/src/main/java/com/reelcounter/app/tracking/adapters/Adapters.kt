package com.reelcounter.app.tracking.adapters

import com.reelcounter.app.core.Platform
import com.reelcounter.app.tracking.engine.AdapterReliability
import com.reelcounter.app.tracking.engine.FeedSignal
import com.reelcounter.app.tracking.engine.Observation
import com.reelcounter.app.tracking.engine.PlatformAdapter
import com.reelcounter.app.tracking.engine.TransitionVerdict
import com.reelcounter.app.tracking.engine.VisibleItem
import kotlin.math.abs

/**
 * Structural "full-screen vertical pager" heuristic shared by all adapters.
 *
 * It deliberately uses no app-specific view IDs or text (PRD §5.3: "Do not invent selector IDs").
 * Signals used:
 *  - the scrolling container spans ≥ [minWidthFraction] of screen width and ≥ [minHeightFraction] of height;
 *  - exactly one item is visible once the scroll settles (fromIndex == toIndex);
 *  - the scroll is vertical (horizontal pagers such as Stories are ignored).
 *
 * Offset-paging fallback (discovered on YouTube Shorts, Oct 2026 build, emulator): some feeds report
 * no item indices but do report per-frame scroll deltas. Net vertical offset per container is
 * accumulated and an item position is emitted only when the offset settles within
 * [SETTLE_TOLERANCE] of a whole container height. Bounces (net 0) and mid-swipe frames never count.
 *
 * Status is EXPERIMENTAL: it has NOT passed the per-platform 95% precision/recall gate and some app
 * builds expose no item indices at all (→ FEED_UNRESOLVED, never counted). Per-platform subclasses are
 * the place to add instrumented-test-derived rules once discovered on real devices.
 */
abstract class PagerFeedAdapter(
    final override val platform: Platform,
    private val minWidthFraction: Float = 0.9f,
    private val minHeightFraction: Float = 0.6f,
) : PlatformAdapter {
    override val rulesetVersion: Int = 1
    override val reliability: AdapterReliability = AdapterReliability.EXPERIMENTAL

    private var lastReason = "idle"

    /** Ephemeral per-container scroll offset (px) — structural only, cleared on reset. */
    private val offsets = HashMap<String, Long>()
    private var offsetItem: VisibleItem? = null
    private val settledSeen = HashSet<String>()

    private fun isFullScreen(c: Observation.Container) =
        c.widthFraction >= minWidthFraction && c.heightFraction >= minHeightFraction

    private fun isHorizontal(o: Observation) =
        o.scrollDeltaX != 0 && abs(o.scrollDeltaX) > abs(o.scrollDeltaY)

    override fun recognizeFeed(observation: Observation): FeedSignal {
        if (observation.kind != Observation.Kind.SCROLLED) return FeedSignal.UNKNOWN.also { lastReason = "not a scroll" }
        val c = observation.container ?: return FeedSignal.UNKNOWN.also { lastReason = "no container" }
        if (isHorizontal(observation)) return FeedSignal.UNKNOWN.also { lastReason = "horizontal pager ignored" }
        val visible = if (observation.fromIndex >= 0 && observation.toIndex >= 0) observation.toIndex - observation.fromIndex + 1 else -1
        if (!isFullScreen(c)) {
            // Small containers (comment sheets, menus, chips) are never the feed.
            return FeedSignal.UNKNOWN.also { lastReason = "partial-screen container" }
        }
        return when {
            visible == 1 -> FeedSignal.FEED.also { lastReason = "single full-screen item" }
            visible >= 3 -> FeedSignal.NOT_FEED.also { lastReason = "multi-item list" }
            visible == 2 -> FeedSignal.UNKNOWN.also { lastReason = "mid-swipe frame" }
            observation.scrollDeltaY != 0 && c.heightPx > 0 -> offsetPaging(c, observation.scrollDeltaY)
            else -> FeedSignal.FEED_UNRESOLVED.also { lastReason = "item index unavailable" }
        }
    }

    private fun offsetPaging(c: Observation.Container, dy: Int): FeedSignal {
        val offset = (offsets[c.key] ?: 0L) + dy
        if (offsets.size > 32 && c.key !in offsets) { offsets.clear(); settledSeen.clear() }
        offsets[c.key] = offset
        val page = Math.round(offset.toDouble() / c.heightPx)
        val drift = abs(offset - page * c.heightPx)
        return if (drift <= c.heightPx * SETTLE_TOLERANCE) {
            offsetItem = VisibleItem(c.key, page.toInt())
            FeedSignal.FEED.also { lastReason = "settled on page $page (offset)" }
        } else {
            offsetItem = null
            FeedSignal.UNKNOWN.also { lastReason = "in motion (offset)" }
        }
    }

    override fun currentItem(observation: Observation): VisibleItem? {
        val c = observation.container ?: return null
        if (observation.fromIndex < 0) return offsetItem?.takeIf { it.containerKey == c.key }
        if (observation.fromIndex < 0 || observation.fromIndex != observation.toIndex) return null
        return VisibleItem(c.key, observation.fromIndex)
    }

    override fun originItem(observation: Observation): VisibleItem? {
        val c = observation.container ?: return null
        if (observation.fromIndex >= 0 || c.key !in offsets) return null
        return if (settledSeen.add(c.key)) VisibleItem(c.key, 0) else null
    }

    override fun recognizeTransition(previous: VisibleItem?, current: VisibleItem, observation: Observation): TransitionVerdict =
        when {
            previous == null -> TransitionVerdict.INSUFFICIENT_EVIDENCE.also { lastReason = "no baseline" }
            previous.containerKey != current.containerKey -> TransitionVerdict.INSUFFICIENT_EVIDENCE.also { lastReason = "different container — rebaseline" }
            previous.index == current.index -> TransitionVerdict.DUPLICATE_UI_EVENT.also { lastReason = "same item" }
            else -> TransitionVerdict.NEW_ITEM.also { lastReason = "item ${previous.index}→${current.index}" }
        }

    override fun confidenceAndReason(): String = "${reliability.name}: $lastReason"

    override fun reset() {
        lastReason = "reset"
        offsets.clear()
        offsetItem = null
        settledSeen.clear()
    }

    companion object {
        const val SETTLE_TOLERANCE = 0.08
    }
}

class InstagramReelsAdapter : PagerFeedAdapter(Platform.INSTAGRAM)
class YoutubeShortsAdapter : PagerFeedAdapter(Platform.YOUTUBE)
class FacebookReelsAdapter : PagerFeedAdapter(Platform.FACEBOOK)
class TikTokFeedAdapter : PagerFeedAdapter(Platform.TIKTOK)

object Adapters {
    fun all(): Map<Platform, PlatformAdapter> = listOf(
        InstagramReelsAdapter(), YoutubeShortsAdapter(), FacebookReelsAdapter(), TikTokFeedAdapter(),
    ).associateBy { it.platform }
}
