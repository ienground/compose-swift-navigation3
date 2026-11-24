package zone.ien.navite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ScrollSyncTest {

    private val top = 168.0
    private val bottom = 34.0
    private val viewport = 874.0

    private fun layout(
        delta: Double,
        absolute: Double = 0.0,
        exact: Boolean = false,
        canScrollForward: Boolean = true,
        contentScrolling: Boolean = false,
        remaining: Double? = null,
    ) = ContentLayout(absolute, exact, delta, canScrollForward, contentScrolling, remaining)

    @Test
    fun restingNativeOffsetNeedsNoComposeScroll() {
        val sync = ScrollSync()
        assertEquals(0.0, sync.deltaForNativeOffset(nativeOffsetY = -top, topInset = top))
    }

    @Test
    fun nativeDragIsForwardedAsDelta() {
        val sync = ScrollSync()
        assertEquals(30.0, sync.deltaForNativeOffset(nativeOffsetY = -top + 30, topInset = top))
    }

    @Test
    fun overscrollPastTopIsNotForwarded() {
        val sync = ScrollSync()
        assertEquals(0.0, sync.deltaForNativeOffset(nativeOffsetY = -top - 80, topInset = top))
    }

    @Test
    fun largeTitleCollapseKeepsComposeStill() {
        // While the large title collapses, contentOffset grows and the inset shrinks by the same amount.
        val sync = ScrollSync()
        assertEquals(0.0, sync.deltaForNativeOffset(nativeOffsetY = -top + 20, topInset = top - 20))
    }

    @Test
    fun pendingDeltaIsNotDispatchedTwiceBeforeLayout() {
        val sync = ScrollSync()
        val first = sync.deltaForNativeOffset(-top + 10, top)
        sync.onDispatched(first)
        // A second didScroll in the same frame only sends the remainder.
        assertEquals(5.0, sync.deltaForNativeOffset(-top + 15, top))
    }

    @Test
    fun nativeDrivenLayoutIsNotMirroredBack() {
        val sync = ScrollSync()
        sync.onDispatched(sync.deltaForNativeOffset(-top + 40, top))
        assertNull(sync.onContentLayout(layout(delta = 40.0), nativeIdle = false, topInset = top))
        assertEquals(40.0, sync.contentOffset)
    }

    @Test
    fun endOfContentTracksActualMovementNotRequest() {
        val sync = ScrollSync()
        sync.onDispatched(sync.deltaForNativeOffset(-top + 50, top))
        // Only 20 could be consumed: the list reached its end.
        assertNull(sync.onContentLayout(layout(delta = 20.0, canScrollForward = false), nativeIdle = false, topInset = top))
        assertEquals(20.0, sync.contentOffset)
        assertFalse(sync.canScrollForward)
        // Native max offset now equals the content end, so UIKit bounces there.
        val height = sync.contentHeight(viewport, top, bottom, currentHeight = 5000.0)
        assertEquals(20.0 - top, ScrollSync.maxOffsetY(height, viewport, top, bottom))
    }

    @Test
    fun composeInitiatedScrollMovesNativeByTheSameDelta() {
        val sync = ScrollSync()
        val target = sync.onContentLayout(layout(delta = 300.0, contentScrolling = true), nativeIdle = true, topInset = top)
        assertEquals(300.0 - top, target)
    }

    @Test
    fun movementWithoutNativeRequestIsComposeInitiated() {
        // e.g. scrollToItem, which may already be finished (isScrollInProgress false) at layout time.
        val sync = ScrollSync()
        assertEquals(120.0 - top, sync.onContentLayout(layout(delta = 120.0), nativeIdle = true, topInset = top))
    }

    @Test
    fun firstLayoutOfRestoredPositionSyncsNative() {
        val sync = ScrollSync()
        val first = layout(delta = 640.0, absolute = 640.0)
        assertEquals(640.0 - top, sync.onContentLayout(first, nativeIdle = true, topInset = top))
    }

    @Test
    fun exactPositionReanchorsDriftWhenIdle() {
        val sync = ScrollSync()
        sync.onContentLayout(layout(delta = 500.0), nativeIdle = true, topInset = top) // estimated jump
        val target = sync.onContentLayout(layout(delta = 0.0, absolute = 480.0, exact = true), nativeIdle = true, topInset = top)
        assertEquals(480.0 - top, target)
        assertEquals(480.0, sync.contentOffset)
    }

    @Test
    fun noReanchorWhileNativeIsMoving() {
        val sync = ScrollSync()
        sync.onContentLayout(layout(delta = 500.0), nativeIdle = true, topInset = top)
        assertNull(sync.onContentLayout(layout(delta = 0.0, absolute = 480.0, exact = true), nativeIdle = false, topInset = top))
    }

    @Test
    fun contentHeightOnlyGrowsWhileMoreContentExists() {
        val sync = ScrollSync()
        assertEquals(9000.0, sync.contentHeight(viewport, top, bottom, currentHeight = 9000.0))
        assertEquals(viewport * 3, sync.contentHeight(viewport, top, bottom, currentHeight = 0.0))
    }

    @Test
    fun knownEndSetsTheExactNativeEndBeforeComposeReachesIt() {
        val sync = ScrollSync()
        sync.onDispatched(sync.deltaForNativeOffset(-top + 900, top))
        // The last item is laid out: 92 left. The native end is set now, while decelerating,
        // so UIKit runs into it and bounces (instead of learning about it past the end).
        sync.onContentLayout(layout(delta = 900.0, remaining = 92.0), nativeIdle = false, topInset = top)
        val height = sync.contentHeight(viewport, top, bottom, currentHeight = 5000.0)
        assertEquals(992.0 - top, ScrollSync.maxOffsetY(height, viewport, top, bottom))
    }

    @Test
    fun smallMovementNearTheEndKeepsTheNativeEnd() {
        // Before, Compose leaving its end by a few points (while bouncing) flipped canScrollForward
        // and grew the native content again; the next layout shrank it and UIKit cut the bounce.
        val sync = ScrollSync()
        sync.onContentLayout(layout(delta = 992.0, canScrollForward = false, remaining = 0.0), nativeIdle = true, topInset = top)
        val atEnd = sync.contentHeight(viewport, top, bottom, currentHeight = 5000.0)
        sync.onContentLayout(layout(delta = -2.0, canScrollForward = true, remaining = 2.0), nativeIdle = false, topInset = top)
        assertEquals(atEnd, sync.contentHeight(viewport, top, bottom, currentHeight = atEnd))
    }

    @Test
    fun overscrollPastTheEndIsNotForwarded() {
        val sync = ScrollSync()
        sync.onContentLayout(layout(delta = 900.0, remaining = 40.0), nativeIdle = true, topInset = top)
        // The native view is pulled 100 beyond Compose's end: only the 40 left reach Compose.
        assertEquals(40.0, sync.deltaForNativeOffset(nativeOffsetY = 1040.0 - top, topInset = top))
        sync.onDispatched(40.0)
        sync.onContentLayout(layout(delta = 40.0, canScrollForward = false, remaining = 0.0), nativeIdle = false, topInset = top)
        // Bouncing back towards the end moves the view, not the content.
        assertEquals(0.0, sync.deltaForNativeOffset(nativeOffsetY = 1010.0 - top, topInset = top))
        assertEquals(940.0, sync.contentOffset)
    }

    @Test
    fun remainingToEndOfLazyLayout() {
        // Last item ends at 1500 in a 700 tall content area with 80 after it: 880 left.
        assertEquals(880f, remainingToEnd(contentEnd = 1500, afterContentPadding = 80, viewportEndOffset = 700))
        // Content shorter than the viewport: nothing to scroll.
        assertEquals(0f, remainingToEnd(contentEnd = 300, afterContentPadding = 80, viewportEndOffset = 700))
    }

    @Test
    fun contentBouncesOnlyOutsideNativeScrollRange() {
        val maxOffset = 1000.0
        assertEquals(60.0, ScrollSync.overscrollOffsetY(-top - 60, top, maxOffset))
        assertEquals(0.0, ScrollSync.overscrollOffsetY(-top, top, maxOffset))
        assertEquals(0.0, ScrollSync.overscrollOffsetY(200.0, top, maxOffset))
        assertEquals(0.0, ScrollSync.overscrollOffsetY(maxOffset, top, maxOffset))
        assertEquals(-40.0, ScrollSync.overscrollOffsetY(maxOffset + 40, top, maxOffset))
    }
}
