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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBarItem
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
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import zone.ien.navigation.NativeBarButton
import zone.ien.navigation.NativeBarState
import zone.ien.navigation.ProvideNativeBarState
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
        // The same nativeScreen metadata and NativeBarItems drive the Material chrome here.
        val top = entryProvider(stack.last())
        val bar = remember { NativeBarState() }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(bar.title ?: top.nativeTitle.orEmpty()) },
                        navigationIcon = {
                            if (stack.size > 1) TextButton(onClick = navigation::goBack) { Text("Back") }
                        },
                        actions = { bar.trailing.forEach { BarActionButton(it) } },
                    )
                    bar.search?.let { search ->
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
                    if (bar.toolbar.isNotEmpty()) {
                        Surface(tonalElevation = 3.dp) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                bar.toolbar.forEach { BarActionButton(it) }
                            }
                        }
                    }
                    // The signed-out flow (navigation.signInStack) has no tabs.
                    if (navigation.signedIn && !top.nativeHidesTabBar) NavigationBar {
                        listOf("Home", "Gallery", "Settings").forEachIndexed { index, label ->
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
                ProvideNativeBarState(bar) {
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
