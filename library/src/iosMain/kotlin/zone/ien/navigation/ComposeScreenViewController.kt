package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation3.runtime.NavEntry
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.NSDirectionalRectEdgeAll
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.hidesBottomBarWhenPushed
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.UIKit.tabBarController
import platform.UIKit.transitionCoordinator
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * A view controller that shows Compose [content] as one screen of a `UINavigationController`.
 *
 * The system navigation bar tracks the screen's content: connect the primary vertical scroll
 * container with [rememberNativeScroll] and the large title collapses with the scroll, the bar
 * switches to its scrolled appearance and (iOS 26+) applies the scroll edge effect. Screens that
 * do not scroll apply [nativeContentPadding] instead.
 *
 * Bar items declared with [NativeBarItems] inside [content] become this screen's native
 * navigation bar items and toolbar. The title can also be set on the returned controller's
 * `navigationItem`. Navigation 3 apps use [NativeNavigationViewController], which creates these
 * per entry.
 */
public fun ComposeScreenViewController(content: @Composable () -> Unit): UIViewController =
    ComposeScreenController(content)

@OptIn(ExperimentalForeignApi::class, InternalComposeUiApi::class)
internal class ComposeScreenController(
    content: @Composable () -> Unit,
    private val onWillAppear: () -> Unit = {},
) : UIViewController(nibName = null, bundle = null) {

    private val proxy = ScrollProxy(::screenBottomInset)
    private val barState = NativeBarState()
    val bar = NativeBarApplier(this)
    val backHandling = NativeBackHandling(this)
    private val keyboardInset = KeyboardInset(proxy.scrollView)

    /** 엔트리의 탭 바 숨김 요청. 제목 전환과 독립적으로 반영한다. */
    var hidesTabBar: Boolean = false
    var hidesNavigationBar by mutableStateOf<Boolean?>(null)

    private var bottomInsetWithoutTabBar by mutableStateOf<Double?>(null)

    private fun tabBarHiddenForScreen(controllers: List<*>): Boolean {
        val index = controllers.indexOf(this)
        return index > 0 && controllers.subList(1, index + 1).any { controller ->
            val screen = controller as UIViewController
            (screen as? ComposeScreenController)?.hidesTabBar == true || screen.hidesBottomBarWhenPushed
        }
    }

    private fun updateBottomInset() {
        val controllers = navigationController?.viewControllers
        val tabs = tabBarController
        bottomInsetWithoutTabBar =
            if (tabs != null && controllers != null && tabBarHiddenForScreen(controllers)) {
                val window = viewIfLoaded?.window ?: tabs.viewIfLoaded?.window
                window?.safeAreaInsets?.useContents { bottom }
            } else {
                null
            }
    }

    private fun screenBottomInset(adjustedBottom: Double): Double {
        val homeIndicator = bottomInsetWithoutTabBar ?: return adjustedBottom
        val navigation = navigationController
        val toolbar = navigation?.toolbar?.takeIf { !navigation.toolbarHidden && it.window != null }
        val toolbarInset = if (toolbar == null) 0.0 else {
            val viewportBottom = view.convertRect(view.bounds, toView = null).useContents { origin.y + size.height }
            val toolbarTop = toolbar.convertRect(toolbar.bounds, toView = null).useContents { origin.y }
            viewportBottom - toolbarTop
        }
        return maxOf(homeIndicator, keyboardInset.overlap, toolbarInset)
    }

    fun updateNavigationBarVisibility(animated: Boolean) {
        val hidden = hidesNavigationBar ?: return
        val navigation = navigationController ?: return
        if (navigation.navigationBarHidden != hidden) navigation.setNavigationBarHidden(hidden, animated)
    }

    fun updateTabBarVisibility(animated: Boolean, onlyIfSelected: Boolean = false) {
        val navigation = navigationController ?: return
        val tabs = tabBarController ?: return
        if (onlyIfSelected && tabs.selectedViewController != navigation) return
        val controllers = navigation.viewControllers
        if (this !in controllers) return
        updateBottomInset()
        val hidden = tabBarHiddenForScreen(controllers)
        if (tabs.tabBarHidden == hidden) return
        val transition = navigation.transitionCoordinator
        if (hidden && animated && transition != null) {
            if (tabBarHidePending) return
            // 전환 중에는 탭 바 여백을 유지하고 화면과 함께 바만 페이드한다.
            val tabBar = tabs.tabBar
            val initialAlpha = tabBar.alpha
            tabBarHidePending = true
            val queued = transition.animateAlongsideTransitionInView(
                view = tabBar,
                animation = { tabBar.alpha = 0.0 },
                completion = {
                    tabBarHidePending = false
                    if (navigation.topViewController == this) {
                        updateTabBarVisibility(animated = false, onlyIfSelected = true)
                    }
                    tabBar.alpha = initialAlpha
                },
            )
            if (queued) return
            tabBarHidePending = false
        }
        tabs.setTabBarHidden(hidden, animated && !hidden)
    }

    private var tabBarHidePending = false
    private var appearance = 0L

    private val composeController = ComposeUIViewController {
        val platformInsets = LocalPlatformWindowInsets.current
        val density = LocalDensity.current
        val bottom = bottomInsetWithoutTabBar
        val screenInsets = remember(platformInsets, density, bottom) {
            if (bottom == null) platformInsets
            else ScreenWindowInsets(platformInsets, with(density) { bottom.dp.roundToPx() })
        }
        CompositionLocalProvider(
            LocalPlatformWindowInsets provides screenInsets,
            LocalNativeScreenProvidable provides true,
            LocalNativeNavigationBarHiddenProvidable provides (hidesNavigationBar == true),
            LocalNativeScrollHost provides proxy,
            LocalNativeBarState provides barState,
            // Back handlers of the content register with this screen's dispatcher, which routes
            // native back to them (NativeBackHandling), instead of Compose Multiplatform's.
            LocalNavigationEventDispatcherOwner provides backHandling.dispatcherOwner,
        ) {
            content()
            // Recorded after the content's effects, so NativeBarItems below registered first.
            SideEffect { bar.onComposed() }
        }
    }

    init {
        barState.listener = bar::apply
        barState.searchListener = bar::applySearch
        barState.subtitleListener = bar::applySubtitle
    }

    override fun loadView() {
        view = proxy.scrollView
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(composeController)
        proxy.scrollView.addSubview(composeController.view)
        composeController.didMoveToParentViewController(this)
        proxy.pinnedView = composeController.view
        setContentScrollView(proxy.scrollView, forEdge = NSDirectionalRectEdgeAll)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateBottomInset()
        proxy.updateInsets()
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        keyboardInset.stop()
    }

    override fun viewWillDisappear(animated: Boolean) {
        appearance++
        super.viewWillDisappear(animated)
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        val currentAppearance = ++appearance
        updateBottomInset()
        updateNavigationBarVisibility(animated)
        if (animated) {
            // UIKit이 전환 코디네이터를 설치한 뒤 표시 상태를 동기화한다.
            dispatch_async(dispatch_get_main_queue()) {
                if (appearance == currentAppearance) updateTabBarVisibility(animated = true)
            }
        } else {
            updateTabBarVisibility(animated = false)
        }
        // The appearing screen decides whether the navigation controller's toolbar is shown
        // (also when an interactive pop is cancelled and this screen appears again).
        bar.updateToolbarVisibility(animated, onlyIfTop = false)
        navigationController?.let(NativeBackSwipe::install)
        backHandling.updateBackAction()
        keyboardInset.start()
        onWillAppear()
    }
}

// 탭 바가 페이드되는 동안 나가는 화면은 탭 바 여백을 유지한다.
@OptIn(InternalComposeUiApi::class)
private class ScreenWindowInsets(
    delegate: PlatformWindowInsets,
    bottom: Int,
) : PlatformWindowInsets by delegate {
    override val navigationBars = delegate.navigationBars.withBottom(bottom)
    override val systemBars = delegate.systemBars.withBottom(bottom)

    private fun PlatformInsets.withBottom(value: Int): PlatformInsets = object : PlatformInsets by this {
        override val bottom: Int = value
    }
}

/**
 * One Navigation 3 entry shown as a native screen ([controller]).
 *
 * The entry (already decorated by the host composition) is rendered here, so decorator state
 * (saveable state, ViewModel store) and its pop clean-up keep Navigation 3 semantics: the entry's
 * content leaves composition when the screen is removed, and Navigation 3 then runs `onPop`.
 */
internal class EntryScreen(
    entry: NavEntry<*>,
    leases: ContentLeases,
    private val titleImages: TitleImages = TitleImages(),
) {

    private val state = EntryScreenState(entry, leases)

    val controller = ComposeScreenController(content = { state.Content() }, onWillAppear = state::appear)

    val contentKey: Any get() = state.contentKey

    var entry: NavEntry<*>
        get() = state.entry
        set(value) {
            state.entry = value
            applyChrome()
        }

    /** One of [NativeTabs]' shared screens: it hides the tab bar whatever its metadata says. */
    var shared: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyChrome()
        }

    init {
        applyChrome()
    }

    /** Removed from the native stack for good: drop the content so Navigation 3 can clean up. */
    fun markRemoved() {
        state.remove()
    }

    private fun applyChrome() {
        val entry = state.entry
        controller.bar.baseTitle = entry.nativeTitle
        controller.bar.initialTrailing = entry.nativeInitialTrailing
        controller.bar.titleImage = titleImages[state.contentKey]
        controller.navigationItem.largeTitleDisplayMode = if (entry.nativeLargeTitle) {
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
        } else {
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
        }
        controller.hidesTabBar = shared || entry.nativeHidesTabBar
    }
}

private class EntryScreenState(initialEntry: NavEntry<*>, private val leases: ContentLeases) {
    val id: Long = nextScreenId++
    val contentKey: Any = initialEntry.contentKey
    var entry: NavEntry<*> by mutableStateOf(initialEntry)
    private var removed by mutableStateOf(false)

    fun appear() {
        if (!removed) leases.acquire(contentKey, id)
    }

    fun remove() {
        removed = true
        leases.forget(contentKey, id)
    }

    @Composable
    fun Content() {
        if (removed || !leases.canCompose(contentKey, id)) return
        val current = entry
        DisposableEffect(contentKey) {
            leases.hold(contentKey, id)
            onDispose { leases.release(contentKey, id) }
        }
        current.Content()
    }

    private companion object {
        var nextScreenId = 0L
    }
}
