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
 *
 * In a tab, the stack shows the tab's own entries followed by [NativeTabs]' shared screens when
 * the tab is selected. Shared screens always hide the tab bar, and UIKit pops of them are
 * reported through `onSharedBack` instead of the tab's `onBack`.
 */
internal class StackCoordinator(
    val navigationController: UINavigationController,
    private val leases: ContentLeases,
    private val titleImages: TitleImages = TitleImages(),
) {
    private var screens: List<EntryScreen> = emptyList()
    private var target: List<NavEntry<*>> = emptyList()

    /** Index in [target] where the shared screens start. */
    private var sharedFrom = 0
    private var onBack: () -> Unit = {}
    private var onSharedBack: () -> Unit = {}
    private var animated = true
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

    /**
     * Shows [entries] followed by [shared]. [animated] is `false` for a stack that is not on screen
     * (an unselected tab), so it changes without a transition.
     */
    fun update(
        entries: List<NavEntry<*>>,
        onBack: () -> Unit,
        shared: List<NavEntry<*>> = emptyList(),
        onSharedBack: () -> Unit = {},
        animated: Boolean = true,
    ) {
        this.target = if (shared.isEmpty()) entries else entries + shared
        this.sharedFrom = entries.size
        this.onBack = onBack
        this.onSharedBack = onSharedBack
        this.animated = animated
        apply()
    }

    /**
     * The stack left the app's navigation (replaced root, or its tab was dropped): every screen's
     * content leaves composition. UIKit callbacks that still arrive (e.g. the end of a transition
     * that was running) no longer reach the app's back stack.
     */
    fun dispose() {
        navigationController.delegate = null
        NativeBackSwipe.installed(navigationController)?.dispose()
        val removed = screens
        screens = emptyList()
        target = emptyList()
        onBack = {}
        onSharedBack = {}
        removed.forEach { it.markRemoved() }
    }

    private fun apply() {
        if (target.isEmpty()) return // Navigation 3 back stacks must not be empty.
        if (navigationController.transitionCoordinator != null) return // re-applied in didShow

        val update = stackUpdate(screens.map { it.contentKey }, target.map { it.contentKey })
        for (index in 0 until update.keep) {
            screens[index].shared = index >= sharedFrom
            screens[index].entry = target[index]
        }
        if (update is StackUpdate.None) return

        val kept = screens.take(update.keep)
        val removed = screens.drop(update.keep)
        val created = target.drop(update.keep).mapIndexed { offset, entry ->
            EntryScreen(entry, leases, titleImages).also { it.shared = update.keep + offset >= sharedFrom }
        }
        val next = kept + created
        val animated = initialized && this.animated
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
        retire(removed)
        updateBackActions()
    }

    /**
     * Screens the app removed from the back stack. During an animated pop they are still on
     * screen: their content stays until the transition ends (as NavDisplay keeps an exiting entry
     * during its animation), then leaves composition.
     */
    private fun retire(removed: List<EntryScreen>) {
        if (removed.isEmpty()) return
        val coordinator = navigationController.transitionCoordinator
        if (coordinator == null) {
            removed.forEach { it.markRemoved() }
        } else {
            coordinator.animateAlongsideTransition(animation = null) { _ -> removed.forEach { it.markRemoved() } }
        }
    }

    /** A screen may have become the root (or stopped being it): only non-root screens route the back button. */
    private fun updateBackActions() {
        screens.forEach { it.controller.backHandling.updateBackAction() }
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
            sharedFrom = minOf(sharedFrom, remaining.size)
            removed.forEach { it.markRemoved() }
            // Top first: the shared screens sit above the tab's own ones.
            removed.asReversed().forEach { screen -> if (screen.shared) onSharedBack() else onBack() }
            Snapshot.sendApplyNotifications()
            updateBackActions()
        }
        // Apply what arrived during the transition (or re-push if the app refused the pop).
        apply()
    }
}
