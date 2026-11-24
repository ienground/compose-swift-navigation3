package zone.ien.navite

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIAction
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UILabel
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
    @Test
    fun mixedLeadingItemsReuseTextByKeyAndKeepButtonsOnTextUpdates() {
        val controller = UIViewController()
        val applier = NativeBarApplier(controller)
        val button = NativeBarButton("일시정지")
        val name = NativeBarText("name", "식사", 40f)
        val elapsed = NativeBarText("elapsed", "00:09", 68f, "식사 시간 00:09")

        fun apply(vararg components: NativeBarComponent): List<*> {
            applier.apply(NativeBarContent(null, components.toList(), emptyList(), emptyList()))
            return controller.navigationItem.leftBarButtonItems.orEmpty()
        }

        val original = apply(name, button, elapsed)
        assertEquals(3, original.size)
        val labelItem = original[2] as UIBarButtonItem
        val label = assertNotNull(labelItem.customView as? UILabel)
        val next = elapsed.copy(text = "00:10", accessibilityLabel = "식사 시간 00:10")

        assertEquals(original, apply(name, button, next))
        assertEquals("00:10", label.text)
        assertEquals("식사 시간 00:10", label.accessibilityLabel)
        assertEquals(listOf(original[2], original[1], original[0]), apply(next, button, name))
        val remaining = apply(button).single() as UIBarButtonItem
        assertEquals("일시정지", remaining.accessibilityLabel)
    }

    @Test
    fun leadingLabelUpdatesWithoutReplacingBarItems() {
        val controller = UIViewController()
        val applier = NativeBarApplier(controller)
        val state = NativeBarState()
        state.listener = applier::apply
        state.leadingLabelListener = applier::applyLeadingLabel
        state.register(Any(), NativeBarContent(null, listOf(NativeBarButton("일시정지")), emptyList(), emptyList()))
        val button = assertNotNull(controller.navigationItem.leftBarButtonItems?.singleOrNull())
        val labelOwner = Any()

        state.registerLeadingLabel(labelOwner, NativeLeadingLabel("00:09", 68f, "경과 시간 00:09"))
        val labelItem = controller.navigationItem.leftBarButtonItems!!.first() as UIBarButtonItem
        val label = assertNotNull(labelItem.customView as? UILabel)
        assertEquals("00:09", label.text)
        assertEquals(button, controller.navigationItem.leftBarButtonItems?.lastOrNull())

        state.registerLeadingLabel(labelOwner, NativeLeadingLabel("00:10", 68f, "경과 시간 00:10"))
        assertEquals("00:10", label.text)
        assertEquals("경과 시간 00:10", label.accessibilityLabel)
        assertEquals(listOf(labelItem, button), controller.navigationItem.leftBarButtonItems)

        state.unregisterLeadingLabel(labelOwner)
        assertEquals(button, controller.navigationItem.leftBarButtonItems?.singleOrNull())
    }

    @Test
    fun subtitleChangesAndClearsIndependentlyOfBarItems() {
        val controller = UIViewController()
        val item = controller.navigationItem
        val applier = NativeBarApplier(controller)
        val state = NativeBarState()
        state.listener = applier::apply
        state.subtitleListener = applier::applySubtitle
        val hasSubtitle = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        fun displayedSubtitle() = if (hasSubtitle) item.subtitle else item.prompt

        val barOwner = Any()
        val subtitleOwner = Any()
        val nextSubtitleOwner = Any()
        state.register(barOwner, NativeBarContent("채팅", emptyList(), listOf(NativeBarButton("신고")), emptyList()))
        val action = item.rightBarButtonItems?.singleOrNull()
        assertNotNull(action)

        state.registerSubtitle(subtitleOwner, "운영자")
        assertEquals("운영자", displayedSubtitle())
        state.registerSubtitle(nextSubtitleOwner, "매칭방")
        assertEquals("매칭방", displayedSubtitle())
        state.unregisterSubtitle(nextSubtitleOwner)
        assertEquals("운영자", displayedSubtitle())
        state.registerSubtitle(subtitleOwner, null)
        assertNull(displayedSubtitle())
        state.registerSubtitle(subtitleOwner, "운영자")
        state.unregisterSubtitle(subtitleOwner)
        assertNull(displayedSubtitle())
        assertEquals("채팅", item.title)
        assertEquals(action, item.rightBarButtonItems?.singleOrNull())
    }

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
    fun `버튼 색을 변경하고 지정하지 않으면 시스템 색으로 되돌린다`() {
        val controller = UIViewController()
        val applier = NativeBarApplier(controller)

        fun apply(tint: Color): UIBarButtonItem {
            val button = NativeBarButton("즐겨찾기", systemImage = "star", tintColor = tint)
            applier.apply(NativeBarContent(null, emptyList(), listOf(button), emptyList()))
            return controller.navigationItem.rightBarButtonItems!!.single() as UIBarButtonItem
        }

        assertEquals(Color.Yellow.toUIColor(), apply(Color.Yellow).tintColor)
        assertEquals(Color.Blue.toUIColor(), apply(Color.Blue).tintColor)
        assertNull(apply(Color.Unspecified).tintColor)
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
