package com.randallengineering.jokarztimeclock.ui.theme

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * The expressive shapes are only as good as the polylines behind them: these pin the properties the
 * Shape classes and the morph rely on (closed, clockwise from the top-centre, inside the box,
 * deterministic, RTL-mirrored, and smoothing that never overshoots).
 */
class ShapeGeometryTest {

    private val w = 300f
    private val h = 180f

    @Test
    fun squircleStartsTopCentreAndStaysInsideItsBox() {
        val pts = ShapeGeometry.squircleOutline(w, h, 40f, 12f, 40f, 12f)
        assertEquals(w / 2f, pts[0], 1e-4f)
        assertEquals(0f, pts[1], 1e-4f)
        assertInsideBox(pts, w, h)
        assertTrue("outline must run clockwise on screen", signedArea(pts) > 0f)
    }

    @Test
    fun squircleCornerIsASuperellipseNotACircularArc() {
        val r = 60f
        val pts = ShapeGeometry.squircleOutline(w, h, r, r, r, r)
        // Every top-left corner point satisfies |x|^4 + |y|^4 = 1 around the corner centre (r, r).
        var cornerPoints = 0
        for (i in 0 until pts.size / 2) {
            val x = pts[2 * i]
            val y = pts[2 * i + 1]
            if (x < r && y < r) {
                val u = (r - x) / r
                val v = (r - y) / r
                assertEquals(1.0, u.toDouble().pow(4) + v.toDouble().pow(4), 1e-3)
                cornerPoints++
            }
        }
        assertTrue("expected sampled corner points", cornerPoints >= 6)
    }

    @Test
    fun oversizeRadiiAreScaledDownTogether() {
        val r = ShapeGeometry.clampRadii(100f, 40f, 100f, 100f, 100f, 100f)
        r.forEach { assertEquals(20f, it, 1e-4f) }
        val pts = ShapeGeometry.squircleOutline(100f, 40f, 100f, 100f, 100f, 100f)
        assertInsideBox(pts, 100f, 40f)
    }

    @Test
    fun zeroRadiiGiveNoDuplicatePoints() {
        val pts = ShapeGeometry.squircleOutline(w, h, 0f, 0f, 0f, 0f)
        val n = pts.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            assertTrue(hypot(pts[2 * j] - pts[2 * i], pts[2 * j + 1] - pts[2 * i + 1]) > 0.001f)
        }
    }

    @Test
    fun cookieIsDeterministicFillsItsBoxAndHasABite() {
        val a = ShapeGeometry.cookieRadii(144, 9, 0.07f, 0.9f, 0.42f, 0.14f)
        val b = ShapeGeometry.cookieRadii(144, 9, 0.07f, 0.9f, 0.42f, 0.14f)
        assertArrayEquals(a, b, 0f)
        assertEquals(1f, a.max(), 1e-6f)
        // The bite (centred 0.9 rad clockwise from the top) is the deepest point of the rim; the
        // neighbouring scallop valley can pull the minimum a few samples (~2.5° each) off-centre.
        val biteIndex = (0.9 / (2 * Math.PI) * 144).toInt()
        val deepest = a.indices.minBy { a[it] }
        assertTrue("deepest point $deepest should be at the bite ~$biteIndex", abs(deepest - biteIndex) <= 4)
        assertTrue(a.min() < 0.88f)

        val pts = ShapeGeometry.cookieOutline(w, h, a, mirrored = false)
        assertEquals(w / 2f, pts[0], 1e-3f)
        assertInsideBox(pts, w, h)
        assertTrue(signedArea(pts) > 0f)
    }

    @Test
    fun rtlCookieIsTheExactMirrorImage() {
        val radii = ShapeGeometry.cookieRadii(144, 9, 0.07f, 0.9f, 0.42f, 0.14f)
        val ltr = ShapeGeometry.cookieOutline(w, h, radii, mirrored = false)
        val rtl = ShapeGeometry.cookieOutline(w, h, radii, mirrored = true)
        val n = radii.size
        for (i in 0 until n) {
            val m = (n - i) % n
            assertEquals(w - ltr[2 * m], rtl[2 * i], 1e-2f)
            assertEquals(ltr[2 * m + 1], rtl[2 * i + 1], 1e-2f)
        }
        assertTrue("mirroring must keep the clockwise order", signedArea(rtl) > 0f)
    }

    @Test
    fun wavyTopDipsIntoTheShapeAndKeepsTheConvention() {
        val amp = 12f
        val pts = ShapeGeometry.wavyTopOutline(w, h, amp, 3, 32f, 32f, 32f, 32f)
        assertInsideBox(pts, w, h)
        assertTrue(signedArea(pts) > 0f)
        // Starts mid-wave on the top edge, and the top edge actually undulates by the amplitude.
        assertEquals(w / 2f, pts[0], 1e-3f)
        val topYs = (0 until pts.size / 2).filter { pts[2 * it] in 40f..(w - 40f) && pts[2 * it + 1] < h / 2 }
            .map { pts[2 * it + 1] }
        assertEquals(0f, topYs.min(), 1e-3f)
        assertEquals(amp, topYs.max(), 1e-3f)
    }

    @Test
    fun resampleGivesEvenSpacingAndKeepsTheStartPoint() {
        val pts = ShapeGeometry.squircleOutline(w, h, 40f, 40f, 40f, 40f)
        val res = ShapeGeometry.resampleClosed(pts, 96)
        assertEquals(96 * 2, res.size)
        assertEquals(pts[0], res[0], 1e-4f)
        assertEquals(pts[1], res[1], 1e-4f)
        val steps = (0 until 96).map { i ->
            val j = (i + 1) % 96
            hypot(res[2 * j] - res[2 * i], res[2 * j + 1] - res[2 * i + 1])
        }
        // Chords are slightly shorter than arc steps around corners; straight runs are exact.
        assertTrue(steps.max() - steps.min() < steps.max() * 0.05f)
    }

    @Test
    fun morphEndpointsReproduceTheirShapes() {
        val a = ShapeGeometry.resampleClosed(
            ShapeGeometry.cookieOutline(150f, 150f, ShapeGeometry.cookieRadii(144, 9, 0.07f, 0.9f, 0.42f, 0.14f), false), 96
        )
        val b = ShapeGeometry.resampleClosed(ShapeGeometry.squircleOutline(150f, 150f, 36f, 36f, 36f, 36f), 96)
        val out = FloatArray(a.size)
        ShapeGeometry.lerpOutline(a, b, 0f, out)
        assertArrayEquals(a, out, 0f)
        ShapeGeometry.lerpOutline(a, b, 1f, out)
        assertArrayEquals(b, out, 1e-4f)
        ShapeGeometry.lerpOutline(a, b, 0.5f, out)
        assertEquals((a[10] + b[10]) / 2f, out[10], 1e-4f)
    }

    @Test
    fun smoothingKeepsStraightEdgesStraight() {
        val squircle = ShapeGeometry.squircleOutline(200f, 100f, 20f, 20f, 20f, 20f)
        val sc = ShapeGeometry.smoothClosedCubics(squircle)
        // On the squircle's long top edge (y = 0) the handles stay within a hair of the edge.
        for (i in 0 until sc.size / 6) {
            val endX = sc[6 * i + 4]
            val endY = sc[6 * i + 5]
            if (endY == 0f && endX in 40f..160f) {
                assertEquals(0f, sc[6 * i + 1], 0.2f)
                assertEquals(0f, sc[6 * i + 3], 0.2f)
            }
        }
    }

    @Test
    fun smoothingHandlesNeverReachPastTheirSegment() {
        // Dense corner beside a long edge is the classic Catmull-Rom overshoot case.
        val pts = ShapeGeometry.squircleOutline(600f, 100f, 12f, 12f, 12f, 12f)
        val c = ShapeGeometry.smoothClosedCubics(pts)
        val n = pts.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            val chord = hypot(pts[2 * j] - pts[2 * i], pts[2 * j + 1] - pts[2 * i + 1])
            val h1 = hypot(c[6 * i] - pts[2 * i], c[6 * i + 1] - pts[2 * i + 1])
            val h2 = hypot(c[6 * i + 2] - pts[2 * j], c[6 * i + 3] - pts[2 * j + 1])
            assertEquals(chord / 3f, h1, 1e-3f)
            assertEquals(chord / 3f, h2, 1e-3f)
        }
    }

    private fun assertInsideBox(pts: FloatArray, bw: Float, bh: Float) {
        for (i in 0 until pts.size / 2) {
            assertTrue("x ${pts[2 * i]} outside 0..$bw", pts[2 * i] in -1e-3f..bw + 1e-3f)
            assertTrue("y ${pts[2 * i + 1]} outside 0..$bh", pts[2 * i + 1] in -1e-3f..bh + 1e-3f)
        }
    }

    /** Shoelace area; positive means clockwise on screen (y grows downwards). */
    private fun signedArea(pts: FloatArray): Float {
        var sum = 0f
        val n = pts.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            sum += pts[2 * i] * pts[2 * j + 1] - pts[2 * j] * pts[2 * i + 1]
        }
        return sum / 2f
    }
}
