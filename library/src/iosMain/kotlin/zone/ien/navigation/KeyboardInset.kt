package zone.ien.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectGetMaxY
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectIntersectsRect
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIScrollView
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/**
 * Adds the part of the screen covered by the software keyboard to the scroll view's bottom
 * content inset, so the content can still be scrolled into view (what `UITableViewController`
 * does natively; a plain scroll view does not). The adjusted content inset, and through it the
 * Compose content padding, follows.
 *
 * Only active between [start] and [stop] (while the screen is visible).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal class KeyboardInset(scrollView: UIScrollView) {
    private val scrollView = WeakReference(scrollView)
    private val observers = mutableListOf<Any>()
    var overlap by mutableDoubleStateOf(0.0)
        private set

    fun start() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        val self = WeakReference(this)
        observers += center.addObserverForName(UIKeyboardWillChangeFrameNotification, null, NSOperationQueue.mainQueue) {
            self.get()?.onKeyboardFrame(it)
        }
        observers += center.addObserverForName(UIKeyboardWillHideNotification, null, NSOperationQueue.mainQueue) {
            self.get()?.setOverlap(0.0)
        }
    }

    fun stop() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers.clear()
        setOverlap(0.0)
    }

    private fun onKeyboardFrame(notification: NSNotification?) {
        val view = scrollView.get() ?: return
        val value = notification?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return
        val keyboard = value.CGRectValue() // window coordinates
        val visible = view.convertRect(view.bounds, toView = null)
        val overlap = if (CGRectIntersectsRect(visible, keyboard)) {
            CGRectGetMaxY(visible) - CGRectGetMinY(keyboard)
        } else {
            0.0
        }
        setOverlap(overlap)
    }

    private fun setOverlap(overlap: Double) {
        this.overlap = overlap
        val view = scrollView.get() ?: return
        // The safe area (home indicator, tab bar) is already part of the adjusted inset.
        val safeBottom = view.safeAreaInsets.useContents { bottom }
        val extra = maxOf(0.0, overlap - safeBottom)
        if (view.contentInset.useContents { bottom } != extra) {
            view.contentInset = UIEdgeInsetsMake(0.0, 0.0, extra, 0.0)
        }
    }
}
