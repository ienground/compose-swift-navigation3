package zone.ien.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.childViewControllers
import platform.UIKit.tabBarItem
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import androidx.navigation3.runtime.NavEntry
import platform.UIKit.UITabBarController
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.hidesBottomBarWhenPushed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A stack in a tab: its own entries, then NativeTabs' shared screens (UIKit, no window). */
class StackCoordinatorTest {

    @Test
    fun customTabImagesReachUIKitBeforeScreenComposition() {
        val controller = NativeNavigationViewController {
            NativeTabs(
                tabs = listOf(NativeTab(
                    title = "Home", backStack = listOf("home"),
                    image = ColorPainter(Color.Black), selectedImage = ColorPainter(Color.White),
                )),
                selectedTab = 0,
                onTabSelected = {},
                entryProvider = { NavEntry(it) {} },
            ) {}
        }
        controller.loadViewIfNeeded()
        val tabs = assertIs<UITabBarController>(controller.childViewControllers.single())
        val item = assertIs<UINavigationController>(tabs.viewControllers!!.single()).tabBarItem
        val image = assertNotNull(item.image)
        val selected = assertNotNull(item.selectedImage)
        assertNotSame(image, selected)
        assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate, image.renderingMode)
        assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate, selected.renderingMode)
    }

    @Test
    fun tabTintTracksChangesAndRestoresSystemColor() {
        val tint = mutableStateOf(Color.Red)
        val controller = NativeNavigationViewController {
            NativeTabBarTint(tint.value) {
                NativeTabs(
                    tabs = listOf(NativeTab("Home", "house", listOf("home"))),
                    selectedTab = 0, onTabSelected = {},
                    entryProvider = { NavEntry(it) {} },
                ) {}
            }
        }
        controller.loadViewIfNeeded()
        val tabs = assertIs<UITabBarController>(controller.childViewControllers.single())
        val systemBar = UITabBarController().tabBar
        assertEquals(UIColor(red = 1.0, green = 0.0, blue = 0.0, alpha = 1.0), tabs.tabBar.tintColor)
        val unselected = tabs.tabBar.unselectedItemTintColor
        tint.value = Color.Blue
        Snapshot.sendApplyNotifications()
        repeat(10) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02)) }
        assertEquals(UIColor(red = 0.0, green = 0.0, blue = 1.0, alpha = 1.0), tabs.tabBar.tintColor)
        assertEquals(unselected, tabs.tabBar.unselectedItemTintColor)
        tint.value = Color.Unspecified
        Snapshot.sendApplyNotifications()
        repeat(10) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02)) }
        assertEquals(systemBar.tintColor, tabs.tabBar.tintColor)
    }

    private fun entry(key: String, hidesTabBar: Boolean = false) =
        NavEntry(key, metadata = nativeScreen(hidesTabBar = hidesTabBar)) {}

    private val calls = mutableListOf<String>()

    private fun coordinator(): Pair<StackCoordinator, UINavigationController> {
        val navigation = UINavigationController()
        return StackCoordinator(navigation, ContentLeases()) to navigation
    }

    private fun StackCoordinator.show(own: List<String>, shared: List<String>) = update(
        entries = own.map { entry(it) },
        onBack = { calls += "own" },
        shared = shared.map { entry(it) },
        onSharedBack = { calls += "shared" },
        animated = false,
    )

    /** What `didShow` reports after UIKit popped by itself (back button, swipe, tab re-tap). */
    private fun UINavigationController.reportShown() {
        delegate!!.navigationController(this, didShowViewController = topViewController!!, animated = false)
    }

    private val UINavigationController.screens: List<UIViewController>
        get() = viewControllers.map { it as UIViewController }

    @Test
    fun sharedScreensArePushedAboveTheTabsOwnAndHideTheTabBar() {
        val (stack, navigation) = coordinator()
        stack.show(own = listOf("home", "detail"), shared = listOf("profile", "edit"))

        assertEquals(4, navigation.screens.size)
        assertEquals(listOf(false, false, true, true), navigation.screens.map { (it as ComposeScreenController).hidesTabBar })
    }

    @Test
    fun uikitPopsReachTheStackTheyCameFrom() {
        val (stack, navigation) = coordinator()
        stack.show(own = listOf("home", "detail"), shared = listOf("profile", "edit"))

        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("shared"), calls)

        // Tab re-tap: back to the root, top first.
        navigation.popToRootViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("shared", "shared", "own"), calls)
    }

    @Test
    fun sharedScreensLeaveATabThatIsNoLongerSelected() {
        val (stack, navigation) = coordinator()
        stack.show(own = listOf("home"), shared = listOf("profile"))
        stack.show(own = listOf("home"), shared = emptyList())

        assertEquals(1, navigation.screens.size)
        assertEquals(emptyList(), calls, "an app-driven change is not reported back")
    }

    @Test
    fun aSharedScreenHidesTheTabBarWhateverItsMetadata() {
        val (stack, navigation) = coordinator()
        stack.update(
            entries = listOf(entry("home"), entry("detail", hidesTabBar = false)),
            onBack = {},
            shared = listOf(entry("profile", hidesTabBar = false)),
            animated = false,
        )
        assertEquals(listOf(false, false, true), navigation.screens.map { (it as ComposeScreenController).hidesTabBar })
    }

    @Test
    fun pendingPushWinsOverAnOlderNativePop() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        val keys = mutableListOf("home", "detail")
        fun update() = stack.update(keys.map { entry(it) }, onBack = { keys.removeLast() }, animated = false)
        update()
        transitioning = true
        keys.add("new")
        update()
        assertEquals(2, navigation.screens.size)
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("home", "detail", "new"), keys)
        assertEquals(3, navigation.screens.size)
        stack.dispose()
    }

    @Test
    fun pendingPopDoesNotPopTheKotlinStackAgain() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        val keys = mutableListOf("home", "detail", "edit")
        var backCalls = 0
        fun update() = stack.update(keys.map { entry(it) }, onBack = {
            backCalls++
            keys.removeLast()
        }, animated = false)
        update()
        transitioning = true
        keys.removeLast()
        update()
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("home", "detail"), keys)
        assertEquals(0, backCalls)
        assertEquals(2, navigation.screens.size)
        stack.dispose()
    }

    @Test
    fun refreshedMetadataStillReportsTheNativePop() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        val keys = mutableListOf("home", "detail")
        stack.update(keys.map { entry(it) }, onBack = { keys.removeLast() }, animated = false)
        transitioning = true
        stack.update(
            keys.map { NavEntry(it, metadata = nativeScreen(title = "새 제목")) {} },
            onBack = { keys.removeLast() },
            animated = false,
        )
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("home"), keys)
        assertEquals(1, navigation.screens.size)
        stack.dispose()
    }

    @Test
    fun pendingSharedBoundaryChangeDoesNotPopTheNewStack() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        stack.show(own = listOf("home", "detail"), shared = emptyList())
        transitioning = true
        stack.show(own = listOf("home"), shared = listOf("detail"))
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(emptyList(), calls)
        assertEquals(2, navigation.screens.size)
        assertTrue((navigation.screens.last() as ComposeScreenController).hidesTabBar)
        stack.dispose()
    }

    private class RecordingTabs : UITabBarController(nibName = null, bundle = null) {
        val changes = mutableListOf<Pair<Boolean, Boolean>>()

        override fun setTabBarHidden(hidden: Boolean, animated: Boolean) {
            changes += hidden to animated
            super.setTabBarHidden(hidden, animated)
        }
    }

    @Test
    fun sharedPushHidesImmediatelyAfterTransitionPreparation() {
        val (stack, navigation) = coordinator()
        val tabs = RecordingTabs()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.show(own = listOf("home"), shared = emptyList())
        tabs.changes.clear()
        stack.update(
            entries = listOf(entry("home")),
            onBack = {},
            shared = listOf(entry("profile")),
            onSharedBack = {},
            animated = true,
        )
        assertTrue(tabs.tabBarHidden)
        assertTrue(tabs.changes.contains(true to false), "상세 화면의 push와 별개로 탭 바 숨김 애니메이션을 지연하면 안 됩니다.")
        assertFalse(tabs.changes.any { (hidden, animated) -> hidden && animated })
        stack.dispose()
    }

    @Test
    fun sharedPushHidesTheTabBarWithoutHidesBottomBarWhenPushed() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.show(own = listOf("home"), shared = emptyList())
        stack.show(own = listOf("home"), shared = listOf("profile"))
        navigation.topViewController!!.viewWillAppear(false)

        assertFalse(navigation.topViewController!!.hidesBottomBarWhenPushed,
            "push와 함께 이 플래그를 켜면 이전 화면의 큰 제목이 먼저 접힌다")
        assertTrue(tabs.tabBarHidden, "탭 바는 UITabBarController에서 숨긴다")
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        navigation.topViewController!!.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden, "루트로 돌아오면 탭 바를 복원한다")
        stack.dispose()
    }

    @Test
    fun appearingScreenRestoresTabVisibilityWhenInteractivePopIsCancelled() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.show(own = listOf("home"), shared = listOf("profile"))
        val root = navigation.screens.first()
        val detail = navigation.screens.last()
        detail.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        root.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden)
        detail.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden, "취소되면 다시 나타나는 상세 화면의 숨김 상태로 돌아간다")
        stack.dispose()
    }

    @Test
    fun screensAboveAHiddenEntryKeepTheTabBarHidden() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.update(listOf(entry("home"), entry("hidden", hidesTabBar = true), entry("detail")), {}, animated = false)
        navigation.topViewController!!.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        navigation.topViewController!!.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        navigation.topViewController!!.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden)
        stack.dispose()
    }

    @Test
    fun changingTheVisibleEntryMetadataUpdatesTabVisibility() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.update(listOf(entry("home"), entry("detail")), {}, animated = false)
        navigation.topViewController!!.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden)
        stack.update(listOf(entry("home"), entry("detail", hidesTabBar = true)), {}, animated = false)
        assertTrue(tabs.tabBarHidden)
        stack.update(listOf(entry("home"), entry("detail")), {}, animated = false)
        assertFalse(tabs.tabBarHidden)
        stack.dispose()
    }

    @Test
    fun unselectedStackDoesNotChangeTheVisibleTabBar() {
        val (stack, navigation) = coordinator()
        val other = UINavigationController()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(other, navigation), animated = false)
        stack.update(listOf(entry("home"), entry("detail")), {}, animated = false)
        stack.update(listOf(entry("home"), entry("detail", hidesTabBar = true)), {}, animated = false)
        assertFalse(tabs.tabBarHidden)
        tabs.selectedIndex = 1u
        navigation.topViewController!!.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        stack.dispose()
    }

}
