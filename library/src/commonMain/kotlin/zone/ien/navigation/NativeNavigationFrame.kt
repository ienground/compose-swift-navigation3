package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntryDecorator

/**
 * The app's frame around [NativeStack] / [NativeTabs], written once in common code the way an app
 * wraps its `NavDisplay` today:
 *
 * ```
 * @Composable
 * fun App() {
 *     NativeNavigationFrame(
 *         providers = { content -> CompositionLocalProvider(LocalAppGraph provides graph, content = content) },
 *         frame = { content -> AppTheme { content() } },
 *     ) {
 *         NativeTabs(...) { /* other platforms: Scaffold + NavDisplay(entries) */ }
 *     }
 * }
 * ```
 *
 * - **Other platforms** (Android, desktop, web): `providers { frame { content() } }`, once around
 *   everything, as before.
 * - **iOS** (inside `NativeNavigationViewController`): every screen is its own composition and
 *   the navigation itself runs in a composition without UI. So [providers] wrap both that
 *   composition (state holders and alerts in [content] see them) and every screen, and [frame]
 *   wraps every screen.
 *
 * @param providers composition locals the whole app needs (dependency injection, design tokens).
 *   It must not emit UI: on iOS it also runs in the navigation's composition without UI.
 * @param frame the app's UI around its screens (theme, root scaffold, snackbar host). On iOS it
 *   runs inside each screen, below [LocalNativeScreen] = `true`.
 */
@Composable
public fun NativeNavigationFrame(
    providers: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    frame: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    content: @Composable () -> Unit,
) {
    if (LocalNativeNavigationHost.current == null) {
        providers { frame(content) }
        return
    }
    val currentProviders = rememberUpdatedState(providers)
    val currentFrame = rememberUpdatedState(frame)
    val screenFrame = remember { NativeScreenFrame(currentProviders, currentFrame) }
    providers {
        CompositionLocalProvider(LocalNativeScreenFrame provides screenFrame, content = content)
    }
}

/**
 * `true` inside a screen shown by the native navigation (iOS: an entry's `UIViewController`, or a
 * [ComposeScreenViewController]). There the system navigation bar shows the title, the back button
 * and the bar items, so a screen does not draw its own top app bar, and the keyboard is handled
 * by the native scroll (no `imePadding`). `false` on other platforms.
 */
public val LocalNativeScreen: CompositionLocal<Boolean> get() = LocalNativeScreenProvidable

/** 현재 네이티브 화면의 내비게이션 바가 숨겨지도록 선언되었는지 나타냅니다. */
public val LocalNativeNavigationBarHidden: CompositionLocal<Boolean>
    get() = LocalNativeNavigationBarHiddenProvidable

internal val LocalNativeNavigationBarHiddenProvidable = staticCompositionLocalOf { false }

internal val LocalNativeScreenProvidable = staticCompositionLocalOf { false }

/** [NativeNavigationFrame]'s providers and frame, applied around each iOS screen. */
internal class NativeScreenFrame(
    private val providers: State<@Composable (@Composable () -> Unit) -> Unit>,
    private val frame: State<@Composable (@Composable () -> Unit) -> Unit>,
) {
    @Composable
    fun Wrap(content: @Composable () -> Unit) {
        val providers by providers
        val frame by frame
        providers { frame(content) }
    }
}

internal val LocalNativeScreenFrame = staticCompositionLocalOf<NativeScreenFrame?> { null }

/**
 * [entryDecorators] with the [NativeNavigationFrame] of the iOS host as the outermost decorator, so
 * each screen's content runs inside the app's providers and frame. Unchanged elsewhere.
 */
@Composable
internal fun <T : Any> rememberFramedDecorators(entryDecorators: List<NavEntryDecorator<T>>): List<NavEntryDecorator<T>> {
    val screenFrame = LocalNativeScreenFrame.current ?: return entryDecorators
    val frameDecorator = remember(screenFrame) {
        NavEntryDecorator<T> { entry -> screenFrame.Wrap { entry.Content() } }
    }
    return listOf(frameDecorator) + entryDecorators
}
