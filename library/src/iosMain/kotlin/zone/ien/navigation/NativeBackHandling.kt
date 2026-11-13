package zone.ien.navigation

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import platform.UIKit.UIAction
import platform.UIKit.UIViewController
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/**
 * Connects back handlers declared in a screen's content (`BackHandler`, `NavigationBackHandler`,
 * `NavigationEventHandler`) to the native back navigation, with Android's semantics: back goes to
 * the topmost handler whose back is enabled, otherwise the screen pops natively.
 *
 * The content registers its handlers with this screen's own [dispatcher] (provided as
 * `LocalNavigationEventDispatcherOwner`), not with Compose Multiplatform's, so this class alone
 * routes back. Navigation event dispatchers only report whether a handler is enabled for back
 * *or* forward (`NavigationEventHandler`'s `isForwardEnabled` defaults to true), so back is not
 * decided up front but where the dispatcher resolves it:
 * - Native back button: while the content has an enabled handler, `navigationItem.backAction`
 *   sends back through the dispatcher. A handler with back enabled takes it; otherwise the
 *   dispatcher's fallback pops natively. A root screen never gets a back action (UIKit would show
 *   a back button on it).
 * - Edge swipe ([NativeBackSwipe]): [beginSwipe] tells, when the gesture starts, whether a handler
 *   with back enabled takes it. That handler then receives the predictive back events; otherwise
 *   UIKit's interactive pop runs.
 */
@OptIn(ExperimentalNativeApi::class)
internal class NativeBackHandling(private val controller: UIViewController) : NavigationEventInput() {

    /** Dispatcher of the screen's content; back that no handler takes pops the screen. */
    val dispatcher = NavigationEventDispatcher(onBackCompletedFallback = { popNatively() })

    val dispatcherOwner: NavigationEventDispatcherOwner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher: NavigationEventDispatcher get() = dispatcher
    }

    /**
     * Back-enabled only while a swipe start is resolved. Handlers are searched last-in first-out
     * and this one is added first, so it receives the start only when no content handler takes it.
     */
    private val probe = ProbeHandler(onCompleted = ::popNatively)

    /** The content has a handler enabled for back or forward (it may take back). */
    private var hasEnabledHandlers = false

    init {
        dispatcher.addHandler(probe)
        dispatcher.addInput(this)
    }

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        if (probe.isBackEnabled) return // the probe itself, not the content
        this.hasEnabledHandlers = hasEnabledHandlers
        updateBackAction()
    }

    /**
     * Routes the back button through the dispatcher while the content may take back. Called when
     * the handlers change and when the screen's place in its stack may have changed.
     */
    fun updateBackAction() {
        val navigation = controller.navigationController
        // isEqual (==): Kotlin references to the same Objective-C object are not always identical.
        val isRoot = navigation == null || navigation.viewControllers.firstOrNull() == controller
        val intercepts = hasEnabledHandlers && !isRoot
        val item = controller.navigationItem
        if ((item.backAction != null) == intercepts) return
        item.backAction = if (intercepts) {
            // Weak: the screen's navigation item keeps the action, and the screen keeps this object.
            val handling = WeakReference(this)
            UIAction.actionWithTitle("", image = null, identifier = null) { _ -> handling.get()?.dispatchBack() }
        } else {
            null
        }
    }

    /** A non-predictive back (the back button): the topmost back handler, or a native pop. */
    fun dispatchBack() {
        dispatchOnBackCompleted()
    }

    /**
     * An edge swipe back starts. Returns true when a content handler with back enabled takes it:
     * it then receives [moveSwipe] and [endSwipe]. Returns false to leave the swipe to UIKit;
     * nothing was delivered to the content then.
     */
    fun beginSwipe(event: NavigationEvent): Boolean {
        if (!hasEnabledHandlers) return false
        probe.reached = false
        probe.isBackEnabled = true
        dispatchOnBackStarted(event)
        probe.isBackEnabled = false
        if (probe.reached) {
            dispatchOnBackCancelled()
            return false
        }
        return true
    }

    fun moveSwipe(event: NavigationEvent) {
        dispatchOnBackProgressed(event)
    }

    fun endSwipe(completed: Boolean) {
        if (completed) dispatchOnBackCompleted() else dispatchOnBackCancelled()
    }

    private fun popNatively() {
        val navigation = controller.navigationController ?: return
        if (navigation.topViewController == controller && navigation.viewControllers.size > 1) {
            navigation.popViewControllerAnimated(true)
        }
    }

    private class ProbeHandler(private val onCompleted: () -> Unit) : NavigationEventHandler<NavigationEventInfo>(
        initialInfo = NavigationEventInfo.None,
        isBackEnabled = false,
        isForwardEnabled = false,
    ) {
        var reached = false

        override fun onBackStarted(event: NavigationEvent) {
            reached = true
        }

        // Not expected (the probe is cancelled right away); behave like the fallback if it happens.
        override fun onBackCompleted() {
            onCompleted()
        }
    }
}
