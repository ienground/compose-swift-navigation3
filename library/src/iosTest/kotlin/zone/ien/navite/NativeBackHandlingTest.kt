package zone.ien.navite

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeBackHandlingTest {

    private class RecordingHandler(back: Boolean, forward: Boolean) :
        NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, back, forward) {
        val events = mutableListOf<String>()

        override fun onBackStarted(event: NavigationEvent) { events += "started" }
        override fun onBackProgressed(event: NavigationEvent) { events += "progressed" }
        override fun onBackCancelled() { events += "cancelled" }
        override fun onBackCompleted() { events += "completed" }
    }

    private fun screen() = UIViewController(nibName = null, bundle = null)

    /** A screen pushed above a root screen, with its back handling. */
    private fun pushedScreen(): Triple<UINavigationController, UIViewController, NativeBackHandling> {
        val navigation = UINavigationController(rootViewController = screen())
        val controller = screen()
        val handling = NativeBackHandling(controller)
        navigation.pushViewController(controller, animated = false)
        return Triple(navigation, controller, handling)
    }

    @Test
    fun withoutHandlersTheSwipeIsUIKits() {
        val (_, controller, handling) = pushedScreen()
        handling.updateBackAction()
        assertFalse(handling.beginSwipe(NavigationEvent()))
        assertNull(controller.navigationItem.backAction)
    }

    @Test
    fun forwardOnlyHandlerDoesNotTakeBack() {
        val (navigation, _, handling) = pushedScreen()
        val forwardOnly = RecordingHandler(back = false, forward = true)
        handling.dispatcher.addHandler(forwardOnly)

        assertFalse(handling.beginSwipe(NavigationEvent()), "the swipe must be left to UIKit's interactive pop")
        handling.dispatchBack()

        assertEquals(emptyList(), forwardOnly.events)
        assertEquals(1, navigation.viewControllers.size, "the back button pops natively")
    }

    @Test
    fun backHandlerTakesTheSwipeAndTheButton() {
        val (navigation, _, handling) = pushedScreen()
        val forwardOnly = RecordingHandler(back = false, forward = true)
        val guard = RecordingHandler(back = true, forward = false)
        handling.dispatcher.addHandler(forwardOnly)
        handling.dispatcher.addHandler(guard)

        assertTrue(handling.beginSwipe(NavigationEvent()))
        handling.moveSwipe(NavigationEvent(progress = 0.3f))
        handling.endSwipe(completed = true)
        handling.dispatchBack()

        assertEquals(listOf("started", "progressed", "completed", "completed"), guard.events)
        assertEquals(emptyList(), forwardOnly.events)
        assertEquals(2, navigation.viewControllers.size, "the handler decides, nothing pops")
    }

    @Test
    fun cancelledSwipeReachesTheHandler() {
        val (_, _, handling) = pushedScreen()
        val guard = RecordingHandler(back = true, forward = false)
        handling.dispatcher.addHandler(guard)

        assertTrue(handling.beginSwipe(NavigationEvent()))
        handling.endSwipe(completed = false)

        assertEquals(listOf("started", "cancelled"), guard.events)
    }

    @Test
    fun onlyPushedScreensRouteTheBackButton() {
        val rootController = screen()
        val rootHandling = NativeBackHandling(rootController)
        val navigation = UINavigationController(rootViewController = rootController)
        rootHandling.dispatcher.addHandler(RecordingHandler(back = true, forward = false))
        rootHandling.updateBackAction()
        assertNull(rootController.navigationItem.backAction, "UIKit would show a back button on the root")

        val pushed = screen()
        val pushedHandling = NativeBackHandling(pushed)
        navigation.pushViewController(pushed, animated = false)
        pushedHandling.dispatcher.addHandler(RecordingHandler(back = false, forward = true))
        assertNotNull(pushed.navigationItem.backAction, "a pushed screen whose content may take back routes the button")
    }
}
