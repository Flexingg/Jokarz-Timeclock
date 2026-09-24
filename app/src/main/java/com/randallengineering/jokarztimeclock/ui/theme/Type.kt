package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Roboto: on Android `FontFamily.Default` is Roboto, so no font file is bundled. */
val AppFontFamily: FontFamily = FontFamily.Default

private val Baseline = Typography()

/**
 * The M3 type scale, every slot on [AppFontFamily] (the library's baseline styles are copied with the
 * family pinned, so no slot can fall back to anything else). A few slots are weighted up for the
 * dense timeclock screens; sizes stay on the M3 scale.
 */
val Typography = Typography(
    displayLarge = Baseline.displayLarge.copy(fontFamily = AppFontFamily),
    displayMedium = Baseline.displayMedium.copy(fontFamily = AppFontFamily),
    displaySmall = Baseline.displaySmall.copy(fontFamily = AppFontFamily),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = Baseline.headlineSmall.copy(fontFamily = AppFontFamily),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = Baseline.titleSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = Baseline.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = Baseline.labelLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = Baseline.labelMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

/**
 * The live shift timer: deliberately the largest type on the main screen. Monospace with tabular
 * figures so the digits never jitter sideways as they tick, and Medium weight (not Light) so the
 * strokes stay solid on any container. Kept as its own style rather than a Typography slot so M3
 * components that default to the display slots are unaffected.
 */
val TimerDisplayStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 40.sp,
    lineHeight = 48.sp,
    fontFeatureSettings = "tnum"
)

/**
 * A running timer inside a sentence-sized slot (the break clock under the hero). Same deliberate
 * monospace + tabular figures as [TimerDisplayStyle], at label size.
 */
val TimerInlineStyle = TimerDisplayStyle.copy(
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp
)

/** Durations and amounts in lists: Roboto with tabular figures, so columns of numbers line up. */
val FigureStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "tnum"
)

/** Earnings figures on the summary cards (second tier, well below [TimerDisplayStyle]). */
val SummaryFigureStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    fontFeatureSettings = "tnum"
)
