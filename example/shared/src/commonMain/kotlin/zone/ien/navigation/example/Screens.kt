package zone.ien.navigation.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zone.ien.navigation.LocalNativeNavigationBarHidden
import zone.ien.navigation.LocalNativeScreen
import zone.ien.navigation.NativeBarButton
import zone.ien.navigation.NativeBarItems
import zone.ien.navigation.NativeBarLeadingLabel
import zone.ien.navigation.NativeBarSubtitle
import zone.ien.navigation.NativeBarText
import zone.ien.navigation.NativeFloatingActionButton
import zone.ien.navigation.NativePullToRefresh
import zone.ien.navigation.isNativeFloatingActionButtonAvailable
import zone.ien.navigation.nativeContentPadding
import zone.ien.navigation.rememberNativeScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDetail: (Int) -> Unit, onRefreshCompleted: () -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val onRefresh: () -> Unit = {
        if (!isRefreshing) {
            isRefreshing = true
            scope.launch {
                delay(750)
                onRefreshCompleted()
                isRefreshing = false
            }
        }
    }

    NativeBarSubtitle(if (isRefreshing) "새로고침 중" else "당겨서 새로고침")
    val hasNativeRefresh = NativePullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh)
    // 추가: 기존 LazyColumn의 스크롤을 iOS 네이티브 내비게이션 바에 연결한다.
    val scroll = rememberNativeScroll(state, background = MaterialTheme.colorScheme.surface)
    val rows: @Composable () -> Unit = {
        LazyColumn(
            state = state,
            userScrollEnabled = scroll.userScrollEnabled,
            contentPadding = scroll.contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(50) { id ->
                ListItem(
                    headlineContent = {
                        val title = if (id == tabBarHiddenDetailId) "Row $id · iOS 하단 탭 바 숨김" else "Row $id"
                        Text(text = title)
                    },
                    modifier = Modifier.clickable { onOpenDetail(id) },
                )
            }
        }
    }
    if (hasNativeRefresh) {
        rows()
    } else {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) { rows() }
    }
}

@Composable
fun SettingsScreen(
    pendingUpdates: Int,
    onClearUpdates: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    NativeBarItems(
        leading = listOf(
            NativeBarText(
                key = "pending-updates",
                text = "${pendingUpdates}건",
                width = 56f,
                accessibilityLabel = "확인할 업데이트 ${pendingUpdates}건",
            ),
        ),
    )
    Column(
        // 추가: 스크롤이 없는 화면은 네이티브 바의 여백만 적용한다. 다른 플랫폼에서는 0이다.
        modifier = Modifier.fillMaxSize().padding(nativeContentPadding()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings")
        Button(onClick = onOpenDetail) { Text(text = "Open detail") }
        Button(onClick = onOpenPreview) { Text(text = "미리보기 열기") }
        Text(text = "새 업데이트 ${pendingUpdates}건")
        Button(onClick = onClearUpdates, enabled = pendingUpdates > 0) {
            Text(text = "업데이트 모두 확인")
        }
    }
}

@Composable
fun DetailScreen(id: Int) {
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var noteCount by rememberSaveable { mutableIntStateOf(0) }
    val toggleFavorite: () -> Unit = { isFavorite = !isFavorite }
    val actionColor = MaterialTheme.colorScheme.primary
    val floatingActionColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primaryContainer else actionColor
    NativeBarItems(
        trailing = listOf(
            NativeBarButton(
                title = favoriteActionTitle,
                systemImage = if (isFavorite) "star.fill" else "star",
                tintColor = actionColor,
                onClick = toggleFavorite,
            ),
        ),
    )
    NativeBarLeadingLabel(
        text = "메모 $noteCount",
        width = 64f,
        accessibilityLabel = "메모 ${noteCount}개",
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(nativeContentPadding()),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            val icon = rememberVectorPainter(AddIcon)
            if (isNativeFloatingActionButtonAvailable) {
                NativeFloatingActionButton(
                    label = "메모 추가",
                    icon = icon,
                    tint = floatingActionColor,
                    onClick = { noteCount++ },
                )
            } else {
                ExtendedFloatingActionButton(
                    text = { Text(text = "메모 추가") },
                    icon = { Icon(painter = icon, contentDescription = null) },
                    onClick = { noteCount++ },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Selected item: $id")
            Text(text = if (isFavorite) "즐겨찾기에 추가됨" else "즐겨찾기 미등록")
            if (!LocalNativeScreen.current) {
                Button(onClick = toggleFavorite) { Text(text = favoriteActionTitle) }
            }
            Text(text = "메모 수 ${noteCount}개")
        }
    }
}

@Composable
fun PreviewScreen(id: Int, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(nativeContentPadding()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (LocalNativeNavigationBarHidden.current) {
            Text(text = "미리보기", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onBack) { Text(text = "미리보기 닫기") }
        }
        Text(text = "항목 $id 미리보기")
    }
}

internal const val favoriteActionTitle = "즐겨찾기 전환"

private val AddIcon = ImageVector.Builder("Add", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(11f, 5f)
        horizontalLineTo(13f)
        verticalLineTo(11f)
        horizontalLineTo(19f)
        verticalLineTo(13f)
        horizontalLineTo(13f)
        verticalLineTo(19f)
        horizontalLineTo(11f)
        verticalLineTo(13f)
        horizontalLineTo(5f)
        verticalLineTo(11f)
        horizontalLineTo(11f)
        close()
    }
}.build()
