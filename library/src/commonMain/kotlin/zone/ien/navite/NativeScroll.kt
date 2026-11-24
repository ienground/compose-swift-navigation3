package zone.ien.navite

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * [NativeScrollContent]는 공통 Scaffold에서 고정된 바를 제외한 콘텐츠의 바운스를 처리합니다.
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
 * 고정된 바를 제외한 화면 콘텐츠의 네이티브 바운스를 처리합니다.
 *
 * 공통 Scaffold의 content 슬롯에 한 번 연결합니다. 개별 스크롤 컨테이너의 Modifier는 변경하지 않습니다.
 * 중첩된 Scaffold에서는 안쪽 콘텐츠 영역만 움직여 바깥 프레임 안의 고정 바를 유지합니다.
 * 그 밖의 플랫폼에서는 일반 Box로 동작합니다.
 *
 * @param modifier 콘텐츠 영역의 배치와 크기
 * @param content 스크롤 콘텐츠
 */
@Composable
public fun NativeScrollContent(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalNativeScrollHost.current
    val parent = LocalNativeScrollContentOwner.current
    val owner = remember(host) { host?.let { NativeScrollContentOwner(it) } }
    DisposableEffect(owner, parent) {
        val linkedParent = parent?.takeIf { owner != null && it.host === host }
        linkedParent?.let { it.children++ }
        onDispose { linkedParent?.let { it.children-- } }
    }
    val bounce = if (owner != null) {
        Modifier.graphicsLayer {
            translationY = if (owner.children == 0) owner.host.overscrollOffset.toFloat() * density else 0f
        }
    } else {
        Modifier
    }
    Box(modifier = modifier.then(bounce)) {
        CompositionLocalProvider(LocalNativeScrollContentOwner provides owner) {
            content()
        }
    }
}

private class NativeScrollContentOwner(val host: NativeScrollHost) {
    var children by mutableIntStateOf(0)
}

private val LocalNativeScrollContentOwner = staticCompositionLocalOf<NativeScrollContentOwner?> { null }

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
                            remaining = current.remainingPx?.let { it / density.toDouble() },
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

/**
 * 현재 네이티브 스크롤 호스트에 당겨서 새로고침을 연결합니다.
 *
 * @param isRefreshing 데이터 갱신이 진행 중인지 여부. false가 되면 인디케이터를 닫습니다.
 * @param onRefresh 사용자가 당겨서 새로고침을 시작할 때 실행할 콜백
 * @return 호스트가 있으면 true. 그 외에는 호출자가 대체 UI를 표시할 수 있습니다.
 */
@Composable
public fun NativePullToRefresh(isRefreshing: Boolean, onRefresh: () -> Unit): Boolean {
    val host = LocalNativeScrollHost.current ?: return false
    val owner = remember(host) { Any() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val refreshing by rememberUpdatedState(isRefreshing)

    DisposableEffect(host, owner) {
        host.attachRefresh(owner) { if (!refreshing) currentOnRefresh() }
        onDispose { host.detachRefresh(owner) }
    }
    LaunchedEffect(host, owner, isRefreshing) {
        if (!isRefreshing) host.endRefresh(owner)
    }
    return true
}

/** Implemented by the platform screen host (the iOS scroll proxy). */
internal interface NativeScrollHost {
    /** Live padding (reads snapshot state at layout time, so changes only re-layout). */
    val contentPadding: PaddingValues

    val overscrollOffset: Double

    /**
     * [scrollBy] scrolls the content by a delta in dp and returns the consumed dp.
     * [background] fills the overscroll area ([Color.Unspecified]: platform default).
     */
    fun attach(background: Color, scrollBy: (Double) -> Double): NativeScrollAttachment

    /** Area (in dp, relative to the screen's Compose root) where the native scroll must not start; null removes it. */
    fun setExclusion(owner: Any, area: Rect?)

    fun attachRefresh(owner: Any, onRefresh: () -> Unit)
    fun endRefresh(owner: Any)
    fun detachRefresh(owner: Any)
}

internal interface NativeScrollAttachment {
    fun onContentLayout(layout: ContentLayout)
    fun detach()
}

internal val LocalNativeScrollHost = staticCompositionLocalOf<NativeScrollHost?> { null }
