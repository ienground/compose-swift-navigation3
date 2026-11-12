package zone.ien.utils.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import zone.ien.navigation.nativeScreen

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class DetailRoute(val id: Int) : NavKey

/** Non-Android targets need the NavKey subclasses registered to save the back stacks. */
private val navigationConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeRoute::class)
            subclass(SettingsRoute::class)
            subclass(DetailRoute::class)
        }
    }
}

/** Debug log hook (the iOS entry point routes it to the system log for UI tests). */
var appLog: (String) -> Unit = {}

/**
 * The app's navigation state: one Navigation 3 back stack per tab. Shared by every platform;
 * Android/desktop render it with NavDisplay, iOS with the native controllers.
 */
@Stable
class AppNavigation(val stacks: List<NavBackStack<NavKey>>, initialTab: Int) {
    var selectedTab by mutableIntStateOf(initialTab)

    val currentStack: NavBackStack<NavKey> get() = stacks[selectedTab]

    fun open(route: NavKey) {
        currentStack.add(route)
    }

    fun goBack() {
        if (currentStack.size > 1) currentStack.removeLastOrNull()
    }

    fun replaceTop(route: NavKey) {
        currentStack[currentStack.lastIndex] = route
    }

    fun popToRoot() {
        while (currentStack.size > 1) currentStack.removeLastOrNull()
    }

    /** Builds a multi-level stack at once, like a deep link would. */
    fun deepLink(tab: Int, vararg routes: NavKey) {
        selectedTab = tab
        val stack = stacks[tab]
        while (stack.size > 1) stack.removeLastOrNull()
        stack.addAll(routes)
    }

    fun describe(): String = "tab=$selectedTab " + stacks.joinToString(" | ") { stack ->
        stack.joinToString(",") { it.toString() }
    }
}

@Composable
fun rememberAppNavigation(): AppNavigation {
    val home = rememberNavBackStack(navigationConfiguration, HomeRoute)
    val settings = rememberNavBackStack(navigationConfiguration, SettingsRoute)
    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val navigation = remember(home, settings) { AppNavigation(listOf(home, settings), selectedTab.intValue) }
    // Keep the saved tab in sync with the state holder.
    SideEffect { selectedTab.intValue = navigation.selectedTab }
    return navigation
}

fun appEntryProvider(navigation: AppNavigation): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<HomeRoute>(metadata = nativeScreen(title = "Home", largeTitle = true)) {
        HomeScreen(navigation)
    }
    // Odd details hide the tab bar, even ones keep it (to show both behaviors).
    entry<DetailRoute>(metadata = { key -> nativeScreen(title = "Detail ${key.id}", hidesTabBar = key.id % 2 == 1) }) { key ->
        DetailScreen(key.id, navigation)
    }
    entry<SettingsRoute>(metadata = nativeScreen(title = "Settings", largeTitle = true)) {
        SettingsScreen()
    }
}
