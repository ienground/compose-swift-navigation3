package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation3.runtime.NavEntry
import platform.UIKit.NSDirectionalRectEdgeAll
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.hidesBottomBarWhenPushed
import platform.UIKit.navigationItem

/**
 * A view controller that shows Compose [content] as one screen of a `UINavigationController`.
 *
 * The system navigation bar tracks the screen's content: connect the primary vertical scroll
 * container with [rememberNativeScroll] and the large title collapses with the scroll, the bar
 * switches to its scrolled appearance and (iOS 26+) applies the scroll edge effect. Screens that
 * do not scroll apply [nativeContentPadding] instead.
 *
 * Configure the title and bar items on the returned controller's `navigationItem`.
 * Navigation 3 apps use [NativeNavigationViewController], which creates these per entry.
 */
public fun ComposeScreenViewController(content: @Composable () -> Unit): UIViewController =
    ComposeScreenController(content)

internal class ComposeScreenController(
    content: @Composable () -> Unit,
    private val onWillAppear: () -> Unit = {},
) : UIViewController(nibName = null, bundle = null) {

    private val proxy = ScrollProxy()

    private val composeController = ComposeUIViewController {
        CompositionLocalProvider(LocalNativeScrollHost provides proxy) {
            content()
        }
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
        proxy.updateInsets()
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        onWillAppear()
    }
}

/**
 * One Navigation 3 entry shown as a native screen ([controller]).
 *
 * The entry (already decorated by the host composition) is rendered here, so decorator state
 * (saveable state, ViewModel store) and its pop clean-up keep Navigation 3 semantics: the entry's
 * content leaves composition when the screen is removed, and Navigation 3 then runs `onPop`.
 */
internal class EntryScreen(entry: NavEntry<*>, leases: ContentLeases) {

    private val state = EntryScreenState(entry, leases)

    val controller = ComposeScreenController(content = { state.Content() }, onWillAppear = state::appear)

    val contentKey: Any get() = state.contentKey

    var entry: NavEntry<*>
        get() = state.entry
        set(value) {
            state.entry = value
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
        controller.navigationItem.title = entry.nativeTitle
        controller.navigationItem.largeTitleDisplayMode = if (entry.nativeLargeTitle) {
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
        } else {
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
        }
        // Must be set before the push to take effect.
        controller.hidesBottomBarWhenPushed = entry.nativeHidesTabBar
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
