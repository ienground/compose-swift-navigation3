package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.systemBackgroundColor
import platform.UIKit.tabBarItem
import platform.darwin.NSObject

/**
 * Root view controller of a Navigation 3 app on iOS, using the native navigation controllers.
 *
 * [content] runs in a composition without UI that owns the navigation state. It creates the back
 * stacks (e.g. `rememberNavBackStack`) and declares the navigation with exactly one [NativeStack]
 * (a single `UINavigationController`) or [NativeTabs] (`UITabBarController` with one
 * `UINavigationController` per tab). Each entry becomes a native screen; its content is the
 * entry's composable, and its navigation bar comes from [nativeScreen] metadata.
 *
 * ```
 * fun MainViewController() = NativeNavigationViewController {
 *     val backStack = rememberNavBackStack(config, Home)
 *     NativeStack(backStack, entryProvider = appEntryProvider(backStack))
 * }
 * ```
 *
 * [content] must not emit UI itself.
 */
public fun NativeNavigationViewController(content: @Composable () -> Unit): UIViewController =
    NativeNavigationRootController(content)

/**
 * A single native navigation stack (`UINavigationController`) for [backStack].
 * Parameters mirror Navigation 3's `NavDisplay`.
 *
 * @param onBack called once per screen UIKit popped (back button, edge swipe, long-press menu).
 *   It must remove the last entry of [backStack], as `NavDisplay`'s `onBack` does.
 */
@Composable
public fun <T : Any> NativeStack(
    backStack: List<T>,
    onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (key: T) -> NavEntry<T>,
) {
    val root = requireRoot("NativeStack")
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    val currentOnBack by rememberUpdatedState(onBack)
    SideEffect { root.updateStack(entries) { currentOnBack() } }
}

/**
 * One tab of [NativeTabs].
 *
 * @param systemImage SF Symbol name of the tab bar icon.
 * @param onBack called once per screen UIKit popped in this tab. It must remove the last entry
 *   of [backStack].
 */
public class NativeTab<T : Any>(
    public val title: String,
    public val systemImage: String,
    public val backStack: List<T>,
    public val onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
)

/**
 * Native tabs (`UITabBarController`), each with its own navigation stack. Every tab keeps its
 * back stack while another tab is selected. Re-tapping the selected tab pops it to its root
 * (native behavior), reported through that tab's `onBack`.
 */
@Composable
public fun <T : Any> NativeTabs(
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (key: T) -> NavEntry<T>,
) {
    val root = requireRoot("NativeTabs")
    // One rememberDecoratedNavEntries per back stack, as Navigation 3 requires for multiple stacks.
    val entriesPerTab = tabs.mapIndexed { index, tab ->
        key(index) { rememberDecoratedNavEntries(tab.backStack, entryDecorators, entryProvider) }
    }
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    SideEffect {
        root.updateTabs(
            tabs = tabs.mapIndexed { index, tab ->
                TabState(tab.title, tab.systemImage, entriesPerTab[index], tab.onBack)
            },
            selectedTab = selectedTab,
            onTabSelected = { currentOnTabSelected(it) },
        )
    }
}

@Composable
private fun requireRoot(name: String): NativeNavigationRootController =
    checkNotNull(LocalNativeNavigationRoot.current) {
        "$name must be called inside NativeNavigationViewController { ... }"
    }

private val LocalNativeNavigationRoot = staticCompositionLocalOf<NativeNavigationRootController?> { null }

internal class TabState(
    val title: String,
    val systemImage: String,
    val entries: List<NavEntry<*>>,
    val onBack: () -> Unit,
)

internal class NativeNavigationRootController(
    content: @Composable () -> Unit,
) : UIViewController(nibName = null, bundle = null) {

    private val leases = ContentLeases()
    private val host = HostComposition {
        CompositionLocalProvider(LocalNativeNavigationRoot provides this) { content() }
    }

    private var stack: StackCoordinator? = null
    private var tabBarController: UITabBarController? = null
    private var tabStacks: List<StackCoordinator> = emptyList()
    private var tabItems: List<Pair<String, String>> = emptyList()
    private var onTabSelected: (Int) -> Unit = {}

    private val tabBarDelegate = object : NSObject(), UITabBarControllerDelegateProtocol {
        override fun tabBarController(
            tabBarController: UITabBarController,
            didSelectViewController: UIViewController,
        ) {
            onTabSelected(tabBarController.selectedIndex.toInt())
            Snapshot.sendApplyNotifications()
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemBackgroundColor
        host.start()
    }

    fun updateStack(entries: List<NavEntry<*>>, onBack: () -> Unit) {
        check(tabBarController == null) { "Use either NativeStack or NativeTabs, not both" }
        val coordinator = stack ?: StackCoordinator(UINavigationController(), leases).also {
            stack = it
            install(it.navigationController)
        }
        coordinator.update(entries, onBack)
    }

    fun updateTabs(tabs: List<TabState>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
        check(stack == null) { "Use either NativeStack or NativeTabs, not both" }
        this.onTabSelected = onTabSelected
        val items = tabs.map { it.title to it.systemImage }

        val controller = tabBarController ?: UITabBarController().also {
            it.delegate = tabBarDelegate
            tabBarController = it
            install(it)
        }
        if (tabStacks.size != tabs.size) {
            tabStacks = tabs.map { StackCoordinator(UINavigationController(), leases) }
            controller.setViewControllers(tabStacks.map { it.navigationController }, animated = false)
            tabItems = emptyList()
        }
        if (items != tabItems) {
            tabStacks.forEachIndexed { index, coordinator ->
                coordinator.navigationController.tabBarItem = UITabBarItem(
                    title = items[index].first,
                    image = UIImage.systemImageNamed(items[index].second),
                    selectedImage = null,
                )
            }
            tabItems = items
        }
        tabs.forEachIndexed { index, tab -> tabStacks[index].update(tab.entries, tab.onBack) }
        if (selectedTab in tabs.indices && controller.selectedIndex.toInt() != selectedTab) {
            controller.selectedIndex = selectedTab.toULong()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun install(child: UIViewController) {
        addChildViewController(child)
        child.view.setFrame(view.bounds)
        child.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(child.view)
        child.didMoveToParentViewController(this)
    }
}
