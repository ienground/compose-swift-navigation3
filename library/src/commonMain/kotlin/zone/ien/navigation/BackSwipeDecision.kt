package zone.ien.navigation

/**
 * Progress (0..1) of an edge swipe back that moved [translation] points away from its edge on a
 * [width]-point wide screen.
 */
internal fun backSwipeProgress(translation: Double, width: Double): Float =
    if (width <= 0.0) 0f else (translation / width).coerceIn(0.0, 1.0).toFloat()

/**
 * Whether an edge swipe back released at [progress] completes, as UIKit's interactive pop decides:
 * a flick ([velocity] in points per second, positive away from the edge) decides by its direction,
 * otherwise the swipe completes past the middle of the screen.
 */
internal fun shouldCompleteBackSwipe(progress: Float, velocity: Double): Boolean = when {
    velocity >= BACK_SWIPE_FLICK_VELOCITY -> true
    velocity <= -BACK_SWIPE_FLICK_VELOCITY -> false
    else -> progress >= 0.5f
}

/** Release speed (points per second) above which the swipe direction alone decides. */
internal const val BACK_SWIPE_FLICK_VELOCITY: Double = 300.0
