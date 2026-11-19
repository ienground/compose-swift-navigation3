package zone.ien.navigation

import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavEntry
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
import platform.UIKit.UIScreen
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
 * the entry's composable, and its navigation bar comes from [nativeScreen] metadata and
 * [NativeBarItems].
 *
 * [NativeStack] and [NativeTabs] are common code, so the app's root composable is usually the
 * same one Android uses: there it calls their `otherPlatforms` UI (`NavDisplay` in the app's
 * Material chrome), here it declares the native controllers and emits no UI.
 * ```
 * // commonMain
 * @Composable
 * fun App() {
 *     val backStack = rememberNavBackStack(config, Home)
 *     NativeStack(backStack, entryProvider = appEntryProvider(backStack)) { AppChrome() }
 * }
 *
 * // iosMain                                         // Android: setContent { App() }
 * fun MainViewController() = NativeNavigationViewController { App() }
 * ```
 *
 * **Theme**: every screen is its own composition, so composition locals provided around the
 * navigation here (e.g. `MaterialTheme`) do not reach the screens. Apply them per entry with a
 * `NavEntryDecorator` (`NavEntryDecorator { entry -> AppTheme { entry.Content() } }` in
 * `entryDecorators`): it runs inside each screen on iOS and inside `NavDisplay` elsewhere.
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
 * [content] must not emit UI itself: UI belongs in the screens (entries) and, for the other
 * platforms, in the `otherPlatforms` block of [NativeStack] / [NativeTabs], which iOS never calls.
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
            NSLog("zone.ien.navigation: ignoring navigation state of version $version (expected $expectedVersion)")
            null
        }
    }
} catch (error: IllegalArgumentException) {
    NSLog("zone.ien.navigation: ignoring unreadable navigation state (${error.message})")
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
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/**
 * What [NativeStack] and [NativeTabs] (common code) do on iOS: hand their decorated entries to
 * [root], which shows them with UIKit controllers. The app's `otherPlatforms` UI is not used.
 */
private class RootNavigationHost(private val root: NativeNavigationRootController) : NativeNavigationHost {
    @Composable
    override fun Stack(entries: List<NavEntry<*>>, onBack: () -> Unit) {
        val declaration = rememberRootDeclaration(root)
        val currentOnBack by rememberUpdatedState(onBack)
        val titleImages = rememberTitleImages(entries)
        val tint = LocalNativeNavigationTint.current
        SideEffect {
            root.titleImages.update(titleImages)
            root.updateStack(declaration, entries) { currentOnBack() }
            root.updateTint(tint)
        }
    }

    @Composable
    override fun Tabs(
        tabs: List<NativeTab<*>>,
        entriesPerTab: List<List<NavEntry<*>>>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        sharedEntries: List<NavEntry<*>>,
        onSharedBack: () -> Unit,
    ) {
        val declaration = rememberRootDeclaration(root)
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)
        val currentOnSharedBack by rememberUpdatedState(onSharedBack)
        val titleImages = rememberTitleImages(entriesPerTab.flatten() + sharedEntries)
        val tint = LocalNativeTabBarTint.current
        val navigationTint = LocalNativeNavigationTint.current
        val states = tabs.mapIndexed { index, tab ->
            key(index) {
                TabState(
                    tab.title, tab.systemImage, entriesPerTab[index], tab.onBack,
                    rememberNativeTabImage(tab.image), rememberNativeTabImage(tab.selectedImage), tab.badgeCount,
                )
            }
        }
        SideEffect {
            root.titleImages.update(titleImages)
            root.updateTabs(
                declaration = declaration,
                tabs = states,
                selectedTab = selectedTab,
                onTabSelected = { currentOnTabSelected(it) },
                shared = SharedState(sharedEntries) { currentOnSharedBack() },
                tint = tint,
            )
            root.updateTint(navigationTint)
        }
    }
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

internal class TabState(
    val title: String,
    val systemImage: String,
    val entries: List<NavEntry<*>>,
    val onBack: () -> Unit,
    val image: UIImage? = null,
    val selectedImage: UIImage? = null,
    val badgeCount: Int? = null,
)

private data class TabAppearance(
    val title: String,
    val systemImage: String,
    val image: UIImage?,
    val selectedImage: UIImage?,
)

/** [NativeTabs]' shared screens: pushed on the selected tab, above its own screens. */
internal class SharedState(
    val entries: List<NavEntry<*>>,
    val onBack: () -> Unit,
)

internal class NativeNavigationRootController(
    content: @Composable () -> Unit,
    restoredState: Map<String, Any?>?,
    private val stateVersion: Int,
) : UIViewController(nibName = null, bundle = null) {

    private val leases = ContentLeases()
    val titleImages = TitleImages()
    private val navigationHost = RootNavigationHost(this)
    private val hostContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalNativeNavigationHost provides navigationHost,
            // The composition has no screen. Alerts it shows (e.g. adaptive dialogs presenting a
            // UIAlertController) present from the root controller, and resources read the
            // density (string and image resource qualifiers).
            LocalUIViewController provides this,
            LocalDensity provides Density(mainScreenScale()),
        ) { content() }
    }
    private val restoring = restoredState != null
    private var host = HostComposition(hostContent, restoredState)

    /** The [NativeStack] or [NativeTabs] call in the host composition, if any. */
    private var declared: Any? = null

    /** The hierarchy on screen; it belongs to [RootLayout.declaration]. */
    private var layout: RootLayout? = null
    private var clearScheduled = false

    private val defaultTint by lazy { view.tintColor }

    /** 호스트 루트의 강조색을 바꾸면 현재 화면과 이후 화면의 UIKit 컨트롤이 함께 상속한다. */
    fun updateTint(color: Color) {
        val platformDefault = defaultTint
        val tint = if (color == Color.Unspecified) platformDefault else color.toUIColor()
        if (view.tintColor != tint) view.tintColor = tint
        val navigationControllers = when (val current = layout?.controller) {
            is UINavigationController -> listOf(current)
            is UITabBarController -> current.viewControllers.orEmpty().filterIsInstance<UINavigationController>()
            else -> emptyList()
        }
        navigationControllers.forEach { navigation ->
            if (navigation.view.tintColor != tint) navigation.view.tintColor = tint
            if (navigation.navigationBar.tintColor != tint) navigation.navigationBar.tintColor = tint
            if (navigation.toolbar.tintColor != tint) navigation.toolbar.tintColor = tint
        }
    }

    fun saveState(): NSData? {
        val dropped = mutableListOf<String>()
        val state = mapOf(STATE_VERSION to stateVersion, STATE to SavedStateCodec.prune(host.saveState(), dropped))
        if (dropped.isNotEmpty()) {
            NSLog("zone.ien.navigation: not saving rememberSaveable values that cannot be persisted: $dropped")
        }
        return try {
            SavedStateCodec.encode(state).encodeToByteArray().toNSData()
        } catch (error: IllegalArgumentException) {
            NSLog("zone.ien.navigation: navigation state not saved (${error.message})")
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
                "zone.ien.navigation: restored navigation state does not fit this app version, starting fresh ($error)"
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
            show(StackLayout(declaration, leases, titleImages).also { it.update(entries, onBack) })
        }
    }

    fun updateTabs(
        declaration: Any,
        tabs: List<TabState>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        shared: SharedState,
        tint: Color = Color.Unspecified,
    ) {
        val current = layout as? TabsLayout
        if (current != null && current.declaration === declaration) {
            current.update(tabs, selectedTab, onTabSelected, shared, tint)
        } else {
            show(TabsLayout(declaration, leases, titleImages).also { it.update(tabs, selectedTab, onTabSelected, shared, tint) })
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

@OptIn(ExperimentalForeignApi::class)
private fun mainScreenScale(): Float = UIScreen.mainScreen.scale.toFloat()

/** Duration of the cross-dissolve when the root hierarchy is replaced (UIKit's default). */
private const val ROOT_TRANSITION_DURATION = 0.3

/** The native hierarchy of one [NativeStack] or [NativeTabs] call ([declaration]). */
private sealed interface RootLayout {
    val declaration: Any
    val controller: UIViewController

    /** Off screen for good: removes every screen, so its entry content leaves composition. */
    fun dispose()
}

private class StackLayout(
    override val declaration: Any,
    leases: ContentLeases,
    titleImages: TitleImages,
) : RootLayout {
    private val stack = StackCoordinator(UINavigationController(), leases, titleImages)
    override val controller: UIViewController get() = stack.navigationController

    fun update(entries: List<NavEntry<*>>, onBack: () -> Unit) = stack.update(entries, onBack)

    override fun dispose() = stack.dispose()
}

private class TabsLayout(
    override val declaration: Any,
    private val leases: ContentLeases,
    private val titleImages: TitleImages,
) : RootLayout {
    private val tabBarController = UITabBarController()
    private val defaultTint = tabBarController.tabBar.tintColor
    override val controller: UIViewController get() = tabBarController

    private var stacks: List<StackCoordinator> = emptyList()
    private var items: List<TabAppearance> = emptyList()
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

    fun update(tabs: List<TabState>, selectedTab: Int, onTabSelected: (Int) -> Unit, shared: SharedState, tint: Color) {
        tabBarController.tabBar.tintColor = if (tint == Color.Unspecified) defaultTint else tint.toUIColor()
        this.onTabSelected = onTabSelected
        if (stacks.size != tabs.size) {
            stacks.forEach { it.dispose() }
            stacks = tabs.map { StackCoordinator(UINavigationController(), leases, titleImages) }
            tabBarController.setViewControllers(stacks.map { it.navigationController }, animated = false)
            items = emptyList()
        }
        val newItems = tabs.map { TabAppearance(it.title, it.systemImage, it.image, it.selectedImage) }
        if (newItems != items) {
            stacks.forEachIndexed { index, stack ->
                stack.navigationController.tabBarItem = UITabBarItem(
                    title = newItems[index].title,
                    image = newItems[index].image ?: UIImage.systemImageNamed(newItems[index].systemImage),
                    selectedImage = newItems[index].selectedImage,
                )
            }
            items = newItems
        }
        // The shared screens sit on the selected tab. Only a tab that stays on screen animates:
        // when the app selects another tab while shared screens are shown, they leave the
        // previous tab and appear on the new one without a push or pop animation.
        val showing = tabBarController.selectedIndex.toInt()
        val selected = if (selectedTab in tabs.indices) selectedTab else showing
        tabs.forEachIndexed { index, tab ->
            val stack = stacks[index]
            tab.badgeCount?.let { count ->
                val item = stack.navigationController.tabBarItem
                val badge = count.takeIf { it > 0 }?.toString()
                if (item.badgeValue != badge) item.badgeValue = badge
            }
            stack.update(
                entries = tab.entries,
                onBack = tab.onBack,
                shared = if (index == selected) shared.entries else emptyList(),
                onSharedBack = shared.onBack,
                animated = index == selected && index == showing,
            )
        }
        if (selectedTab in tabs.indices && showing != selectedTab) {
            tabBarController.selectedIndex = selectedTab.toULong()
        }
    }

    override fun dispose() {
        tabBarController.delegate = null
        stacks.forEach { it.dispose() }
        stacks = emptyList()
    }
}
