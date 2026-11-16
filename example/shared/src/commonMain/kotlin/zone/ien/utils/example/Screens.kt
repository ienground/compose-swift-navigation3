package zone.ien.utils.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.jetbrains.compose.resources.painterResource
import zone.ien.utils.example.resources.Res
import zone.ien.utils.example.resources.example_bar_icon
import zone.ien.utils.example.resources.compose_multiplatform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.lifecycle.viewmodel.compose.viewModel
import zone.ien.navigation.NativeBarButton
import zone.ien.navigation.NativeBarItems
import zone.ien.navigation.NativeMenuAction
import zone.ien.navigation.NativeSearchBar
import zone.ien.navigation.nativeContentPadding
import zone.ien.navigation.nativeScrollExclusion
import zone.ien.navigation.rememberNativeScroll

/** Home: a LazyColumn connected to the native navigation bar; rows open details. */
@Composable
fun HomeScreen(navigation: AppNavigation) {
    LogComposition("Home")
    val listState = rememberLazyListState()
    val nativeScroll = rememberNativeScroll(listState, background = MaterialTheme.colorScheme.background)
    NativeBarItems(
        trailing = listOf(NativeBarButton("Open 42", systemImage = "42.circle", image = painterResource(Res.drawable.example_bar_icon)) { navigation.open(DetailRoute(42)) }),
    )
    // Native search field (UISearchController on iOS); the screen filters its own rows.
    var query by rememberSaveable { mutableStateOf("") }
    NativeSearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search rows")
    val rows = (0 until 100).filter { query.isBlank() || "Row $it".contains(query.trim(), ignoreCase = true) }

    LazyColumn(
        state = listState,
        userScrollEnabled = nativeScroll.userScrollEnabled,
        contentPadding = nativeScroll.contentPadding,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item {
            LazyRow(Modifier.fillMaxWidth().height(80.dp)) {
                items(20) { index ->
                    Text(
                        text = "Card $index",
                        modifier = Modifier
                            .padding(8.dp)
                            .width(120.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFC8E6C9))
                            .padding(8.dp),
                    )
                }
            }
        }
        item {
            Row {
                Button(
                    onClick = { navigation.deepLink(0, DetailRoute(3), DetailRoute(7)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Deep link 3 > 7") }
                Button(
                    onClick = { navigation.openShared(ProfileRoute) },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text("Open profile") }
            }
        }
        items(rows, key = { it }) { index ->
            Text(
                text = "Row $index",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navigation.open(DetailRoute(index)) }
                    .background(if (index % 2 == 0) Color(0xFFE3F2FD) else Color(0xFFFFF3E0))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

class DetailViewModel(private val id: Int) : ViewModel() {
    var count by mutableIntStateOf(0)
        private set

    init {
        appLog("VM created Detail $id")
    }

    fun increment() {
        count++
    }

    fun decrement() {
        if (count > 0) count--
    }

    fun reset() {
        count = 0
    }

    override fun onCleared() {
        appLog("VM cleared Detail $id")
    }
}

/** Detail: a verticalScroll (ScrollState) connected to the native bar, with navigation actions. */
@Composable
fun DetailScreen(id: Int, navigation: AppNavigation) {
    LogComposition("Detail $id")
    val viewModel = viewModel { DetailViewModel(id) }
    val scrollState = rememberScrollState()
    val nativeScroll = rememberNativeScroll(scrollState, background = MaterialTheme.colorScheme.background)

    val addImage = rememberVectorPainter(AddImage)

    // Native bar items follow the ViewModel: dynamic title, menu, toolbar with a disabled state.
    NativeBarItems(
        title = if (viewModel.count > 0) "Detail $id (${viewModel.count})" else null,
        trailing = listOf(
            NativeBarButton("Add one", image = addImage, onClick = viewModel::increment),
            NativeBarButton(
                title = "More",
                systemImage = "ellipsis.circle",
                menu = listOf(
                    NativeMenuAction("Open Detail ${id + 1}", systemImage = "arrow.right") {
                        navigation.open(DetailRoute(id + 1))
                    },
                    NativeMenuAction("Reset count", image = painterResource(Res.drawable.compose_multiplatform), destructive = true) {
                        viewModel.reset()
                    },
                ),
            ),
        ),
        toolbar = listOf(
            NativeBarButton("Decrease", systemImage = "minus.circle", enabled = viewModel.count > 0, onClick = viewModel::decrement),
            NativeBarButton("Increase", image = addImage, onClick = viewModel::increment),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState, enabled = nativeScroll.userScrollEnabled)
            .padding(nativeScroll.contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Detail $id", style = MaterialTheme.typography.headlineSmall)
        Text("Count: ${viewModel.count}")
        Button(onClick = viewModel::increment) { Text("Increment") }
        Button(onClick = { navigation.open(DetailRoute(id + 1)) }) { Text("Open Detail ${id + 1}") }
        Button(onClick = { navigation.open(DetailRoute(id)) }) { Text("Open same Detail") }
        Button(onClick = { navigation.replaceTop(DetailRoute(id + 100)) }) { Text("Replace with Detail ${id + 100}") }
        Button(onClick = navigation::popToRoot) { Text("Pop to root") }

        // A back handler in the screen intercepts back (native back button and edge swipe on
        // iOS, system back on Android) while it is enabled.
        var guardBack by rememberSaveable { mutableStateOf(false) }
        var intercepted by rememberSaveable { mutableIntStateOf(0) }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = guardBack,
            onBackCompleted = { intercepted++ },
        )
        Button(onClick = { guardBack = !guardBack }) { Text(if (guardBack) "Guard back: on" else "Guard back: off") }
        Text("Back intercepted: $intercepted")

        // A handler enabled only for forward navigation (NavigationEventHandler's default
        // isForwardEnabled = true, as in a dirty-check guard that is currently off) does not
        // take back: the back button and the edge swipe still pop natively.
        NavigationEventHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = false,
        )

        // Vertical drags inside the pad go to Compose, not to the native page scroll.
        var pad by rememberSaveable { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(Color(0xFFFFE0B2))
                .nativeScrollExclusion()
                .draggable(rememberDraggableState { delta -> pad += delta }, Orientation.Vertical),
            contentAlignment = Alignment.Center,
        ) { Text("Drag pad: ${pad.roundToInt()}") }
        repeat(30) { line -> Text("Line $line of detail $id") }
    }
}

/** Gallery: a LazyVerticalGrid or LazyVerticalStaggeredGrid connected to the native bar. */
@Composable
fun GalleryScreen(navigation: AppNavigation) {
    LogComposition("Gallery")
    var staggered by rememberSaveable { mutableStateOf(false) }
    NativeBarItems(
        trailing = listOf(
            NativeBarButton(
                title = if (staggered) "Grid" else "Staggered",
                systemImage = if (staggered) "square.grid.3x3" else "rectangle.3.group",
            ) { staggered = !staggered },
        ),
    )
    val background = MaterialTheme.colorScheme.background
    val modifier = Modifier.fillMaxSize().background(background)

    if (staggered) {
        val state = rememberLazyStaggeredGridState()
        val nativeScroll = rememberNativeScroll(state, background)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = state,
            userScrollEnabled = nativeScroll.userScrollEnabled,
            contentPadding = nativeScroll.contentPadding + PaddingValues(8.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
        ) {
            items(60, key = { it }) { index ->
                Tile("Tile $index", height = (80 + index * 37 % 120).dp) { navigation.open(DetailRoute(index)) }
            }
        }
    } else {
        val state = rememberLazyGridState()
        val nativeScroll = rememberNativeScroll(state, background)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = state,
            userScrollEnabled = nativeScroll.userScrollEnabled,
            contentPadding = nativeScroll.contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
        ) {
            items(90, key = { it }) { index ->
                Tile("Cell $index", height = 100.dp) { navigation.open(DetailRoute(index)) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, height: Dp, onClick: () -> Unit) {
    val hue = label.hashCode().mod(360).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.hsv(hue, 0.25f, 0.95f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label) }
}

/** Settings: does not scroll, so it only applies the native chrome padding. */
@Composable
fun SettingsScreen(navigation: AppNavigation) {
    LogComposition("Settings")
    var taps by rememberSaveable { mutableIntStateOf(0) }
    // A leading bar item on a root screen that changes with the state: when the left items are
    // set again while the screen's back action is set, UIKit shows a back button even on the root
    // (see the root guard below).
    NativeBarItems(leading = listOf(NativeBarButton("Taps: $taps") { taps++ }))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(nativeContentPadding())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings taps: $taps")
        Button(onClick = { taps++ }) { Text("Tap settings") }
        // A back handler on a tab's root screen (e.g. an open dialog's) does not add a back
        // button to it on iOS. On Android it keeps system back from leaving the app while on.
        var guardRoot by rememberSaveable { mutableStateOf(false) }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = guardRoot,
            onBackCompleted = {},
        )
        Button(onClick = { guardRoot = !guardRoot }) { Text(if (guardRoot) "Root guard: on" else "Root guard: off") }
        Button(onClick = { navigation.openShared(ProfileRoute) }) { Text("Open profile from settings") }
        // Replaces the tabs with the sign-in stack (on iOS: the root tab bar controller is swapped).
        Button(onClick = navigation::signOut) { Text("Sign out") }
    }
}

/**
 * A shared screen: any tab opens it over itself (NativeTabs' shared back stack). The tab bar is
 * hidden while it is shown; on iOS it is pushed on the selected tab's navigation controller.
 */
@Composable
fun ProfileScreen(navigation: AppNavigation) {
    LogComposition("Profile")
    var edits by rememberSaveable { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(nativeContentPadding())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Profile edits: $edits")
        Button(onClick = { edits++ }) { Text("Edit profile") }
        // Pushes on the shared stack too (AppNavigation.open uses it while a shared screen is shown).
        Button(onClick = { navigation.open(DetailRoute(50)) }) { Text("Open Detail 50") }
        // The app selects another tab: the shared screens move to it.
        Button(onClick = { navigation.selectedTab = 1 }) { Text("Switch to Gallery") }
        Button(onClick = navigation::closeShared) { Text("Close profile") }
    }
}

/** Root of the signed-out flow: a plain stack without tabs. */
@Composable
fun SignInScreen(navigation: AppNavigation) {
    LogComposition("SignIn")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(nativeContentPadding())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("You are signed out.")
        Button(onClick = navigation::signIn) { Text("Sign in") }
        Button(onClick = { navigation.open(SignInHelpRoute) }) { Text("Sign-in help") }
    }
}

/** Pushed inside the sign-in stack; signing in from here also switches to the tabs. */
@Composable
fun SignInHelpScreen(navigation: AppNavigation) {
    LogComposition("SignInHelp")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(nativeContentPadding())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Any account works in this example.")
        Button(onClick = navigation::signIn) { Text("Sign in from help") }
    }
}

@Composable
private fun LogComposition(name: String) {
    DisposableEffect(name) {
        appLog("COMPOSE $name")
        onDispose { appLog("DISPOSE $name") }
    }
}

internal operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val first = this
    return object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
            first.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)

        override fun calculateTopPadding(): Dp =
            first.calculateTopPadding() + other.calculateTopPadding()

        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
            first.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)

        override fun calculateBottomPadding(): Dp =
            first.calculateBottomPadding() + other.calculateBottomPadding()
    }
}

private val AddImage = ImageVector.Builder(
    name = "Add",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(11f, 3f)
        lineTo(13f, 3f)
        lineTo(13f, 11f)
        lineTo(21f, 11f)
        lineTo(21f, 13f)
        lineTo(13f, 13f)
        lineTo(13f, 21f)
        lineTo(11f, 21f)
        lineTo(11f, 13f)
        lineTo(3f, 13f)
        lineTo(3f, 11f)
        lineTo(11f, 11f)
        close()
    }
}.build()
