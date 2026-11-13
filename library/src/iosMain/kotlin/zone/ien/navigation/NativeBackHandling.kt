package zone.ien.navigation

import androidx.navigationevent.NavigationEventInput
import platform.UIKit.UIAction
import platform.UIKit.UIViewController
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.UIKit.transitionCoordinator

/**
 * Connects back handlers declared in a screen's content (`BackHandler`, `NavigationBackHandler`)
 * to the native back navigation, with Android's semantics: while the top screen has an enabled
 * handler, back goes to the handler instead of popping.
 *
 * - The native back button keeps its look; `navigationItem.backAction` calls the handler.
 * - The `UINavigationController` edge-swipe pop is disabled; Compose Multiplatform's own edge
 *   swipe (enabled because a handler is) delivers the gesture, with progress, to the handler.
 *
 * It is an input of the screen's navigation event dispatcher, so it learns when handlers become
 * enabled and can dispatch the back event.
 */
internal class NativeBackHandling(private val controller: UIViewController) : NavigationEventInput() {

    /** The screen's content has an enabled back handler. */
    var intercepts: Boolean = false
        private set

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        intercepts = hasEnabledHandlers
        controller.navigationItem.backAction = if (hasEnabledHandlers) {
            UIAction.actionWithTitle("", image = null, identifier = null) { _ -> dispatchOnBackCompleted() }
        } else {
            null
        }
        // Do not touch the shared gesture while a transition (e.g. an interactive pop) runs.
        if (controller.navigationController?.transitionCoordinator == null) updateEdgeSwipe()
    }

    /**
     * The top screen decides whether the navigation controller's swipe pop is enabled. Called
     * when the screen has appeared, never while a transition runs: disabling the recognizer in
     * the middle of a swipe would cancel it.
     */
    fun updateEdgeSwipe() {
        val navigation = controller.navigationController ?: return
        if (navigation.topViewController !== controller) return
        navigation.interactivePopGestureRecognizer?.enabled = !intercepts
    }
}
