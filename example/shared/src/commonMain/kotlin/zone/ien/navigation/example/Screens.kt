package zone.ien.navigation.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zone.ien.navigation.nativeContentPadding
import zone.ien.navigation.rememberNativeScroll

@Composable
fun HomeScreen(onOpenDetail: (Int) -> Unit) {
    val state = rememberLazyListState()
    // 추가: 기존 LazyColumn의 스크롤을 iOS 네이티브 내비게이션 바에 연결한다.
    val scroll = rememberNativeScroll(state)
    LazyColumn(
        state = state,
        userScrollEnabled = scroll.userScrollEnabled,
        contentPadding = scroll.contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(50) { id ->
            ListItem(
                headlineContent = { Text(text = "Row $id") },
                modifier = Modifier.clickable { onOpenDetail(id) },
            )
        }
    }
}

@Composable
fun SettingsScreen(onOpenDetail: () -> Unit) {
    Column(
        // 추가: 스크롤이 없는 화면은 네이티브 바의 여백만 적용한다. 다른 플랫폼에서는 0이다.
        modifier = Modifier.fillMaxSize().padding(nativeContentPadding()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Settings")
        Button(onClick = onOpenDetail) { Text(text = "Open detail") }
    }
}

@Composable
fun DetailScreen(id: Int) {
    Text(
        text = "Selected item: $id",
        modifier = Modifier.fillMaxSize().padding(nativeContentPadding()).padding(24.dp),
    )
}
