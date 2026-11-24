package zone.ien.navite

import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState

/**
 * Text encoding of the values saveable/saved state registries produce, so the navigation host's
 * state survives the process (iOS state restoration).
 *
 * Supported: null, primitives, String, Char, primitive arrays, Array, List, Set, Map, SavedState,
 * and snapshot states (`mutableStateOf` with the built-in policies, `mutableIntStateOf`, ...),
 * which are restored with their exact type. Everything else is reported by [canEncode] and left
 * out by [prune].
 *
 * Format: one tag character per value; scalars and strings are length-prefixed
 * (`<tag><length>:<text>`), containers are count-prefixed (`<tag><count>:` + children).
 */
internal object SavedStateCodec {
    // Keep the original format identifier so existing scene state survives the package rename.
    private const val HEADER = "zone.ien.navigation.state/1\n"

    fun canEncode(value: Any?): Boolean = when (value) {
        null, is Boolean, is Int, is Long, is Float, is Double, is Short, is Byte, is Char, is String -> true
        is IntArray, is LongArray, is FloatArray, is DoubleArray, is ShortArray, is ByteArray,
        is BooleanArray, is CharArray -> true
        is Array<*> -> value.all(::canEncode)
        is List<*> -> value.all(::canEncode)
        is Set<*> -> value.all(::canEncode)
        is Map<*, *> -> value.all { (key, item) -> canEncode(key) && canEncode(item) }
        is SavedState -> value.read { toMap() }.values.all(::canEncode)
        is MutableIntState, is MutableLongState, is MutableFloatState, is MutableDoubleState -> true
        is SnapshotMutableState<*> -> policyTag(value.policy) != null && canEncode(value.value)
        else -> false
    }

    /**
     * Drops the saveable registry entries (`Map<String, List<Any?>>`, also nested ones such as
     * a SaveableStateHolder's per-entry maps) whose values cannot be encoded, so that only those
     * `rememberSaveable`s start from their initial value after restoration. Their keys are
     * added to [dropped].
     */
    fun prune(value: Any?, dropped: MutableList<String>): Any? {
        if (value !is Map<*, *>) return value
        val registryLike = value.isNotEmpty() && value.all { (key, item) -> key is String && item is List<*> }
        val result = LinkedHashMap<Any?, Any?>()
        for ((key, item) in value) {
            if (registryLike) {
                val values = (item as List<*>).map { prune(it, dropped) }
                if (canEncode(values)) result[key] = values else dropped += key as String
            } else {
                result[key] = prune(item, dropped)
            }
        }
        return result
    }

    fun encode(value: Any?): String = buildString {
        append(HEADER)
        write(value)
    }

    fun decode(text: String): Any? {
        require(text.startsWith(HEADER)) { "Unknown navigation state format" }
        val reader = Reader(text, HEADER.length)
        val value = reader.read()
        require(reader.atEnd) { "Trailing data in navigation state" }
        return value
    }

    private fun StringBuilder.write(value: Any?) {
        when (value) {
            null -> append('n')
            is Boolean -> append(if (value) 'T' else 'F')
            is Int -> scalar('i', value.toString())
            is Long -> scalar('l', value.toString())
            is Float -> scalar('f', value.toBits().toString())
            is Double -> scalar('d', value.toBits().toString())
            is Short -> scalar('h', value.toString())
            is Byte -> scalar('y', value.toString())
            is Char -> scalar('c', value.code.toString())
            is String -> scalar('s', value)
            is IntArray -> container('I', value.size) { value.forEach { write(it) } }
            is LongArray -> container('G', value.size) { value.forEach { write(it) } }
            is FloatArray -> container('R', value.size) { value.forEach { write(it) } }
            is DoubleArray -> container('D', value.size) { value.forEach { write(it) } }
            is ShortArray -> container('H', value.size) { value.forEach { write(it) } }
            is ByteArray -> container('Y', value.size) { value.forEach { write(it) } }
            is BooleanArray -> container('Z', value.size) { value.forEach { write(it) } }
            is CharArray -> container('C', value.size) { value.forEach { write(it) } }
            is Array<*> -> container('A', value.size) { value.forEach { write(it) } }
            is List<*> -> container('L', value.size) { value.forEach { write(it) } }
            is Set<*> -> container('S', value.size) { value.forEach { write(it) } }
            is Map<*, *> -> container('M', value.size) {
                value.forEach { (key, item) ->
                    write(key)
                    write(item)
                }
            }
            is SavedState -> {
                val map = value.read { toMap() }
                container('B', map.size) {
                    map.forEach { (key, item) ->
                        write(key)
                        write(item)
                    }
                }
            }
            is MutableIntState -> scalar('q', value.intValue.toString())
            is MutableLongState -> scalar('w', value.longValue.toString())
            is MutableFloatState -> scalar('x', value.floatValue.toBits().toString())
            is MutableDoubleState -> scalar('z', value.doubleValue.toBits().toString())
            is SnapshotMutableState<*> -> {
                append('P')
                append(requireNotNull(policyTag(value.policy)) { "Unsupported MutableState policy" })
                write(value.value)
            }
            else -> throw IllegalArgumentException("Cannot save ${value::class.simpleName}")
        }
    }

    private fun StringBuilder.scalar(tag: Char, text: String) {
        append(tag).append(text.length).append(':').append(text)
    }

    private inline fun StringBuilder.container(tag: Char, count: Int, children: () -> Unit) {
        append(tag).append(count).append(':')
        children()
    }

    private fun policyTag(policy: SnapshotMutationPolicy<*>): Char? = when {
        policy === structuralEqualityPolicy<Any?>() -> 's'
        policy === referentialEqualityPolicy<Any?>() -> 'r'
        policy === neverEqualPolicy<Any?>() -> 'n'
        else -> null
    }

    private fun policy(tag: Char): SnapshotMutationPolicy<Any?> = when (tag) {
        's' -> structuralEqualityPolicy()
        'r' -> referentialEqualityPolicy()
        'n' -> neverEqualPolicy()
        else -> throw IllegalArgumentException("Unknown MutableState policy '$tag'")
    }

    private class Reader(private val text: String, private var position: Int) {
        val atEnd: Boolean get() = position == text.length

        fun read(): Any? = when (val tag = next()) {
            'n' -> null
            'T' -> true
            'F' -> false
            'i' -> scalar().toInt()
            'l' -> scalar().toLong()
            'f' -> Float.fromBits(scalar().toInt())
            'd' -> Double.fromBits(scalar().toLong())
            'h' -> scalar().toShort()
            'y' -> scalar().toByte()
            'c' -> scalar().toInt().toChar()
            's' -> scalar()
            'I' -> count().let { n -> IntArray(n) { read() as Int } }
            'G' -> count().let { n -> LongArray(n) { read() as Long } }
            'R' -> count().let { n -> FloatArray(n) { read() as Float } }
            'D' -> count().let { n -> DoubleArray(n) { read() as Double } }
            'H' -> count().let { n -> ShortArray(n) { read() as Short } }
            'Y' -> count().let { n -> ByteArray(n) { read() as Byte } }
            'Z' -> count().let { n -> BooleanArray(n) { read() as Boolean } }
            'C' -> count().let { n -> CharArray(n) { read() as Char } }
            'A' -> count().let { n -> arrayOfNulls<Any?>(n).also { array -> for (i in 0 until n) array[i] = read() } }
            'L' -> count().let { n -> ArrayList<Any?>(n).also { list -> repeat(n) { list += read() } } }
            'S' -> count().let { n -> LinkedHashSet<Any?>(n).also { set -> repeat(n) { set += read() } } }
            'M' -> readMap(count())
            'B' -> {
                @Suppress("UNCHECKED_CAST")
                val map = readMap(count()) as Map<String, Any?>
                savedState(map) {}
            }
            'q' -> mutableIntStateOf(scalar().toInt())
            'w' -> mutableLongStateOf(scalar().toLong())
            'x' -> mutableFloatStateOf(Float.fromBits(scalar().toInt()))
            'z' -> mutableDoubleStateOf(Double.fromBits(scalar().toLong()))
            'P' -> {
                val policy = policy(next())
                mutableStateOf(read(), policy)
            }
            else -> throw IllegalArgumentException("Unknown tag '$tag' at ${position - 1}")
        }

        private fun readMap(count: Int): LinkedHashMap<Any?, Any?> {
            val map = LinkedHashMap<Any?, Any?>(count)
            repeat(count) {
                val key = read()
                map[key] = read()
            }
            return map
        }

        private fun next(): Char {
            require(position < text.length) { "Unexpected end of navigation state" }
            return text[position++]
        }

        private fun count(): Int {
            val colon = text.indexOf(':', position)
            require(colon > position) { "Malformed navigation state at $position" }
            val value = text.substring(position, colon).toInt()
            position = colon + 1
            return value
        }

        private fun scalar(): String {
            val length = count()
            require(position + length <= text.length) { "Malformed navigation state at $position" }
            return text.substring(position, position + length).also { position += length }
        }
    }
}
