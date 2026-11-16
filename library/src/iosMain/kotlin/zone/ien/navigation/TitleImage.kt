package zone.ien.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.kCGInterpolationHigh
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage

/**
 * Title images ([nativeScreen] `titleImage`) rendered by the host composition, by content key.
 * The navigation host fills it before it updates the stacks, so a screen created for a push finds
 * its image right away.
 */
internal class TitleImages {
    private var images: Map<Any, UIImage> = emptyMap()

    operator fun get(contentKey: Any): UIImage? = images[contentKey]

    fun update(images: Map<Any, UIImage>) {
        this.images = images
    }
}

/**
 * Renders the title images of [entries]. The painter lambdas are composable (e.g.
 * `painterResource`), so they run here in the host composition, before the screens exist: a
 * screen's own composition only starts after its push began, too late for the transition.
 */
@Composable
internal fun rememberTitleImages(entries: List<NavEntry<*>>): Map<Any, UIImage> {
    val density = LocalDensity.current
    val images = HashMap<Any, UIImage>()
    for (entry in entries) {
        val titleImage = entry.nativeTitleImage
        if (titleImage != null) {
            key(entry.contentKey) {
                val painter = titleImage()
                val image = remember(painter, density) { renderNativeImage(painter, density, height = TITLE_IMAGE_HEIGHT) }
                if (image != null) images[entry.contentKey] = image
            }
        }
    }
    return images
}

/** Height of a title image in the navigation bar, in points. */
private const val TITLE_IMAGE_HEIGHT = 28f

/** Painter를 비율을 유지한 UIKit 이미지로 변환한다. 바 버튼은 [maxWidth]로 크기를 제한한다. */
@OptIn(ExperimentalForeignApi::class)
internal fun renderNativeImage(
    painter: Painter,
    density: Density,
    height: Float,
    maxWidth: Float = Float.POSITIVE_INFINITY,
): UIImage? {
    val intrinsic = painter.intrinsicSize
    val known = intrinsic.isSpecified && intrinsic.width.isFinite() && intrinsic.height.isFinite() &&
        intrinsic.width > 0f && intrinsic.height > 0f
    val naturalWidth = if (known) height * intrinsic.width / intrinsic.height else height
    val fit = min(1f, maxWidth / naturalWidth)
    val renderedHeight = height * fit
    val width = naturalWidth * fit
    val target = Size(width * density.density, renderedHeight * density.density)
    val drawSize = if (known && intrinsic.height > target.height) {
        intrinsic * min(1f, 4 * target.height / intrinsic.height)
    } else {
        target
    }

    val bitmap = ImageBitmap(ceil(drawSize.width).toInt(), ceil(drawSize.height).toInt())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), drawSize) {
        with(painter) { draw(drawSize) }
    }
    val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: return null
    val drawn = UIImage.imageWithData(png.toNSData()) ?: return null

    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), renderedHeight.toDouble()))
    return renderer.imageWithActions { context ->
        CGContextSetInterpolationQuality(context?.CGContext, kCGInterpolationHigh)
        drawn.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), renderedHeight.toDouble()))
    }
}
