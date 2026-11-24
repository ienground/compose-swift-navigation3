package zone.ien.navite

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState

/** Scroll position of a container as seen in one layout, in px. */
internal sealed interface ScrollSnapshot {
    val absolutePx: Float
    val exact: Boolean
    val canScrollForward: Boolean
    val isScrollInProgress: Boolean

    /**
     * How far the content can still scroll forward, in px, when it is known: always for
     * [ScrollState], and for lazy layouts once their last item is laid out. Null otherwise.
     */
    val remainingPx: Float?
}

/** Item key and main axis offset (px), used to measure exact movement between two layouts. */
internal data class VisibleItem(val key: Any, val offset: Int)

/** A lazy list or (vertical) grid: positions come from the laid out items. */
internal data class LazyLayoutSnapshot(
    override val absolutePx: Float,
    override val exact: Boolean,
    override val canScrollForward: Boolean,
    override val isScrollInProgress: Boolean,
    val visibleItems: List<VisibleItem>,
    override val remainingPx: Float? = null,
) : ScrollSnapshot

internal data class ScrollStateSnapshot(
    override val absolutePx: Float,
    override val canScrollForward: Boolean,
    override val isScrollInProgress: Boolean,
    override val remainingPx: Float? = null,
) : ScrollSnapshot {
    override val exact: Boolean get() = true
}

internal fun LazyListState.snapshot(): LazyLayoutSnapshot {
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
    val last = visible.lastOrNull()
    return LazyLayoutSnapshot(
        absolutePx = absolute,
        exact = exact,
        canScrollForward = canScrollForward,
        isScrollInProgress = isScrollInProgress,
        visibleItems = visible.map { VisibleItem(it.key, it.offset) },
        remainingPx = when {
            last == null -> 0f
            last.index == info.totalItemsCount - 1 ->
                remainingToEnd(last.offset + last.size, info.afterContentPadding, info.viewportEndOffset)
            else -> null
        },
    )
}

/** Vertical grids (`LazyVerticalGrid`): the main axis is y. */
internal fun LazyGridState.snapshot(): LazyLayoutSnapshot {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val first = visible.minByOrNull { it.index }
    val (absolute, exact) = when {
        first == null -> 0f to true
        // Item 0 is in the first row: exact while it is laid out.
        first.index == 0 -> (-first.offset.y).toFloat() to true
        else -> {
            val rowHeights = visible.groupBy { it.row }.values.map { row -> row.maxOf { it.size.height } }
            val averageRow = rowHeights.average().toFloat() + info.mainAxisItemSpacing
            (first.row * averageRow - first.offset.y) to false
        }
    }
    return LazyLayoutSnapshot(
        absolutePx = absolute,
        exact = exact,
        canScrollForward = canScrollForward,
        isScrollInProgress = isScrollInProgress,
        visibleItems = visible.map { VisibleItem(it.key, it.offset.y) },
        // The last row holds the last item, so it is laid out whole: its bottom is the content end.
        remainingPx = when {
            first == null -> 0f
            visible.any { it.index == info.totalItemsCount - 1 } -> remainingToEnd(
                visible.maxOf { it.offset.y + it.size.height }, info.afterContentPadding, info.viewportEndOffset,
            )
            else -> null
        },
    )
}

/** Vertical staggered grids (`LazyVerticalStaggeredGrid`): the main axis is y. */
internal fun LazyStaggeredGridState.snapshot(): LazyLayoutSnapshot {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    val first = visible.minByOrNull { it.index }
    val (absolute, exact) = when {
        first == null -> 0f to true
        // Item 0 starts the first lane at the very top: exact while it is laid out.
        first.index == 0 -> (-first.offset.y).toFloat() to true
        else -> {
            val lanes = visible.maxOf { it.lane } + 1
            val averageItem = visible.map { it.size.height }.average().toFloat() + info.mainAxisItemSpacing
            (first.index / lanes * averageItem - first.offset.y) to false
        }
    }
    return LazyLayoutSnapshot(
        absolutePx = absolute,
        exact = exact,
        canScrollForward = canScrollForward,
        isScrollInProgress = isScrollInProgress,
        visibleItems = visible.map { VisibleItem(it.key, it.offset.y) },
        // Items go to the shortest lane, so no item starts below the last one: once the last
        // item is laid out, every item is laid out or above the viewport.
        remainingPx = when {
            first == null -> 0f
            visible.any { it.index == info.totalItemsCount - 1 } -> remainingToEnd(
                visible.maxOf { it.offset.y + it.size.height }, info.afterContentPadding, info.viewportEndOffset,
            )
            else -> null
        },
    )
}

internal fun ScrollState.snapshot(): ScrollStateSnapshot = ScrollStateSnapshot(
    absolutePx = value.toFloat(),
    canScrollForward = canScrollForward,
    isScrollInProgress = isScrollInProgress,
    // maxValue is Int.MAX_VALUE until the content is measured.
    remainingPx = if (maxValue == Int.MAX_VALUE) null else (maxValue - value).coerceAtLeast(0).toFloat(),
)

/**
 * Distance (px) a lazy layout can still scroll forward, given where its content ends
 * ([contentEnd], relative to the start of the content area) and its layout info: at the end,
 * the content end plus [afterContentPadding] meets [viewportEndOffset].
 */
internal fun remainingToEnd(contentEnd: Int, afterContentPadding: Int, viewportEndOffset: Int): Float =
    (contentEnd + afterContentPadding - viewportEndOffset).coerceAtLeast(0).toFloat()

/**
 * How far the content moved between two layouts, in px (positive = towards the end).
 *
 * Exact when an item (by key) is visible in both layouts, which is the case for every
 * frame of a drag or fling. Falls back to the difference of absolute positions for jumps
 * (e.g. scrollToItem far away), which may be estimated.
 */
internal fun scrollDeltaPx(previous: ScrollSnapshot?, current: ScrollSnapshot): Float {
    if (previous == null) return current.absolutePx
    if (previous is LazyLayoutSnapshot && current is LazyLayoutSnapshot) {
        val previousOffsets = previous.visibleItems.associate { it.key to it.offset }
        for (item in current.visibleItems) {
            val before = previousOffsets[item.key] ?: continue
            return (before - item.offset).toFloat()
        }
    }
    return current.absolutePx - previous.absolutePx
}
