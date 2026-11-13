package zone.ien.navigation

import androidx.navigationevent.NavigationEvent
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UINavigationController
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIRectEdgeRight
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIUserInterfaceLayoutDirection
import platform.UIKit.transitionCoordinator
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/**
 * Edge swipe back of one `UINavigationController` for screens whose content takes back itself
 * (AGENTS.md 6.9).
 *
 * UIKit's interactive pop waits for this recognizer to fail. When a swipe starts, the top screen
 * resolves it ([NativeBackHandling.beginSwipe]): if a content handler with back enabled takes it,
 * this recognizer begins and delivers the predictive back events (progress, then completion or
 * cancellation) to that handler; otherwise it fails at once and the native interactive pop runs
 * (also on screens whose handlers are enabled only for forward navigation).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal class NativeBackSwipe private constructor(navigationController: UINavigationController) {

    // The navigation controller's view keeps the recognizer, which keeps this object: refer back
    // to the controller weakly, or the controller (and every screen in it) would never be released.
    private val navigationRef = WeakReference(navigationController)

    private val fromRight = UIApplication.sharedApplication.userInterfaceLayoutDirection ==
        UIUserInterfaceLayoutDirection.UIUserInterfaceLayoutDirectionRightToLeft

    private val target = Target(WeakReference(this))
    private val delegate = object : NSObject(), UIGestureRecognizerDelegateProtocol {
        override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean = begin()
    }
    private val recognizer = Recognizer(this, target)

    /** The screen whose content handler is receiving the current swipe. */
    private var active: NativeBackHandling? = null

    init {
        recognizer.edges = if (fromRight) UIRectEdgeRight else UIRectEdgeLeft
        // UIGestureRecognizer.delegate is weak: this object keeps the strong reference.
        recognizer.delegate = delegate
        navigationController.view.addGestureRecognizer(recognizer)
        navigationController.interactivePopGestureRecognizer?.requireGestureRecognizerToFail(recognizer)
    }

    /** The stack left the app's navigation: end a running swipe and stop taking new ones. */
    fun dispose() {
        recognizer.enabled = false
        end(completed = false)
    }

    private fun begin(): Boolean {
        val navigationController = navigationRef.get() ?: return false
        if (navigationController.viewControllers.size < 2) return false
        if (navigationController.transitionCoordinator != null) return false
        val screen = navigationController.topViewController as? ComposeScreenController ?: return false
        val handling = screen.backHandling
        if (!handling.beginSwipe(event())) return false
        active = handling
        // Safety net: if the recognizer still does not begin (another one wins), end the swipe so
        // the handler is not left in progress.
        dispatch_async(dispatch_get_main_queue()) {
            val state = recognizer.state
            if (active === handling &&
                state != UIGestureRecognizerStateBegan &&
                state != UIGestureRecognizerStateChanged
            ) {
                end(completed = false)
            }
        }
        return true
    }

    fun onPan() {
        val handling = active ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan,
            UIGestureRecognizerStateChanged -> handling.moveSwipe(event())
            UIGestureRecognizerStateEnded -> end(completed = shouldCompleteBackSwipe(progress(), velocity()))
            else -> end(completed = false)
        }
    }

    private fun end(completed: Boolean) {
        val handling = active ?: return
        active = null
        handling.endSwipe(completed)
    }

    private fun event(): NavigationEvent {
        val location = recognizer.locationInView(recognizer.view)
        return NavigationEvent(
            touchX = location.useContents { x }.toFloat().coerceAtLeast(0f),
            touchY = location.useContents { y }.toFloat().coerceAtLeast(0f),
            progress = progress(),
            swipeEdge = if (fromRight) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT,
            frameTimeMillis = (NSProcessInfo.processInfo.systemUptime * 1000).toLong(),
        )
    }

    private fun progress(): Float {
        val view = recognizer.view ?: return 0f
        val translation = recognizer.translationInView(view).useContents { x }
        val width = view.bounds.useContents { size.width }
        return backSwipeProgress(if (fromRight) -translation else translation, width)
    }

    private fun velocity(): Double {
        val velocity = recognizer.velocityInView(recognizer.view).useContents { x }
        return if (fromRight) -velocity else velocity
    }

    companion object {
        /**
         * The swipe of [navigationController], installed when the first library screen appears in
         * it. This covers navigation controllers an app made itself for [ComposeScreenViewController].
         */
        fun install(navigationController: UINavigationController): NativeBackSwipe =
            installed(navigationController) ?: NativeBackSwipe(navigationController)

        /** The swipe already installed on [navigationController], if any. */
        fun installed(navigationController: UINavigationController): NativeBackSwipe? =
            if (!navigationController.isViewLoaded()) {
                null
            } else {
                navigationController.view.gestureRecognizers.orEmpty()
                    .firstNotNullOfOrNull { (it as? Recognizer)?.swipe }
            }
    }

    /** Marks this swipe's recognizer on the view and keeps the swipe alive as long as the view has it. */
    private class Recognizer(val swipe: NativeBackSwipe, target: Target) :
        UIScreenEdgePanGestureRecognizer(target = target, action = NSSelectorFromString("onPan:"))

    /**
     * Objective-C target of the recognizer (selector `onPan:`). Weak: the recognizer keeps the
     * swipe alive ([Recognizer.swipe]); a strong reference through UIKit's target list could not
     * be collected.
     */
    private class Target(private val swipe: WeakReference<NativeBackSwipe>) : NSObject() {
        @OptIn(BetaInteropApi::class)
        @ObjCAction
        fun onPan(sender: UIScreenEdgePanGestureRecognizer) {
            swipe.get()?.onPan()
        }
    }
}
