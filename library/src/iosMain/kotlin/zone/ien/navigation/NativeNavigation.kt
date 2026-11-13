package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSLog
import platform.Foundation.create
import platform.posix.memcpy
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.systemBackgroundColor
import platform.UIKit.tabBarItem
import platform.UIKit.willMoveToParentViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Root view controller of a Navigation 3 app on iOS, using the native navigation controllers.
 *
 * [content] runs in a composition without UI that owns the navigation state. It creates the back
 * stacks (e.g. `rememberNavBackStack`) and declares the navigation with one [NativeStack]
 * (a single `UINavigationController`) or [NativeTabs] (`UITabBarController` with one
 * `UINavigationController` per tab) at a time. Each entry becomes a native screen; its content is
 * the entry's composable, and its navigation bar comes from [nativeScreen] metadata.
 *
 * ```
 * fun MainViewController() = NativeNavigationViewController {
 *     val backStack = rememberNavBackStack(config, Home)
 *     NativeStack(backStack, entryProvider = appEntryProvider(backStack))
 * }
 * ```
 *
 * **Switching the root**: which [NativeStack] or [NativeTabs] is declared may change, e.g. from a
 * sign-in flow to the main tabs:
 * ```
 * if (signedIn) NativeTabs(tabs, ...) else NativeStack(signInBackStack, ...)
 * ```
 * Like replacing a window's root view controller, the switch cross-dissolves to the new
 * hierarchy and removes every screen of the previous one: their entries' content leaves
 * composition, as when a `NavDisplay` leaves composition. Tabs inside a pushed screen (nested tab
 * bars) are not supported.
 *
 * [content] must not emit UI itself.
 *
 * **State restoration**: pass the data from [saveNativeNavigationState] back as [restoredState]
 * to restore the back stacks, the selected tab and every entry's `rememberSaveable` state (scroll
 * positions included). Where to keep it is the app's choice; the native way is scene-scoped
 * storage (SwiftUI `@SceneStorage`, or `UIScene.stateRestorationActivity`), which iOS discards
 * when the user force-quits the app. Data that cannot be read (other format, corrupted) is ignored.
 *
 * [stateVersion] is saved with the state: bump it when saved routes or saveable state change
 * shape in an app update, so state written by the previous version is dropped instead of failing
 * to decode (e.g. inside `rememberNavBackStack`).
 */
public fun NativeNavigationViewController(
    restoredState: NSData? = null,
    stateVersion: Int = 0,
    content: @Composable () -> Unit,
): UIViewController = NativeNavigationRootController(
    content = content,
    restoredState = restoredState?.let { decodeState(it, stateVersion) },
    stateVersion = stateVersion,
)

/**
 * Serializes the navigation state of a controller created by [NativeNavigationViewController]
 * (call it when the scene goes to the background). Returns `null` for other controllers or when
 * nothing could be encoded.
 *
 * Values a `rememberSaveable` holds that cannot be persisted (e.g. enums, arbitrary objects; see
 * the supported types in AGENTS.md 6.8) are left out: after restoration only those start from
 * their initial value. ViewModels are not saved.
 */
public fun saveNativeNavigationState(controller: UIViewController): NSData? =
    (controller as? NativeNavigationRootController)?.saveState()

private const val STATE_VERSION = "version"
private const val STATE = "state"

private fun decodeState(data: NSData, expectedVersion: Int): Map<String, Any?>? = try {
    val saved = SavedStateCodec.decode(data.toByteArray().decodeToString()) as? Map<*, *>
    when (val version = saved?.get(STATE_VERSION)) {
        expectedVersion -> {
            @Suppress("UNCHECKED_CAST")
            saved[STATE] as? Map<String, Any?>
        }
        else -> {
            NSLog("compose-swift-navigation3: ignoring navigation state of version $version (expected $expectedVersion)")
            null
        }
    }
} catch (error: IllegalArgumentException) {
    NSLog("compose-swift-navigation3: ignoring unreadable navigation state (${error.message})")
    null
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val result = ByteArray(size)
    if (size > 0) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return result
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

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
    val declaration = rememberRootDeclaration(root)
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    val currentOnBack by rememberUpdatedState(onBack)
    SideEffect { root.updateStack(declaration, entries) { currentOnBack() } }
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
    val declaration = rememberRootDeclaration(root)
    // One rememberDecoratedNavEntries per back stack, as Navigation 3 requires for multiple stacks.
    val entriesPerTab = tabs.mapIndexed { index, tab ->
        key(index) { rememberDecoratedNavEntries(tab.backStack, entryDecorators, entryProvider) }
    }
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    SideEffect {
        root.updateTabs(
            declaration = declaration,
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

/**
 * Identifies one [NativeStack] or [NativeTabs] call while it is in the host composition. The root
 * shows the hierarchy of the declaration in composition and replaces it when another one takes
 * its place.
 */
@Composable
private fun rememberRootDeclaration(root: NativeNavigationRootController): Any {
    val declaration = remember(root) { Any() }
    // Remember observers run before side effects, and a leaving call's onDispose before an
    // entering call's effect, so an if/else switch retracts first and declares second.
    DisposableEffect(root, declaration) {
        root.declare(declaration)
        onDispose { root.retract(declaration) }
    }
    return declaration
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
    restoredState: Map<String, Any?>?,
    private val stateVersion: Int,
) : UIViewController(nibName = null, bundle = null) {

    private val leases = ContentLeases()
    private val hostContent: @Composable () -> Unit = {
        CompositionLocalProvider(LocalNativeNavigationRoot provides this) { content() }
    }
    private val restoring = restoredState != null
    private var host = HostComposition(hostContent, restoredState)

    /** The [NativeStack] or [NativeTabs] call in the host composition, if any. */
    private var declared: Any? = null

    /** The hierarchy on screen; it belongs to [RootLayout.declaration]. */
    private var layout: RootLayout? = null
    private var clearScheduled = false

    fun saveState(): NSData? {
        val dropped = mutableListOf<String>()
        val state = mapOf(STATE_VERSION to stateVersion, STATE to SavedStateCodec.prune(host.saveState(), dropped))
        if (dropped.isNotEmpty()) {
            NSLog("compose-swift-navigation3: not saving rememberSaveable values that cannot be persisted: $dropped")
        }
        return try {
            SavedStateCodec.encode(state).encodeToByteArray().toNSData()
        } catch (error: IllegalArgumentException) {
            NSLog("compose-swift-navigation3: navigation state not saved (${error.message})")
            null
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemBackgroundColor
        startHost()
    }

    /**
     * Starts the host composition. Restored `rememberSaveable` values are matched by call
     * position, so state saved by an app version whose navigation code had another shape can fail
     * the first composition (e.g. a ClassCastException inside `rememberNavBackStack`). iOS hands
     * the same scene data to every launch, so that would crash every launch: drop the state and
     * start fresh instead. Without restored state the error is the app's own and is rethrown.
     */
    private fun startHost() {
        try {
            host.start()
        } catch (error: Exception) {
            if (!restoring) throw error
            NSLog(
                "compose-swift-navigation3: restored navigation state does not fit this app version, starting fresh ($error)"
                    .replace("%", "%%"),
            )
            runCatching { host.dispose() }
            host = HostComposition(hostContent, restoredState = null)
            host.start()
        }
    }

    fun declare(declaration: Any) {
        check(declared == null || declared === declaration) {
            "Declare one NativeStack or NativeTabs at a time inside NativeNavigationViewController"
        }
        declared = declaration
    }

    fun retract(declaration: Any) {
        if (declared !== declaration) return
        declared = null
        // The replacement of an if/else switch declares in the same frame. When none follows,
        // the previous hierarchy goes away (its back stacks no longer reach it).
        if (clearScheduled) return
        clearScheduled = true
        dispatch_async(dispatch_get_main_queue()) {
            clearScheduled = false
            if (declared == null) show(null)
        }
    }

    fun updateStack(declaration: Any, entries: List<NavEntry<*>>, onBack: () -> Unit) {
        val current = layout as? StackLayout
        if (current != null && current.declaration === declaration) {
            current.update(entries, onBack)
        } else {
            show(StackLayout(declaration, leases).also { it.update(entries, onBack) })
        }
    }

    fun updateTabs(declaration: Any, tabs: List<TabState>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
        val current = layout as? TabsLayout
        if (current != null && current.declaration === declaration) {
            current.update(tabs, selectedTab, onTabSelected)
        } else {
            show(TabsLayout(declaration, leases).also { it.update(tabs, selectedTab, onTabSelected) })
        }
    }

    /**
     * Replaces the hierarchy on screen. Like replacing a window's root view controller with a
     * cross-dissolve: a snapshot of the previous hierarchy fades out over the new one, which is
     * live (and touchable) right away. The previous screens are removed immediately.
     */
    private fun show(next: RootLayout?) {
        val previous = layout
        if (previous === next) return
        layout = next
        val snapshot = previous?.takeIf { view.window != null }?.controller?.view
            ?.snapshotViewAfterScreenUpdates(false)
        previous?.let {
            uninstall(it.controller)
            it.dispose()
        }
        next?.let { install(it.controller) }
        snapshot?.let(::fadeOut)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fadeOut(snapshot: UIView) {
        snapshot.userInteractionEnabled = false
        snapshot.setFrame(view.bounds)
        view.addSubview(snapshot)
        UIView.animateWithDuration(
            duration = ROOT_TRANSITION_DURATION,
            animations = { snapshot.alpha = 0.0 },
            completion = { _ -> snapshot.removeFromSuperview() },
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun install(child: UIViewController) {
        addChildViewController(child)
        child.view.setFrame(view.bounds)
        child.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(child.view)
        child.didMoveToParentViewController(this)
    }

    private fun uninstall(child: UIViewController) {
        child.willMoveToParentViewController(null)
        child.view.removeFromSuperview()
        child.removeFromParentViewController()
    }
}

/** Duration of the cross-dissolve when the root hierarchy is replaced (UIKit's default). */
private const val ROOT_TRANSITION_DURATION = 0.3

/** The native hierarchy of one [NativeStack] or [NativeTabs] call ([declaration]). */
private sealed interface RootLayout {
    val declaration: Any
    val controller: UIViewController

    /** Off screen for good: removes every screen, so its entry content leaves composition. */
    fun dispose()
}

private class StackLayout(override val declaration: Any, leases: ContentLeases) : RootLayout {
    private val stack = StackCoordinator(UINavigationController(), leases)
    override val controller: UIViewController get() = stack.navigationController

    fun update(entries: List<NavEntry<*>>, onBack: () -> Unit) = stack.update(entries, onBack)

    override fun dispose() = stack.dispose()
}

private class TabsLayout(override val declaration: Any, private val leases: ContentLeases) : RootLayout {
    private val tabBarController = UITabBarController()
    override val controller: UIViewController get() = tabBarController

    private var stacks: List<StackCoordinator> = emptyList()
    private var items: List<Pair<String, String>> = emptyList()
    private var onTabSelected: (Int) -> Unit = {}

    private val delegate = object : NSObject(), UITabBarControllerDelegateProtocol {
        override fun tabBarController(
            tabBarController: UITabBarController,
            didSelectViewController: UIViewController,
        ) {
            onTabSelected(tabBarController.selectedIndex.toInt())
            Snapshot.sendApplyNotifications()
        }
    }

    init {
        // UITabBarController.delegate is weak: this layout keeps the strong reference.
        tabBarController.delegate = delegate
    }

    fun update(tabs: List<TabState>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
        this.onTabSelected = onTabSelected
        if (stacks.size != tabs.size) {
            stacks.forEach { it.dispose() }
            stacks = tabs.map { StackCoordinator(UINavigationController(), leases) }
            tabBarController.setViewControllers(stacks.map { it.navigationController }, animated = false)
            items = emptyList()
        }
        val newItems = tabs.map { it.title to it.systemImage }
        if (newItems != items) {
            stacks.forEachIndexed { index, stack ->
                stack.navigationController.tabBarItem = UITabBarItem(
                    title = newItems[index].first,
                    image = UIImage.systemImageNamed(newItems[index].second),
                    selectedImage = null,
                )
            }
            items = newItems
        }
        tabs.forEachIndexed { index, tab -> stacks[index].update(tab.entries, tab.onBack) }
        if (selectedTab in tabs.indices && tabBarController.selectedIndex.toInt() != selectedTab) {
            tabBarController.selectedIndex = selectedTab.toULong()
        }
    }

    override fun dispose() {
        tabBarController.delegate = null
        stacks.forEach { it.dispose() }
        stacks = emptyList()
    }
}
