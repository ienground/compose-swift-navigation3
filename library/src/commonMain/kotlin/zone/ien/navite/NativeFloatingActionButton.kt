package zone.ien.navite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

/**
 * 스캐폴드의 플로팅 액션 슬롯에 현재 화면의 네이티브 버튼을 표시합니다.
 * 네이티브 화면을 지원하지 않는 플랫폼에서는 호출자가 대체 UI를 제공할 수 있습니다.
 */
@Composable
public fun NativeFloatingActionButton(
    label: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    LocalNativeFloatingActionButtonHost.current?.Button(label, icon, modifier, enabled, tint, onClick)
}

/** 현재 슬롯에서 네이티브 FAB를 사용할 수 있는지 나타냅니다. */
public val isNativeFloatingActionButtonAvailable: Boolean
    @Composable get() = LocalNativeFloatingActionButtonHost.current != null

internal val LocalNativeFloatingActionButtonHost =
    staticCompositionLocalOf<NativeFloatingActionButtonHost?> { null }

internal interface NativeFloatingActionButtonHost {
    @Composable
    fun Button(
        label: String,
        icon: Painter,
        modifier: Modifier,
        enabled: Boolean,
        tint: Color,
        onClick: () -> Unit,
    )
}
