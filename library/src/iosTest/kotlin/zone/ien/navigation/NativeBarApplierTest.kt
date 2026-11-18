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
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityValue
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

    @Test
    fun `메뉴 비활성 상태와 배지를 UIKit에 반영한다`() {
        val controller = UIViewController()
        val applier = NativeBarApplier(controller)
        fun content(enabled: Boolean, badge: Int) = NativeBarContent(null, emptyList(), listOf(
            NativeBarButton("메뉴", menu = listOf(NativeMenuAction("알림", enabled = enabled, badge = badge) {})),
        ), emptyList())
        applier.apply(content(false, 12))
        val item = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        val action = item.menu!!.children.single() as UIAction
        assertEquals(platform.UIKit.UIMenuElementAttributesDisabled, action.attributes)
        assertEquals("12", action.subtitle)
        applier.apply(content(true, 12))
        val enabledItem = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        assertEquals(0uL, (enabledItem.menu!!.children.single() as UIAction).attributes)
        applier.apply(content(true, -1))
        val updated = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        val updatedAction = updated.menu!!.children.single() as UIAction
        assertEquals(0uL, updatedAction.attributes)
        assertEquals("•", updatedAction.subtitle)
    }

    @Test
    fun `버튼 배지를 표시하고 제거하며 클릭은 최신 선언을 사용한다`() {
        var calls = 0
        val controller = UIViewController()
        val applier = NativeBarApplier(controller)
        fun content(badge: Int, click: () -> Unit) = NativeBarContent(null, emptyList(), listOf(
            NativeBarButton("알림", systemImage = "bell", badge = badge, onClick = click),
        ), emptyList())
        applier.apply(content(3) { calls++ })
        val item = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        val container = assertNotNull(item.customView)
        val button = container.subviews.filterIsInstance<platform.UIKit.UIButton>().single()
        val badgeView = container.subviews.filterIsInstance<platform.UIKit.UIImageView>().single()
        assertEquals("3", button.accessibilityValue)
        assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal, assertNotNull(badgeView.image).renderingMode)
        applier.apply(content(3) { calls += 10 })
        button.sendActionsForControlEvents(platform.UIKit.UIControlEventTouchUpInside)
        assertEquals(10, calls)
        applier.apply(content(0) {})
        val cleared = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        assertNull(cleared.customView)
    }

    @Test
    fun `배지가 있는 버튼과 메뉴도 컨트롤러를 유지하지 않는다`() {
        val button = NativeBarButton("알림", badge = 5)
        val menu = NativeBarButton("메뉴", badge = -1, menu = listOf(NativeMenuAction("알림", badge = 3) {}))
        assertReleased(NativeBarContent(null, listOf(button), listOf(menu), listOf(button)))
    }

    @Test
    fun `배지는 네이티브 바가 버튼 크기를 바꿔도 버튼 안에 남는다`() {
        val controller = UIViewController()
        val navigation = platform.UIKit.UINavigationController(rootViewController = controller)
        val window = platform.UIKit.UIWindow(frame = platform.UIKit.UIScreen.mainScreen.bounds)
        window.rootViewController = navigation
        window.makeKeyAndVisible()
        try {
            val applier = NativeBarApplier(controller)
            for (badge in listOf(12, -1)) {
                applier.apply(NativeBarContent(null, emptyList(), listOf(
                    NativeBarButton("알림", systemImage = "bell", badge = badge),
                ), emptyList()))
                navigation.view.layoutIfNeeded()
                val item = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
                val container = assertNotNull(item.customView)
                val badgeView = container.subviews.filterIsInstance<platform.UIKit.UIImageView>().single()
                assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal, assertNotNull(badgeView.image).renderingMode)
                container.setFrame(platform.CoreGraphics.CGRectMake(0.0, 0.0, 34.0, 34.0))
                container.layoutIfNeeded()
                val right = badgeView.frame.useContents { origin.x + size.width }
                val width = container.bounds.useContents { size.width }
                kotlin.test.assertTrue(right <= width, "배지 오른쪽=$right, 버튼 너비=$width")
                assertEquals(if (badge > 0) 16.0 else 8.0, badgeView.bounds.useContents { size.height })
            }
        } finally {
            window.hidden = true
            window.rootViewController = null
        }
    }

    @Test
    fun `주요 액션은 네이티브 강조 스타일로 구분하고 변경을 반영한다`() {
        val controller = UIViewController()
        val applier = NativeBarApplier(controller)
        fun content(primary: Boolean) = NativeBarContent(null, emptyList(), listOf(
            NativeBarButton("저장", systemImage = "checkmark", isPrimary = primary),
        ), emptyList())
        applier.apply(content(true))
        val primary = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        assertEquals(platform.UIKit.UIBarButtonItemStyle.UIBarButtonItemStyleDone, primary.style)
        applier.apply(content(false))
        val regular = controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        assertEquals(platform.UIKit.UIBarButtonItemStyle.UIBarButtonItemStylePlain, regular.style)
    }

    @Test
    fun `주요 액션을 오른쪽 끝에 유지하고 일반 액션과 간격을 둔다`() {
        val controller = UIViewController()
        NativeBarApplier(controller).apply(NativeBarContent(null, emptyList(), listOf(
            NativeBarButton("일반", systemImage = "plus"),
            NativeBarButton("저장", systemImage = "checkmark", isPrimary = true),
        ), emptyList()))
        val items = controller.navigationItem.rightBarButtonItems!!.map { it as UIBarButtonItem }
        assertEquals(3, items.size)
        assertEquals("저장", items.first().accessibilityLabel)
        assertEquals(platform.UIKit.UIBarButtonItemStyle.UIBarButtonItemStyleDone, items.first().style)
        assertNull(items[1].primaryAction)
        kotlin.test.assertTrue(items[1].width > 0.0)
        assertEquals("일반", items.last().accessibilityLabel)
    }

}
