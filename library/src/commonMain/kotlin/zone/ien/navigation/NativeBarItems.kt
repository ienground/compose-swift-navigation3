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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

/** 내비게이션 바 왼쪽에 배치할 수 있는 항목입니다. */
public sealed interface NativeBarComponent

/**
 * 내비게이션 바에 표시할 읽기 전용 텍스트입니다.
 *
 * @param key 같은 내비게이션 바 안에서 고유한 항목 키
 * @param text 화면에 표시할 문구
 * @param width 항목에 확보할 너비(포인트)
 * @param accessibilityLabel 화면 읽기 기능에 제공할 문구. null이면 [text]를 읽습니다.
 */
@Immutable
public data class NativeBarText(
    public val key: String,
    public val text: String,
    public val width: Float,
    public val accessibilityLabel: String? = null,
) : NativeBarComponent {
    init {
        require(width.isFinite() && width > 0)
    }
}
/**
 * A button of the navigation bar or the bottom toolbar.
 *
 * @param title label; with [systemImage] or [image] it is the accessibility label.
 * @param systemImage SF Symbol name shown on iOS instead of the title.
 * @param image 사용자 이미지. 지정하면 [systemImage]보다 우선하며, iOS에서는 네이티브 tint를 적용한다.
 * @param badge 0이면 숨김, 양수이면 숫자, 음수이면 표시점이다. iOS 18.2 이상에서 UIKit 버튼에 표시한다.
 * @param isPrimary 주요 액션이면 UIKit 강조 스타일을 적용하고, 오른쪽 일반 액션 그룹과 간격을 둔다.
 * @param tintColor 이 버튼에 적용할 UIKit 틴트 색이다. [Color.Unspecified]이면 시스템 색을 따른다.
 * 일반 버튼은 아이콘이나 텍스트에, iOS 26 이상의 강조 버튼은 배경에 색이 적용된다.
 * @param menu when not empty, tapping the button opens this menu (`UIMenu` on iOS) and
 *   [onClick] is not used.
 */
@Immutable
public class NativeBarButton(
    public val title: String,
    public val systemImage: String? = null,
    public val enabled: Boolean = true,
    public val menu: List<NativeMenuAction> = emptyList(),
    public val image: Painter? = null,
    public val badge: Int = 0,
    public val isPrimary: Boolean = false,
    public val tintColor: Color,
    public val onClick: () -> Unit = {},
) : NativeBarComponent {
    /** 기존 강조 스타일 인수를 지원하는 생성자입니다. */
    public constructor(
        title: String,
        systemImage: String? = null,
        enabled: Boolean = true,
        menu: List<NativeMenuAction> = emptyList(),
        image: Painter? = null,
        badge: Int = 0,
        isPrimary: Boolean = false,
        onClick: () -> Unit = {},
    ) : this(title, systemImage, enabled, menu, image, badge, isPrimary, Color.Unspecified, onClick)

    /** 기존 생성자의 위치 인수와 기본 인수 호출을 유지한다. */
    public constructor(
        title: String,
        systemImage: String? = null,
        enabled: Boolean = true,
        menu: List<NativeMenuAction> = emptyList(),
        onClick: () -> Unit = {},
    ) : this(title, systemImage, enabled, menu, image = null, badge = 0, onClick = onClick)

    /** Painter를 받던 기존 위치 인수 생성자를 유지한다. */
    public constructor(
        title: String,
        systemImage: String?,
        enabled: Boolean,
        menu: List<NativeMenuAction>,
        image: Painter?,
        onClick: () -> Unit,
    ) : this(title, systemImage, enabled, menu, image, badge = 0, onClick = onClick)

    /** 배지를 받던 기존 위치 인수 생성자를 유지한다. */
    public constructor(
        title: String,
        systemImage: String?,
        enabled: Boolean,
        menu: List<NativeMenuAction>,
        image: Painter?,
        badge: Int,
        onClick: () -> Unit,
    ) : this(title, systemImage, enabled, menu, image, badge, isPrimary = false, onClick = onClick)
}

/**
 * An action of a [NativeBarButton.menu].
 *
 * @param enabled 메뉴 항목의 활성 상태.
 * @param badge 0이면 숨김, 양수이면 숫자, 음수이면 표시점이다. UIKit 메뉴에서는 부제목으로 표시한다.
 *
 * @param image 사용자 이미지. 지정하면 [systemImage]보다 우선하며, iOS에서는 네이티브 tint를 적용한다.
 */
@Immutable
public class NativeMenuAction(
    public val title: String,
    public val systemImage: String? = null,
    public val destructive: Boolean = false,
    public val image: Painter? = null,
    public val enabled: Boolean = true,
    public val badge: Int = 0,
    public val onClick: () -> Unit,
) {
    /** 기존 생성자의 위치 인수와 기본 인수 호출을 유지한다. */
    public constructor(
        title: String,
        systemImage: String? = null,
        destructive: Boolean = false,
        onClick: () -> Unit,
    ) : this(title, systemImage, destructive, image = null, enabled = true, badge = 0, onClick = onClick)

    /** Painter를 받던 기존 위치 인수 생성자를 유지한다. */
    public constructor(
        title: String,
        systemImage: String?,
        destructive: Boolean,
        image: Painter?,
        onClick: () -> Unit,
    ) : this(title, systemImage, destructive, image, enabled = true, badge = 0, onClick = onClick)
}

/** Everything one screen declared with [NativeBarItems]. */
@Immutable
public class NativeBarContent internal constructor(
    public val title: String?,
    public val leadingComponents: List<NativeBarComponent>,
    public val trailing: List<NativeBarButton>,
    public val toolbar: List<NativeBarButton>,
) {
    public val leading: List<NativeBarButton> get() = leadingComponents.filterIsInstance<NativeBarButton>()

    init {
        val textItems = leadingComponents.filterIsInstance<NativeBarText>()
        require(textItems.distinctBy(NativeBarText::key).size == textItems.size) {
            "내비게이션 바 텍스트의 키는 중복될 수 없습니다."
        }
    }
}

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
    leading: List<NativeBarComponent> = emptyList(),
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

/**
 * 현재 화면의 네이티브 내비게이션 바 부제목을 선언합니다.
 * 제목과 버튼은 [NativeBarItems]로 별도 선언할 수 있습니다.
 * iOS 26 이상에서는 부제목을, 이전 버전에서는 프롬프트를 사용합니다.
 *
 * @param text 표시할 부제목. null이면 부제목을 지웁니다.
 */
@Composable
public fun NativeBarSubtitle(text: String?) {
    val state = LocalNativeBarState.current ?: return
    val owner = remember { Any() }
    SideEffect { state.registerSubtitle(owner, text) }
    DisposableEffect(state, owner) {
        onDispose { state.unregisterSubtitle(owner) }
    }
}

/**
 * 네이티브 내비게이션 바 왼쪽에 읽기 전용 라벨을 표시합니다.
 * [text]가 null이면 라벨을 숨기며, 표시 중 텍스트 갱신은 버튼과 독립적으로 처리합니다.
 *
 * @param width 라벨에 확보할 너비(포인트)
 * @param accessibilityLabel 화면 읽기 기능에 제공할 문구. null이면 [text]를 읽습니다.
 */
@Composable
public fun NativeBarLeadingLabel(text: String?, width: Float, accessibilityLabel: String? = null) {
    val state = LocalNativeBarState.current ?: return
    val owner = remember { Any() }
    val label = text?.let { NativeLeadingLabel(it, width, accessibilityLabel) }
    SideEffect { state.registerLeadingLabel(owner, label) }
    DisposableEffect(state, owner) {
        onDispose { state.unregisterLeadingLabel(owner) }
    }
}

internal data class NativeLeadingLabel(val text: String, val width: Float, val accessibilityLabel: String?)

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

    private val subtitles = mutableStateListOf<Pair<Any, String?>>()
    internal var subtitleListener: ((String?) -> Unit)? = null
    public val subtitle: String? get() = subtitles.lastOrNull()?.second

    internal fun registerSubtitle(owner: Any, text: String?) {
        val index = subtitles.indexOfFirst { it.first === owner }
        if (index >= 0) subtitles[index] = owner to text else subtitles.add(owner to text)
        subtitleListener?.invoke(subtitle)
    }

    internal fun unregisterSubtitle(owner: Any) {
        subtitles.removeAll { it.first === owner }
        subtitleListener?.invoke(subtitle)
    }

    private val leadingLabels = mutableStateListOf<Pair<Any, NativeLeadingLabel?>>()
    internal var leadingLabelListener: ((NativeLeadingLabel?) -> Unit)? = null

    internal fun registerLeadingLabel(owner: Any, label: NativeLeadingLabel?) {
        val index = leadingLabels.indexOfFirst { it.first === owner }
        if (index >= 0) leadingLabels[index] = owner to label else leadingLabels.add(owner to label)
        leadingLabelListener?.invoke(leadingLabels.lastOrNull()?.second)
    }

    internal fun unregisterLeadingLabel(owner: Any) {
        leadingLabels.removeAll { it.first === owner }
        leadingLabelListener?.invoke(leadingLabels.lastOrNull()?.second)
    }

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
    val leading: List<ComponentShape>,
    val trailing: List<ButtonShape>,
    val toolbar: List<ButtonShape>,
) {
    sealed interface ComponentShape

    data class TextShape(val key: String) : ComponentShape

    data class ButtonShape(
        val title: String,
        val systemImage: String?,
        val enabled: Boolean,
        val image: Painter?,
        val badge: Int,
        val isPrimary: Boolean,
        val tintColor: Color,
        val menu: List<MenuShape>,
    ) : ComponentShape

    data class MenuShape(
        val title: String,
        val systemImage: String?,
        val destructive: Boolean,
        val image: Painter?,
        val enabled: Boolean,
        val badge: Int,
    )

    companion object {
        val Empty = BarShape(emptyList(), emptyList(), emptyList())
    }
}

internal fun NativeBarContent?.shape(): BarShape {
    if (this == null) return BarShape.Empty
    fun NativeBarButton.shape() =
        BarShape.ButtonShape(
            title = title,
            systemImage = systemImage,
            enabled = enabled,
            image = image,
            badge = badge,
            isPrimary = isPrimary,
            tintColor = tintColor,
            menu = menu.map { BarShape.MenuShape(it.title, it.systemImage, it.destructive, it.image, it.enabled, it.badge) },
        )
    val leadingShapes = leadingComponents.map { item ->
        when (item) {
            is NativeBarButton -> item.shape()
            is NativeBarText -> BarShape.TextShape(item.key)
        }
    }
    return BarShape(leadingShapes, trailing.map { it.shape() }, toolbar.map { it.shape() })
}
