package zone.ien.utils.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import zone.ien.navigation.NativeBarButton
import zone.ien.navigation.NativeNavigationScope
import zone.ien.navigation.NativeStack
import zone.ien.navigation.NativeTab
import zone.ien.navigation.NativeTabs

/**
 * The app, shared by every platform: the same back stacks, screens and bar declarations.
 *
 * - iOS (`MainViewController`): native `UITabBarController` / `UINavigationController`. The
 *   `otherPlatforms` chrome below is never called and this function emits no UI.
 * - Android and desktop (`setContent { App() }`): [MaterialChrome] draws what the screens declared
 *   with Material 3 around `NavDisplay`.
 */
@Composable
fun App(navigation: AppNavigation = rememberAppNavigation()) {
    val entryProvider = remember(navigation) { appEntryProvider(navigation) }

    // Signed in: tabs. Signed out: a plain stack. The decorators belong to each branch, so the
    // state of the branch that leaves is cleared (as when a NavDisplay leaves composition); on iOS
    // the switch swaps the root controller with a cross-dissolve.
    if (navigation.signedIn) {
        NativeTabs(
            tabs = listOf(
                NativeTab("Home", "house", navigation.stacks[0]),
                NativeTab("Gallery", "square.grid.2x2", navigation.stacks[1]),
                NativeTab("Settings", "gearshape", navigation.stacks[2]),
            ),
            selectedTab = navigation.selectedTab,
            onTabSelected = { navigation.selectedTab = it },
            entryDecorators = rememberAppEntryDecorators(),
            entryProvider = entryProvider,
        ) { MaterialChrome() }
    } else {
        NativeStack(
            backStack = navigation.signInStack,
            entryDecorators = rememberAppEntryDecorators(),
            entryProvider = entryProvider,
        ) { MaterialChrome() }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

/**
 * The theme is applied per entry: on iOS every screen is its own composition, so a theme around
 * the navigation would not reach it. Elsewhere `NavDisplay` runs the same decorator.
 */
@Composable
private fun rememberAppEntryDecorators(): List<NavEntryDecorator<NavKey>> = listOf(
    remember { NavEntryDecorator<NavKey> { entry -> AppTheme { entry.Content() } } },
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
)

/**
 * Android / desktop chrome: Material 3 drawn from what the screens declared (`nativeScreen`
 * metadata, `NativeBarItems`, `NativeSearchBar`) and from the tabs. The library draws no Android
 * UI; iOS shows the same declarations in the native bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeNavigationScope<NavKey>.MaterialChrome() {
    AppTheme {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(title.orEmpty()) },
                        navigationIcon = {
                            Row {
                                if (canGoBack) TextButton(onClick = onBack) { Text("Back") }
                                leading.forEach { BarActionButton(it) }
                            }
                        },
                        actions = { trailing.forEach { BarActionButton(it) } },
                    )
                    search?.let { search ->
                        OutlinedTextField(
                            value = search.query,
                            onValueChange = search.onQueryChange,
                            placeholder = { Text(search.placeholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            },
            bottomBar = {
                Column {
                    if (toolbar.isNotEmpty()) {
                        Surface(tonalElevation = 3.dp) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                toolbar.forEach { BarActionButton(it) }
                            }
                        }
                    }
                    if (tabBarVisible) NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { onTabSelected(index) },
                                icon = { Text(tab.title.take(1)) },
                                label = { Text(tab.title) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavDisplay(entries = entries, modifier = Modifier.padding(innerPadding), onBack = onBack)
        }
    }
}

/** Material rendering of a NativeBarButton (the app's choice; the library draws no Android UI). */
@Composable
private fun BarActionButton(button: NativeBarButton) {
    if (button.menu.isEmpty()) {
        TextButton(onClick = button.onClick, enabled = button.enabled) { Text(button.title) }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = button.enabled) { Text(button.title) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            button.menu.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.title) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
