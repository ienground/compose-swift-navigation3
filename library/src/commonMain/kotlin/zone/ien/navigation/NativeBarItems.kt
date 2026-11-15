package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A button of the navigation bar or the bottom toolbar.
 *
 * @param title label; with [systemImage] it is the accessibility label.
 * @param systemImage SF Symbol name shown on iOS instead of the title.
 * @param menu when not empty, tapping the button opens this menu (`UIMenu` on iOS) and
 *   [onClick] is not used.
 */
@Immutable
public class NativeBarButton(
    public val title: String,
    public val systemImage: String? = null,
    public val enabled: Boolean = true,
    public val menu: List<NativeMenuAction> = emptyList(),
    public val onClick: () -> Unit = {},
)

/** An action of a [NativeBarButton.menu]. */
@Immutable
public class NativeMenuAction(
    public val title: String,
    public val systemImage: String? = null,
    public val destructive: Boolean = false,
    public val onClick: () -> Unit,
)

/** Everything one screen declared with [NativeBarItems]. */
@Immutable
public class NativeBarContent internal constructor(
    public val title: String?,
    public val leading: List<NativeBarButton>,
    public val trailing: List<NativeBarButton>,
    public val toolbar: List<NativeBarButton>,
)

/**
 * Declares the screen's bar items from inside its composable content, so they can follow the
 * screen's state (enabled flags, titles, ViewModel actions).
 *
 * On iOS, inside a screen hosted by this library, they become native items: [leading] and
 * [trailing] are `UIBarButtonItem`s of the screen's `navigationItem` (listed left to right; the
 * back button stays), [toolbar] is the `UINavigationController` bottom toolbar (shown while this
 * screen is on top, hidden otherwise), and [title] overrides the `nativeScreen` title.
 *
 * The items exist only once the screen's content has composed, which starts when a push is
 * already under way, and later still when the call sits inside a subcomposition (e.g. the content
 * of a `Scaffold`, which composes during the first layout). UIKit animates the bar with the title
 * the screen has when the push starts, so a title that comes from here appears only after the
 * transition. Declare titles that do not depend on the screen's state with `nativeScreen(title)`
 * and use [title] for titles that change while the screen is shown.
 *
 * Elsewhere nothing is drawn by the library. Below [NativeStack] or [NativeTabs] they reach the
 * app's `otherPlatforms` UI as [NativeNavigationScope.title], [NativeNavigationScope.trailing]
 * etc., for its own chrome (e.g. a Material 3 `TopAppBar`). Without them, an app can collect them
 * by providing a [NativeBarState] with [ProvideNativeBarState].
 */
@Composable
public fun NativeBarItems(
    title: String? = null,
    leading: List<NativeBarButton> = emptyList(),
    trailing: List<NativeBarButton> = emptyList(),
    toolbar: List<NativeBarButton> = emptyList(),
) {
    val state = LocalNativeBarState.current ?: return
    val owner = remember { Any() }
    val content = NativeBarContent(title, leading, trailing, toolbar)
    SideEffect { state.register(owner, content) }
    DisposableEffect(state, owner) {
        onDispose { state.unregister(owner) }
    }
}

/** A search field declared with [NativeSearchBar]. */
@Immutable
public class NativeSearch internal constructor(
    public val query: String,
    public val placeholder: String,
    public val onQueryChange: (String) -> Unit,
)

/**
 * Declares the screen's search field from inside its composable content. The app owns the
 * [query] (e.g. `rememberSaveable`) and filters its content with it.
 *
 * On iOS it is the screen's native search field (`UISearchController` in the `navigationItem`):
 * below the large title, hidden while scrolling down, with the system Cancel button. Elsewhere
 * nothing is drawn by the library; an app can show it in its own chrome
 * ([NativeNavigationScope.search], or [NativeBarState.search]).
 */
@Composable
public fun NativeSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String = "") {
    val state = LocalNativeBarState.current ?: return
    val owner = remember { Any() }
    val search = NativeSearch(query, placeholder, onQueryChange)
    SideEffect { state.registerSearch(owner, search) }
    DisposableEffect(state, owner) {
        onDispose { state.unregisterSearch(owner) }
    }
}

/**
 * Collects the [NativeBarItems] declared by the screens composed below it. The most recently
 * registered screen that is still composed wins, which is the top screen of a back stack also
 * while a transition composes two screens.
 *
 * [NativeStack] and [NativeTabs] provide one to their `otherPlatforms` UI; create one only to
 * collect the declarations below a plain `NavDisplay`.
 */
@Stable
public class NativeBarState {
    private val registrations = mutableStateListOf<Pair<Any, NativeBarContent>>()

    /** Called synchronously on every change (used by the iOS screen host). */
    internal var listener: ((NativeBarContent?) -> Unit)? = null

    public val current: NativeBarContent? get() = registrations.lastOrNull()?.second
    public val title: String? get() = current?.title
    public val leading: List<NativeBarButton> get() = current?.leading.orEmpty()
    public val trailing: List<NativeBarButton> get() = current?.trailing.orEmpty()
    public val toolbar: List<NativeBarButton> get() = current?.toolbar.orEmpty()

    private val searches = mutableStateListOf<Pair<Any, NativeSearch>>()

    /** Called synchronously when the search field changes (used by the iOS screen host). */
    internal var searchListener: ((NativeSearch?) -> Unit)? = null

    /** The search field of the most recently registered screen, if it declared one. */
    public val search: NativeSearch? get() = searches.lastOrNull()?.second

    internal fun registerSearch(owner: Any, search: NativeSearch) {
        val index = searches.indexOfFirst { it.first === owner }
        if (index >= 0) searches[index] = owner to search else searches.add(owner to search)
        searchListener?.invoke(this.search)
    }

    internal fun unregisterSearch(owner: Any) {
        searches.removeAll { it.first === owner }
        searchListener?.invoke(search)
    }

    internal fun register(owner: Any, content: NativeBarContent) {
        val index = registrations.indexOfFirst { it.first === owner }
        if (index >= 0) registrations[index] = owner to content else registrations.add(owner to content)
        listener?.invoke(current)
    }

    internal fun unregister(owner: Any) {
        registrations.removeAll { it.first === owner }
        listener?.invoke(current)
    }
}

/** Makes [NativeBarItems] below report to [state]. */
@Composable
public fun ProvideNativeBarState(state: NativeBarState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNativeBarState provides state, content = content)
}

internal val LocalNativeBarState = staticCompositionLocalOf<NativeBarState?> { null }

/** What a native bar looks like, without the click handlers: equal shapes reuse native items. */
internal data class BarShape(
    val leading: List<ButtonShape>,
    val trailing: List<ButtonShape>,
    val toolbar: List<ButtonShape>,
) {
    data class ButtonShape(
        val title: String,
        val systemImage: String?,
        val enabled: Boolean,
        val menu: List<MenuShape>,
    )

    data class MenuShape(val title: String, val systemImage: String?, val destructive: Boolean)

    companion object {
        val Empty = BarShape(emptyList(), emptyList(), emptyList())
    }
}

internal fun NativeBarContent?.shape(): BarShape {
    if (this == null) return BarShape.Empty
    fun List<NativeBarButton>.shapes() = map { button ->
        BarShape.ButtonShape(
            title = button.title,
            systemImage = button.systemImage,
            enabled = button.enabled,
            menu = button.menu.map { BarShape.MenuShape(it.title, it.systemImage, it.destructive) },
        )
    }
    return BarShape(leading.shapes(), trailing.shapes(), toolbar.shapes())
}
