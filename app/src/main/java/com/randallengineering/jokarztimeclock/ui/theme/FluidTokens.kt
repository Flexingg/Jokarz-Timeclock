package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/*
 * "Living Flow" (ColorOS 17-style fluid) design tokens: one corner family, one glass scale, one
 * light scale. Pure values except LocalFluid, so they are readable on the JVM.
 *
 * Interpretation notes: OPPO publishes no numeric tokens, so these are estimates. Colours are NOT
 * here on purpose: every fill and glow is derived from a MaterialTheme colour role at an alpha from
 * this file, so the owner's palettes (and ColorRoleEnforcementTest) still decide every hue. Motion
 * stays on the owner's no-bounce policy (AppMotion): the "rebound" of the reference spec is a
 * non-overshooting ease here.
 */

/** Corner radii: one continuous family, nested corners = outer - padding. */
object FluidRadius {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    /** List rows, text fields. */
    val md: Dp = 16.dp
    /** Cards, grouped lists, widgets. */
    val lg: Dp = 24.dp
    /** Dialogs, the hero stage, large panels. */
    val xl: Dp = 32.dp
}

/** One frosted-glass level: backdrop blur and how opaque its fill is in light / dark. */
@Immutable
data class GlassLevel(val blur: Dp, val lightFill: Float, val darkFill: Float)

/** The glass scale. Content stays on calm, near-opaque cards; only floating chrome is glass. */
object FluidGlass {
    /** Floating island, top bar, chips over content. */
    val thin = GlassLevel(blur = 24.dp, lightFill = 0.62f, darkFill = 0.55f)
    /** Menus, popovers. */
    val regular = GlassLevel(blur = 32.dp, lightFill = 0.72f, darkFill = 0.66f)
    /** Dialogs, sheets. */
    val thick = GlassLevel(blur = 44.dp, lightFill = 0.86f, darkFill = 0.82f)
}

/** Light used as signal: contour edges, touch glow, the ambient wash and the hero's halo. */
object FluidLight {
    /** Contour glow (1 dp edge, top -> bottom) in dark mode. */
    const val CONTOUR_DARK_TOP = 0.16f
    const val CONTOUR_DARK_BOTTOM = 0.04f
    /** Contour glow in light mode (an outline-variant edge on near-white cards). */
    const val CONTOUR_LIGHT_TOP = 0.55f
    const val CONTOUR_LIGHT_BOTTOM = 0.22f
    /** A selected / active element's accent edge. */
    const val CONTOUR_SELECTED = 0.6f

    /** Radial highlight under a finger, as a fraction of the element's short side. */
    const val TOUCH_GLOW_RADIUS = 1.2f
    const val TOUCH_GLOW_ALPHA_LIGHT = 0.14f
    const val TOUCH_GLOW_ALPHA_DARK = 0.10f
    const val TOUCH_GLOW_FADE_MS = 250

    /** The primary-coloured wash behind the top of the screen. */
    const val AMBIENT_ALPHA_LIGHT = 0.10f
    const val AMBIENT_ALPHA_DARK = 0.20f

    /** Glow is ~30 % calmer in dark mode. */
    const val DARK_GLOW_FACTOR = 0.7f
}

/**
 * Where the glass is allowed to blur. [glass] is false with "Reduce transparency" on or below
 * Android 12 (no RenderEffect): every glass surface then falls back to an opaque fill and a plain
 * contour. [hazeState] is the blurred backdrop (the scrolling content), null outside the main screen.
 */
@Immutable
data class FluidEnvironment(val glass: Boolean = true, val hazeState: HazeState? = null)

val LocalFluid = staticCompositionLocalOf { FluidEnvironment() }
