package zone.ien.navite

import kotlin.test.Test
import kotlin.test.assertEquals

class StackUpdateTest {

    @Test
    fun sameKeysAreUnchanged() {
        assertEquals(StackUpdate.None(2), stackUpdate(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun pushOne() {
        assertEquals(StackUpdate.Push(keep = 1, count = 1), stackUpdate(listOf("a"), listOf("a", "b")))
    }

    @Test
    fun pushSeveral() {
        assertEquals(StackUpdate.Push(keep = 1, count = 2), stackUpdate(listOf("a"), listOf("a", "b", "c")))
    }

    @Test
    fun popOne() {
        assertEquals(StackUpdate.PopTo(keep = 2), stackUpdate(listOf("a", "b", "c"), listOf("a", "b")))
    }

    @Test
    fun popToRoot() {
        assertEquals(StackUpdate.PopTo(keep = 1), stackUpdate(listOf("a", "b", "c"), listOf("a")))
    }

    @Test
    fun replaceTop() {
        assertEquals(StackUpdate.Replace(keep = 1), stackUpdate(listOf("a", "b"), listOf("a", "c")))
    }

    @Test
    fun deepLinkReplacesBelowTheCommonPrefix() {
        assertEquals(StackUpdate.Replace(keep = 1), stackUpdate(listOf("a", "b"), listOf("a", "x", "y")))
    }

    @Test
    fun newRootIsAFullReplace() {
        assertEquals(StackUpdate.Replace(keep = 0), stackUpdate(listOf("a", "b"), listOf("z")))
    }

    @Test
    fun initialStackIsAPushFromEmpty() {
        assertEquals(StackUpdate.Push(keep = 0, count = 2), stackUpdate(emptyList(), listOf("a", "b")))
    }

    @Test
    fun duplicateKeysAreMatchedByPosition() {
        // The same route pushed twice: the second copy is a new screen, not a no-op.
        assertEquals(StackUpdate.Push(keep = 2, count = 1), stackUpdate(listOf("a", "d"), listOf("a", "d", "d")))
        assertEquals(StackUpdate.PopTo(keep = 2), stackUpdate(listOf("a", "d", "d"), listOf("a", "d")))
    }

    @Test
    fun emptyTargetIsAFullReplace() {
        assertEquals(StackUpdate.Replace(keep = 0), stackUpdate(listOf("a"), emptyList()))
    }
}
