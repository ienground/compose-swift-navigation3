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
 * @property remaining how far the content can still scroll forward, when known (see
 *   [ScrollSnapshot.remainingPx]); null while the end is not laid out.
 */
internal data class ContentLayout(
    val absolute: Double,
    val exact: Boolean,
    val delta: Double,
    val canScrollForward: Boolean,
    val contentScrolling: Boolean,
    val remaining: Double? = null,
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

    /** How far Compose can still scroll forward as of the last layout, when known. */
    var remaining: Double? = null
        private set

    /** Deltas sent to Compose that have not shown up in a layout yet. */
    private var pendingDispatched = 0.0

    /** Compose's end position, when known: the content cannot scroll beyond it. */
    private val endOffset: Double?
        get() = remaining?.let { contentOffset + it } ?: contentOffset.takeIf { !canScrollForward }

    fun reset() {
        contentOffset = 0.0
        canScrollForward = true
        remaining = null
        pendingDispatched = 0.0
    }

    /**
     * The native scroll view moved. Returns the delta Compose has to scroll by to follow it
     * (0 if it already matches). Overscroll past either end is not forwarded: the native bounce
     * moves only the connected Compose scroll container instead, and Compose stays exactly at its end.
     */
    fun deltaForNativeOffset(nativeOffsetY: Double, topInset: Double): Double {
        var desired = max(0.0, nativeOffsetY + topInset)
        endOffset?.let { desired = min(desired, it) }
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
        remaining = layout.remaining
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
     * Native content height. Once Compose's end is known ([remaining], or the end reached), the
     * native max offset equals that end, so a deceleration runs into it and UIKit bounces there
     * as for any scroll view. It does not depend on where Compose is: a small movement near the
     * end (e.g. while bouncing) leaves it unchanged.
     *
     * While the end is not laid out the remaining length is unknown, so keep plenty of room
     * (only growing, to not clamp a running deceleration).
     */
    fun contentHeight(viewport: Double, topInset: Double, bottomInset: Double, currentHeight: Double): Double {
        val end = endOffset ?: return max(currentHeight, contentOffset + viewport * 3)
        return max(0.0, end + viewport - topInset - bottomInset)
    }

    companion object {
        const val EPSILON = 0.01
        const val REANCHOR_THRESHOLD = 1.0

        fun maxOffsetY(contentHeight: Double, viewport: Double, topInset: Double, bottomInset: Double): Double =
            max(-topInset, contentHeight + bottomInset - viewport)

        /** 스크롤 끝을 넘은 거리만 콘텐츠의 바운스 이동량으로 반환합니다. */
        fun overscrollOffsetY(nativeOffsetY: Double, topInset: Double, maxOffsetY: Double): Double =
            min(max(nativeOffsetY, -topInset), maxOffsetY) - nativeOffsetY
    }
}
