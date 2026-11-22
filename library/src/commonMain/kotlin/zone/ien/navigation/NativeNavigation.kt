package zone.ien.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * UIKit 내비게이션 계층에 강조색을 공급한다. 뒤로가기·툴바·주요 액션이 같은 색을 상속한다.
 * [Color.Unspecified]는 호스트의 기본 강조색을 복원한다. Android 등 다른 플랫폼의 UI는 바꾸지 않는다.
 */
@Composable
public fun NativeNavigationTint(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNativeNavigationTint provides color, content = content)
}

internal val LocalNativeNavigationTint = staticCompositionLocalOf { Color.Unspecified }

/**
 * Supplies the selected tab icon and label color to [NativeTabs].
 * [Color.Unspecified] restores the platform tint. Unselected tabs keep their platform colors.
 */
@Composable
public fun NativeTabBarTint(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNativeTabBarTint provides color, content = content)
}

internal val LocalNativeTabBarTint = staticCompositionLocalOf { Color.Unspecified }

/**
 * One tab of [NativeTabs].
 *
 * @param title tab title (tab bar item on iOS, [NativeNavigationScope.tabs] elsewhere).
 * @param systemImage SF Symbol name used when [image] is absent.
 * @param image custom tab icon, rendered as a UIKit template image.
 * @param selectedImage selected-state icon; omitted uses the default tab image.
 * @param badgeCount 탭 배지에 표시할 개수. 0 이하는 배지를 숨기고, null이면 기존 배지를 유지합니다.
 * @param onBack called once per screen popped in this tab. It must remove the last entry of
 *   [backStack], as `NavDisplay`'s `onBack` does.
 */
public class NativeTab<T : Any>(
    public val title: String,
    public val systemImage: String = "",
    public val backStack: List<T>,
    public val image: Painter?,
    public val selectedImage: Painter? = null,
    public val badgeCount: Int?,
    public val onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
) {
    public constructor(
        title: String,
        systemImage: String = "",
        backStack: List<T>,
        image: Painter?,
        selectedImage: Painter? = null,
        onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
    ) : this(title, systemImage, backStack, image, selectedImage, badgeCount = null, onBack = onBack)

    public constructor(
        title: String,
        systemImage: String,
        backStack: List<T>,
        onBack: () -> Unit = { (backStack as? MutableList<T>)?.removeLastOrNull() },
    ) : this(title, systemImage, backStack, image = null, selectedImage = null, onBack = onBack)
}

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
    val entries = rememberDecoratedNavEntries(backStack, rememberFramedDecorators(entryDecorators), entryProvider)
    val host = LocalNativeNavigationHost.current
    if (host != null) {
        host.Stack(entries, onBack)
    } else {
        checkWithoutNativeNavigation("NativeStack")
        OtherPlatformNavigation(
            tabEntries = entries,
            sharedEntries = emptyList(),
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
 *   tab pops it to its root (native behavior), reported through that tab's `onBack` (and
 *   [onSharedBack] for the shared screens).
 * - **Other platforms**: the library draws nothing. [otherPlatforms] draws the app's own UI with
 *   what [NativeNavigationScope] hands it: the visible entries for `NavDisplay`, and the tabs for
 *   a Material 3 `NavigationBar`.
 *
 * **Shared screens**: [sharedBackStack] holds screens shown over the tabs, shared by every tab,
 * like a `NavDisplay` around the tabs whose first entry is the tabs themselves (e.g. a profile or
 * an editor that any tab opens). It may be empty. Its screens always hide the tab bar, so the
 * user cannot switch tabs while one is shown.
 * - iOS: they are pushed on the selected tab's `UINavigationController`, above that tab's own
 *   screens (native back button, edge swipe and transitions). If the app changes [selectedTab]
 *   while they are shown, they move to the newly selected tab.
 * - Other platforms: [NativeNavigationScope.entries] is the selected tab's entries followed by
 *   the shared ones; [NativeNavigationScope.tabEntries] and [NativeNavigationScope.sharedEntries]
 *   hand them apart, e.g. for an outer `NavDisplay` over the tabs.
 *
 * Tabs are only supported at the root: not inside a pushed screen.
 *
 * @param sharedBackStack screens shown over the selected tab, shared by every tab.
 * @param onSharedBack called once per popped shared screen. It must remove the last entry of
 *   [sharedBackStack].
 */
@Composable
public fun <T : Any> NativeTabs(
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    sharedBackStack: List<T> = emptyList(),
    onSharedBack: () -> Unit = { (sharedBackStack as? MutableList<T>)?.removeLastOrNull() },
    entryDecorators: List<NavEntryDecorator<T>> = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (key: T) -> NavEntry<T>,
    otherPlatforms: @Composable NativeNavigationScope<T>.() -> Unit,
) {
    // One rememberDecoratedNavEntries per back stack, as Navigation 3 requires for multiple
    // stacks: the entries of an unselected tab stay in their back stack, so their state is kept.
    val decorators = rememberFramedDecorators(entryDecorators)
    val entriesPerTab = tabs.mapIndexed { index, tab ->
        key(index) { rememberDecoratedNavEntries(tab.backStack, decorators, entryProvider) }
    }
    val sharedEntries = rememberDecoratedNavEntries(sharedBackStack, decorators, entryProvider)
    val host = LocalNativeNavigationHost.current
    if (host != null) {
        host.Tabs(tabs, entriesPerTab, selectedTab, onTabSelected, sharedEntries, onSharedBack)
    } else {
        checkWithoutNativeNavigation("NativeTabs")
        val index = selectedTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        OtherPlatformNavigation(
            tabEntries = entriesPerTab.getOrElse(index) { emptyList() },
            sharedEntries = sharedEntries,
            onBack = if (sharedEntries.isNotEmpty()) onSharedBack else tabs.getOrNull(index)?.onBack ?: {},
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
     * Decorated entries of the visible back stack, for `NavDisplay(entries, onBack = onBack)`:
     * [NativeStack]'s, or the selected tab's followed by [NativeTabs]' shared screens
     * ([tabEntries] + [sharedEntries]). Each tab has its own, so switching tabs keeps every tab's
     * state.
     */
    public val entries: List<NavEntry<T>>

    /** [entries] without [sharedEntries]: the selected tab's own back stack ([NativeStack]: all of it). */
    public val tabEntries: List<NavEntry<T>>

    /**
     * Decorated entries of [NativeTabs]' `sharedBackStack`, shown over the selected tab (the end
     * of [entries]). Empty for [NativeStack] and while no shared screen is shown.
     */
    public val sharedEntries: List<NavEntry<T>>

    /**
     * Pops the top of [entries]: [NativeStack]'s `onBack`, [NativeTabs]' `onSharedBack` while a
     * shared screen is shown, else the selected [NativeTab.onBack].
     */
    public val onBack: () -> Unit

    /** Whether the visible back stack has a screen below the top one (iOS shows a back button). */
    public val canGoBack: Boolean

    /** Title of the top screen: its [NativeBarItems] title if it declared one, else its [nativeScreen] title. */
    public val title: String?

    /** Whether the top screen asked for a large title ([nativeScreen] `largeTitle`). */
    public val largeTitle: Boolean

    /**
     * Image the top screen shows in place of its title text ([nativeScreen] `titleImage`), e.g.
     * `Image(it(), contentDescription = title)` in the top app bar's title slot.
     */
    public val titleImage: (@Composable () -> Painter)?

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
     * Whether to show the tab bar: there are [tabs], no shared screen is shown, and no screen
     * pushed on the selected tab has [nativeScreen] `hidesTabBar` (UIKit's
     * `hidesBottomBarWhenPushed` rule: once a screen hides the tab bar, the screens pushed above
     * it keep it hidden).
     */
    public val tabBarVisible: Boolean
}

@Composable
private fun <T : Any> OtherPlatformNavigation(
    tabEntries: List<NavEntry<T>>,
    sharedEntries: List<NavEntry<T>>,
    onBack: () -> Unit,
    tabs: List<NativeTab<T>>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    content: @Composable NativeNavigationScope<T>.() -> Unit,
) {
    val bar = remember { NativeBarState() }
    val scope = OtherPlatformScope(tabEntries, sharedEntries, onBack, tabs, selectedTab, onTabSelected, bar)
    ProvideNativeBarState(bar) { scope.content() }
}

/** Reads the bar declarations when the app reads them, so only the chrome recomposes. */
internal class OtherPlatformScope<T : Any>(
    override val tabEntries: List<NavEntry<T>>,
    override val sharedEntries: List<NavEntry<T>>,
    override val onBack: () -> Unit,
    override val tabs: List<NativeTab<T>>,
    override val selectedTab: Int,
    override val onTabSelected: (Int) -> Unit,
    private val bar: NativeBarState,
) : NativeNavigationScope<T> {
    override val entries: List<NavEntry<T>> = if (sharedEntries.isEmpty()) tabEntries else tabEntries + sharedEntries
    override val canGoBack: Boolean get() = entries.size > 1
    override val title: String? get() = bar.title ?: entries.lastOrNull()?.nativeTitle
    override val largeTitle: Boolean get() = entries.lastOrNull()?.nativeLargeTitle ?: false
    override val titleImage: (@Composable () -> Painter)? get() = entries.lastOrNull()?.nativeTitleImage
    override val leading: List<NativeBarButton> get() = bar.leading
    override val trailing: List<NativeBarButton> get() = bar.trailing
    override val toolbar: List<NativeBarButton> get() = bar.toolbar
    override val search: NativeSearch? get() = bar.search
    override val tabBarVisible: Boolean
        get() = tabs.isNotEmpty() && sharedEntries.isEmpty() && tabEntries.drop(1).none { it.nativeHidesTabBar }
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
        sharedEntries: List<NavEntry<*>>,
        onSharedBack: () -> Unit,
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
