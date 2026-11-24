package zone.ien.navite

import androidx.compose.runtime.mutableStateMapOf

/**
 * Makes sure a Navigation 3 content key is composed by at most one native screen at a time.
 *
 * Native screens stay alive while covered (UIKit keeps them in the stack, Compose Multiplatform
 * keeps their composition), and Navigation 3 entries with the same content key share decorator
 * state. `SaveableStateHolder` throws if two compositions provide the same key concurrently, so:
 * - the lease of a key goes to the screen that most recently [acquire]d it (appeared), and
 * - that screen only composes once the previous holder has [release]d it (its content left
 *   composition), so the two never overlap.
 *
 * Snapshot-state backed: screens observing [canCompose] recompose when it changes.
 */
internal class ContentLeases {
    private val leases = mutableStateMapOf<Any, Long>()
    private val holders = mutableStateMapOf<Any, Long>()

    fun acquire(contentKey: Any, owner: Long) {
        leases[contentKey] = owner
    }

    fun canCompose(contentKey: Any, owner: Long): Boolean {
        if (leases[contentKey] != owner) return false
        val holder = holders[contentKey]
        return holder == null || holder == owner
    }

    /** The owner's content entered composition. */
    fun hold(contentKey: Any, owner: Long) {
        holders[contentKey] = owner
    }

    /** The owner's content left composition. */
    fun release(contentKey: Any, owner: Long) {
        if (holders[contentKey] == owner) holders.remove(contentKey)
    }

    /**
     * The owner is gone for good (its screen was removed from the stack). Its lease ends, but it
     * keeps holding the key until its content actually leaves composition ([release]): a removed
     * screen that is no longer in the window is not recomposed and keeps its content until
     * Compose Multiplatform disposes its scene. Another screen with the same key composes after
     * that, never while the removed content is still registered with the `SaveableStateHolder`.
     */
    fun forget(contentKey: Any, owner: Long) {
        if (leases[contentKey] == owner) leases.remove(contentKey)
    }
}
