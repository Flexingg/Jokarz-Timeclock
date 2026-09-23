package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Expressive shapes for the main screen.
 *
 * material3 1.3.1 predates the Material 3 Expressive shape library (MaterialShapes / RoundedPolygon
 * morphing), so these are hand-built Shape implementations. The maths lives in ShapeGeometry (pure,
 * unit tested); this file only turns its polylines into Bézier Paths.
 *
 * Allocation: shapes are data classes, so an equal shape on the next recomposition is recognised and
 * Compose keeps its cached outline instead of asking again. Each instance also keeps a single-entry
 * (size, direction, density) → Outline cache, so list items of one size sharing an instance from
 * ExpressiveShapes build their Path once. Nothing here runs per frame except during a morph.
 */

/** Closed Path of cubic Béziers through every point of this polyline (see [ShapeGeometry.smoothClosedCubics]). */
private fun FloatArray.toSmoothClosedPath(): Path {
    val cubics = ShapeGeometry.smoothClosedCubics(this)
    return Path().apply {
        moveTo(this@toSmoothClosedPath[0], this@toSmoothClosedPath[1])
        for (i in 0 until cubics.size / 6) {
            cubicTo(
                cubics[6 * i], cubics[6 * i + 1],
                cubics[6 * i + 2], cubics[6 * i + 3],
                cubics[6 * i + 4], cubics[6 * i + 5]
            )
        }
        close()
    }
}

/**
 * A shape defined by a smooth closed polyline from [ShapeGeometry]. Subclasses only supply the
 * points; this class owns the Bézier conversion and the single-entry outline cache.
 */
abstract class SmoothOutlineShape : Shape {
    private var cachedSize = Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedDensity = 0f
    private var cachedOutline: Outline? = null

    /** Clockwise polyline from the top-centre, in pixels, for a box of [size]. */
    internal abstract fun outlinePoints(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray

    final override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        cachedOutline?.let {
            if (size == cachedSize && layoutDirection == cachedDirection && density.density == cachedDensity) return it
        }
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(size.toRect())
        return Outline.Generic(outlinePoints(size, layoutDirection, density).toSmoothClosedPath()).also {
            cachedSize = size
            cachedDirection = layoutDirection
            cachedDensity = density.density
            cachedOutline = it
        }
    }
}

/**
 * A true squircle: straight edges joined by superellipse corners (|x|⁴ + |y|⁴ = 1), whose curvature
 * falls to zero where it meets the edge, so there is no visible "kink" where a rounded rectangle's
 * arc starts. Per-corner radii are logical (start/end) and swap under RTL; oversize radii are scaled
 * down together, so a large radius gives a squircle-ended pill.
 */
data class SquircleShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp
) : SmoothOutlineShape() {

    constructor(all: Dp) : this(all, all, all, all)

    override fun outlinePoints(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray =
        with(density) {
            val ltr = layoutDirection == LayoutDirection.Ltr
            ShapeGeometry.squircleOutline(
                size.width, size.height,
                topLeft = (if (ltr) topStart else topEnd).toPx(),
                topRight = (if (ltr) topEnd else topStart).toPx(),
                bottomRight = (if (ltr) bottomEnd else bottomStart).toPx(),
                bottomLeft = (if (ltr) bottomStart else bottomEnd).toPx()
            )
        }
}

/**
 * A "cookie with a bite": a disc whose rim is scalloped into [lobes] shallow waves of deliberately
 * uneven depth, with one smooth bite taken out of the upper end-side shoulder. It is the playful
 * resting shape of the clock-in button and the start of the confirmation morph.
 *
 * Deterministic: the irregularity comes from the fixed [ShapeGeometry.COOKIE_JITTER] table, never a
 * random source, and the unit radii are computed once per instance. Under RTL the whole shape is
 * mirrored so the bite stays on the end side.
 */
data class CookieShape(
    val lobes: Int = 9,
    val scallopDepth: Float = 0.07f,
    val biteDepth: Float = 0.14f
) : SmoothOutlineShape() {

    private val unitRadii: FloatArray by lazy {
        ShapeGeometry.cookieRadii(
            samples = SAMPLES,
            lobes = lobes,
            scallopDepth = scallopDepth,
            biteCentre = BITE_CENTRE,
            biteHalfWidth = BITE_HALF_WIDTH,
            biteDepth = biteDepth
        )
    }

    override fun outlinePoints(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray =
        ShapeGeometry.cookieOutline(size.width, size.height, unitRadii, mirrored = layoutDirection == LayoutDirection.Rtl)

    private companion object {
        const val SAMPLES = 144
        /** Clockwise from the top, in radians: the upper-end shoulder. */
        const val BITE_CENTRE = 0.9f
        const val BITE_HALF_WIDTH = 0.42f
    }
}

/**
 * The hero "stage": squircle corners at the bottom and a gentle [waves]-crest cosine wave along the
 * top edge, so the header reads as a playful container rather than a flat card. The wave is
 * mirror-symmetric, so RTL only swaps the (logical) corner radii. Every parameter is a plain value,
 * so the stage can animate its corners and wave depth between shift states.
 */
data class WavyTopShape(
    val waveAmplitude: Dp = 10.dp,
    val waves: Int = 3,
    val topStart: Dp = 32.dp,
    val topEnd: Dp = 32.dp,
    val bottomEnd: Dp = 32.dp,
    val bottomStart: Dp = 32.dp
) : SmoothOutlineShape() {

    override fun outlinePoints(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray =
        with(density) {
            val ltr = layoutDirection == LayoutDirection.Ltr
            ShapeGeometry.wavyTopOutline(
                size.width, size.height, waveAmplitude.toPx(), waves,
                topLeft = (if (ltr) topStart else topEnd).toPx(),
                topRight = (if (ltr) topEnd else topStart).toPx(),
                bottomRight = (if (ltr) bottomEnd else bottomStart).toPx(),
                bottomLeft = (if (ltr) bottomStart else bottomEnd).toPx()
            )
        }
}

/**
 * Morphs between two smooth shapes (e.g. [CookieShape] → [SquircleShape]) point-for-point: both are
 * resampled to [POINTS] evenly spaced points from the top-centre and interpolated. The resampled
 * endpoints are cached per (size, direction, density); only the interpolation runs per frame.
 * Tuned for button / badge sizes — on a very large box the fixed point count softens small corners.
 */
@Stable
class ShapeMorph(private val start: SmoothOutlineShape, private val end: SmoothOutlineShape) {
    private var cachedSize = Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedDensity = 0f
    private var from = FloatArray(0)
    private var to = FloatArray(0)

    /** The shape at [progress] (0 = start, 1 = end; a spring's small overshoot is allowed). */
    fun at(progress: Float): Shape = MorphFrame(this, progress.coerceIn(-0.25f, 1.25f))

    internal fun outlineAt(progress: Float, size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.width <= 0f || size.height <= 0f) return Outline.Rectangle(size.toRect())
        if (size != cachedSize || layoutDirection != cachedDirection || density.density != cachedDensity) {
            from = ShapeGeometry.resampleClosed(start.outlinePoints(size, layoutDirection, density), POINTS)
            to = ShapeGeometry.resampleClosed(end.outlinePoints(size, layoutDirection, density), POINTS)
            cachedSize = size
            cachedDirection = layoutDirection
            cachedDensity = density.density
        }
        val points = FloatArray(from.size)
        ShapeGeometry.lerpOutline(from, to, progress, points)
        return Outline.Generic(points.toSmoothClosedPath())
    }

    private data class MorphFrame(val morph: ShapeMorph, val progress: Float) : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            morph.outlineAt(progress, size, layoutDirection, density)
    }

    private companion object {
        const val POINTS = 96
    }
}

/**
 * Clips to [morph] at the current [progress]. The lambda is read inside the graphics layer, so an
 * animating morph only re-draws the layer — it never recomposes the caller.
 */
fun Modifier.shapeMorph(morph: ShapeMorph, progress: () -> Float): Modifier = graphicsLayer {
    shape = morph.at(progress())
    clip = true
}

/** Shared instances, so equal-size list items reuse one cached outline. */
object ExpressiveShapes {
    /** Chips, segmented toggles, small counters. Radius is clamped, so this is always a pill. */
    val Pill = SquircleShape(100.dp)

    /** Icon buttons and the app mark. */
    val Tile = SquircleShape(14.dp)

    /** Grouped containers (rate capsule, history rows, week cards, warnings). */
    val Container = SquircleShape(28.dp)

    /** The leading summary card: generous start-top / end-bottom, tight on the other diagonal. */
    val CardLeading = SquircleShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 12.dp)

    /** The trailing summary card, mirrored so the pair "faces" each other. */
    val CardTrailing = SquircleShape(topStart = 12.dp, topEnd = 32.dp, bottomEnd = 12.dp, bottomStart = 32.dp)

    /** The clock-in button at rest and the start of the confirmation badge. */
    val Cookie = CookieShape()

    /** Where the button's press morph and the confirmation badge end up. */
    val Badge = SquircleShape(36.dp)
}
