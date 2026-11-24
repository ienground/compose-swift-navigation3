package zone.ien.navite.example

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import zone.ien.navite.NativeNavigationFrame
import zone.ien.navite.NativeScrollContent
import zone.ien.navite.NativeTab
import zone.ien.navite.NativeTabs
import zone.ien.navite.nativeBarTransitionPlaceholder
import zone.ien.navite.nativeScreen

/** Material 3 + Navigation 3 앱에서 네이티브 바, 스크롤, 화면 전환을 보여주는 예제. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val home = rememberNavBackStack(navigationConfiguration, HomeRoute)
    val settings = rememberNavBackStack(navigationConfiguration, SettingsRoute)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingUpdates by rememberSaveable { mutableIntStateOf(0) }
    val stacks = listOf(home, settings)
    val openDetail: (Int) -> Unit = { id -> stacks[selectedTab].add(DetailRoute(id)) }

    // 추가: iOS의 각 화면에도 기존 앱 테마와 스크롤 바운스를 적용한다.
    NativeNavigationFrame(frame = { content ->
        val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = colorScheme) {
            Surface { NativeScrollContent { content() } }
        }
    }) {
        // 추가: iOS는 네이티브 탭·스택을 사용하고, 나머지 플랫폼은 아래 Material UI를 사용한다.
        NativeTabs(
            tabs = listOf(
                NativeTab("Home", "house", home),
                NativeTab("Settings", "gearshape", settings, image = null, badgeCount = pendingUpdates),
            ),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            entryProvider = entryProvider {
                // 추가: UIKit이 push 전에 제목을 알 수 있도록 엔트리에 metadata를 선언한다.
                entry<HomeRoute>(metadata = nativeScreen(title = "Home", largeTitle = true)) {
                    HomeScreen(onOpenDetail = openDetail, onRefreshCompleted = { pendingUpdates++ })
                }
                entry<SettingsRoute>(metadata = nativeScreen(title = "Settings", largeTitle = true)) {
                    SettingsScreen(
                        pendingUpdates = pendingUpdates,
                        onClearUpdates = { pendingUpdates = 0 },
                        onOpenDetail = { openDetail(42) },
                        onOpenPreview = { settings.add(DetailRoute(id = 42, preview = true)) },
                    )
                }
                entry<DetailRoute>(metadata = { route ->
                    if (route.preview) {
                        nativeScreen(title = "미리보기", hidesNavigationBar = true)
                    } else {
                        nativeScreen(
                            title = "Detail ${route.id}",
                            initialTrailing = listOf(
                                nativeBarTransitionPlaceholder(
                                    title = favoriteActionTitle,
                                    systemImage = "star",
                                ),
                            ),
                        ) + nativeScreen(hidesTabBar = route.id == tabBarHiddenDetailId)
                    }
                }) { route ->
                    if (route.preview) {
                        PreviewScreen(id = route.id, onBack = { settings.removeLastOrNull() })
                    } else {
                        DetailScreen(id = route.id)
                    }
                }
            },
        ) {
            // 기존 Material 3 + NavDisplay 구성. iOS에서는 이 블록을 실행하지 않는다.
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = title.orEmpty()) },
                        navigationIcon = {
                            if (canGoBack) TextButton(onClick = onBack) { Text(text = "Back") }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { onTabSelected(index) },
                                icon = {
                                    BadgedBox(badge = {
                                        tab.badgeCount?.takeIf { it > 0 }?.let { count ->
                                            Badge { Text(text = count.toString()) }
                                        }
                                    }) {
                                        Text(text = tab.title)
                                    }
                                },
                            )
                        }
                    }
                },
            ) { padding ->
                NavDisplay(entries = entries, onBack = onBack, modifier = Modifier.padding(padding))
            }
        }
    }
}
