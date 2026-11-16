package zone.ien.navigation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIAction
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.navigationItem
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertNull
import platform.UIKit.UIViewController

@OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class, BetaInteropApi::class, ExperimentalForeignApi::class)
class NativeBarApplierTest {
    private fun controller(content: NativeBarContent): WeakReference<UIViewController> = autoreleasepool {
        val controller = UIViewController()
        NativeBarApplier(controller).apply(content)
        WeakReference(controller)
    }

    private fun assertReleased(content: NativeBarContent) {
        val reference = controller(content)
        repeat(5) { autoreleasepool { GC.collect() } }
        assertNull(reference.get(), "바 아이템이 화면 컨트롤러를 유지하지 않아야 한다")
    }

    @Test
    fun emptyBarReleasesItsController() {
        assertReleased(NativeBarContent(null, emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun leadingButtonReleasesItsController() {
        assertReleased(NativeBarContent(null, listOf(NativeBarButton("버튼")), emptyList(), emptyList()))
    }

    @Test
    fun trailingButtonReleasesItsController() {
        assertReleased(NativeBarContent(null, emptyList(), listOf(NativeBarButton("버튼")), emptyList()))
    }

    @Test
    fun toolbarButtonReleasesItsController() {
        assertReleased(NativeBarContent(null, emptyList(), emptyList(), listOf(NativeBarButton("버튼"))))
    }

    @Test
    fun menuReleasesItsController() {
        val button = NativeBarButton("메뉴", menu = listOf(NativeMenuAction("실행") {}))
        assertReleased(NativeBarContent(null, emptyList(), listOf(button), emptyList()))
    }
    @Test
    fun customPainterOverridesTheSystemImageAndKeepsNativeTinting() {
        val controller = UIViewController()
        val painter = ColorPainter(Color.Red)
        val applier = NativeBarApplier(controller)
        applier.apply(NativeBarContent(null, emptyList(), listOf(
            NativeBarButton("그림", systemImage = "존재하지 않는 심볼", image = painter),
        ), emptyList()))
        val item = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        val image = assertNotNull(item.image)
        assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate, image.renderingMode)
        image.size.useContents {
            assertEquals(22.0, width, 0.01)
            assertEquals(22.0, height, 0.01)
        }
        applier.apply(NativeBarContent(null, emptyList(), listOf(NativeBarButton("그림", image = painter) {}), emptyList()))
        val updated = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        assertEquals(image, updated.image, "같은 Painter는 변환된 이미지를 재사용한다")
        applier.apply(NativeBarContent(null, emptyList(), listOf(NativeBarButton("그림")), emptyList()))
        assertNull((controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem).image)
    }

    @Test
    fun menuPainterBecomesANativeActionImage() {
        val controller = UIViewController()
        val button = NativeBarButton("메뉴", menu = listOf(NativeMenuAction("그림", image = ColorPainter(Color.Blue)) {}))
        NativeBarApplier(controller).apply(NativeBarContent(null, emptyList(), listOf(button), emptyList()))
        val item = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        val action = item.menu!!.children.single() as UIAction
        assertNotNull(action.image)
    }

    @Test
    fun painterButtonsAndMenusReleaseTheirController() {
        val painter = ColorPainter(Color.Red)
        val button = NativeBarButton("그림", image = painter, menu = listOf(NativeMenuAction("실행", image = painter) {}))
        assertReleased(NativeBarContent(null, listOf(button), listOf(button), listOf(button)))
    }

    @Test
    fun painterRenderingFitsWideIconsAndKeepsTitleImageSizing() {
        val painter = object : Painter() {
            override val intrinsicSize = Size(200f, 100f)
            override fun DrawScope.onDraw() { drawRect(Color.Black) }
        }
        val icon = assertNotNull(renderNativeImage(painter, Density(2f), height = 22f, maxWidth = 22f))
        icon.size.useContents {
            assertEquals(22.0, width, 0.01)
            assertEquals(11.0, height, 0.01)
        }
        val title = assertNotNull(renderNativeImage(painter, Density(2f), height = 28f))
        title.size.useContents {
            assertEquals(56.0, width, 0.01)
            assertEquals(28.0, height, 0.01)
        }
    }

}
