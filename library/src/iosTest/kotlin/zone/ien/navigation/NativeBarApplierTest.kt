package zone.ien.navigation

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertNull
import platform.UIKit.UIViewController

@OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class, BetaInteropApi::class)
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
}
