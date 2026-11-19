package zone.ien.navigation

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.systemRedColor
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleLeftMargin
import platform.UIKit.UIViewAutoresizingFlexibleBottomMargin
import platform.UIKit.UILabel
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.accessibilityValue
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIScreen
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle.UIBarButtonItemStyleDone
import platform.UIKit.UIBarButtonItemStyle.UIBarButtonItemStylePlain
import platform.UIKit.UIBarButtonSystemItem.UIBarButtonSystemItemFixedSpace
import platform.UIKit.UIAccessibilityTraitHeader
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UISearchController
import platform.UIKit.UISearchResultsUpdatingProtocol
import platform.darwin.NSObject
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityLabel
import platform.UIKit.accessibilityTraits
import platform.UIKit.isAccessibilityElement
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.UIKit.setToolbarItems
import platform.UIKit.transitionCoordinator
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/**
 * Applies a screen's [NativeBarContent] to its `navigationItem` and toolbar items.
 *
 * Native items are rebuilt only when the [BarShape] changes (titles, images, enabled flags,
 * menus). Click handlers look up the latest content at tap time, so recompositions that only
 * create new lambdas do not touch UIKit (no flicker, open menus stay open).
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal class NativeBarApplier(private val controller: UIViewController) {

    private var searchController: UISearchController? = null
    private var latestSearch: NativeSearch? = null

    // UISearchController.searchResultsUpdater is weak: kept here.
    private val searchUpdater = object : NSObject(), UISearchResultsUpdatingProtocol {
        override fun updateSearchResultsForSearchController(searchController: UISearchController) {
            val search = latestSearch ?: return
            val text = searchController.searchBar.text.orEmpty()
            if (text != search.query) search.onQueryChange(text)
        }
    }

    /** Shows [search] as the screen's native search field, or removes it when null. */
    fun applySearch(search: NativeSearch?) {
        latestSearch = search
        val item = controller.navigationItem
        if (search == null) {
            if (searchController != null) {
                item.searchController = null
                searchController = null
            }
            return
        }
        val searchController = searchController ?: UISearchController(searchResultsController = null).also {
            it.searchResultsUpdater = searchUpdater
            it.obscuresBackgroundDuringPresentation = false
            item.searchController = it
            item.hidesSearchBarWhenScrolling = true
            controller.definesPresentationContext = true
            searchController = it
        }
        val bar = searchController.searchBar
        if (bar.placeholder != search.placeholder) bar.placeholder = search.placeholder
        // Only push app-side changes; typing already matches (no cursor jumps).
        if (bar.text.orEmpty() != search.query) bar.text = search.query
    }

    private var latestSubtitle: String? = null

    fun applySubtitle(text: String?) {
        if (latestSubtitle == text) return
        latestSubtitle = text
        val item = controller.navigationItem
        if (NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }) {
            item.subtitle = text
        } else {
            item.prompt = text
        }
    }

    private var latest: NativeBarContent? = null
    private var appliedShape: BarShape? = null
    private var images: Map<Painter, UIImage?> = emptyMap()
    private var imageScale = 0f

    /**
     * Title used when no [NativeBarItems] title is declared (the entry's `nativeScreen` title).
     * Until it is set, a screen without a declared title keeps whatever title the app set.
     */
    var baseTitle: String? = null
        set(value) {
            field = value
            hasBaseTitle = true
            applyTitle()
        }
    private var hasBaseTitle = false

    /**
     * The entry's `nativeScreen` title image, shown as the navigation item's `titleView` in place
     * of the title text. VoiceOver reads the title.
     */
    var titleImage: UIImage? = null
        set(value) {
            if (field === value) return
            field = value
            applyTitleImage()
        }
    private var titleImageView: UIImageView? = null

    var initialTrailing: List<NativeBarButton>? = null
        set(value) {
            if (field == value) return
            field = value
            if (latest == null && !waitingForTransition) applyItems(null)
        }

    private var waitingForTransition = false
    private var appliedContent: NativeBarContent? = null
    private var trailingItems = emptyList<UIBarButtonItem>()

    fun apply(content: NativeBarContent?) {
        latest = content
        applyTitle()
        if (waitingForTransition) return

        val navigation = controller.navigationController
        val transition = navigation?.transitionCoordinator
        val changesNavigationItems = transition != null && content.shape().let { shape ->
            shape.leading != appliedShape?.leading.orEmpty() || shape.trailing != appliedShape?.trailing.orEmpty()
        }
        if (transition != null && changesNavigationItems) {
            val applier = WeakReference(this)
            waitingForTransition = true
            if (transition.animateAlongsideTransition(animation = null, completion = {
                    dispatch_async(dispatch_get_main_queue()) {
                        applier.get()?.applyAfterTransition()
                    }
                })
            ) return
            waitingForTransition = false
        }
        applyItems(content)
    }

    private fun applyAfterTransition() {
        waitingForTransition = false
        apply(latest)
    }

    private fun applyItems(declared: NativeBarContent?) {
        val content = declared ?: initialTrailing?.let {
            NativeBarContent(null, emptyList(), it, emptyList())
        }
        appliedContent = content
        val shape = content.shape()
        val scale = controller.traitCollection.displayScale.toFloat().takeIf { it > 0f }
            ?: UIScreen.mainScreen.scale.toFloat()
        if (shape == appliedShape && scale == imageScale) return
        val appliedLeading = appliedShape?.leading.orEmpty()
        val appliedTrailing = appliedShape?.trailing.orEmpty()
        val previousShape = appliedShape.takeIf { scale == imageScale }
        val previousImages = if (scale == imageScale) images else emptyMap()
        val painters = (shape.leading + shape.trailing + shape.toolbar).flatMap { button ->
            listOfNotNull(button.image) + button.menu.mapNotNull { it.image }
        }.toSet()
        images = painters.associateWith { painter ->
            previousImages[painter] ?: renderNativeImage(painter, Density(scale), height = 22f, maxWidth = 22f)
                ?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
        }
        imageScale = scale
        appliedShape = shape

        val item = controller.navigationItem
        val applier = WeakReference(this)
        item.leftItemsSupplementBackButton = true
        val navigation = controller.navigationController
        val animateBarItems = navigation?.topViewController == controller &&
            !navigation.navigationBarHidden && navigation.navigationBar.topItem == item &&
            controller.viewIfLoaded?.window != null
        val pendingActivation = mutableListOf<UIBarButtonItem>()
        fun prepareButton(
            shape: BarShape.ButtonShape,
            previous: List<BarShape.ButtonShape>,
            current: () -> NativeBarButton?,
        ): UIBarButtonItem = barButton(shape, current).also { button ->
            if (animateBarItems && shape.enabled && previous.none { it.title == shape.title }) {
                button.enabled = false
                button.customView?.userInteractionEnabled = false
                pendingActivation += button
            }
        }
        if (shape.leading != previousShape?.leading) {
            item.setLeftBarButtonItems(
                shape.leading.mapIndexed { index, button ->
                    prepareButton(button, appliedLeading) { applier.get()?.appliedContent?.leading?.getOrNull(index) }
                },
                animated = animateBarItems,
            )
        }
        // rightBarButtonItems are ordered from the right edge; the API takes them left to right.
        if (shape.trailing != previousShape?.trailing) {
            trailingItems = shape.trailing.mapIndexed { index, button ->
                if (button == previousShape?.trailing?.getOrNull(index)) trailingItems[index]
                else prepareButton(button, appliedTrailing) { applier.get()?.appliedContent?.trailing?.getOrNull(index) }
            }
            item.setRightBarButtonItems(
                shape.trailing.flatMapIndexed { index, button ->
                    val barItem = trailingItems[index]
                    if (button.isPrimary && index > 0 && !shape.trailing[index - 1].isPrimary) {
                        val separator = UIBarButtonItem(barButtonSystemItem = UIBarButtonSystemItemFixedSpace, target = null, action = null)
                        separator.width = 8.0
                        listOf(separator, barItem)
                    } else {
                        listOf(barItem)
                    }
                }.reversed(),
                animated = animateBarItems,
            )
        }
        if (pendingActivation.isNotEmpty()) {
            dispatch_async(dispatch_get_main_queue()) {
                val installedItems = item.leftBarButtonItems.orEmpty() + item.rightBarButtonItems.orEmpty()
                pendingActivation.filter { it in installedItems }.forEach { button ->
                    button.enabled = true
                    button.customView?.userInteractionEnabled = true
                }
            }
        }
        if (shape.toolbar != previousShape?.toolbar) {
            controller.setToolbarItems(toolbarItems(shape.toolbar), animated = false)
        }
        updateToolbarVisibility(animated = true, onlyIfTop = true)
    }

    /**
     * Called after each composition of the screen: a screen whose content declared nothing still
     * gets an (empty) bar applied once, so it hides a toolbar left by the previous screen.
     */
    fun onComposed() {
        if (appliedShape == null) apply(latest)
    }

    /**
     * The toolbar belongs to the navigation controller: show it only for screens that have items.
     * A screen that has not composed yet does not decide (its items are unknown); its first
     * [apply] does, which avoids hiding and re-showing the toolbar between two screens that both
     * have one.
     */
    fun updateToolbarVisibility(animated: Boolean, onlyIfTop: Boolean) {
        val shape = appliedShape ?: return
        val navigation = controller.navigationController ?: return
        if (onlyIfTop && navigation.topViewController !== controller) return
        val hidden = shape.toolbar.isEmpty()
        if (navigation.toolbarHidden != hidden) navigation.setToolbarHidden(hidden, animated)
    }

    /**
     * Sets the declared title, else the base title. The navigation item is written only when the
     * title changes: screens recompose often (e.g. every second for a running timer) and each
     * recomposition re-declares the same title, also while the bar is in a push or pop transition.
     */
    private fun applyTitle() {
        val title = latest?.title ?: if (hasBaseTitle) baseTitle else return
        val item = controller.navigationItem
        if (item.title != title) item.title = title
        titleImageView?.accessibilityLabel = title
    }

    private fun applyTitleImage() {
        val item = controller.navigationItem
        val image = titleImage
        if (image == null) {
            if (titleImageView != null) {
                item.titleView = null
                titleImageView = null
            }
            return
        }
        val view = titleImageView ?: UIImageView().also {
            it.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
            it.isAccessibilityElement = true
            it.accessibilityTraits = UIAccessibilityTraitHeader
            titleImageView = it
            item.titleView = it
        }
        view.image = image
        view.accessibilityLabel = item.title
        view.sizeToFit()
    }

    private fun toolbarItems(buttons: List<BarShape.ButtonShape>): List<UIBarButtonItem> {
        val applier = WeakReference(this)
        val items = buttons.mapIndexed { index, button -> barButton(button) { applier.get()?.appliedContent?.toolbar?.getOrNull(index) } }
        return when (items.size) {
            0 -> emptyList()
            1 -> listOf(UIBarButtonItem.flexibleSpaceItem(), items.single(), UIBarButtonItem.flexibleSpaceItem())
            else -> items.flatMapIndexed { index, item ->
                if (index == 0) listOf(item) else listOf(UIBarButtonItem.flexibleSpaceItem(), item)
            }
        }
    }

    private fun barButton(shape: BarShape.ButtonShape, current: () -> NativeBarButton?): UIBarButtonItem {
        val image = shape.image?.let { images[it] } ?: shape.systemImage?.let { UIImage.systemImageNamed(it) }
        val item = if (shape.menu.isEmpty()) {
            val action = UIAction.actionWithTitle(shape.title, image, identifier = null) { _ ->
                current()?.takeIf { it.enabled }?.onClick?.invoke()
            }
            UIBarButtonItem(primaryAction = action, menu = null)
        } else {
            val actions = shape.menu.mapIndexed { index, menuShape ->
                UIAction.actionWithTitle(
                    menuShape.title,
                    menuShape.image?.let { images[it] } ?: menuShape.systemImage?.let { UIImage.systemImageNamed(it) },
                    identifier = null,
                ) { _ ->
                    current()?.takeIf { it.enabled }?.menu?.getOrNull(index)
                        ?.takeIf { it.enabled }?.onClick?.invoke()
                }.apply {
                    attributes = (if (menuShape.destructive) UIMenuElementAttributesDestructive else 0uL) or
                        (if (menuShape.enabled) 0uL else UIMenuElementAttributesDisabled)
                    subtitle = when {
                        menuShape.badge > 0 -> menuShape.badge.toString()
                        menuShape.badge < 0 -> "•"
                        else -> null
                    }
                }
            }
            UIBarButtonItem(primaryAction = null, menu = UIMenu.menuWithTitle("", children = actions)).apply {
                title = shape.title
                this.image = image
            }
        }
        item.style = if (shape.isPrimary) UIBarButtonItemStyleDone else UIBarButtonItemStylePlain
        item.enabled = shape.enabled
        item.accessibilityLabel = shape.title
        if (shape.badge != 0) {
            item.customView = badgedButton(shape, image, item, current)
        }
        return item
    }

    /** iOS 18.2부터 지원하는 UIKit 컨트롤로 배지를 표시하며, 시스템 바 자체는 유지한다. */
    private fun badgedButton(
        shape: BarShape.ButtonShape,
        image: UIImage?,
        item: UIBarButtonItem,
        current: () -> NativeBarButton?,
    ): UIView {
        val button = UIButton.buttonWithType(UIButtonTypeSystem)
        if (image != null) button.setImage(image, forState = UIControlStateNormal)
        else button.setTitle(shape.title, forState = UIControlStateNormal)
        if (item.menu != null) {
            button.menu = item.menu
            button.showsMenuAsPrimaryAction = true
        } else {
            button.addAction(UIAction.actionWithHandler { _ ->
                current()?.takeIf { it.enabled }?.onClick?.invoke()
            }, forControlEvents = UIControlEventTouchUpInside)
        }
        button.enabled = shape.enabled
        button.accessibilityLabel = shape.title
        button.accessibilityValue = if (shape.badge > 0) shape.badge.toString() else "•"
        button.sizeToFit()
        val width = button.bounds.useContents { size.width.coerceAtLeast(44.0) } + 8.0
        val container = UIView(frame = CGRectMake(0.0, 0.0, width, 44.0))
        button.setFrame(container.bounds)
        button.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        container.addSubview(button)
        val label = UILabel().apply {
            text = if (shape.badge > 0) shape.badge.toString() else ""
            font = UIFont.systemFontOfSize(11.0, weight = UIFontWeightSemibold)
            textAlignment = NSTextAlignmentCenter
            textColor = UIColor.whiteColor
            backgroundColor = UIColor.systemRedColor
            userInteractionEnabled = false
            isAccessibilityElement = false
            sizeToFit()
        }
        val height = if (shape.badge > 0) 16.0 else 8.0
        val badgeWidth = label.bounds.useContents { size.width + 8.0 }.coerceAtLeast(height)
        label.setFrame(CGRectMake(0.0, 0.0, badgeWidth, height))
        label.layer.cornerRadius = height / 2.0
        label.clipsToBounds = true
        // 네이티브 바의 vibrancy가 배지 색상을 바꾸지 않도록 원본 색상의 이미지로 전달한다.
        val badgeImage = UIGraphicsImageRenderer(size = CGSizeMake(badgeWidth, height)).imageWithActions {
            label.layer.renderInContext(it!!.CGContext)
        }.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
        val badgeView = UIImageView(image = badgeImage)
        badgeView.setFrame(CGRectMake(width - badgeWidth, 0.0, badgeWidth, height))
        badgeView.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin or UIViewAutoresizingFlexibleBottomMargin
        badgeView.isAccessibilityElement = false
        badgeView.userInteractionEnabled = false
        badgeView.alpha = if (shape.enabled) 1.0 else 0.5
        container.addSubview(badgeView)
        return container
    }
}
