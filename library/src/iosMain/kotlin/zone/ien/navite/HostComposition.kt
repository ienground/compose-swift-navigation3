package zone.ien.navite

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.savedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * A composition without UI that holds the app's navigation state (back stacks, decorated
 * Navigation 3 entries and their decorators). Native screens render the entries it produces.
 *
 * It runs its own [Recomposer] on the main thread with a frame clock that ticks right after a
 * frame is requested. [content] must not emit UI nodes.
 *
 * It provides a lifecycle (RESUMED), a ViewModelStoreOwner, a SavedStateRegistryOwner and a
 * SaveableStateRegistry, so `rememberNavBackStack`, `rememberSaveableStateHolderNavEntryDecorator`
 * and `rememberViewModelStoreNavEntryDecorator` work.
 *
 * [saveState] collects both registries (back stacks, tab selection, and through the
 * SaveableStateHolder every entry's `rememberSaveable` state, also of screens in other
 * compositions); [restoredState] feeds such a map back on the next launch.
 */
internal class HostComposition(
    private val content: @Composable () -> Unit,
    restoredState: Map<String, Any?>? = null,
) {

    private val frameClock = BroadcastFrameClock(onNewAwaiters = ::scheduleFrame)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + frameClock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = Composition(NoUiApplier, recomposer)
    private val owner = HostOwner(restoredState?.get(REGISTRY) as? SavedState)

    // Any value can be registered (as in memory before); values that cannot be persisted are
    // left out when saving (SavedStateCodec.prune).
    @Suppress("UNCHECKED_CAST")
    private val saveableStateRegistry = SaveableStateRegistry(
        restoredValues = restoredState?.get(SAVEABLE) as? Map<String, List<Any?>>,
        canBeSaved = { true },
    )
    private var frameScheduled = false

    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
        composition.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalViewModelStoreOwner provides owner,
                LocalSavedStateRegistryOwner provides owner,
                LocalSaveableStateRegistry provides saveableStateRegistry,
            ) {
                content()
            }
        }
    }

    fun saveState(): Map<String, Any?> = mapOf(
        SAVEABLE to saveableStateRegistry.performSave(),
        REGISTRY to savedState().also(owner::save),
    )

    fun dispose() {
        composition.dispose()
        recomposer.cancel()
        scope.cancel()
        owner.viewModelStore.clear()
    }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        dispatch_async(dispatch_get_main_queue()) {
            frameScheduled = false
            frameClock.sendFrame((NSProcessInfo.processInfo.systemUptime * 1_000_000_000).toLong())
        }
    }
}

private object NoUiApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = error("The navigation host composition cannot show UI")
    override fun insertBottomUp(index: Int, instance: Unit) = error("The navigation host composition cannot show UI")
    override fun remove(index: Int, count: Int) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun onClear() {}
}

private const val SAVEABLE = "saveable"
private const val REGISTRY = "registry"

private class HostOwner(restored: SavedState?) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(restored)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun save(into: SavedState) {
        savedStateController.performSave(into)
    }
}
