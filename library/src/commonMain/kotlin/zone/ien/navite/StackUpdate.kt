package zone.ien.navite

/**
 * How a native stack showing [current] content keys must change to show [target].
 *
 * Screens are matched by position and content key: the longest common prefix is kept (its
 * screens and their state stay), everything after it is replaced. Equal content keys mean the
 * same content in Navigation 3, so a prefix match is exactly "nothing changed there".
 */
internal sealed interface StackUpdate {
    /** Number of leading screens that stay. */
    val keep: Int

    /** Same keys: only refresh the entries (new lambdas/metadata). */
    data class None(override val keep: Int) : StackUpdate

    /** [current] is a prefix of [target]: push the remaining screens. */
    data class Push(override val keep: Int, val count: Int) : StackUpdate

    /** [target] is a prefix of [current]: pop back to the screen at `keep - 1`. */
    data class PopTo(override val keep: Int) : StackUpdate

    /** Anything else (replace, deep link, new root): set the whole stack at once. */
    data class Replace(override val keep: Int) : StackUpdate
}

internal fun stackUpdate(current: List<Any>, target: List<Any>): StackUpdate {
    var prefix = 0
    while (prefix < current.size && prefix < target.size && current[prefix] == target[prefix]) prefix++
    return when {
        prefix == current.size && prefix == target.size -> StackUpdate.None(prefix)
        prefix == current.size -> StackUpdate.Push(prefix, target.size - prefix)
        prefix == target.size && prefix > 0 -> StackUpdate.PopTo(prefix)
        else -> StackUpdate.Replace(prefix)
    }
}
