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
                val image = remember(painter, density) { renderTitleImage(painter, density) }
                if (image != null) images[entry.contentKey] = image
            }
        }
    }
    return images
}

/** Height of a title image in the navigation bar, in points. */
private const val TITLE_IMAGE_HEIGHT = 28f

/**
 * Draws [painter] [TITLE_IMAGE_HEIGHT] points high (its aspect ratio kept, square without an
 * intrinsic size) into a `UIImage` at the screen scale.
 *
 * A painter larger than that (a bitmap) is drawn at up to 4× the target size first: painters
 * shrink with bilinear filtering, which aliases when shrinking a lot. Core Graphics then shrinks
 * it with high-quality interpolation.
 */
@OptIn(ExperimentalForeignApi::class)
private fun renderTitleImage(painter: Painter, density: Density): UIImage? {
    val intrinsic = painter.intrinsicSize
    val known = intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f
    val height = TITLE_IMAGE_HEIGHT
    val width = if (known) height * intrinsic.width / intrinsic.height else height
    val target = Size(width * density.density, height * density.density)
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

    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()))
    return renderer.imageWithActions { context ->
        CGContextSetInterpolationQuality(context?.CGContext, kCGInterpolationHigh)
        drawn.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
    }
}
