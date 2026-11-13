package zone.ien.navigation

import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIImage
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UISearchController
import platform.UIKit.UISearchResultsUpdatingProtocol
import platform.darwin.NSObject
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityLabel
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.UIKit.setToolbarItems

/**
 * Applies a screen's [NativeBarContent] to its `navigationItem` and toolbar items.
 *
 * Native items are rebuilt only when the [BarShape] changes (titles, images, enabled flags,
 * menus). Click handlers look up the latest content at tap time, so recompositions that only
 * create new lambdas do not touch UIKit (no flicker, open menus stay open).
 */
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

    private var latest: NativeBarContent? = null
    private var appliedShape: BarShape? = null

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

    fun apply(content: NativeBarContent?) {
        latest = content
        applyTitle()

        val shape = content.shape()
        if (shape == appliedShape) return
        appliedShape = shape

        val item = controller.navigationItem
        item.leftItemsSupplementBackButton = true
        item.setLeftBarButtonItems(
            shape.leading.mapIndexed { index, button -> barButton(button) { latest?.leading?.getOrNull(index) } },
            animated = false,
        )
        // rightBarButtonItems are ordered from the right edge; the API takes them left to right.
        item.setRightBarButtonItems(
            shape.trailing.mapIndexed { index, button -> barButton(button) { latest?.trailing?.getOrNull(index) } }
                .reversed(),
            animated = false,
        )
        controller.setToolbarItems(toolbarItems(shape.toolbar), animated = false)
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

    private fun applyTitle() {
        val override = latest?.title
        when {
            override != null -> controller.navigationItem.title = override
            hasBaseTitle -> controller.navigationItem.title = baseTitle
        }
    }

    private fun toolbarItems(buttons: List<BarShape.ButtonShape>): List<UIBarButtonItem> {
        val items = buttons.mapIndexed { index, button -> barButton(button) { latest?.toolbar?.getOrNull(index) } }
        return when (items.size) {
            0 -> emptyList()
            1 -> listOf(UIBarButtonItem.flexibleSpaceItem(), items.single(), UIBarButtonItem.flexibleSpaceItem())
            else -> items.flatMapIndexed { index, item ->
                if (index == 0) listOf(item) else listOf(UIBarButtonItem.flexibleSpaceItem(), item)
            }
        }
    }

    private fun barButton(shape: BarShape.ButtonShape, current: () -> NativeBarButton?): UIBarButtonItem {
        val image = shape.systemImage?.let { UIImage.systemImageNamed(it) }
        val item = if (shape.menu.isEmpty()) {
            val action = UIAction.actionWithTitle(shape.title, image, identifier = null) { _ ->
                current()?.takeIf { it.enabled }?.onClick?.invoke()
            }
            UIBarButtonItem(primaryAction = action, menu = null)
        } else {
            val actions = shape.menu.mapIndexed { index, menuShape ->
                UIAction.actionWithTitle(
                    menuShape.title,
                    menuShape.systemImage?.let { UIImage.systemImageNamed(it) },
                    identifier = null,
                ) { _ -> current()?.menu?.getOrNull(index)?.onClick?.invoke() }.apply {
                    if (menuShape.destructive) attributes = UIMenuElementAttributesDestructive
                }
            }
            UIBarButtonItem(primaryAction = null, menu = UIMenu.menuWithTitle("", children = actions)).apply {
                title = shape.title
                this.image = image
            }
        }
        item.enabled = shape.enabled
        item.accessibilityLabel = shape.title
        return item
    }
}
