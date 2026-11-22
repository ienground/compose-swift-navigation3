package zone.ien.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSProcessInfo
import platform.UIKit.NSDirectionalEdgeInsetsMake
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIEvent
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIView
import platform.UIKit.accessibilityLabel

@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
internal object UIKitFloatingActionButton : NativeFloatingActionButtonHost {
    @Composable
    override fun Button(
        label: String,
        icon: Painter,
        modifier: Modifier,
        enabled: Boolean,
        tint: Color,
        onClick: () -> Unit,
    ) {
        val currentClick by rememberUpdatedState(onClick)
        val currentEnabled by rememberUpdatedState(enabled)
        val density = LocalDensity.current
        val image = remember(icon, density) {
            renderNativeImage(icon, density, height = 24f, maxWidth = 24f)
                ?.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
        }
        val isGlassAvailable = remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
        val effectPadding = if (isGlassAvailable) GlassEffectPadding else 0.0
        val configuration = remember(label, image, tint, isGlassAvailable) {
            val style = if (isGlassAvailable) UIButtonConfiguration.prominentGlassButtonConfiguration()
            else UIButtonConfiguration.filledButtonConfiguration()
            style.apply {
                title = label
                this.image = image
                imagePadding = 8.0
                cornerStyle = UIButtonConfigurationCornerStyleCapsule
                contentInsets = NSDirectionalEdgeInsetsMake(0.0, 16.0, 0.0, 16.0)
                if (tint != Color.Unspecified) baseBackgroundColor = tint.toUIColor()
            }
        }
        val width = remember(configuration, density) {
            UIButton.buttonWithConfiguration(configuration, primaryAction = null)
                .sizeThatFits(CGSizeMake(10_000.0, 56.0))
                .useContents { width.toFloat().coerceAtLeast(56f).dp }
        }
        UIKitView(
            factory = {
                val button = UIButton.buttonWithConfiguration(configuration, primaryAction = UIAction.actionWithHandler {
                    if (currentEnabled) currentClick()
                })
                FloatingButtonView(button, configuration, effectPadding)
            },
            modifier = modifier.size(width, 56.dp).nativeScrollExclusion().layout { measurable, constraints ->
                // UIKitView는 뷰 경계를 자르므로 글래스가 커질 공간까지 측정한다.
                val padding = effectPadding.dp.roundToPx()
                val widthPx = constraints.maxWidth
                val heightPx = constraints.maxHeight
                val view = measurable.measure(Constraints.fixed(widthPx + padding * 2, heightPx + padding * 2))
                layout(widthPx, heightPx) { view.place(-padding, -padding) }
            },
            update = { view -> view.update(configuration, enabled, label) },
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true,
                placedAsOverlay = true,
            ),
        )
    }
}

private const val GlassEffectPadding = 16.0

@OptIn(ExperimentalForeignApi::class)
private class FloatingButtonView(
    private val button: UIButton,
    private var configuration: UIButtonConfiguration,
    private val effectPadding: Double,
) : UIView(frame = CGRectZero.readValue()) {
    init {
        addSubview(button)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        bounds.useContents {
            button.setFrame(
                CGRectMake(
                    effectPadding,
                    effectPadding,
                    (size.width - effectPadding * 2).coerceAtLeast(0.0),
                    (size.height - effectPadding * 2).coerceAtLeast(0.0),
                ),
            )
        }
    }

    override fun pointInside(point: CValue<CGPoint>, withEvent: UIEvent?): Boolean =
        button.pointInside(convertPoint(point, toView = button), withEvent)

    fun update(configuration: UIButtonConfiguration, enabled: Boolean, label: String) {
        if (this.configuration !== configuration) {
            this.configuration = configuration
            button.configuration = configuration
        }
        if (button.enabled != enabled) button.enabled = enabled
        if (button.accessibilityLabel != label) button.accessibilityLabel = label
    }
}
