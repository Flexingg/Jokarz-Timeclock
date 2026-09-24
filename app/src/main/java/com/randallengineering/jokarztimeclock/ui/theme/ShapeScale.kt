package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Material 3 Expressive shape and size tokens: the single source of truth for corner radii and
 * button sizes. Pure values (no composables), so ExpressiveButtonSizeTest checks the table on the JVM.
 */

/** Corner radius of every card (list rows, summary cards, grouped containers). */
val CardCornerRadius: Dp = 20.dp

/** Corner radius of every dialog (M3 dialogs read `shapes.extraLarge`). */
val DialogCornerRadius: Dp = 28.dp

/** A fully rounded pill at any height: 50 % of the shorter side. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * The theme's shape scale. M3 components read these slots: cards use `medium` (20 dp), dialogs and
 * date/time pickers use `extraLarge` (28 dp). Buttons are pill-shaped by default in M3 (`CircleShape`)
 * and [ExpressiveButtonSize.shape] keeps that true at every explicit height.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(CardCornerRadius),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(DialogCornerRadius)
)

/**
 * The M3 Expressive button size scale. A button given an explicit height takes the whole row: its
 * side padding, label style, icon size and a corner radius of exactly half its height (a pill).
 */
enum class ExpressiveButtonSize(
    val height: Dp,
    val horizontalPadding: Dp,
    val iconSize: Dp,
    /** Gap between the icon and the label. */
    val iconSpacing: Dp
) {
    ExtraSmall(height = 32.dp, horizontalPadding = 12.dp, iconSize = 20.dp, iconSpacing = 4.dp),
    Small(height = 40.dp, horizontalPadding = 16.dp, iconSize = 20.dp, iconSpacing = 8.dp),
    Medium(height = 56.dp, horizontalPadding = 24.dp, iconSize = 24.dp, iconSpacing = 8.dp),
    Large(height = 96.dp, horizontalPadding = 48.dp, iconSize = 32.dp, iconSpacing = 12.dp),
    ExtraLarge(height = 136.dp, horizontalPadding = 64.dp, iconSize = 40.dp, iconSpacing = 16.dp);

    /** Corner radius = half the height, so the button is a pill at this size. */
    val cornerRadius: Dp get() = height / 2

    val shape: RoundedCornerShape get() = RoundedCornerShape(cornerRadius)

    /** The label's type role at this size (M3 Expressive: label L → title M → headline S → headline L). */
    fun labelStyle(typography: Typography): TextStyle = when (this) {
        ExtraSmall, Small -> typography.labelLarge
        Medium -> typography.titleMedium
        Large -> typography.headlineSmall
        ExtraLarge -> typography.headlineLarge
    }
}

/**
 * Geometry of a connected button group: items sit [Gap] apart, and only the corners where two items
 * meet shrink to [InnerCorner]; the group's outer corners stay fully round.
 */
object ConnectedButtonShapes {

    /** Space between adjoining items. */
    val Gap: Dp = 3.dp

    /** Radius of the corners where two items meet. */
    val InnerCorner: Dp = 8.dp

    private val Outer = CornerSize(percent = 50)
    private val Inner = CornerSize(InnerCorner)

    /** Shape of item [index] of [count] (start → end; logical corners, so RTL mirrors correctly). */
    fun shapeFor(index: Int, count: Int): RoundedCornerShape {
        require(count >= 1 && index in 0 until count) { "index $index out of 0 until $count" }
        val first = index == 0
        val last = index == count - 1
        return RoundedCornerShape(
            topStart = if (first) Outer else Inner,
            bottomStart = if (first) Outer else Inner,
            topEnd = if (last) Outer else Inner,
            bottomEnd = if (last) Outer else Inner
        )
    }
}
