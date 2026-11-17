package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.navigation3.runtime.NavEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeNavigationScopeTest {

    @Test
    fun tabImagesAndLegacyConstructorKeepTheirContracts() {
        val image = ColorPainter(Color.Black)
        val selected = ColorPainter(Color.White)
        val tab = NativeTab(title = "Home", backStack = listOf("home"), image = image, selectedImage = selected)
        assertSame(image, tab.image)
        assertSame(selected, tab.selectedImage)
        var pops = 0
        val legacy = NativeTab("Home", "house", listOf("home")) { pops++ }
        assertEquals("house", legacy.systemImage)
        assertNull(legacy.image)
        legacy.onBack()
        assertEquals(1, pops)
    }

    private fun entry(key: String, title: String? = null, hidesTabBar: Boolean = false) =
        NavEntry(key, metadata = nativeScreen(title = title, hidesTabBar = hidesTabBar)) {}

    private fun scope(
        entries: List<NavEntry<String>>,
        tabs: List<NativeTab<String>> = emptyList(),
        bar: NativeBarState = NativeBarState(),
        shared: List<NavEntry<String>> = emptyList(),
    ) = OtherPlatformScope(
        tabEntries = entries,
        sharedEntries = shared,
        onBack = {},
        tabs = tabs,
        selectedTab = 0,
        onTabSelected = {},
        bar = bar,
    )

    private val tabs = listOf(NativeTab("Home", "house", listOf("home")))

    /** The key the entry was made for (the default content key is the key plus its class). */
    private val NavEntry<String>.name: String get() = contentKey.toString().substringBefore(':')

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
    fun titleImageComesFromTheTopEntryMetadata() {
        val logo: @Composable () -> Painter = { ColorPainter(Color.Red) }
        val home = NavEntry("home", metadata = nativeScreen(title = "Home", titleImage = logo)) {}
        assertSame(logo, scope(listOf(home)).titleImage)
        assertNull(scope(listOf(home, entry("detail", "Detail"))).titleImage)
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

    @Test
    fun sharedScreensFollowTheSelectedTab() {
        val scope = scope(listOf(entry("home"), entry("detail")), tabs, shared = listOf(entry("profile", "Profile")))
        assertEquals(listOf("home", "detail", "profile"), scope.entries.map { it.name })
        assertEquals(listOf("home", "detail"), scope.tabEntries.map { it.name })
        assertEquals(listOf("profile"), scope.sharedEntries.map { it.name })
        assertEquals("Profile", scope.title)
        assertTrue(scope.canGoBack)
    }

    @Test
    fun sharedScreensHideTheTabBar() {
        // Whatever their metadata says: the user cannot switch tabs while a shared screen is shown.
        assertFalse(scope(listOf(entry("home")), tabs, shared = listOf(entry("profile"))).tabBarVisible)
        assertTrue(scope(listOf(entry("home")), tabs, shared = emptyList()).tabBarVisible)
    }

    @Test
    fun aSharedScreenAboveATabRootCanGoBack() {
        assertTrue(scope(listOf(entry("home")), tabs, shared = listOf(entry("profile"))).canGoBack)
    }
}
