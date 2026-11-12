package zone.ien.utils.example

import io.github.kotlin.fibonacci.generateFibi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun greetingContainsPlatformName() {
        assertTrue(Greeting().greet().contains(getPlatform().name))
    }

    @Test
    fun libraryIsAvailable() {
        assertEquals(10, generateFibi().take(10).count())
    }
}
