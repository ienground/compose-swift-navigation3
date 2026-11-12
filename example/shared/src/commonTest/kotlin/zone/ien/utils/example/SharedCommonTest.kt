package zone.ien.utils.example

import kotlin.test.Test
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun greetingContainsPlatformName() {
        assertTrue(Greeting().greet().contains(getPlatform().name))
    }
}
