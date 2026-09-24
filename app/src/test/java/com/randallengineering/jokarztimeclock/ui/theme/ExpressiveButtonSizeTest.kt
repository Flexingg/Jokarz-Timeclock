package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveButtonSizeTest {

    private val density = Density(1f)

    @Test
    fun heightsFollowTheM3Scale() {
        assertEquals(
            listOf(32.dp, 40.dp, 56.dp, 96.dp, 136.dp),
            ExpressiveButtonSize.values().map { it.height }
        )
    }

    @Test
    fun cornerRadiusIsHalfTheHeightSoEverySizeIsAPill() {
        for (size in ExpressiveButtonSize.values()) {
            assertEquals(size.height / 2, size.cornerRadius)
            assertEquals(RoundedCornerShape(size.height / 2), size.shape)
            // Measured on a wide button: every corner is exactly half the height, in px.
            val box = Size(width = 400f, height = size.height.value)
            val corners = listOf(size.shape.topStart, size.shape.topEnd, size.shape.bottomEnd, size.shape.bottomStart)
            corners.forEach { assertEquals(size.height.value / 2f, it.toPx(box, density), 1e-4f) }
        }
    }

    @Test
    fun paddingIconAndLabelGrowWithTheSize() {
        val sizes = ExpressiveButtonSize.values()
        sizes.toList().zipWithNext { a, b ->
            assertTrue("$b height", b.height > a.height)
            assertTrue("$b padding", b.horizontalPadding >= a.horizontalPadding)
            assertTrue("$b icon", b.iconSize >= a.iconSize)
            assertTrue("$b label", b.labelStyle(Typography).fontSize.value >= a.labelStyle(Typography).fontSize.value)
        }
        // Medium is the M3 default for a primary action: 56 dp, 24 dp sides, 24 dp icon, title M label.
        val m = ExpressiveButtonSize.Medium
        assertEquals(56.dp, m.height)
        assertEquals(24.dp, m.horizontalPadding)
        assertEquals(24.dp, m.iconSize)
        assertEquals(Typography.titleMedium, m.labelStyle(Typography))
    }

    @Test
    fun matchesTheLibraryTablesWhereTheyExist() {
        // material3 1.5.0-alpha10 ships the same scale; keep the app's single source in step with it.
        for (size in ExpressiveButtonSize.values()) {
            val padding = ButtonDefaults.contentPaddingFor(size.height)
            assertEquals("$size start padding", size.horizontalPadding, padding.calculateLeftPadding(LayoutDirection.Ltr))
            assertEquals("$size end padding", size.horizontalPadding, padding.calculateRightPadding(LayoutDirection.Ltr))
            assertEquals("$size icon", size.iconSize, ButtonDefaults.iconSizeFor(size.height))
        }
    }

    @Test
    fun connectedGroupOnlyShrinksTheInnerCorners() {
        val h = ExpressiveButtonSize.Small.height.value
        val box = Size(200f, h)
        fun corners(i: Int, n: Int) = ConnectedButtonShapes.shapeFor(i, n).let { s ->
            listOf(s.topStart, s.topEnd, s.bottomEnd, s.bottomStart).map { it.toPx(box, density) }
        }
        val round = h / 2f
        val inner = ConnectedButtonShapes.InnerCorner.value

        assertEquals(8.dp, ConnectedButtonShapes.InnerCorner)
        assertEquals(3.dp, ConnectedButtonShapes.Gap)
        // topStart, topEnd, bottomEnd, bottomStart
        assertEquals(listOf(round, inner, inner, round), corners(0, 3))
        assertEquals(listOf(inner, inner, inner, inner), corners(1, 3))
        assertEquals(listOf(inner, round, round, inner), corners(2, 3))
        // A lone item is a plain pill; a pair only meets in the middle.
        assertEquals(listOf(round, round, round, round), corners(0, 1))
        assertEquals(listOf(round, inner, inner, round), corners(0, 2))
        assertEquals(listOf(inner, round, round, inner), corners(1, 2))
    }

    @Test
    fun shapeTokensMatchTheBrief() {
        val box = Size(300f, 200f)
        assertEquals(20f, AppShapes.medium.topStart.toPx(box, density), 0f)
        assertEquals(28f, AppShapes.extraLarge.topStart.toPx(box, density), 0f)
        assertEquals(CardCornerRadius, 20.dp)
        assertEquals(DialogCornerRadius, 28.dp)
        // Pill at any height.
        listOf(24f, 40f, 56f, 136f).forEach { h ->
            assertEquals(h / 2f, PillShape.topStart.toPx(Size(500f, h), density), 1e-4f)
        }
    }
}
