package com.reelcounter.app

import com.reelcounter.app.core.Platform
import com.reelcounter.app.tracking.adapters.Adapters
import com.reelcounter.app.tracking.engine.Observation
import com.reelcounter.app.tracking.engine.Observation.Kind
import com.reelcounter.app.tracking.engine.TransitionEngine
import com.reelcounter.app.tracking.engine.TransitionEngine.Output
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Acceptance criteria from PRD §17, exercised against the pure counting engine. */
class TransitionEngineTest {
    private val paused = mutableSetOf<Platform>()
    private lateinit var engine: TransitionEngine
    private var t = 1_000_000L
    private var ids = 0

    private val feed = Observation.Container("w1:pager", 1f, 1f)
    private val otherFeed = Observation.Container("w2:pager", 1f, 1f)
    private val comments = Observation.Container("w1:comments", 1f, 0.45f)
    private val list = Observation.Container("w1:list", 1f, 0.9f)

    @Before
    fun setUp() {
        engine = TransitionEngine(Adapters.all(), { it in paused }, { "id${ids++}" })
        engine.process(obs(Kind.SERVICE_CONNECTED, null))
    }

    private fun obs(kind: Kind, p: Platform?, c: Observation.Container? = null, from: Int = -1, to: Int = from, dx: Int = 0, dy: Int = 0, advance: Long = 500): Observation {
        t += advance
        return Observation(kind, p, wallMs = t, elapsedMs = t, container = c, fromIndex = from, toIndex = to, scrollDeltaX = dx, scrollDeltaY = dy)
    }

    private fun scroll(index: Int, c: Observation.Container = feed, p: Platform = Platform.INSTAGRAM) =
        engine.process(obs(Kind.SCROLLED, p, c, index, index, dy = 1200))

    private fun List<Output>.confirmed() = filterIsInstance<Output.Confirmed>().size

    @Test
    fun ac01_initialVideoIsNotCounted() {
        assertEquals(0, scroll(0).confirmed())
    }

    @Test
    fun ac02_transitionToDistinctItemCountsOnce() {
        scroll(0)
        assertEquals(1, scroll(1).confirmed())
    }

    @Test
    fun ac03_duplicateEventsForSameItemCountOnce() {
        scroll(0)
        var total = scroll(1).confirmed()
        repeat(10) { total += scroll(1).confirmed() }
        assertEquals(1, total)
    }

    @Test
    fun ac04_revisitABACountsTwice() {
        scroll(0)
        val total = scroll(1).confirmed() + scroll(0).confirmed()
        assertEquals(2, total)
    }

    @Test
    fun ac05_commentsScrollDoesNotCount() {
        scroll(0)
        val r = engine.process(obs(Kind.SCROLLED, Platform.INSTAGRAM, comments, 3, 7, dy = 300))
        assertEquals(0, r.confirmed())
        // and the feed baseline is unaffected
        assertEquals(1, scroll(1).confirmed())
    }

    @Test
    fun ac06_reEntryAfterLeavingFeedRebaselines() {
        scroll(0)
        scroll(1)
        // regular multi-item list → leaves feed
        engine.process(obs(Kind.SCROLLED, Platform.INSTAGRAM, list, 2, 6, dy = 400))
        // re-entry into a (new) feed container: first visible is baseline only
        assertEquals(0, scroll(5, otherFeed).confirmed())
        assertEquals(1, scroll(6, otherFeed).confirmed())
    }

    @Test
    fun windowChangeRequiresFreshBaseline() {
        scroll(0)
        engine.process(obs(Kind.WINDOW_CHANGED, Platform.INSTAGRAM))
        assertEquals(0, scroll(1).confirmed())
        assertEquals(1, scroll(2).confirmed())
    }

    @Test
    fun ac08_pausedPlatformIsNotCountedOthersStillWork() {
        paused += Platform.TIKTOK
        scroll(0, p = Platform.TIKTOK)
        assertEquals(0, scroll(1, p = Platform.TIKTOK).confirmed())
        scroll(0, p = Platform.YOUTUBE)
        assertEquals(1, scroll(1, p = Platform.YOUTUBE).confirmed())
    }

    @Test
    fun horizontalPagerSuchAsStoriesIsIgnored() {
        engine.process(obs(Kind.SCROLLED, Platform.INSTAGRAM, feed, 0, 0, dx = 900))
        assertEquals(0, engine.process(obs(Kind.SCROLLED, Platform.INSTAGRAM, feed, 1, 1, dx = 900)).confirmed())
    }

    @Test
    fun midSwipeFramesDoNotCount() {
        scroll(0)
        assertEquals(0, engine.process(obs(Kind.SCROLLED, Platform.INSTAGRAM, feed, 0, 1, dy = 400)).confirmed())
        assertEquals(1, scroll(1).confirmed())
    }

    @Test
    fun unresolvedIndicesAreNeverCountedButTrackedAsUncertain() {
        scroll(0)
        var c = 0
        repeat(5) { c += engine.process(obs(Kind.SCROLLED, Platform.INSTAGRAM, feed, -1, -1, dy = 900, advance = 20_000)).confirmed() }
        assertEquals(0, c)
        val ended = engine.process(obs(Kind.SCREEN_OFF, null)).filterIsInstance<Output.SessionEnded>().single()
        assertTrue(ended.uncertainMs >= 80_000)
        assertTrue(ended.incomplete)
    }

    @Test
    fun screenOffEndsSessionAndRebaselines() {
        scroll(0)
        val out = engine.process(obs(Kind.SCREEN_OFF, null))
        assertEquals(1, out.filterIsInstance<Output.SessionEnded>().size)
        assertEquals(0, scroll(1).confirmed())
    }

    @Test
    fun idleTickClosesSessionAtLastActivity() {
        scroll(0)
        scroll(1)
        val lastActivity = t
        val out = engine.process(obs(Kind.TICK, null, advance = TransitionEngine.DEFAULT_IDLE_TIMEOUT_MS + 1))
        val ended = out.filterIsInstance<Output.SessionEnded>().single()
        assertEquals(lastActivity, ended.endedAtMs)
        assertTrue(ended.incomplete)
    }

    @Test
    fun dedupeKeysAreUniqueWithinSession() {
        scroll(0)
        val keys = (1..20).flatMap { scroll(it).filterIsInstance<Output.Confirmed>() }.map { it.dedupeKey }
        assertEquals(20, keys.toSet().size)
    }

    @Test
    fun ac17_unknownPackagesProduceNothing() {
        val r = engine.process(obs(Kind.SCROLLED, null, feed, 0, 0, dy = 900)) + engine.process(obs(Kind.SCROLLED, null, feed, 1, 1, dy = 900))
        assertTrue(r.isEmpty())
        assertEquals(null, Platform.forPackage("com.whatsapp"))
    }

    // Replays structural events captured from YouTube Shorts (Oct 2026 build) during adapter discovery:
    // no item indices, only per-frame vertical deltas in a full-width container 2136px tall.
    private val yt = Observation.Container("w9:shorts", 1f, 0.89f, heightPx = 2136)

    private fun ytGesture(vararg dys: Int): Int =
        dys.sumOf { engine.process(obs(Kind.SCROLLED, Platform.YOUTUBE, yt, -1, -1, dy = it, advance = 110)).confirmed() }

    @Test
    fun youtubeOffsetPaging_capturedSession() {
        assertEquals(0, ytGesture(744, 110, -677, -160, -17)) // bounce back: not a transition
        assertEquals(1, ytGesture(658, 779, 624, 87))          // A → B (first swipe counts; A is the excluded initial video)
        assertEquals(1, ytGesture(378, 644, 781, 333, 12))     // B → C
    }

    @Test
    fun youtubeOffsetPaging_revisitCountsAndMidSwipeDoesNot() {
        assertEquals(1, ytGesture(1000, 1136))      // A → B
        assertEquals(0, ytGesture(-900))            // dragging, not settled
        assertEquals(1, ytGesture(-1236))           // settles back on A: revisit counts (AC04)
    }
}
