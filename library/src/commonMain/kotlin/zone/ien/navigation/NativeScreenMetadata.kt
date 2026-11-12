package zone.ien.navigation

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
 * visibility). Elsewhere it is plain data the app can read with [nativeTitle] etc., e.g. for its
 * own Material 3 top app bar.
 *
 * @param title navigation bar title.
 * @param largeTitle show the large title (collapsing with the scroll) instead of an inline title.
 * @param hidesTabBar hide the tab bar while this screen is pushed (`hidesBottomBarWhenPushed`).
 */
public fun nativeScreen(
    title: String? = null,
    largeTitle: Boolean = false,
    hidesTabBar: Boolean = false,
): Map<String, Any> = metadata {
    if (title != null) put(NativeTitle, title)
    put(NativeLargeTitle, largeTitle)
    put(NativeHidesTabBar, hidesTabBar)
}

public val NavEntry<*>.nativeTitle: String? get() = metadata[NativeTitle]
public val NavEntry<*>.nativeLargeTitle: Boolean get() = metadata[NativeLargeTitle] ?: false
public val NavEntry<*>.nativeHidesTabBar: Boolean get() = metadata[NativeHidesTabBar] ?: false

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
