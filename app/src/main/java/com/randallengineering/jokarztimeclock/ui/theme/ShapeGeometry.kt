package com.randallengineering.jokarztimeclock.ui.theme

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry behind the expressive shapes in `Shapes.kt`.
 *
 * Free of Android and Compose types so it runs in plain JVM unit tests. An outline is a flat
 * `FloatArray` of x,y pairs in pixels, implicitly closed (the last point joins the first) and
 * traversed clockwise on screen starting at the top-centre, so two outlines resampled to the same
 * point count line up point-for-point and can be interpolated.
 */
internal object ShapeGeometry {

    /** Superellipse exponent of a squircle corner. 2 would be a circular arc; above 2 the curvature
     *  falls to zero where the corner meets the straight edge, which is what makes it "continuous". */
    const val SQUIRCLE_EXPONENT = 4.0

    /** Points per corner quadrant, both ends included. */
    private const val CORNER_SAMPLES = 10

    /** Points per half wave on [wavyTopOutline]'s top edge. */
    private const val HALF_WAVE_SAMPLES = 6

    /** Consecutive points closer than this (px) are merged, so zero-length segments never occur. */
    private const val MERGE_EPSILON = 0.01f

    /** Unit superellipse quadrant: x = cos(t)^(2/n), y = sin(t)^(2/n), t = 0..π/2. */
    private val QUADRANT_X = FloatArray(CORNER_SAMPLES)
    private val QUADRANT_Y = FloatArray(CORNER_SAMPLES)

    init {
        val e = 2.0 / SQUIRCLE_EXPONENT
        for (i in 0 until CORNER_SAMPLES) {
            val t = (PI / 2.0) * i / (CORNER_SAMPLES - 1)
            QUADRANT_X[i] = cos(t).coerceAtLeast(0.0).pow(e).toFloat()
            QUADRANT_Y[i] = sin(t).coerceAtLeast(0.0).pow(e).toFloat()
        }
    }

    // ---- Squircle -----------------------------------------------------------------------------

    /**
     * Rectangle with superellipse corners. Radii are physical (already resolved from start/end) and
     * scaled down together, CSS-style, when two neighbours would overlap.
     */
    fun squircleOutline(
        width: Float, height: Float,
        topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float
    ): FloatArray {
        val r = clampRadii(width, height, topLeft, topRight, bottomRight, bottomLeft)
        val out = PointBuffer(4 * CORNER_SAMPLES + 1)
        out.add(width / 2f, 0f)
        addCorner(out, width - r[1], r[1], r[1], sx = 1f, sy = -1f, reversed = true)
        addCorner(out, width - r[2], height - r[2], r[2], sx = 1f, sy = 1f, reversed = false)
        addCorner(out, r[3], height - r[3], r[3], sx = -1f, sy = 1f, reversed = true)
        addCorner(out, r[0], r[0], r[0], sx = -1f, sy = -1f, reversed = false)
        return out.toClosedArray()
    }

    // ---- Wavy top -----------------------------------------------------------------------------

    /**
     * Squircle-cornered rectangle whose top edge is a cosine wave dipping [amplitude] px into the
     * shape. The wave has zero slope where it meets the top corners, so the joins are smooth, and an
     * integer [waves] count makes it mirror-symmetric (only the corner radii change under RTL).
     */
    fun wavyTopOutline(
        width: Float, height: Float, amplitude: Float, waves: Int,
        topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float
    ): FloatArray {
        val r = clampRadii(width, height, topLeft, topRight, bottomRight, bottomLeft)
        val amp = amplitude.coerceIn(0f, height / 4f)
        val waveCount = max(1, waves)
        val steps = waveCount * 2 * HALF_WAVE_SAMPLES
        val out = PointBuffer(steps + 1 + 4 * CORNER_SAMPLES)
        val x0 = r[0]
        val span = width - r[1] - x0
        fun addWave(from: Int, to: Int) {
            for (i in from..to) {
                val u = i.toFloat() / steps
                out.add(x0 + span * u, amp * (1f - cos(2.0 * PI * waveCount * u).toFloat()) / 2f)
            }
        }
        // Start mid-wave so this outline shares the top-centre-first convention of the others.
        addWave(steps / 2, steps)
        addCorner(out, width - r[1], r[1], r[1], sx = 1f, sy = -1f, reversed = true)
        addCorner(out, width - r[2], height - r[2], r[2], sx = 1f, sy = 1f, reversed = false)
        addCorner(out, r[3], height - r[3], r[3], sx = -1f, sy = 1f, reversed = true)
        addCorner(out, r[0], r[0], r[0], sx = -1f, sy = -1f, reversed = false)
        addWave(0, steps / 2 - 1)
        return out.toClosedArray()
    }

    // ---- Cookie -------------------------------------------------------------------------------

    /** Fixed per-scallop depth multipliers: the "hand-made" irregularity, identical on every draw. */
    val COOKIE_JITTER = floatArrayOf(1.0f, 0.7f, 1.25f, 0.85f, 1.1f, 0.75f, 1.3f, 0.9f, 1.05f)

    /**
     * Unit radii of a scalloped disc with one bite, sampled at [samples] angles starting at the top
     * and going clockwise. Normalised so the largest radius is exactly 1 (the shape fills its box).
     *
     * r(φ) = 1 − depth·jitter[k]·(1 − cos(lobes·φ))/2 − bite·cos²(π/2·(φ − φb)/w) inside the bite.
     * Both terms have zero value *and* slope at their edges, so the outline stays smooth.
     */
    fun cookieRadii(
        samples: Int,
        lobes: Int,
        scallopDepth: Float,
        biteCentre: Float,
        biteHalfWidth: Float,
        biteDepth: Float
    ): FloatArray {
        val radii = FloatArray(samples)
        var largest = 0f
        for (i in 0 until samples) {
            val phi = 2.0 * PI * i / samples
            val lobe = ((phi * lobes) / (2.0 * PI)).toInt().coerceIn(0, lobes - 1)
            val jitter = COOKIE_JITTER[lobe % COOKIE_JITTER.size]
            var r = 1.0 - scallopDepth * jitter * (1.0 - cos(lobes * phi)) / 2.0
            val u = (phi - biteCentre) / biteHalfWidth
            if (u > -1.0 && u < 1.0) {
                val w = cos(PI / 2.0 * u)
                r -= biteDepth * w * w
            }
            radii[i] = r.toFloat()
            largest = max(largest, radii[i])
        }
        for (i in radii.indices) radii[i] /= largest
        return radii
    }

    /**
     * Places [unitRadii] (from [cookieRadii]) in a [width]×[height] box. [mirrored] reflects the
     * shape left↔right (for RTL) while keeping the clockwise, top-centre-first order.
     */
    fun cookieOutline(width: Float, height: Float, unitRadii: FloatArray, mirrored: Boolean): FloatArray {
        val n = unitRadii.size
        val cx = width / 2f
        val cy = height / 2f
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            // Reflecting about the vertical axis maps angle θ to π − θ, i.e. sample i to sample n − i.
            val r = unitRadii[if (mirrored) (n - i) % n else i]
            val theta = -PI / 2.0 + 2.0 * PI * i / n
            out[2 * i] = cx + cx * r * cos(theta).toFloat()
            out[2 * i + 1] = cy + cy * r * sin(theta).toFloat()
        }
        return out
    }

    // ---- Morphing support ---------------------------------------------------------------------

    /** Resamples a closed polyline to [count] points evenly spaced by arc length, keeping point 0. */
    fun resampleClosed(points: FloatArray, count: Int): FloatArray {
        val n = points.size / 2
        val cumulative = FloatArray(n + 1)
        for (i in 0 until n) {
            val j = (i + 1) % n
            cumulative[i + 1] = cumulative[i] + dist(points, i, j)
        }
        val total = cumulative[n]
        val out = FloatArray(count * 2)
        var seg = 0
        for (k in 0 until count) {
            val s = total * k / count
            while (seg < n - 1 && cumulative[seg + 1] < s) seg++
            val len = cumulative[seg + 1] - cumulative[seg]
            val f = if (len > 0f) (s - cumulative[seg]) / len else 0f
            val j = (seg + 1) % n
            out[2 * k] = points[2 * seg] + (points[2 * j] - points[2 * seg]) * f
            out[2 * k + 1] = points[2 * seg + 1] + (points[2 * j + 1] - points[2 * seg + 1]) * f
        }
        return out
    }

    /** Point-wise interpolation; [t] outside 0..1 extrapolates (a spring's overshoot reads as jelly). */
    fun lerpOutline(from: FloatArray, to: FloatArray, t: Float, out: FloatArray) {
        for (i in out.indices) out[i] = from[i] + (to[i] - from[i]) * t
    }

    // ---- Smoothing ----------------------------------------------------------------------------

    /**
     * Turns a closed polyline into cubic Bézier segments through every point: six floats per segment
     * (control1, control2, end). Each tangent follows the neighbouring points (Catmull-Rom direction)
     * but its handles are a third of *that segment's* chord, so a dense corner next to a long straight
     * edge never overshoots, and collinear points stay a straight line.
     */
    fun smoothClosedCubics(points: FloatArray): FloatArray {
        val n = points.size / 2
        val dirX = FloatArray(n)
        val dirY = FloatArray(n)
        for (i in 0 until n) {
            val prev = (i - 1 + n) % n
            val next = (i + 1) % n
            var dx = points[2 * next] - points[2 * prev]
            var dy = points[2 * next + 1] - points[2 * prev + 1]
            var len = sqrt(dx * dx + dy * dy)
            if (len < 1e-6f) {
                dx = points[2 * next] - points[2 * i]
                dy = points[2 * next + 1] - points[2 * i + 1]
                len = max(sqrt(dx * dx + dy * dy), 1e-6f)
            }
            dirX[i] = dx / len
            dirY[i] = dy / len
        }
        val out = FloatArray(n * 6)
        for (i in 0 until n) {
            val j = (i + 1) % n
            val handle = dist(points, i, j) / 3f
            out[6 * i] = points[2 * i] + dirX[i] * handle
            out[6 * i + 1] = points[2 * i + 1] + dirY[i] * handle
            out[6 * i + 2] = points[2 * j] - dirX[j] * handle
            out[6 * i + 3] = points[2 * j + 1] - dirY[j] * handle
            out[6 * i + 4] = points[2 * j]
            out[6 * i + 5] = points[2 * j + 1]
        }
        return out
    }

    // ---- Helpers ------------------------------------------------------------------------------

    /** Returns [topLeft, topRight, bottomRight, bottomLeft], non-negative and non-overlapping. */
    internal fun clampRadii(
        width: Float, height: Float,
        topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float
    ): FloatArray {
        val r = floatArrayOf(topLeft, topRight, bottomRight, bottomLeft).also {
            for (i in it.indices) it[i] = max(0f, it[i])
        }
        var scale = 1f
        fun fit(available: Float, a: Float, b: Float) {
            if (a + b > available && a + b > 0f) scale = min(scale, available / (a + b))
        }
        fit(width, r[0], r[1])
        fit(width, r[3], r[2])
        fit(height, r[0], r[3])
        fit(height, r[1], r[2])
        for (i in r.indices) r[i] *= scale
        return r
    }

    /**
     * Appends one superellipse corner around the centre (cx, cy). Sample 0 lies on the horizontal
     * axis (x = sx·r) and the last on the vertical (y = sy·r); [reversed] walks it the other way.
     */
    private fun addCorner(
        out: PointBuffer, cx: Float, cy: Float, radius: Float,
        sx: Float, sy: Float, reversed: Boolean
    ) {
        for (k in 0 until CORNER_SAMPLES) {
            val i = if (reversed) CORNER_SAMPLES - 1 - k else k
            out.add(cx + sx * radius * QUADRANT_X[i], cy + sy * radius * QUADRANT_Y[i])
        }
    }

    private fun dist(p: FloatArray, i: Int, j: Int): Float {
        val dx = p[2 * j] - p[2 * i]
        val dy = p[2 * j + 1] - p[2 * i + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /** Growable point list that drops points duplicating their predecessor (e.g. a zero radius). */
    private class PointBuffer(capacity: Int) {
        private var data = FloatArray(capacity * 2)
        private var size = 0

        fun add(x: Float, y: Float) {
            if (size > 0) {
                val dx = x - data[2 * size - 2]
                val dy = y - data[2 * size - 1]
                if (dx * dx + dy * dy < MERGE_EPSILON * MERGE_EPSILON) return
            }
            if (2 * size + 2 > data.size) data = data.copyOf(data.size * 2)
            data[2 * size] = x
            data[2 * size + 1] = y
            size++
        }

        /** Drops a final point that coincides with the first, since the outline closes on its own. */
        fun toClosedArray(): FloatArray {
            var n = size
            if (n > 1) {
                val dx = data[2 * n - 2] - data[0]
                val dy = data[2 * n - 1] - data[1]
                if (dx * dx + dy * dy < MERGE_EPSILON * MERGE_EPSILON) n--
            }
            return data.copyOf(n * 2)
        }
    }
}
