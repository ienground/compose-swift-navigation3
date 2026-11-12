package zone.ien.navigation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One layout of the Compose scroll container, in dp (== UIKit points on iOS).
 *
 * @property absolute scroll position from the content start. Exact when [exact], otherwise an
 *   estimate (e.g. LazyList far from its first item).
 * @property delta how far the content actually moved since the previous layout.
 * @property contentScrolling Compose itself is scrolling (animateScrollToItem, bringIntoView, ...).
 */
internal data class ContentLayout(
    val absolute: Double,
    val exact: Boolean,
    val delta: Double,
    val canScrollForward: Boolean,
    val contentScrolling: Boolean,
)

/**
 * Platform-independent bookkeeping between a native scroll view (which owns the vertical
 * gesture and physics) and the Compose scroll container that follows it.
 *
 * Coordinates: the native offset is `contentOffset.y`, which rests at `-topInset`. The
 * Compose position in native space is therefore `nativeOffsetY + topInset`.
 */
internal class ScrollSync {

    /** Compose's scroll position in dp, tracked from actual layout deltas. */
    var contentOffset: Double = 0.0
        private set

    var canScrollForward: Boolean = true
        private set

    /** Deltas sent to Compose that have not shown up in a layout yet. */
    private var pendingDispatched = 0.0

    fun reset() {
        contentOffset = 0.0
        canScrollForward = true
        pendingDispatched = 0.0
    }

    /**
     * The native scroll view moved. Returns the delta Compose has to scroll by to follow it
     * (0 if it already matches). Overscroll past the top is not forwarded: the native bounce
     * moves the whole Compose view instead.
     */
    fun deltaForNativeOffset(nativeOffsetY: Double, topInset: Double): Double {
        val desired = max(0.0, nativeOffsetY + topInset)
        val delta = desired - (contentOffset + pendingDispatched)
        return if (abs(delta) < EPSILON) 0.0 else delta
    }

    fun onDispatched(delta: Double) {
        pendingDispatched += delta
    }

    /**
     * Compose laid out. Returns the native offset to apply when Compose scrolled on its own
     * (or drifted while the native side is idle), or `null` when the native side is already right.
     */
    fun onContentLayout(layout: ContentLayout, nativeIdle: Boolean, topInset: Double): Double? {
        val causedByNative = pendingDispatched != 0.0
        pendingDispatched = 0.0
        canScrollForward = layout.canScrollForward
        contentOffset += layout.delta

        return when {
            // Movement we asked for (possibly less, at the end of the content): nothing to mirror.
            causedByNative && !layout.contentScrolling -> null
            // Compose scrolled by itself: move the native view by the same amount.
            abs(layout.delta) >= EPSILON -> contentOffset - topInset
            // Near the start the position is exact: correct drift from estimated deltas.
            layout.exact && nativeIdle && abs(layout.absolute - contentOffset) > REANCHOR_THRESHOLD -> {
                contentOffset = layout.absolute
                contentOffset - topInset
            }
            else -> null
        }
    }

    /**
     * Native content height. While Compose can scroll further the remaining length is unknown,
     * so keep plenty of room (only growing, to not clamp a running deceleration). At the real
     * end, the native max offset equals Compose's end so UIKit bounces there.
     */
    fun contentHeight(viewport: Double, topInset: Double, bottomInset: Double, currentHeight: Double): Double =
        if (canScrollForward) {
            max(currentHeight, contentOffset + viewport * 3)
        } else {
            max(0.0, contentOffset + viewport - topInset - bottomInset)
        }

    companion object {
        const val EPSILON = 0.01
        const val REANCHOR_THRESHOLD = 1.0

        fun maxOffsetY(contentHeight: Double, viewport: Double, topInset: Double, bottomInset: Double): Double =
            max(-topInset, contentHeight + bottomInset - viewport)

        /**
         * Where the Compose view sits in native content coordinates: on the viewport in the
         * normal range (Compose renders the scroll), at the edge during rubber-band overscroll
         * (so the content visibly bounces with it).
         */
        fun pinnedOffsetY(nativeOffsetY: Double, topInset: Double, maxOffsetY: Double): Double =
            min(max(nativeOffsetY, -topInset), maxOffsetY)
    }
}
