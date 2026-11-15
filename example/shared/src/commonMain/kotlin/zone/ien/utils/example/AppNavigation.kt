package zone.ien.utils.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.painterResource
import zone.ien.navigation.nativeScreen
import zone.ien.utils.example.resources.Res
import zone.ien.utils.example.resources.compose_multiplatform
import zone.ien.utils.example.resources.example_logo

@Serializable
data object HomeRoute : NavKey

@Serializable
data object GalleryRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class DetailRoute(val id: Int) : NavKey

/** Opened from any tab over the selected tab (NativeTabs' shared back stack). */
@Serializable
data object ProfileRoute : NavKey

/** Signed-out flow: its own stack, shown instead of the tabs. */
@Serializable
data object SignInRoute : NavKey

@Serializable
data object SignInHelpRoute : NavKey

/** Non-Android targets need the NavKey subclasses registered to save the back stacks. */
private val navigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeRoute::class)
            subclass(GalleryRoute::class)
            subclass(SettingsRoute::class)
            subclass(DetailRoute::class)
            subclass(ProfileRoute::class)
            subclass(SignInRoute::class)
            subclass(SignInHelpRoute::class)
        }
    }
}

/** Debug log hook (the iOS entry point routes it to the system log for UI tests). */
var appLog: (String) -> Unit = {}

/**
 * The app's navigation state: one Navigation 3 back stack per tab, a [shared] stack of screens
 * shown over the selected tab (opened from any tab), and a separate sign-in stack shown instead of
 * the tabs while signed out. Shared by every platform; Android/desktop render it with NavDisplay,
 * iOS with the native controllers.
 *
 * [selectedTab] and [signedIn] live directly in the saveable states they are given, so they are
 * saved whoever reads them (no copying in a side effect, which only runs when the caller of
 * [rememberAppNavigation] recomposes).
 */
@Stable
class AppNavigation(
    val stacks: List<NavBackStack<NavKey>>,
    val shared: NavBackStack<NavKey>,
    val signInStack: NavBackStack<NavKey>,
    selectedTab: MutableIntState,
    signedIn: MutableState<Boolean>,
) {
    var selectedTab by selectedTab
    var signedIn by signedIn
        private set

    /** Where [open] pushes: the shared screens while one is shown, else the selected tab's stack. */
    val currentStack: NavBackStack<NavKey>
        get() = when {
            !signedIn -> signInStack
            shared.isNotEmpty() -> shared
            else -> stacks[selectedTab]
        }

    /** Leaves the sign-in flow for the tabs (iOS switches the root to the tab bar controller). */
    fun signIn() {
        signedIn = true
        signInStack.resetTo(SignInRoute)
    }

    /** Leaves the tabs for the sign-in flow; the tabs start over at their roots next time. */
    fun signOut() {
        signedIn = false
        selectedTab = 0
        shared.clear()
        stacks.forEach { stack -> stack.resetTo(stack.first()) }
    }

    fun open(route: NavKey) {
        currentStack.add(route)
    }

    /** Opens [route] over the selected tab, whichever tab it is (e.g. a profile). */
    fun openShared(route: NavKey) {
        shared.add(route)
    }

    fun closeShared() {
        shared.clear()
    }

    fun goBack() {
        if (shared.isNotEmpty()) shared.removeLastOrNull()
        else if (currentStack.size > 1) currentStack.removeLastOrNull()
    }

    fun replaceTop(route: NavKey) {
        currentStack[currentStack.lastIndex] = route
    }

    fun popToRoot() {
        shared.clear()
        val stack = currentStack
        while (stack.size > 1) stack.removeLastOrNull()
    }

    /** Builds a multi-level stack at once, like a deep link would. */
    fun deepLink(tab: Int, vararg routes: NavKey) {
        selectedTab = tab
        shared.clear()
        val stack = stacks[tab]
        while (stack.size > 1) stack.removeLastOrNull()
        stack.addAll(routes)
    }

    fun describe(): String = if (!signedIn) {
        "signedOut " + signInStack.joinToString(",") { it.toString() }
    } else {
        "tab=$selectedTab " + stacks.joinToString(" | ") { stack -> stack.joinToString(",") { it.toString() } } +
            " shared=" + shared.joinToString(",") { it.toString() }
    }
}

private fun NavBackStack<NavKey>.resetTo(root: NavKey) {
    if (size == 1 && first() == root) return
    clear()
    add(root)
}

@Composable
fun rememberAppNavigation(): AppNavigation {
    val home = rememberNavBackStack(navigationConfiguration, HomeRoute)
    val gallery = rememberNavBackStack(navigationConfiguration, GalleryRoute)
    val settings = rememberNavBackStack(navigationConfiguration, SettingsRoute)
    val shared = rememberNavBackStack(navigationConfiguration)
    val signIn = rememberNavBackStack(navigationConfiguration, SignInRoute)
    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val signedIn = rememberSaveable { mutableStateOf(true) }
    return remember(home, gallery, settings, shared, signIn, selectedTab, signedIn) {
        AppNavigation(listOf(home, gallery, settings), shared, signIn, selectedTab, signedIn)
    }
}

fun appEntryProvider(navigation: AppNavigation): (NavKey) -> NavEntry<NavKey> = entryProvider {
    // Title images: a bitmap logo on a large-title root, a vector one on a pushed inline screen.
    entry<HomeRoute>(
        metadata = nativeScreen(title = "Home", largeTitle = true, titleImage = { painterResource(Res.drawable.example_logo) }),
    ) {
        HomeScreen(navigation)
    }
    // Odd details hide the tab bar, even ones keep it (to show both behaviors).
    entry<DetailRoute>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = key.id % 2 == 1) }) { key ->
        DetailScreen(key.id, navigation)
    }
    entry<GalleryRoute>(metadata = nativeScreen(title = "Gallery", largeTitle = true)) {
        GalleryScreen(navigation)
    }
    entry<SettingsRoute>(metadata = nativeScreen(title = "Settings", largeTitle = true)) {
        SettingsScreen(navigation)
    }
    // Shared screens always hide the tab bar; no hidesTabBar needed.
    entry<ProfileRoute>(
        metadata = nativeScreen(title = "Profile", titleImage = { painterResource(Res.drawable.compose_multiplatform) }),
    ) {
        ProfileScreen(navigation)
    }
    entry<SignInRoute>(metadata = nativeScreen(title = "Welcome", largeTitle = true)) {
        SignInScreen(navigation)
    }
    entry<SignInHelpRoute>(metadata = nativeScreen(title = "Help")) {
        SignInHelpScreen(navigation)
    }
}
