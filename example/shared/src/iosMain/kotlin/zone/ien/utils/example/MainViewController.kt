package zone.ien.utils.example

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.childViewControllers
import platform.UIKit.navigationItem
import zone.ien.navigation.NativeNavigationViewController
import zone.ien.navigation.NativeTab
import zone.ien.navigation.NativeTabs

/**
 * iOS entry point: the shared routes and screens in native UIKit tabs and navigation stacks.
 *
 * Launch argument `-traceNav` logs the Kotlin back stacks and the native stack to the system
 * log (used by UI tests).
 */
fun MainViewController(): UIViewController {
    val trace = NSProcessInfo.processInfo.arguments.contains("-traceNav")
    if (trace) appLog = ::demoLog
    lateinit var root: UIViewController

    root = NativeNavigationViewController {
        val navigation = rememberAppNavigation()
        val entryProvider = remember(navigation) { appEntryProvider(navigation) }

        NativeTabs(
            tabs = listOf(
                NativeTab("Home", "house", navigation.stacks[0]),
                NativeTab("Settings", "gearshape", navigation.stacks[1]),
            ),
            selectedTab = navigation.selectedTab,
            onTabSelected = { navigation.selectedTab = it },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider,
        )

        if (trace) {
            LaunchedEffect(navigation) {
                snapshotFlow { navigation.describe() }.collect { demoLog("KOTLIN $it") }
            }
            LaunchedEffect(Unit) {
                var last = ""
                while (true) {
                    val current = describeNative(root)
                    if (current != last) demoLog("NATIVE $current")
                    last = current
                    delay(100)
                }
            }
        }
    }
    return root
}

@OptIn(ExperimentalForeignApi::class)
private fun describeNative(root: UIViewController): String {
    val tabs = root.childViewControllers.firstOrNull() as? UITabBarController ?: return "no tabs"
    val navigation = tabs.selectedViewController as? UINavigationController ?: return "no stack"
    val titles = navigation.viewControllers.joinToString(",") {
        (it as UIViewController).navigationItem.title ?: "?"
    }
    val bar = navigation.navigationBar.frame.useContents { "${origin.y}+${size.height}" }
    return "tab=${tabs.selectedIndex} stack=[$titles] bar=$bar tabBarHidden=${tabs.tabBar.hidden || tabs.tabBar.frame.useContents { origin.y } >= tabs.view.frame.useContents { size.height }}"
}

private fun demoLog(message: String) {
    val millis = (NSDate().timeIntervalSince1970 * 1000).toLong()
    NSLog("[ScrollDemo $millis] $message".replace("%", "%%"))
}
