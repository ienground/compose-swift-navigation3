package zone.ien.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How a screen's primary vertical scroll container has to be configured on the current platform.
 *
 * On iOS, inside a screen hosted by this library, the native `UIScrollView` behind the screen
 * owns the vertical scroll gesture and physics so that the system navigation bar can track it
 * (large title collapse, scroll edge appearance, iOS 26 scroll edge effect). The Compose
 * container must then not handle user scrolling itself, and must apply [contentPadding] which
 * follows the navigation bar height (it changes every frame while a large title collapses).
 *
 * Everywhere else (Android, desktop, web, or iOS outside a native screen) this is a no-op:
 * [userScrollEnabled] is `true` and [contentPadding] is zero.
 *
 * ```
 * val listState = rememberLazyListState()
 * val nativeScroll = rememberNativeScroll(listState)
 * LazyColumn(
 *     state = listState,
 *     userScrollEnabled = nativeScroll.userScrollEnabled,
 *     contentPadding = nativeScroll.contentPadding,
 * ) { ... }
 * ```
 */
@Stable
public class NativeScroll internal constructor(
    /** Pass to the container's `userScrollEnabled` (`verticalScroll(enabled = ...)`). */
    public val userScrollEnabled: Boolean,
    /** Padding the platform navigation chrome requires around the scrolling content. */
    public val contentPadding: PaddingValues,
)

/**
 * Connects [state] to the platform navigation chrome. Only one container per screen can be
 * connected; connecting another one replaces the previous one.
 *
 * @param background the screen's background color. On iOS it fills the area uncovered while
 *   the content is pulled beyond its ends (rubber-band overscroll); unspecified uses the system
 *   background color.
 */
@Composable
public fun rememberNativeScroll(state: LazyListState, background: Color = Color.Unspecified): NativeScroll =
    rememberNativeScroll(state, background) { state.snapshot() }

/** [ScrollState] variant, for `Modifier.verticalScroll`. See the [LazyListState] overload. */
@Composable
public fun rememberNativeScroll(state: ScrollState, background: Color = Color.Unspecified): NativeScroll =
    rememberNativeScroll(state, background) { state.snapshot() }

/** [LazyGridState] variant, for `LazyVerticalGrid`. See the [LazyListState] overload. */
@Composable
public fun rememberNativeScroll(state: LazyGridState, background: Color = Color.Unspecified): NativeScroll =
    rememberNativeScroll(state, background) { state.snapshot() }

/**
 * [LazyStaggeredGridState] variant, for `LazyVerticalStaggeredGrid`.
 * See the [LazyListState] overload.
 */
@Composable
public fun rememberNativeScroll(state: LazyStaggeredGridState, background: Color = Color.Unspecified): NativeScroll =
    rememberNativeScroll(state, background) { state.snapshot() }

/**
 * Padding the platform navigation chrome requires around screen content that does not scroll.
 * Zero where the chrome is drawn by the app itself (Android, desktop, web).
 */
@Composable
public fun nativeContentPadding(): PaddingValues =
    LocalNativeScrollHost.current?.contentPadding ?: ZeroPadding

private val ZeroPadding = PaddingValues(0.dp)

@Composable
private fun rememberNativeScroll(
    state: androidx.compose.foundation.gestures.ScrollableState,
    background: Color,
    snapshot: () -> ScrollSnapshot,
): NativeScroll {
    val host = LocalNativeScrollHost.current
    val density = LocalDensity.current.density

    if (host != null) {
        LaunchedEffect(host, state, density, background) {
            val attachment = host.attach(background) { deltaDp ->
                state.dispatchRawDelta((deltaDp * density).toFloat()) / density.toDouble()
            }
            try {
                var previous: ScrollSnapshot? = null
                snapshotFlow(snapshot).collect { current ->
                    val delta = scrollDeltaPx(previous, current)
                    previous = current
                    attachment.onContentLayout(
                        ContentLayout(
                            absolute = current.absolutePx / density.toDouble(),
                            exact = current.exact,
                            delta = delta / density.toDouble(),
                            canScrollForward = current.canScrollForward,
                            contentScrolling = current.isScrollInProgress,
                        ),
                    )
                }
            } finally {
                attachment.detach()
            }
        }
    }

    return remember(host) {
        NativeScroll(
            userScrollEnabled = host == null,
            contentPadding = host?.contentPadding ?: ZeroPadding,
        )
    }
}

/**
 * Excludes this element from the native vertical scroll gesture: on iOS, a vertical drag that
 * starts inside it is not taken by the screen's native scroll view and reaches Compose instead.
 * Use it for content that drags vertically itself (vertical sliders, reorder handles, drawing).
 * Elsewhere it does nothing.
 */
public fun Modifier.nativeScrollExclusion(): Modifier = composed {
    val host = LocalNativeScrollHost.current ?: return@composed this
    val owner = remember { Any() }
    val density = LocalDensity.current.density
    DisposableEffect(host, owner) {
        onDispose { host.setExclusion(owner, null) }
    }
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        host.setExclusion(
            owner,
            Rect(bounds.left / density, bounds.top / density, bounds.right / density, bounds.bottom / density),
        )
    }
}

/** Implemented by the platform screen host (the iOS scroll proxy). */
internal interface NativeScrollHost {
    /** Live padding (reads snapshot state at layout time, so changes only re-layout). */
    val contentPadding: PaddingValues

    /**
     * [scrollBy] scrolls the content by a delta in dp and returns the consumed dp.
     * [background] fills the overscroll area ([Color.Unspecified]: platform default).
     */
    fun attach(background: Color, scrollBy: (Double) -> Double): NativeScrollAttachment

    /** Area (in dp, relative to the screen's Compose root) where the native scroll must not start; null removes it. */
    fun setExclusion(owner: Any, area: Rect?)
}

internal interface NativeScrollAttachment {
    fun onContentLayout(layout: ContentLayout)
    fun detach()
}

internal val LocalNativeScrollHost = staticCompositionLocalOf<NativeScrollHost?> { null }
