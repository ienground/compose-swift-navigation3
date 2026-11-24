package zone.ien.navite

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SavedStateCodecTest {

    private fun roundTrip(value: Any?): Any? = SavedStateCodec.decode(SavedStateCodec.encode(value))

    @Test
    fun scalarsKeepTheirTypes() {
        val values = listOf(null, true, false, 42, -7L, 1.5f, Double.NaN, 3.toShort(), 9.toByte(), 'x', "", "a:b\nc")
        assertEquals(values, roundTrip(values))
    }

    @Test
    fun floatingPointIsExact() {
        val values = listOf(0.1f, Float.MIN_VALUE, -0.0, Double.MAX_VALUE, Double.POSITIVE_INFINITY)
        val restored = roundTrip(values) as List<*>
        values.zip(restored).forEach { (expected, actual) -> assertEquals(expected, actual) }
    }

    @Test
    fun registryShapedMapsRoundTrip() {
        // What SaveableStateRegistry / SaveableStateHolder save.
        val value = mapOf(
            "HomeRoute:class HomeRoute" to mapOf("list" to listOf(listOf(12, 340))),
            "settings" to mapOf("taps" to listOf(3)),
        )
        assertEquals(value, roundTrip(value))
    }

    @Test
    fun primitiveArraysRoundTrip() {
        val restored = roundTrip(listOf(intArrayOf(1, 2), longArrayOf(3), doubleArrayOf(0.5), booleanArrayOf(true), charArrayOf('k'))) as List<*>
        assertContentEquals(intArrayOf(1, 2), restored[0] as IntArray)
        assertContentEquals(longArrayOf(3), restored[1] as LongArray)
        assertContentEquals(doubleArrayOf(0.5), restored[2] as DoubleArray)
        assertContentEquals(booleanArrayOf(true), restored[3] as BooleanArray)
        assertContentEquals(charArrayOf('k'), restored[4] as CharArray)
    }

    @Test
    fun savedStateRoundTripsWithNesting() {
        val inner = savedState { putString("key", "DetailRoute") }
        val outer = savedState {
            putInt("count", 2)
            putSavedState("inner", inner)
            putStringList("names", listOf("a", "b"))
        }
        val restored = roundTrip(outer)
        assertIs<androidx.savedstate.SavedState>(restored)
        restored.read {
            assertEquals(2, getInt("count"))
            assertEquals("DetailRoute", getSavedState("inner").read { getString("key") })
            assertEquals(listOf("a", "b"), getStringList("names"))
        }
    }

    @Test
    fun intStateIsRestoredAsIntState() {
        val restored = roundTrip(mutableIntStateOf(5))
        assertIs<MutableIntState>(restored)
        assertEquals(5, restored.intValue)
    }

    @Test
    fun mutableStateKeepsValueAndPolicy() {
        val restored = roundTrip(mutableStateOf("x", neverEqualPolicy()))
        assertIs<SnapshotMutableState<*>>(restored)
        assertEquals("x", restored.value)
        assertSame(neverEqualPolicy<Any?>(), restored.policy)
    }

    private enum class Mode { A }

    @Test
    fun unsupportedValuesAreReported() {
        assertFalse(SavedStateCodec.canEncode(Mode.A))
        assertFalse(SavedStateCodec.canEncode(listOf(1, Any())))
        assertFalse(SavedStateCodec.canEncode(mutableStateOf(Mode.A)))
        assertTrue(SavedStateCodec.canEncode(mapOf("a" to listOf(mutableStateOf(1)))))
    }

    @Test
    fun pruneDropsOnlyTheUnsupportedRegistryEntries() {
        val holder = mapOf(
            "detail" to mapOf(
                "scroll" to listOf(listOf(4, 0)),
                "mode" to listOf(mutableStateOf(Mode.A)),
            ),
        )
        val registry = mapOf("holder" to listOf(holder), "tab" to listOf(mutableIntStateOf(1)))
        val dropped = mutableListOf<String>()
        val pruned = SavedStateCodec.prune(registry, dropped)

        assertEquals(listOf("mode"), dropped)
        assertTrue(SavedStateCodec.canEncode(pruned))
        @Suppress("UNCHECKED_CAST")
        val restored = roundTrip(pruned) as Map<String, List<Any?>>
        val detail = (restored.getValue("holder").single() as Map<*, *>)["detail"] as Map<*, *>
        assertEquals(listOf(listOf(4, 0)), detail["scroll"])
        assertFalse("mode" in detail)
        assertEquals(1, (restored.getValue("tab").single() as MutableIntState).intValue)
    }

    @Test
    fun corruptOrForeignDataIsRejected() {
        assertFailsWith<IllegalArgumentException> { SavedStateCodec.decode("something else") }
        assertFailsWith<IllegalArgumentException> { SavedStateCodec.decode(SavedStateCodec.encode(listOf(1)).dropLast(1)) }
    }

    @Test
    fun restoredMutableStateIsAState() {
        @Suppress("UNCHECKED_CAST")
        val restored = roundTrip(mutableStateOf(listOf(1, 2))) as MutableState<List<Int>>
        assertEquals(listOf(1, 2), restored.value)
    }
}
