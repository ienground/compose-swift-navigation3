package zone.ien.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class ScrollProxyTest {
    @Test
    fun refreshControlIsRemovedWhenTheLastOwnerDetaches() {
        val proxy = ScrollProxy()
        val first = Any()
        val second = Any()
        var calls = 0
        proxy.attachRefresh(first) { calls++ }
        val control = assertNotNull(proxy.scrollView.refreshControl)
        proxy.attachRefresh(second) { calls += 10 }
        control.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(10, calls)
        proxy.detachRefresh(second)
        control.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(11, calls)
        proxy.detachRefresh(first)
        assertNull(proxy.scrollView.refreshControl)
        control.sendActionsForControlEvents(UIControlEventValueChanged)
        assertEquals(11, calls)
    }

    @Test
    fun overscrollKeepsScreenAndFixedBarsInViewport() {
        val proxy = ScrollProxy()
        proxy.scrollView.setFrame(CGRectMake(0.0, 0.0, 320.0, 600.0))
        val screen = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 600.0))
        proxy.scrollView.addSubview(screen)
        proxy.pinnedView = screen
        val attachment = proxy.attach(Color.Unspecified) { it }
        attachment.onContentLayout(ContentLayout(0.0, true, 0.0, true, false, 300.0))
        for ((offset, bounce) in listOf(-60.0 to 60.0, 0.0 to 0.0, 150.0 to 0.0, 300.0 to 0.0, 340.0 to -40.0)) {
            proxy.scrollView.setContentOffset(CGPointMake(0.0, offset), animated = false)
            proxy.updateInsets()
            val viewportY = proxy.scrollView.bounds.useContents { origin.y }
            assertEquals(viewportY, screen.frame.useContents { origin.y }, 0.01)
            assertEquals(bounce, proxy.overscrollOffset, 0.01)
        }
        attachment.detach()
        assertEquals(0.0, proxy.overscrollOffset, 0.01)
    }
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sharedContentMovesWithoutPerScreenModifierAndKeepsBottomBarFixed() = runComposeUiTest {
        val proxy = ScrollProxy()
        runOnUiThread {
            proxy.scrollView.setFrame(CGRectMake(0.0, 0.0, 320.0, 600.0))
            proxy.pinnedView = UIView(frame = CGRectMake(0.0, 0.0, 320.0, 600.0))
            proxy.attach(Color.Unspecified) { it }
                .onContentLayout(ContentLayout(0.0, true, 0.0, true, false, 300.0))
        }
        setContent {
            CompositionLocalProvider(LocalNativeScrollHost provides proxy) {
                NativeScrollContent {
                    Column(Modifier.size(200.dp, 300.dp)) {
                        NativeScrollContent(Modifier.weight(1f)) {
                            NativeScrollContent(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().testTag("content"))
                            }
                        }
                        Box(Modifier.height(48.dp).testTag("bottomBar"))
                    }
                }
            }
        }
        val contentTop = onNodeWithTag("content").getUnclippedBoundsInRoot().top
        val barTop = onNodeWithTag("bottomBar").getUnclippedBoundsInRoot().top
        for ((offset, bounce) in listOf(-60.0 to 60.dp, 0.0 to 0.dp, 340.0 to (-40).dp)) {
            runOnIdle {
                proxy.scrollView.setContentOffset(CGPointMake(0.0, offset), animated = false)
                proxy.updateInsets()
            }
            assertEquals(contentTop + bounce, onNodeWithTag("content").getUnclippedBoundsInRoot().top)
            assertEquals(barTop, onNodeWithTag("bottomBar").getUnclippedBoundsInRoot().top)
        }
    }

}
