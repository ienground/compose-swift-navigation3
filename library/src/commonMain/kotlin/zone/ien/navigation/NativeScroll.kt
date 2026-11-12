package zone.ien.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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
 */
@Composable
public fun rememberNativeScroll(state: LazyListState): NativeScroll =
    rememberNativeScroll(state) { state.snapshot() }

/** [ScrollState] variant, for `Modifier.verticalScroll`. See the [LazyListState] overload. */
@Composable
public fun rememberNativeScroll(state: ScrollState): NativeScroll =
    rememberNativeScroll(state) { state.snapshot() }

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
    snapshot: () -> ScrollSnapshot,
): NativeScroll {
    val host = LocalNativeScrollHost.current
    val density = LocalDensity.current.density

    if (host != null) {
        LaunchedEffect(host, state, density) {
            val attachment = host.attach { deltaDp ->
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

/** Implemented by the platform screen host (the iOS scroll proxy). */
internal interface NativeScrollHost {
    /** Live padding (reads snapshot state at layout time, so changes only re-layout). */
    val contentPadding: PaddingValues

    /** [scrollBy] scrolls the content by a delta in dp and returns the consumed dp. */
    fun attach(scrollBy: (Double) -> Double): NativeScrollAttachment
}

internal interface NativeScrollAttachment {
    fun onContentLayout(layout: ContentLayout)
    fun detach()
}

internal val LocalNativeScrollHost = staticCompositionLocalOf<NativeScrollHost?> { null }
