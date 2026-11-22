package zone.ien.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIAction
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIRefreshControl
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIView
import platform.UIKit.systemBackgroundColor
import platform.darwin.NSObject
import kotlin.math.abs

/**
 * The UIScrollView UIKit tracks as the screen's content scroll view.
 *
 * UIKit drives the interactive large title collapse, the scroll edge appearance and the
 * iOS 26 scroll edge effect only from a scroll view that is actually panned, so while a
 * Compose container is attached ([attach]) this view owns the vertical gesture and physics
 * and Compose follows its offset. The Compose view is a subview pinned to the viewport.
 * See AGENTS.md 6.5.
 */
@OptIn(ExperimentalForeignApi::class)
internal class ScrollProxy(private val screenBottomInset: (Double) -> Double = { it }) : NativeScrollHost {

    /** Areas (dp == pt, in the Compose view's coordinates) where the pan must not start. */
    private val exclusions = mutableMapOf<Any, Rect>()

    val scrollView: UIScrollView = ProxyScrollView(::allowsPanAt).apply {
        scrollEnabled = false
        delaysContentTouches = false
        canCancelContentTouches = true
        showsVerticalScrollIndicator = false
        showsHorizontalScrollIndicator = false
        backgroundColor = UIColor.systemBackgroundColor
        contentInsetAdjustmentBehavior =
            UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentAlways
    }

    private val refreshActions = linkedMapOf<Any, () -> Unit>()
    private var previousRefreshControl: UIRefreshControl? = null
    private val refreshControl by lazy {
        val actions = refreshActions
        UIRefreshControl().apply {
            addAction(
                UIAction.actionWithTitle("", image = null, identifier = null) {
                    actions.values.lastOrNull()?.invoke()
                },
                forControlEvents = UIControlEventValueChanged,
            )
        }
    }

    override fun attachRefresh(owner: Any, onRefresh: () -> Unit) {
        if (refreshActions.isEmpty()) {
            previousRefreshControl = scrollView.refreshControl
            scrollView.refreshControl = refreshControl
        }
        refreshActions[owner] = onRefresh
    }

    override fun endRefresh(owner: Any) {
        if (refreshActions.keys.lastOrNull() == owner) refreshControl.endRefreshing()
    }

    override fun detachRefresh(owner: Any) {
        if (refreshActions.remove(owner) == null || refreshActions.isNotEmpty()) return
        refreshControl.endRefreshing()
        if (scrollView.refreshControl == refreshControl) scrollView.refreshControl = previousRefreshControl
        previousRefreshControl = null
    }

    /** The Compose view, kept on the viewport. */
    var pinnedView: UIView? = null

    override var overscrollOffset by mutableDoubleStateOf(0.0)
        private set

    private var insetTop by mutableDoubleStateOf(0.0)
    private var insetBottom by mutableDoubleStateOf(0.0)
    private var insetLeft by mutableDoubleStateOf(0.0)
    private var insetRight by mutableDoubleStateOf(0.0)

    override val contentPadding: PaddingValues = object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = insetLeft.dp
        override fun calculateTopPadding(): Dp = insetTop.dp
        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = insetRight.dp
        override fun calculateBottomPadding(): Dp = screenBottomInset(insetBottom).dp
    }

    private val sync = ScrollSync()
    private var scrollContent: ((Double) -> Double)? = null
    private var attachment: Attachment? = null

    /** Set while we change the offset/size ourselves, so the change is not fed back to Compose. */
    private var adjusting = false

    /** A UIKit-driven animation (status bar scroll-to-top) is running. */
    private var nativeAnimating = false

    private val delegate = object : NSObject(), UIScrollViewDelegateProtocol {
        override fun scrollViewDidScroll(scrollView: UIScrollView) {
            if (!adjusting) followNativeOffset()
            pinToViewport()
        }

        override fun scrollViewDidChangeAdjustedContentInset(scrollView: UIScrollView) {
            updateInsets()
        }

        override fun scrollViewShouldScrollToTop(scrollView: UIScrollView): Boolean {
            nativeAnimating = true
            return true
        }

        override fun scrollViewDidScrollToTop(scrollView: UIScrollView) {
            nativeAnimating = false
        }

        override fun scrollViewDidEndScrollingAnimation(scrollView: UIScrollView) {
            nativeAnimating = false
        }
    }

    init {
        scrollView.delegate = delegate
    }

    private val nativeOffsetY: Double get() = scrollView.contentOffset.useContents { y }
    private val viewportHeight: Double get() = scrollView.bounds.useContents { size.height }
    private val contentHeight: Double get() = scrollView.contentSize.useContents { height }

    override fun setExclusion(owner: Any, area: Rect?) {
        if (area == null) exclusions.remove(owner) else exclusions[owner] = area
    }

    /** False when the pan started inside an area excluded with `Modifier.nativeScrollExclusion()`. */
    private fun allowsPanAt(pan: UIPanGestureRecognizer): Boolean {
        if (exclusions.isEmpty()) return true
        val composeView = pinnedView ?: return true
        // Where the touch started: current location minus the translation so far.
        val (x, y) = pan.locationInView(composeView).useContents { x to y }
        val (dx, dy) = pan.translationInView(composeView).useContents { x to y }
        val start = Offset((x - dx).toFloat(), (y - dy).toFloat())
        return exclusions.values.none { it.contains(start) }
    }

    override fun attach(background: Color, scrollBy: (Double) -> Double): NativeScrollAttachment {
        attachment?.detach()
        scrollView.backgroundColor = background.toUIColor()
        val newAttachment = Attachment()
        attachment = newAttachment
        scrollContent = scrollBy
        sync.reset()
        scrollView.scrollEnabled = true
        scrollView.alwaysBounceVertical = true
        updateContentSize()
        return newAttachment
    }

    private inner class Attachment : NativeScrollAttachment {
        override fun onContentLayout(layout: ContentLayout) {
            if (attachment !== this) return
            val nativeIdle = !scrollView.isDragging() && !scrollView.isDecelerating() &&
                !scrollView.isTracking() && !nativeAnimating
            sync.onContentLayout(layout, nativeIdle, insetTop)?.let(::setNativeOffset)
            updateContentSize()
            pinToViewport()
        }

        override fun detach() {
            if (attachment !== this) return
            attachment = null
            scrollContent = null
            sync.reset()
            scrollView.scrollEnabled = false
            scrollView.alwaysBounceVertical = false
            scrollView.backgroundColor = UIColor.systemBackgroundColor
            setNativeOffset(-insetTop)
            updateContentSize()
            pinToViewport()
        }
    }

    /** Called on layout and when UIKit changes the adjusted content inset. */
    fun updateInsets() {
        scrollView.adjustedContentInset.useContents {
            insetTop = top
            insetBottom = bottom
            insetLeft = left
            insetRight = right
        }
        updateContentSize()
        pinToViewport()
    }

    /** UIKit (pan, deceleration, bounce, scroll-to-top) moved: make Compose follow. */
    private fun followNativeOffset() {
        val scrollBy = scrollContent ?: return
        val delta = sync.deltaForNativeOffset(nativeOffsetY, insetTop)
        if (delta == 0.0) return
        scrollBy(delta)
        sync.onDispatched(delta)
    }

    private fun setNativeOffset(y: Double) {
        if (abs(y - nativeOffsetY) <= OFFSET_TOLERANCE) return
        adjusting = true
        try {
            scrollView.setContentOffset(CGPointMake(0.0, y), animated = false)
        } finally {
            adjusting = false
        }
    }

    private fun updateContentSize() {
        val viewport = viewportHeight
        if (viewport <= 0.0) return
        val height = if (attachment == null) {
            // Nothing scrolls: content exactly fills the area between the bars.
            maxOf(0.0, viewport - insetTop - insetBottom)
        } else {
            sync.contentHeight(viewport, insetTop, insetBottom, contentHeight)
        }
        if (abs(height - contentHeight) <= OFFSET_TOLERANCE) return
        val width = scrollView.bounds.useContents { size.width }
        adjusting = true
        try {
            scrollView.setContentSize(CGSizeMake(width, height))
        } finally {
            adjusting = false
        }
    }

    private fun pinToViewport() {
        val view = pinnedView ?: return
        val maxOffsetY = ScrollSync.maxOffsetY(contentHeight, viewportHeight, insetTop, insetBottom)
        overscrollOffset = ScrollSync.overscrollOffsetY(nativeOffsetY, insetTop, maxOffsetY)
        scrollView.bounds.useContents {
            view.setFrame(CGRectMake(origin.x, origin.y, size.width, size.height))
        }
    }

    private companion object {
        const val OFFSET_TOLERANCE = 0.5
    }
}

internal fun Color.toUIColor(): UIColor {
    if (this == Color.Unspecified) return UIColor.systemBackgroundColor
    val srgb = convert(ColorSpaces.Srgb)
    return UIColor(
        red = srgb.red.toDouble(),
        green = srgb.green.toDouble(),
        blue = srgb.blue.toDouble(),
        alpha = srgb.alpha.toDouble(),
    )
}

/**
 * Vertical-only: horizontal pans are left to Compose (pagers, carousels, sliders), and so are
 * pans starting in areas excluded with `Modifier.nativeScrollExclusion()`.
 */
@OptIn(ExperimentalForeignApi::class)
private class ProxyScrollView(
    private val allowsPan: (UIPanGestureRecognizer) -> Boolean,
) : UIScrollView(frame = CGRectZero.readValue()) {
    override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean {
        if (gestureRecognizer == panGestureRecognizer && gestureRecognizer is UIPanGestureRecognizer) {
            val horizontal = gestureRecognizer.velocityInView(this).useContents { abs(x) > abs(y) }
            if (horizontal || !allowsPan(gestureRecognizer)) return false
        }
        return super.gestureRecognizerShouldBegin(gestureRecognizer)
    }
}
