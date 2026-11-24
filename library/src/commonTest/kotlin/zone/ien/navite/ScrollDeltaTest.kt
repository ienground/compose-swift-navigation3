package zone.ien.navite

import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollDeltaTest {

    private fun list(absolute: Float, vararg items: Pair<String, Int>) = LazyLayoutSnapshot(
        absolutePx = absolute,
        exact = false,
        canScrollForward = true,
        isScrollInProgress = false,
        visibleItems = items.map { VisibleItem(it.first, it.second) },
    )

    @Test
    fun firstSnapshotReportsItsAbsolutePosition() {
        assertEquals(250f, scrollDeltaPx(null, list(250f, "a" to 0)))
    }

    @Test
    fun deltaUsesAnItemVisibleInBothLayouts() {
        // Estimates disagree (average item size changed), the shared item gives the exact movement.
        val before = list(1000f, "row10" to -20, "row11" to 100)
        val after = list(1090f, "row11" to 40, "row12" to 160)
        assertEquals(60f, scrollDeltaPx(before, after))
    }

    @Test
    fun keysSurviveInsertionsAboveTheViewport() {
        // An item inserted above shifts indices but not the visible items' offsets: no movement.
        val before = list(500f, "x" to 0, "y" to 120)
        val after = list(620f, "x" to 0, "y" to 120)
        assertEquals(0f, scrollDeltaPx(before, after))
    }

    @Test
    fun jumpWithoutSharedItemsFallsBackToAbsoluteDifference() {
        val before = list(100f, "a" to 0)
        val after = list(4000f, "z" to 0)
        assertEquals(3900f, scrollDeltaPx(before, after))
    }

    @Test
    fun scrollStateDeltaIsExact() {
        val before = ScrollStateSnapshot(absolutePx = 10f, canScrollForward = true, isScrollInProgress = false)
        val after = ScrollStateSnapshot(absolutePx = 42f, canScrollForward = true, isScrollInProgress = false)
        assertEquals(32f, scrollDeltaPx(before, after))
    }
}
