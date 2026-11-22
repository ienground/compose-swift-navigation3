package zone.ien.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.childViewControllers
import platform.UIKit.accessibilityLabel
import platform.UIKit.navigationItem
import platform.UIKit.tabBarItem
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import androidx.navigation3.runtime.NavEntry
import platform.UIKit.UITabBarController
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.hidesBottomBarWhenPushed
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow
import platform.UIKit.transitionCoordinator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A stack in a tab: its own entries, then NativeTabs' shared screens (UIKit, no window). */
class StackCoordinatorTest {

    @Test
    fun `탭 제목이나 아이콘이 바뀌어도 null 배지는 기존 값을 유지한다`() {
        val controller = NativeNavigationRootController(content = {}, restoredState = null, stateVersion = 1)
        controller.loadViewIfNeeded()
        val declaration = Any()
        val home = entry("home")
        val settings = entry("settings")

        fun update(title: String, icon: String, count: Int?) {
            controller.updateTabs(
                declaration = declaration,
                tabs = listOf(
                    TabState(title, "house", listOf(home), onBack = {}, badgeCount = count),
                    TabState("Settings", icon, listOf(settings), onBack = {}),
                ),
                selectedTab = 0,
                onTabSelected = {},
                shared = SharedState(emptyList(), {}),
            )
        }

        update("Home", "gearshape", 3)
        val tabs = assertIs<UITabBarController>(controller.childViewControllers.single())
        val homeTab = assertIs<UINavigationController>(tabs.viewControllers!!.first())
        assertEquals("3", homeTab.tabBarItem.badgeValue)

        update("Today", "gearshape.fill", null)
        assertEquals("Today", homeTab.tabBarItem.title)
        assertEquals("3", homeTab.tabBarItem.badgeValue)

        update("Today", "gearshape.fill", 0)
        assertNull(homeTab.tabBarItem.badgeValue)
    }

    @Test
    fun `스택의 네이티브 강조색은 변경과 기본색 복원을 반영한다`() = verifyNavigationTint(false)

    @Test
    fun `탭 안의 네이티브 강조색도 변경과 기본색 복원을 반영한다`() = verifyNavigationTint(true)

    private fun verifyNavigationTint(tabs: Boolean) {
        val tint = mutableStateOf(Color.Red)
        val controller = NativeNavigationViewController {
            NativeNavigationTint(tint.value) {
                if (tabs) {
                    NativeTabs(
                        tabs = listOf(NativeTab("Home", "house", listOf("home"))),
                        selectedTab = 0, onTabSelected = {},
                        entryProvider = { NavEntry(it) {} },
                    ) {}
                } else {
                    NativeStack(backStack = listOf("home"), onBack = {}, entryProvider = { NavEntry(it) {} }) {}
                }
            }
        }
        controller.loadViewIfNeeded()
        val child = controller.childViewControllers.single()
        val navigation = if (tabs) {
            assertIs<UINavigationController>(assertIs<UITabBarController>(child).viewControllers!!.single())
        } else assertIs<UINavigationController>(child)
        val systemTint = UIViewController().view.tintColor
        for ((color, expected) in listOf(
            Color.Red to UIColor(red = 1.0, green = 0.0, blue = 0.0, alpha = 1.0),
            Color.Blue to UIColor(red = 0.0, green = 0.0, blue = 1.0, alpha = 1.0),
            Color.Unspecified to systemTint,
        )) {
            tint.value = color
            Snapshot.sendApplyNotifications()
            repeat(10) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02)) }
            assertEquals(expected, navigation.navigationBar.tintColor)
            assertEquals(expected, navigation.toolbar.tintColor)
        }
    }

    @Test
    fun customTabImagesReachUIKitBeforeScreenComposition() {
        val controller = NativeNavigationViewController {
            NativeTabs(
                tabs = listOf(NativeTab(
                    title = "Home", backStack = listOf("home"),
                    image = ColorPainter(Color.Black), selectedImage = ColorPainter(Color.White),
                )),
                selectedTab = 0,
                onTabSelected = {},
                entryProvider = { NavEntry(it) {} },
            ) {}
        }
        controller.loadViewIfNeeded()
        val tabs = assertIs<UITabBarController>(controller.childViewControllers.single())
        val item = assertIs<UINavigationController>(tabs.viewControllers!!.single()).tabBarItem
        val image = assertNotNull(item.image)
        val selected = assertNotNull(item.selectedImage)
        assertNotSame(image, selected)
        assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate, image.renderingMode)
        assertEquals(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate, selected.renderingMode)
    }

    @Test
    fun tabTintTracksChangesAndRestoresSystemColor() {
        val tint = mutableStateOf(Color.Red)
        val controller = NativeNavigationViewController {
            NativeTabBarTint(tint.value) {
                NativeTabs(
                    tabs = listOf(NativeTab("Home", "house", listOf("home"))),
                    selectedTab = 0, onTabSelected = {},
                    entryProvider = { NavEntry(it) {} },
                ) {}
            }
        }
        controller.loadViewIfNeeded()
        val tabs = assertIs<UITabBarController>(controller.childViewControllers.single())
        val systemBar = UITabBarController().tabBar
        assertEquals(UIColor(red = 1.0, green = 0.0, blue = 0.0, alpha = 1.0), tabs.tabBar.tintColor)
        val unselected = tabs.tabBar.unselectedItemTintColor
        tint.value = Color.Blue
        Snapshot.sendApplyNotifications()
        repeat(10) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02)) }
        assertEquals(UIColor(red = 0.0, green = 0.0, blue = 1.0, alpha = 1.0), tabs.tabBar.tintColor)
        assertEquals(unselected, tabs.tabBar.unselectedItemTintColor)
        tint.value = Color.Unspecified
        Snapshot.sendApplyNotifications()
        repeat(10) { NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02)) }
        assertEquals(systemBar.tintColor, tabs.tabBar.tintColor)
    }

    private fun entry(key: String, hidesTabBar: Boolean = false) =
        NavEntry(key, metadata = nativeScreen(hidesTabBar = hidesTabBar)) {}

    private val calls = mutableListOf<String>()

    private fun coordinator(): Pair<StackCoordinator, UINavigationController> {
        val navigation = UINavigationController()
        return StackCoordinator(navigation, ContentLeases()) to navigation
    }

    private fun StackCoordinator.show(own: List<String>, shared: List<String>) = update(
        entries = own.map { entry(it) },
        onBack = { calls += "own" },
        shared = shared.map { entry(it) },
        onSharedBack = { calls += "shared" },
        animated = false,
    )

    /** What `didShow` reports after UIKit popped by itself (back button, swipe, tab re-tap). */
    private fun UINavigationController.reportShown() {
        delegate!!.navigationController(this, didShowViewController = topViewController!!, animated = false)
    }

    private val UINavigationController.screens: List<UIViewController>
        get() = viewControllers.map { it as UIViewController }

    @Test
    fun sharedScreensArePushedAboveTheTabsOwnAndHideTheTabBar() {
        val (stack, navigation) = coordinator()
        stack.show(own = listOf("home", "detail"), shared = listOf("profile", "edit"))

        assertEquals(4, navigation.screens.size)
        assertEquals(listOf(false, false, true, true), navigation.screens.map { (it as ComposeScreenController).hidesTabBar })
    }

    @Test
    fun uikitPopsReachTheStackTheyCameFrom() {
        val (stack, navigation) = coordinator()
        stack.show(own = listOf("home", "detail"), shared = listOf("profile", "edit"))

        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("shared"), calls)

        // Tab re-tap: back to the root, top first.
        navigation.popToRootViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("shared", "shared", "own"), calls)
    }

    @Test
    fun sharedScreensLeaveATabThatIsNoLongerSelected() {
        val (stack, navigation) = coordinator()
        stack.show(own = listOf("home"), shared = listOf("profile"))
        stack.show(own = listOf("home"), shared = emptyList())

        assertEquals(1, navigation.screens.size)
        assertEquals(emptyList(), calls, "an app-driven change is not reported back")
    }

    @Test
    fun aSharedScreenHidesTheTabBarWhateverItsMetadata() {
        val (stack, navigation) = coordinator()
        stack.update(
            entries = listOf(entry("home"), entry("detail", hidesTabBar = false)),
            onBack = {},
            shared = listOf(entry("profile", hidesTabBar = false)),
            animated = false,
        )
        assertEquals(listOf(false, false, true), navigation.screens.map { (it as ComposeScreenController).hidesTabBar })
    }

    @Test
    fun pendingPushWinsOverAnOlderNativePop() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        val keys = mutableListOf("home", "detail")
        fun update() = stack.update(keys.map { entry(it) }, onBack = { keys.removeLast() }, animated = false)
        update()
        transitioning = true
        keys.add("new")
        update()
        assertEquals(2, navigation.screens.size)
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("home", "detail", "new"), keys)
        assertEquals(3, navigation.screens.size)
        stack.dispose()
    }

    @Test
    fun pendingPopDoesNotPopTheKotlinStackAgain() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        val keys = mutableListOf("home", "detail", "edit")
        var backCalls = 0
        fun update() = stack.update(keys.map { entry(it) }, onBack = {
            backCalls++
            keys.removeLast()
        }, animated = false)
        update()
        transitioning = true
        keys.removeLast()
        update()
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("home", "detail"), keys)
        assertEquals(0, backCalls)
        assertEquals(2, navigation.screens.size)
        stack.dispose()
    }

    @Test
    fun refreshedMetadataStillReportsTheNativePop() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        val keys = mutableListOf("home", "detail")
        stack.update(keys.map { entry(it) }, onBack = { keys.removeLast() }, animated = false)
        transitioning = true
        stack.update(
            keys.map { NavEntry(it, metadata = nativeScreen(title = "새 제목")) {} },
            onBack = { keys.removeLast() },
            animated = false,
        )
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(listOf("home"), keys)
        assertEquals(1, navigation.screens.size)
        stack.dispose()
    }

    @Test
    fun pendingSharedBoundaryChangeDoesNotPopTheNewStack() {
        var transitioning = false
        val navigation = UINavigationController()
        val stack = StackCoordinator(navigation, ContentLeases(), isTransitioning = { transitioning })
        stack.show(own = listOf("home", "detail"), shared = emptyList())
        transitioning = true
        stack.show(own = listOf("home"), shared = listOf("detail"))
        transitioning = false
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        assertEquals(emptyList(), calls)
        assertEquals(2, navigation.screens.size)
        assertTrue((navigation.screens.last() as ComposeScreenController).hidesTabBar)
        stack.dispose()
    }

    private class RecordingTabs : UITabBarController(nibName = null, bundle = null) {
        val changes = mutableListOf<Pair<Boolean, Boolean>>()

        override fun setTabBarHidden(hidden: Boolean, animated: Boolean) {
            changes += hidden to animated
            super.setTabBarHidden(hidden, animated)
        }
    }

    @Test
    fun sharedPushWithoutActiveTransitionHidesTabBarImmediately() {
        val (stack, navigation) = coordinator()
        val tabs = RecordingTabs()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.show(own = listOf("home"), shared = emptyList())
        tabs.changes.clear()
        stack.update(
            entries = listOf(entry("home")),
            onBack = {},
            shared = listOf(entry("profile")),
            onSharedBack = {},
            animated = true,
        )
        assertTrue(tabs.tabBarHidden)
        assertTrue(tabs.changes.contains(true to false), "실제 전환이 없으면 탭 바를 즉시 숨겨야 합니다.")
        assertFalse(tabs.changes.any { (hidden, animated) -> hidden && animated })
        stack.dispose()
    }

    private class RecordingNavigation : UINavigationController(nibName = null, bundle = null) {
        var pushedWithHiddenBar: Boolean? = null
        var pushRequestedAnimation: Boolean? = null
        val navigationBarChanges = mutableListOf<Pair<Boolean, Boolean>>()

        override fun setNavigationBarHidden(hidden: Boolean, animated: Boolean) {
            navigationBarChanges += hidden to animated
            super.setNavigationBarHidden(hidden, animated)
        }

        override fun pushViewController(viewController: UIViewController, animated: Boolean) {
            pushedWithHiddenBar = viewController.hidesBottomBarWhenPushed
            pushRequestedAnimation = animated
            super.pushViewController(viewController, animated)
        }
    }

    private fun awaitUIKit(message: String, condition: () -> Boolean) {
        val deadline = TimeSource.Monotonic.markNow() + 5.seconds
        while (!condition() && deadline.hasNotPassedNow()) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        assertTrue(condition(), message)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun pushAnimatesNavigationBarHidingAndPopRestoresVisibility() {
        val navigation = RecordingNavigation()
        val stack = StackCoordinator(navigation, ContentLeases())
        val home = entry("home")
        val detail = NavEntry("detail", metadata = nativeScreen(hidesNavigationBar = true)) {}
        stack.update(listOf(home), onBack = {})
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = navigation
        window.makeKeyAndVisible()
        try {
            val homeController = assertNotNull(navigation.topViewController)
            awaitUIKit("홈 화면이 테스트 윈도에 나타나야 합니다.") { homeController.viewIfLoaded?.window == window }
            stack.update(listOf(home, detail), onBack = {})
            val animatingPush = navigation.transitionCoordinator != null
            assertEquals(true, navigation.pushRequestedAnimation, "상세 push는 UIKit에 애니메이션을 요청해야 합니다.")
            awaitUIKit("상세 push가 완료되면 내비게이션 바가 숨겨져야 합니다.") {
                navigation.topViewController != homeController &&
                    navigation.transitionCoordinator == null && navigation.navigationBarHidden
            }
            assertTrue(
                navigation.navigationBarChanges.any { (hidden, animated) -> hidden && (!animatingPush || animated) },
                "UIKit 전환이 시작되면 상단 바도 애니메이션으로 숨겨야 합니다. 호출 기록 ${navigation.navigationBarChanges}",
            )

            stack.update(listOf(home), onBack = {})
            awaitUIKit("pop이 완료되면 홈 화면의 내비게이션 바가 복원되어야 합니다.") {
                navigation.topViewController == homeController &&
                    navigation.transitionCoordinator == null && !navigation.navigationBarHidden
            }
        } finally {
            window.hidden = true
            window.rootViewController = null
            stack.dispose()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun initialTrailingItemsStayUntilPushCompletes() {
        val (stack, navigation) = coordinator()
        val home = entry("home")
        val subscribe = nativeBarTransitionPlaceholder("구독", systemImage = "bell")
        val detail = NavEntry("detail", metadata = nativeScreen(initialTrailing = listOf(subscribe))) {}
        stack.update(listOf(home), onBack = {})
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = navigation
        window.makeKeyAndVisible()
        try {
            val homeController = assertNotNull(navigation.topViewController)
            awaitUIKit("홈 화면이 테스트 윈도에 나타나야 합니다.") { homeController.viewIfLoaded?.window == window }
            stack.update(listOf(home, detail), onBack = {})
            val screen = assertIs<ComposeScreenController>(navigation.topViewController)
            fun labels() = screen.navigationItem.rightBarButtonItems.orEmpty()
                .filterIsInstance<UIBarButtonItem>().map { it.accessibilityLabel }

            assertEquals(listOf("구독"), labels(), "상세 화면을 열면 초기 버튼이 먼저 표시되어야 합니다.")
            val initialButton = assertIs<UIBarButtonItem>(screen.navigationItem.rightBarButtonItems?.single())
            assertFalse(initialButton.enabled, "초기 버튼은 비활성 상태여야 합니다.")
            screen.bar.apply(NativeBarContent(null, emptyList(), listOf(subscribe, NativeBarButton("수정")), emptyList()))
            screen.bar.apply(NativeBarContent(null, emptyList(), listOf(subscribe, NativeBarButton("신고")), emptyList()))
            if (navigation.transitionCoordinator != null) {
                assertEquals(listOf("구독"), labels(), "전환이 진행되는 동안 초기 버튼이 유지되어야 합니다.")
            }

            awaitUIKit("전환 완료 후 마지막으로 선언한 버튼이 표시되어야 합니다.") {
                navigation.transitionCoordinator == null && labels() == listOf("신고", "구독")
            }
            assertEquals(initialButton, screen.navigationItem.rightBarButtonItems?.last(), "동일한 초기 버튼은 재사용되어야 합니다.")
        } finally {
            window.hidden = true
            window.rootViewController = null
            stack.dispose()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun unseededActionsWaitForPushAndNewActionsStartDisabled() {
        val (stack, navigation) = coordinator()
        val home = entry("home")
        stack.update(listOf(home), onBack = {})
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = navigation
        window.makeKeyAndVisible()
        try {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.1))
            stack.update(listOf(home, entry("detail")), onBack = {})
            assertNotNull(navigation.transitionCoordinator)
            val screen = assertIs<ComposeScreenController>(navigation.topViewController)
            val item = screen.navigationItem
            val report = NativeBarButton("신고")
            screen.bar.apply(NativeBarContent(null, emptyList(), listOf(report), emptyList()))
            assertTrue(item.rightBarButtonItems.isNullOrEmpty())

            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(1.1))
            assertTrue(assertIs<UIBarButtonItem>(item.rightBarButtonItems?.single()).enabled)

            screen.bar.apply(NativeBarContent(null, emptyList(), listOf(report, NativeBarButton("차단")), emptyList()))
            val block = assertIs<UIBarButtonItem>(item.rightBarButtonItems?.first())
            assertEquals("차단", block.accessibilityLabel)
            assertFalse(block.enabled)
            if (!UIAccessibilityIsReduceMotionEnabled()) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
                assertFalse(block.enabled, "새 액션은 페이드 중에 활성화되지 않아야 합니다.")
            }
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.4))
            assertTrue(block.enabled)
        } finally {
            window.hidden = true
            window.rootViewController = null
            stack.dispose()
        }
    }

    @Test
    fun sharedPushLeavesLargeTitleTransitionIndependentOfTabBarPolicy() {
        val navigation = RecordingNavigation()
        val stack = StackCoordinator(navigation, ContentLeases())
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.show(own = listOf("home"), shared = emptyList())
        stack.show(own = listOf("home"), shared = listOf("profile"))
        navigation.topViewController!!.viewWillAppear(false)

        assertEquals(false, navigation.pushedWithHiddenBar, "UIKit이 push 중 큰 제목을 먼저 접는 정책을 설정하지 않습니다.")
        assertFalse(navigation.topViewController!!.hidesBottomBarWhenPushed)
        assertTrue(tabs.tabBarHidden, "탭 바는 UITabBarController에서 숨긴다")
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        navigation.topViewController!!.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden, "루트로 돌아오면 탭 바를 복원한다")
        stack.dispose()
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun sharedPushHidesTabBarAfterTheAnimatedTransition() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.update(
            listOf(NavEntry("home", metadata = nativeScreen(title = "Home", largeTitle = true)) {}),
            onBack = {},
        )
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = tabs
        window.makeKeyAndVisible()
        try {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.10))
            stack.update(
                listOf(NavEntry("home", metadata = nativeScreen(title = "Home", largeTitle = true)) {}),
                onBack = {},
                shared = listOf(entry("detail")),
            )
            assertNotNull(navigation.transitionCoordinator, "화면에 연결된 UIKit push가 시작되어야 합니다.")
            assertFalse(tabs.tabBarHidden, "나가는 큰 제목이 움직이는 동안 탭 바 여백을 유지해야 합니다.")
            repeat(20) {
                if (!tabs.tabBarHidden) {
                    NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
                }
            }
            assertTrue(tabs.tabBarHidden, "push 완료 후 상세 화면에서 탭 바가 숨겨져야 합니다.")
        } finally {
            window.hidden = true
            window.rootViewController = null
            stack.dispose()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun sharedPopRevealsTheTabBarWithoutASeparateUIKitAnimation() {
        val (stack, navigation) = coordinator()
        val tabs = RecordingTabs()
        tabs.setViewControllers(listOf(navigation), animated = false)
        val home = entry("home")
        stack.update(listOf(home), onBack = {}, shared = listOf(entry("settings")), animated = false)
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = tabs
        window.makeKeyAndVisible()
        try {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.1))
            assertTrue(tabs.tabBarHidden)
            tabs.tabBar.alpha = 0.0
            tabs.changes.clear()
            stack.update(listOf(home), onBack = {})
            assertNotNull(navigation.transitionCoordinator)
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.7))
            assertFalse(tabs.tabBarHidden)
            assertTrue(tabs.changes.contains(false to false))
            assertFalse(tabs.changes.contains(false to true))
            assertEquals(1.0, tabs.tabBar.alpha)
        } finally {
            window.hidden = true
            window.rootViewController = null
            stack.dispose()
        }
    }

    @Test
    fun appearingScreenRestoresTabVisibilityWhenInteractivePopIsCancelled() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.show(own = listOf("home"), shared = listOf("profile"))
        val root = navigation.screens.first()
        val detail = navigation.screens.last()
        detail.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        root.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden)
        detail.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden, "취소되면 다시 나타나는 상세 화면의 숨김 상태로 돌아간다")
        stack.dispose()
    }

    @Test
    fun screensAboveAHiddenEntryKeepTheTabBarHidden() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.update(listOf(entry("home"), entry("hidden", hidesTabBar = true), entry("detail")), {}, animated = false)
        navigation.topViewController!!.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        navigation.topViewController!!.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        navigation.popViewControllerAnimated(false)
        navigation.reportShown()
        navigation.topViewController!!.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden)
        stack.dispose()
    }

    @Test
    fun changingTheVisibleEntryMetadataUpdatesTabVisibility() {
        val (stack, navigation) = coordinator()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(navigation), animated = false)
        stack.update(listOf(entry("home"), entry("detail")), {}, animated = false)
        navigation.topViewController!!.viewWillAppear(false)
        assertFalse(tabs.tabBarHidden)
        stack.update(listOf(entry("home"), entry("detail", hidesTabBar = true)), {}, animated = false)
        assertTrue(tabs.tabBarHidden)
        stack.update(listOf(entry("home"), entry("detail")), {}, animated = false)
        assertFalse(tabs.tabBarHidden)
        stack.dispose()
    }

    @Test
    fun unselectedStackDoesNotChangeTheVisibleTabBar() {
        val (stack, navigation) = coordinator()
        val other = UINavigationController()
        val tabs = UITabBarController()
        tabs.setViewControllers(listOf(other, navigation), animated = false)
        stack.update(listOf(entry("home"), entry("detail")), {}, animated = false)
        stack.update(listOf(entry("home"), entry("detail", hidesTabBar = true)), {}, animated = false)
        assertFalse(tabs.tabBarHidden)
        tabs.selectedIndex = 1u
        navigation.topViewController!!.viewWillAppear(false)
        assertTrue(tabs.tabBarHidden)
        stack.dispose()
    }

}
