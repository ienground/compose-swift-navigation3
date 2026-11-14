package zone.ien.utils.example

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.Foundation.NSData
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
import zone.ien.navigation.saveNativeNavigationState

/**
 * iOS entry point: the shared [App] in native UIKit tabs and navigation stacks.
 *
 * [restoredState] is what [saveNavigationState] returned before the app was terminated in the
 * background (the Swift side keeps it in `@SceneStorage`).
 *
 * Launch arguments (UI tests): `-traceNav` logs the Kotlin back stacks and the native stack to
 * the system log; `-freshStart` ignores [restoredState]; `-demoDeepLink` starts on
 * Home > Detail 3 > Detail 7 when nothing is restored.
 */
fun MainViewController(restoredState: NSData?): UIViewController {
    val arguments = NSProcessInfo.processInfo.arguments
    val trace = arguments.contains("-traceNav")
    if (trace) appLog = ::demoLog
    val restored = restoredState.takeUnless { arguments.contains("-freshStart") }
    if (trace) demoLog("RESTORE ${if (restored != null) "${restored.length} bytes" else "none"}")
    lateinit var root: UIViewController

    // Version 1: the sign-in flow added saved values to rememberAppNavigation().
    // Version 2: the navigation is declared inside the shared App(), so saved values sit at other
    // call positions. State saved by an older version is dropped.
    root = NativeNavigationViewController(restored, stateVersion = 2) {
        // The same App() as Android: here it declares the native controllers and emits no UI.
        val navigation = rememberAppNavigation()
        App(navigation)

        if (restored == null && arguments.contains("-demoDeepLink")) {
            // Demo/test hook: start with a multi-level stack (Home > Detail 3 > Detail 7).
            LaunchedEffect(navigation) { navigation.deepLink(0, DetailRoute(3), DetailRoute(7)) }
        }

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

/** Called by the Swift side when the scene goes to the background. */
fun saveNavigationState(controller: UIViewController): NSData? =
    saveNativeNavigationState(controller).also { appLog("SAVE ${it?.length ?: 0} bytes") }

@OptIn(ExperimentalForeignApi::class)
private fun describeNative(root: UIViewController): String {
    val child = root.childViewControllers.firstOrNull()
    val tabs = child as? UITabBarController
    val navigation = (tabs?.selectedViewController ?: child) as? UINavigationController ?: return "no stack"
    val titles = navigation.viewControllers.joinToString(",") {
        (it as UIViewController).navigationItem.title ?: "?"
    }
    val bar = navigation.navigationBar.frame.useContents { "${origin.y}+${size.height}" }
    val tabBar = if (tabs == null) "none" else {
        "tab=${tabs.selectedIndex} tabBarHidden=${tabs.tabBar.hidden || tabs.tabBar.frame.useContents { origin.y } >= tabs.view.frame.useContents { size.height }}"
    }
    return "stack=[$titles] bar=$bar toolbarHidden=${navigation.toolbarHidden} tabs=$tabBar"
}

private fun demoLog(message: String) {
    val millis = (NSDate().timeIntervalSince1970 * 1000).toLong()
    NSLog("[ScrollDemo $millis] $message".replace("%", "%%"))
}
