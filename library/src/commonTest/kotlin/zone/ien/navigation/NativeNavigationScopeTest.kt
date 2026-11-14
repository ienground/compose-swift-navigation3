package zone.ien.navigation

import androidx.navigation3.runtime.NavEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeNavigationScopeTest {

    private fun entry(key: String, title: String? = null, hidesTabBar: Boolean = false) =
        NavEntry(key, metadata = nativeScreen(title = title, hidesTabBar = hidesTabBar)) {}

    private fun scope(
        entries: List<NavEntry<String>>,
        tabs: List<NativeTab<String>> = emptyList(),
        bar: NativeBarState = NativeBarState(),
    ) = OtherPlatformScope(entries, onBack = {}, tabs = tabs, selectedTab = 0, onTabSelected = {}, bar = bar)

    private val tabs = listOf(NativeTab("Home", "house", listOf("home")))

    @Test
    fun titleComesFromTheTopEntryMetadata() {
        val scope = scope(listOf(entry("home", "Home"), entry("detail", "Detail")))
        assertEquals("Detail", scope.title)
    }

    @Test
    fun declaredTitleOverridesTheMetadata() {
        val bar = NativeBarState()
        val scope = scope(listOf(entry("home", "Home"), entry("detail", "Detail")), bar = bar)
        bar.register("detail", NativeBarContent("Detail (2)", emptyList(), emptyList(), emptyList()))
        assertEquals("Detail (2)", scope.title)
        bar.register("detail", NativeBarContent(null, emptyList(), emptyList(), emptyList()))
        assertEquals("Detail", scope.title)
    }

    @Test
    fun canGoBackOnlyAboveTheRoot() {
        assertFalse(scope(listOf(entry("home"))).canGoBack)
        assertTrue(scope(listOf(entry("home"), entry("detail"))).canGoBack)
    }

    @Test
    fun aStackHasNoTabBar() {
        assertFalse(scope(listOf(entry("home"))).tabBarVisible)
    }

    @Test
    fun tabBarFollowsThePushedScreens() {
        assertTrue(scope(listOf(entry("home")), tabs).tabBarVisible)
        assertTrue(scope(listOf(entry("home"), entry("detail")), tabs).tabBarVisible)
        assertFalse(scope(listOf(entry("home"), entry("detail", hidesTabBar = true)), tabs).tabBarVisible)
    }

    @Test
    fun screensAboveAHidingScreenKeepTheTabBarHidden() {
        // UIKit's hidesBottomBarWhenPushed: the bar stays hidden above a screen that hid it.
        val entries = listOf(entry("home"), entry("hiding", hidesTabBar = true), entry("detail"))
        assertFalse(scope(entries, tabs).tabBarVisible)
    }

    @Test
    fun onlyPushedScreensHideTheTabBar() {
        // hidesTabBar means "while pushed": the root of a tab is never pushed.
        assertTrue(scope(listOf(entry("home", hidesTabBar = true)), tabs).tabBarVisible)
    }

    @Test
    fun barItemsComeFromTheTopScreen() {
        val bar = NativeBarState()
        val scope = scope(listOf(entry("home")), bar = bar)
        assertNull(scope.search)
        val add = NativeBarButton("Add")
        bar.register("home", NativeBarContent(null, emptyList(), listOf(add), emptyList()))
        assertEquals(listOf(add), scope.trailing)
    }
}
