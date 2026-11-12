package zone.ien.utils.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import zone.ien.navigation.nativeContentPadding
import zone.ien.navigation.rememberNativeScroll

/** Home: a LazyColumn connected to the native navigation bar; rows open details. */
@Composable
fun HomeScreen(navigation: AppNavigation) {
    LogComposition("Home")
    val listState = rememberLazyListState()
    val nativeScroll = rememberNativeScroll(listState)

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
            Button(
                onClick = { navigation.deepLink(0, DetailRoute(3), DetailRoute(7)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Deep link 3 > 7") }
        }
        items(100) { index ->
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
    val nativeScroll = rememberNativeScroll(scrollState)

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
        repeat(30) { line -> Text("Line $line of detail $id") }
    }
}

/** Settings: does not scroll, so it only applies the native chrome padding. */
@Composable
fun SettingsScreen() {
    LogComposition("Settings")
    var taps by rememberSaveable { mutableIntStateOf(0) }
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
