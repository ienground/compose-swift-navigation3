package io.github.kotlin.fibonacci

import kotlin.test.Test
import kotlin.test.assertEquals

class WebFibiTest {

    @Test
    fun `test 3rd element`() {
        assertEquals(2, generateFibi().take(3).last())
    }
}
