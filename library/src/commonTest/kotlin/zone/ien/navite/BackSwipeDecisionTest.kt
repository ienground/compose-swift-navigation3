package zone.ien.navite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackSwipeDecisionTest {

    @Test
    fun progressIsTheShareOfTheWidth() {
        assertEquals(0.25f, backSwipeProgress(translation = 100.0, width = 400.0))
        assertEquals(0f, backSwipeProgress(translation = -30.0, width = 400.0))
        assertEquals(1f, backSwipeProgress(translation = 900.0, width = 400.0))
        assertEquals(0f, backSwipeProgress(translation = 100.0, width = 0.0))
    }

    @Test
    fun slowReleaseCompletesPastTheMiddle() {
        assertFalse(shouldCompleteBackSwipe(progress = 0.49f, velocity = 0.0))
        assertTrue(shouldCompleteBackSwipe(progress = 0.5f, velocity = 0.0))
        assertTrue(shouldCompleteBackSwipe(progress = 0.8f, velocity = -100.0))
    }

    @Test
    fun flickDecidesByDirection() {
        assertTrue(shouldCompleteBackSwipe(progress = 0.1f, velocity = BACK_SWIPE_FLICK_VELOCITY))
        assertFalse(shouldCompleteBackSwipe(progress = 0.9f, velocity = -BACK_SWIPE_FLICK_VELOCITY))
    }
}
