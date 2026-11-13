package zone.ien.navigation

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavEntry
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator
import platform.darwin.NSObject

/**
 * Keeps one `UINavigationController` in sync with one Navigation 3 back stack.
 *
 * The Kotlin back stack is the single source of truth (AGENTS.md 6.3):
 * - Kotlin → UIKit: [update] diffs the decorated entries against the native screens by content
 *   key and pushes, pops or sets the stack. Equal input is a no-op, so feeding the same state
 *   back never moves anything.
 * - UIKit → Kotlin: when UIKit itself pops (back button, edge swipe, back button long-press
 *   menu, tab re-tap), `didShow` reports it through `onBack()`, once per popped screen. A
 *   cancelled swipe never reaches `didShow` with a pop, so it changes nothing.
 * - Updates that arrive during a transition are applied when it finishes (`didShow`).
 */
internal class StackCoordinator(
    val navigationController: UINavigationController,
    private val leases: ContentLeases,
) {
    private var screens: List<EntryScreen> = emptyList()
    private var target: List<NavEntry<*>> = emptyList()
    private var onBack: () -> Unit = {}
    private var initialized = false

    private val delegate = object : NSObject(), UINavigationControllerDelegateProtocol {
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean,
        ) {
            onDidShow()
        }
    }

    init {
        // UINavigationController.delegate is weak: this coordinator keeps the strong reference.
        navigationController.delegate = delegate
        navigationController.navigationBar.prefersLargeTitles = true
    }

    fun update(entries: List<NavEntry<*>>, onBack: () -> Unit) {
        this.target = entries
        this.onBack = onBack
        apply()
    }

    /**
     * The stack left the app's navigation (replaced root, or its tab was dropped): every screen's
     * content leaves composition. UIKit callbacks that still arrive (e.g. the end of a transition
     * that was running) no longer reach the app's back stack.
     */
    fun dispose() {
        navigationController.delegate = null
        val removed = screens
        screens = emptyList()
        target = emptyList()
        onBack = {}
        removed.forEach { it.markRemoved() }
    }

    private fun apply() {
        if (target.isEmpty()) return // Navigation 3 back stacks must not be empty.
        if (navigationController.transitionCoordinator != null) return // re-applied in didShow

        val update = stackUpdate(screens.map { it.contentKey }, target.map { it.contentKey })
        for (index in 0 until update.keep) screens[index].entry = target[index]
        if (update is StackUpdate.None) return

        val kept = screens.take(update.keep)
        val removed = screens.drop(update.keep)
        val created = target.drop(update.keep).map { EntryScreen(it, leases) }
        val next = kept + created
        val animated = initialized
        screens = next
        initialized = true

        when (update) {
            is StackUpdate.Push ->
                if (created.size == 1 && kept.isNotEmpty()) {
                    navigationController.pushViewController(created.single().controller, animated)
                } else {
                    navigationController.setViewControllers(next.map { it.controller }, animated)
                }
            is StackUpdate.PopTo -> navigationController.popToViewController(next.last().controller, animated)
            is StackUpdate.Replace -> navigationController.setViewControllers(next.map { it.controller }, animated)
            is StackUpdate.None -> Unit
        }
        removed.forEach { it.markRemoved() }
    }

    private fun onDidShow() {
        val shown = navigationController.viewControllers
        val remaining = screens.takeWhile { screen -> shown.any { it === screen.controller } }
        val popped = screens.size - remaining.size
        if (popped > 0) {
            // UIKit popped by itself: mirror it in Kotlin. Drop the popped entries from the
            // target too, so the re-apply below does not push them back before the host
            // composition has seen the new back stack.
            val removed = screens.drop(remaining.size)
            screens = remaining
            target = target.take(remaining.size)
            removed.forEach { it.markRemoved() }
            repeat(popped) { onBack() }
            Snapshot.sendApplyNotifications()
        }
        // Apply what arrived during the transition (or re-push if the app refused the pop).
        apply()
    }
}
