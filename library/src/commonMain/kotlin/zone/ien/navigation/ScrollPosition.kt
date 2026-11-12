package zone.ien.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState

/** Scroll position of a container as seen in one layout, in px. */
internal sealed interface ScrollSnapshot {
    val absolutePx: Float
    val exact: Boolean
    val canScrollForward: Boolean
    val isScrollInProgress: Boolean
}

/** Item key and main axis offset (px), used to measure exact movement between two layouts. */
internal data class VisibleItem(val key: Any, val offset: Int)

internal data class LazyListSnapshot(
    override val absolutePx: Float,
    override val exact: Boolean,
    override val canScrollForward: Boolean,
    override val isScrollInProgress: Boolean,
    val visibleItems: List<VisibleItem>,
) : ScrollSnapshot

internal data class ScrollStateSnapshot(
    override val absolutePx: Float,
    override val canScrollForward: Boolean,
    override val isScrollInProgress: Boolean,
) : ScrollSnapshot {
    override val exact: Boolean get() = true
}

internal fun LazyListState.snapshot(): LazyListSnapshot {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val (absolute, exact) = when {
        visible.isEmpty() -> 0f to true
        // Exact while the first item is laid out (covers the large title collapse range).
        visible.first().index == 0 -> (-visible.first().offset).toFloat() to true
        else -> {
            val first = visible.first()
            val averageSize = visible.sumOf { it.size + info.mainAxisItemSpacing } / visible.size.toFloat()
            (first.index * averageSize - first.offset) to false
        }
    }
    return LazyListSnapshot(
        absolutePx = absolute,
        exact = exact,
        canScrollForward = canScrollForward,
        isScrollInProgress = isScrollInProgress,
        visibleItems = visible.map { VisibleItem(it.key, it.offset) },
    )
}

internal fun ScrollState.snapshot(): ScrollStateSnapshot = ScrollStateSnapshot(
    absolutePx = value.toFloat(),
    canScrollForward = canScrollForward,
    isScrollInProgress = isScrollInProgress,
)

/**
 * How far the content moved between two layouts, in px (positive = towards the end).
 *
 * Exact when an item (by key) is visible in both layouts, which is the case for every
 * frame of a drag or fling. Falls back to the difference of absolute positions for jumps
 * (e.g. scrollToItem far away), which may be estimated.
 */
internal fun scrollDeltaPx(previous: ScrollSnapshot?, current: ScrollSnapshot): Float {
    if (previous == null) return current.absolutePx
    if (previous is LazyListSnapshot && current is LazyListSnapshot) {
        val previousOffsets = previous.visibleItems.associate { it.key to it.offset }
        for (item in current.visibleItems) {
            val before = previousOffsets[item.key] ?: continue
            return (before - item.offset).toFloat()
        }
    }
    return current.absolutePx - previous.absolutePx
}
