package zone.ien.navigation

import androidx.navigation3.runtime.NavEntry
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.hidesBottomBarWhenPushed
import kotlin.test.Test
import kotlin.test.assertEquals

/** A stack in a tab: its own entries, then NativeTabs' shared screens (UIKit, no window). */
class StackCoordinatorTest {

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
        assertEquals(listOf(false, false, true, true), navigation.screens.map { it.hidesBottomBarWhenPushed })
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
        assertEquals(listOf(false, false, true), navigation.screens.map { it.hidesBottomBarWhenPushed })
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
        assertEquals(true, navigation.screens.last().hidesBottomBarWhenPushed)
        stack.dispose()
    }

}
