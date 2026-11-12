package zone.ien.utils.example

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import zone.ien.navigation.nativeHidesTabBar
import zone.ien.navigation.nativeTitle

/**
 * Android / desktop entry point: plain Navigation 3 (`NavDisplay`) with Material 3 chrome drawn
 * by the app. The routes, back stacks and screens are the same ones iOS shows natively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        val navigation = rememberAppNavigation()
        val entryProvider = remember(navigation) { appEntryProvider(navigation) }
        val stack = navigation.currentStack
        // The same nativeScreen metadata drives the Material chrome here.
        val top = entryProvider(stack.last())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(top.nativeTitle.orEmpty()) },
                    navigationIcon = {
                        if (stack.size > 1) TextButton(onClick = navigation::goBack) { Text("Back") }
                    },
                )
            },
            bottomBar = {
                if (!top.nativeHidesTabBar) {
                    NavigationBar {
                        listOf("Home", "Settings").forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = navigation.selectedTab == index,
                                onClick = { navigation.selectedTab = index },
                                icon = { Text(label.take(1)) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                NavDisplay(
                    backStack = stack,
                    onBack = navigation::goBack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider,
                )
            }
        }
    }
}
