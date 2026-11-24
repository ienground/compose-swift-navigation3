package zone.ien.navite

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NativeBarItemsTest {

    private fun content(title: String? = null, vararg trailing: NativeBarButton) =
        NativeBarContent(title, emptyList(), trailing.toList(), emptyList())

    @Test
    fun latestRegisteredScreenWins() {
        val state = NativeBarState()
        val home = content("Home")
        val detail = content("Detail")
        state.register("home", home)
        state.register("detail", detail)
        assertSame(detail, state.current)
    }

    @Test
    fun updatingACoveredScreenKeepsTheTopOne() {
        val state = NativeBarState()
        state.register("home", content("Home"))
        val detail = content("Detail")
        state.register("detail", detail)
        state.register("home", content("Home 2")) // the covered screen recomposed
        assertSame(detail, state.current)
    }

    @Test
    fun poppingTheTopScreenRevealsThePreviousOne() {
        val state = NativeBarState()
        val home = content("Home")
        state.register("home", home)
        state.register("detail", content("Detail"))
        state.unregister("detail")
        assertSame(home, state.current)
        state.unregister("home")
        assertNull(state.current)
    }

    @Test
    fun listenerSeesEveryChange() {
        val state = NativeBarState()
        val seen = mutableListOf<String?>()
        state.listener = { seen += it?.title }
        state.register("a", content("A"))
        state.register("b", content("B"))
        state.unregister("b")
        assertEquals(listOf<String?>("A", "B", "A"), seen)
    }

    @Test
    fun newLambdasKeepTheSameShape() {
        val first = content(null, NativeBarButton("Share", "square.and.arrow.up", onClick = { println(1) }))
        val second = content(null, NativeBarButton("Share", "square.and.arrow.up", onClick = { println(2) }))
        assertEquals(first.shape(), second.shape())
    }

    @Test
    fun enabledAndMenuChangesChangeTheShape() {
        val enabled = content(null, NativeBarButton("Share", enabled = true))
        val disabled = content(null, NativeBarButton("Share", enabled = false))
        assertNotEquals(enabled.shape(), disabled.shape())

        val menuA = content(null, NativeBarButton("More", menu = listOf(NativeMenuAction("A") {})))
        val menuB = content(null, NativeBarButton("More", menu = listOf(NativeMenuAction("A", destructive = true) {})))
        assertNotEquals(menuA.shape(), menuB.shape())
    }

    @Test
    fun noContentIsTheEmptyShape() {
        assertEquals(BarShape.Empty, (null as NativeBarContent?).shape())
    }

    @Test
    fun searchFollowsTheLatestScreenAndIsNotifiedSynchronously() {
        val state = NativeBarState()
        val seen = mutableListOf<String?>()
        state.searchListener = { seen += it?.placeholder }
        state.registerSearch("home", NativeSearch("", "Search rows") {})
        state.registerSearch("detail", NativeSearch("x", "Search lines") {})
        assertEquals("x", state.search?.query)
        state.unregisterSearch("detail")
        assertEquals("Search rows", state.search?.placeholder)
        state.unregisterSearch("home")
        assertNull(state.search)
        assertEquals(listOf<String?>("Search rows", "Search lines", "Search rows", null), seen)
    }
    @Test
    fun painterChangesUpdateButtonsAndMenuImages() {
        val red = ColorPainter(Color.Red)
        val blue = ColorPainter(Color.Blue)
        val first = content(null, NativeBarButton("이미지", image = red))
        val same = content(null, NativeBarButton("이미지", image = red) {})
        val changed = content(null, NativeBarButton("이미지", image = blue))
        assertEquals(first.shape(), same.shape())
        assertNotEquals(first.shape(), changed.shape())
        val menuA = content(null, NativeBarButton("메뉴", menu = listOf(NativeMenuAction("실행", image = red) {})))
        val menuB = content(null, NativeBarButton("메뉴", menu = listOf(NativeMenuAction("실행", image = blue) {})))
        assertNotEquals(menuA.shape(), menuB.shape())
    }

    @Test
    fun existingPositionalConstructorsAndTrailingLambdasStayCompatible() {
        var calls = 0
        val button = NativeBarButton("버튼", "plus", true, emptyList()) { calls++ }
        val action = NativeMenuAction("실행", "checkmark", false) { calls++ }
        button.onClick()
        action.onClick()
        assertEquals(2, calls)
        assertNull(button.image)
        assertNull(action.image)
        val painter = ColorPainter(Color.Red)
        val imageButton = NativeBarButton("버튼", null, true, emptyList(), painter) { calls++ }
        val imageAction = NativeMenuAction("실행", null, false, painter) { calls++ }
        imageButton.onClick()
        imageAction.onClick()
        assertEquals(4, calls)
        assertSame(painter, imageButton.image)
        assertSame(painter, imageAction.image)
    }

    @Test
    fun badgeAndMenuEnabledChangesUpdateTheNativeShape() {
        val plain = content(null, NativeBarButton("알림"))
        val badged = content(null, NativeBarButton("알림", badge = 3))
        assertNotEquals(plain.shape(), badged.shape())
        val enabled = content(null, NativeBarButton("메뉴", menu = listOf(NativeMenuAction("실행", enabled = true) {})))
        val disabled = content(null, NativeBarButton("메뉴", menu = listOf(NativeMenuAction("실행", enabled = false) {})))
        assertNotEquals(enabled.shape(), disabled.shape())
        val menuBadge = content(null, NativeBarButton("메뉴", menu = listOf(NativeMenuAction("실행", badge = -1) {})))
        assertNotEquals(enabled.shape(), menuBadge.shape())
    }

}
