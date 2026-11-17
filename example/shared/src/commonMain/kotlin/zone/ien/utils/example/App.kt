package zone.ien.utils.example

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import zone.ien.navigation.NativeNavigationFrame
import zone.ien.navigation.NativeScrollContent
import zone.ien.navigation.NativeTab
import zone.ien.navigation.NativeTabs
import zone.ien.navigation.nativeScreen

/** Material 3 + Navigation 3 앱에 네이티브 내비게이션을 연결하는 최소 예제. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val home = rememberNavBackStack(navigationConfiguration, HomeRoute)
    val settings = rememberNavBackStack(navigationConfiguration, SettingsRoute)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val stacks = listOf(home, settings)
    val openDetail: (Int) -> Unit = { id -> stacks[selectedTab].add(DetailRoute(id)) }

    // 추가: iOS의 각 화면에도 기존 앱 테마와 스크롤 바운스를 적용한다.
    NativeNavigationFrame(frame = { content ->
        MaterialTheme { NativeScrollContent { content() } }
    }) {
        // 추가: iOS는 네이티브 탭·스택을 사용하고, 나머지 플랫폼은 아래 Material UI를 사용한다.
        NativeTabs<NavKey>(
            tabs = listOf(
                NativeTab("Home", "house", home),
                NativeTab("Settings", "gearshape", settings),
            ),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            entryProvider = entryProvider {
                // 추가: UIKit이 push 전에 제목을 알 수 있도록 엔트리에 metadata를 선언한다.
                entry<HomeRoute>(metadata = nativeScreen(title = "Home", largeTitle = true)) {
                    HomeScreen(onOpenDetail = openDetail)
                }
                entry<SettingsRoute>(metadata = nativeScreen(title = "Settings", largeTitle = true)) {
                    SettingsScreen(onOpenDetail = { openDetail(42) })
                }
                entry<DetailRoute>(metadata = { route -> nativeScreen(title = "Detail ${route.id}") }) { route ->
                    DetailScreen(id = route.id)
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
                                selected = this@NativeTabs.selectedTab == index,
                                onClick = { onTabSelected(index) },
                                icon = { Text(text = tab.title) },
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
