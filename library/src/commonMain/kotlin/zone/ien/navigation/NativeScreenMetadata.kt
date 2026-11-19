package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata

/**
 * Screen chrome for a [NavEntry], declared in the entry's metadata so it is shared by all
 * platforms:
 *
 * ```
 * entryProvider {
 *     entry<Detail>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = true) }) { ... }
 * }
 * ```
 *
 * On iOS the native navigation controllers read it (navigation bar title, large title, tab bar
 * visibility). Elsewhere it is plain data: [NativeStack] and [NativeTabs] hand it to the app's
 * `otherPlatforms` UI ([NativeNavigationScope.title], [NativeNavigationScope.tabBarVisible]), and
 * [nativeTitle] etc. read it from any entry.
 *
 * @param title navigation bar title.
 * @param largeTitle show the large title (collapsing with the scroll) instead of an inline title.
 * @param hidesTabBar hide the tab bar while this screen or a screen pushed above it is shown.
 * @param titleImage an image shown in the navigation bar in place of the title text, e.g. the
 *   app's logo: `{ painterResource(Res.drawable.logo) }`. Keep [title]: it still names the screen
 *   for VoiceOver and for the back button of the screen pushed above.
 *   - iOS: the navigation item's `titleView`, 28pt high with the painter's aspect ratio. The
 *     lambda runs once in the navigation host, outside every screen, so the image is ready for
 *     the push transition; the screen's theme and dark mode do not reach it.
 *   - Other platforms: [NativeNavigationScope.titleImage], for the app's own top app bar.
 */
public fun nativeScreen(
    title: String? = null,
    largeTitle: Boolean = false,
    hidesTabBar: Boolean = false,
    titleImage: (@Composable () -> Painter)? = null,
): Map<String, Any> = metadata {
    if (title != null) put(NativeTitle, title)
    put(NativeLargeTitle, largeTitle)
    put(NativeHidesTabBar, hidesTabBar)
    if (titleImage != null) put(NativeTitleImage, titleImage)
}

/**
 * UIKit 내비게이션 바를 화면 전환에 맞춰 표시하거나 숨깁니다.
 *
 * @param hidesNavigationBar 이 화면에서 내비게이션 바를 숨길지 여부. 값을 선언한 스택은
 * 표시 여부를 관리하며, 선언이 없는 화면에서는 내비게이션 바를 표시합니다.
 */
public fun nativeScreen(
    title: String? = null,
    largeTitle: Boolean = false,
    hidesTabBar: Boolean = false,
    titleImage: (@Composable () -> Painter)? = null,
    hidesNavigationBar: Boolean,
): Map<String, Any> = nativeScreen(title, largeTitle, hidesTabBar, titleImage) + metadata {
    put(NativeHidesNavigationBar, hidesNavigationBar)
}

/**
 * 화면이 열리기 전에 오른쪽 바 버튼을 준비합니다.
 * 화면에서 선언한 [NativeBarItems]는 UIKit 화면 전환이 끝나면 초기 버튼을 대체합니다.
 */
public fun nativeScreen(
    initialTrailing: List<NativeBarButton>,
    title: String? = null,
): Map<String, Any> = nativeScreen(title = title) + metadata {
    put(NativeInitialTrailing, initialTrailing)
}

internal val NavEntry<*>.nativeInitialTrailing: List<NativeBarButton>?
    get() = metadata[NativeInitialTrailing]

internal val NavEntry<*>.nativeHidesNavigationBar: Boolean? get() = metadata[NativeHidesNavigationBar]

public val NavEntry<*>.nativeTitle: String? get() = metadata[NativeTitle]
public val NavEntry<*>.nativeLargeTitle: Boolean get() = metadata[NativeLargeTitle] ?: false
public val NavEntry<*>.nativeHidesTabBar: Boolean get() = metadata[NativeHidesTabBar] ?: false
public val NavEntry<*>.nativeTitleImage: (@Composable () -> Painter)? get() = metadata[NativeTitleImage]

// Metadata maps are keyed by toString(): keep the names namespaced.
private object NativeTitle : NavMetadataKey<String> {
    override fun toString(): String = "zone.ien.navigation.title"
}

private object NativeLargeTitle : NavMetadataKey<Boolean> {
    override fun toString(): String = "zone.ien.navigation.largeTitle"
}

private object NativeHidesTabBar : NavMetadataKey<Boolean> {
    override fun toString(): String = "zone.ien.navigation.hidesTabBar"
}

private object NativeTitleImage : NavMetadataKey<@Composable () -> Painter> {
    override fun toString(): String = "zone.ien.navigation.titleImage"
}

private object NativeHidesNavigationBar : NavMetadataKey<Boolean> {
    override fun toString(): String = "zone.ien.navigation.hidesNavigationBar"
}

private object NativeInitialTrailing : NavMetadataKey<List<NativeBarButton>> {
    override fun toString(): String = "zone.ien.navigation.initialTrailing"
}
