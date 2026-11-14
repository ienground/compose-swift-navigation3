package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * One tab of [NativeTabs].
 *
 * @param title tab title (tab bar item on iOS, [NativeNavigationScope.tabs] elsewhere).
 * @param systemImage SF Symbol name of the iOS tab bar icon.
 * @param onBack called once per screen popped in this tab. It must remove the last entry of
 *   [backStack], as `NavDisplay`'s `onBack` does.
 */
public class NativeTab<T : Any>(
    public val title: String,
    public val systemImage: String,
    public val backStack: List<T>,
    public val onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
)

/**
 * A navigation stack for [backStack], declared once in common code. Parameters mirror
 * Navigation 3's `NavDisplay`.
 *
 * - **iOS**: inside `NativeNavigationViewController` it is a native `UINavigationController`;
 *   every entry is a native screen and [otherPlatforms] is not called.
 * - **Other platforms**: the library draws nothing. [otherPlatforms] draws the app's own UI with
 *   what [NativeNavigationScope] hands it, typically `NavDisplay(entries, onBack = onBack)` in a
 *   Material 3 `Scaffold` whose top app bar shows [NativeNavigationScope.title].
 *
 * ```
 * @Composable
 * fun App() {
 *     val backStack = rememberNavBackStack(config, Home)
 *     NativeStack(backStack, entryProvider = appEntryProvider(backStack)) {
 *         AppChrome()   // NativeNavigationScope<NavKey>.AppChrome(): Scaffold + NavDisplay(entries)
 *     }
 * }
 * ```
 *
 * @param onBack called once per popped screen (iOS: back button, edge swipe, long-press menu;
 *   elsewhere: [NativeNavigationScope.onBack]). It must remove the last entry of [backStack].
 * @param otherPlatforms the app's UI on platforms other than iOS.
 */
@Composable
public fun <T : Any> NativeStack(
    backStack: List<T>,
    onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (key: T) -> NavEntry<T>,
    otherPlatforms: @Composable NativeNavigationScope<T>.() -> Unit,
) {
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    val host = LocalNativeNavigationHost.current
    if (host != null) {
        host.Stack(entries, onBack)
    } else {
        checkWithoutNativeNavigation("NativeStack")
        OtherPlatformNavigation(
            entries = entries,
            onBack = onBack,
            tabs = emptyList(),
            selectedTab = 0,
            onTabSelected = {},
            content = otherPlatforms,
        )
    }
}

/**
 * Tabs, each with its own navigation stack, declared once in common code. Every tab keeps its
 * back stack and its entries' state (saveable state, ViewModels) while another tab is selected.
 *
 * - **iOS**: inside `NativeNavigationViewController` it is a native `UITabBarController` with one
 *   `UINavigationController` per tab, and [otherPlatforms] is not called. Re-tapping the selected
 *   tab pops it to its root (native behavior), reported through that tab's `onBack`.
 * - **Other platforms**: the library draws nothing. [otherPlatforms] draws the app's own UI with
 *   what [NativeNavigationScope] hands it: the selected tab's entries for `NavDisplay`, and the
 *   tabs for a Material 3 `NavigationBar`.
 *
 * Tabs are only supported at the root: not inside a pushed screen.
 */
@Composable
public fun <T : Any> NativeTabs(
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (key: T) -> NavEntry<T>,
    otherPlatforms: @Composable NativeNavigationScope<T>.() -> Unit,
) {
    // One rememberDecoratedNavEntries per back stack, as Navigation 3 requires for multiple
    // stacks: the entries of an unselected tab stay in their back stack, so their state is kept.
    val entriesPerTab = tabs.mapIndexed { index, tab ->
        key(index) { rememberDecoratedNavEntries(tab.backStack, entryDecorators, entryProvider) }
    }
    val host = LocalNativeNavigationHost.current
    if (host != null) {
        host.Tabs(tabs, entriesPerTab, selectedTab, onTabSelected)
    } else {
        checkWithoutNativeNavigation("NativeTabs")
        val index = selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        OtherPlatformNavigation(
            entries = entriesPerTab.getOrElse(index) { emptyList() },
            onBack = tabs.getOrNull(index)?.onBack ?: {},
            tabs = tabs,
            selectedTab = index,
            onTabSelected = onTabSelected,
            content = otherPlatforms,
        )
    }
}

/**
 * What [NativeStack] and [NativeTabs] hand the app's UI on platforms other than iOS. The library
 * draws nothing there: these are the same declarations iOS shows natively ([nativeScreen]
 * metadata, [NativeBarItems], [NativeSearchBar], the tabs), ready for the app's own components:
 *
 * ```
 * @Composable
 * fun NativeNavigationScope<NavKey>.AppChrome() = Scaffold(
 *     topBar = {
 *         TopAppBar(
 *             title = { Text(title.orEmpty()) },
 *             navigationIcon = { if (canGoBack) BackButton(onClick = onBack) },
 *             actions = { trailing.forEach { BarButton(it) } },
 *         )
 *     },
 *     bottomBar = {
 *         if (tabBarVisible) NavigationBar {
 *             tabs.forEachIndexed { index, tab ->
 *                 NavigationBarItem(selected = index == selectedTab, onClick = { onTabSelected(index) }, ...)
 *             }
 *         }
 *     },
 * ) { padding -> NavDisplay(entries, Modifier.padding(padding), onBack = onBack) }
 * ```
 *
 * Bar items and the search field come from the screens composed below this scope (the
 * `NavDisplay`): the most recently composed screen wins, which is the top one also while
 * `NavDisplay` animates two screens.
 */
public interface NativeNavigationScope<T : Any> {
    /**
     * Decorated entries of the visible back stack ([NativeStack]'s, or the selected tab's), for
     * `NavDisplay(entries, onBack = onBack)`. Each tab has its own, so switching tabs keeps every
     * tab's state.
     */
    public val entries: List<NavEntry<T>>

    /** Pops the visible back stack: [NativeStack]'s `onBack`, or the selected [NativeTab.onBack]. */
    public val onBack: () -> Unit

    /** Whether the visible back stack has a screen below the top one (iOS shows a back button). */
    public val canGoBack: Boolean

    /** Title of the top screen: its [NativeBarItems] title if it declared one, else its [nativeScreen] title. */
    public val title: String?

    /** Whether the top screen asked for a large title ([nativeScreen] `largeTitle`). */
    public val largeTitle: Boolean

    /** [NativeBarItems] `leading` buttons of the top screen. */
    public val leading: List<NativeBarButton>

    /** [NativeBarItems] `trailing` buttons of the top screen. */
    public val trailing: List<NativeBarButton>

    /** [NativeBarItems] `toolbar` buttons of the top screen (iOS: the bottom toolbar). */
    public val toolbar: List<NativeBarButton>

    /** [NativeSearchBar] of the top screen, if it declared one. */
    public val search: NativeSearch?

    /** The tabs of [NativeTabs]; empty for [NativeStack]. */
    public val tabs: List<NativeTab<T>>

    /** Index of the selected tab in [tabs] (0 for [NativeStack]). */
    public val selectedTab: Int

    /** Selects a tab: [NativeTabs]' `onTabSelected`. */
    public val onTabSelected: (Int) -> Unit

    /**
     * Whether to show the tab bar: there are [tabs], and no screen pushed on the selected tab has
     * [nativeScreen] `hidesTabBar` (UIKit's `hidesBottomBarWhenPushed` rule: once a screen hides
     * the tab bar, the screens pushed above it keep it hidden).
     */
    public val tabBarVisible: Boolean
}

@Composable
private fun <T : Any> OtherPlatformNavigation(
    entries: List<NavEntry<T>>,
    onBack: () -> Unit,
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    content: @Composable NativeNavigationScope<T>.() -> Unit,
) {
    val bar = remember { NativeBarState() }
    val scope = OtherPlatformScope(entries, onBack, tabs, selectedTab, onTabSelected, bar)
    ProvideNativeBarState(bar) { scope.content() }
}

/** Reads the bar declarations when the app reads them, so only the chrome recomposes. */
internal class OtherPlatformScope<T : Any>(
    override val entries: List<NavEntry<T>>,
    override val onBack: () -> Unit,
    override val tabs: List<NativeTab<T>>,
    override val selectedTab: Int,
    override val onTabSelected: (Int) -> Unit,
    private val bar: NativeBarState,
) : NativeNavigationScope<T> {
    override val canGoBack: Boolean get() = entries.size > 1
    override val title: String? get() = bar.title ?: entries.lastOrNull()?.nativeTitle
    override val largeTitle: Boolean get() = entries.lastOrNull()?.nativeLargeTitle ?: false
    override val leading: List<NativeBarButton> get() = bar.leading
    override val trailing: List<NativeBarButton> get() = bar.trailing
    override val toolbar: List<NativeBarButton> get() = bar.toolbar
    override val search: NativeSearch? get() = bar.search
    override val tabBarVisible: Boolean get() = tabs.isNotEmpty() && entries.drop(1).none { it.nativeHidesTabBar }
}

/**
 * The platform's native navigation, provided by the iOS root controller to its host composition.
 * `null` elsewhere, where [NativeStack] and [NativeTabs] call the app's `otherPlatforms` UI.
 */
internal interface NativeNavigationHost {
    @Composable
    fun Stack(entries: List<NavEntry<*>>, onBack: () -> Unit)

    @Composable
    fun Tabs(
        tabs: List<NativeTab<*>>,
        entriesPerTab: List<List<NavEntry<*>>>,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
    )
}

internal val LocalNativeNavigationHost = staticCompositionLocalOf<NativeNavigationHost?> { null }

/**
 * Whether this platform shows [NativeStack] and [NativeTabs] with native controllers (iOS). There
 * they need `NativeNavigationViewController`: no Compose fallback is drawn on iOS.
 */
internal expect val usesNativeNavigation: Boolean

private fun checkWithoutNativeNavigation(name: String) = check(!usesNativeNavigation) {
    "$name must be called in the content of NativeNavigationViewController { ... } (not inside a screen)"
}
