package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The ONLY file in app/src/main that may spell a colour literal (ColorRoleEnforcementTest fails the
 * build otherwise). Composables never read these values directly: they are wired into the scheme
 * roles in Theme.kt, and UI code reads `MaterialTheme.colorScheme.<role>`.
 */

// ---- Canonical LIGHT scheme (the owner's Material 3 brief; exact values, checked by ThemeSchemeTest) ----

val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)

val LightSecondary = Color(0xFF635A75)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D192B)

val LightTertiaryContainer = Color(0xFFFFD8E4)
val LightOnTertiaryContainer = Color(0xFF31111D)

val LightSurface = Color(0xFFFEF7FF)
val LightSurfaceContainerLow = Color(0xFFF7F2FA)
val LightSurfaceContainer = Color(0xFFF3EDF7)
val LightSurfaceContainerHigh = Color(0xFFECE6F0)
val LightSurfaceContainerHighest = Color(0xFFE6E0E9)

val LightOnSurface = Color(0xFF1D1B20)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)
val LightOutlineVariant = Color(0xFFCAC4D0)

val LightInverseSurface = Color(0xFF322F35)
val LightInverseOnSurface = Color(0xFFF5EFF7)
val LightInversePrimary = Color(0xFFD0BCFF)

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)

// Roles the brief leaves open, filled with the Material 3 baseline tones that belong to the same
// purple palette (so nothing falls back to an unrelated library default).
val LightOnSecondary = Color(0xFFFFFFFF)
val LightTertiary = Color(0xFF7D5260)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFDED8E1)

// ---- Neutral ink ----

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

/** "No fill" for an unselected segment or an outlined button's container. */
val Transparent = Color.Transparent

// ---- Dark presets (Slate / AMOLED / Emerald / Amber) ----

val PurplePrimary = Color(0xFF9333EA)
val PurplePrimaryDark = Color(0xFF581C87)
val PurpleAccent = Color(0xFFC084FC)

val SlateBackground = Color(0xFF0F172A)
val SlateSurface = Color(0xFF1E293B)
val SlateCard = Color(0xFF1E293B)
val SlateBorder = Color(0xFF334155)
val SlateOutlineVariant = Color(0xFF475569)

/** Success accent in the dark presets; composables reach it as `tertiary`. */
val EmeraldSuccess = Color(0xFF10B981)
val EmeraldContainer = Color(0xFF064E3B)
val EmeraldOnContainer = Color(0xFF6EE7B7)

/** Overtime / destructive accent in the dark presets; composables reach it as `error`. */
val RoseError = Color(0xFFF43F5E)
val RoseErrorContainer = Color(0xFF881337)
val RoseOnErrorContainer = Color(0xFFFFE4E6)

/** Break / banking accent in the dark presets; composables reach it as `secondary`. */
val AmberWarning = Color(0xFFF59E0B)
val AmberContainer = Color(0xFF78350F)
val AmberOnContainer = Color(0xFFFCD34D)

val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextMutedDark = Color(0xFF64748B)

val AmoledBlack = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0A)
val AmoledBorder = Color(0xFF262626)

val CyberEmerald = Color(0xFF059669)
val AmberGlow = Color(0xFFD97706)

// Tonal container steps for the Slate-based dark palettes (the highest step is SlateBorder).
val SlateContainerLowest = Color(0xFF0B1220)
val SlateContainerLow = Color(0xFF172033)
val SlateContainerHigh = Color(0xFF273449)

// Tonal container steps for AMOLED (the highest step is AmoledBorder).
val AmoledContainer = Color(0xFF121212)
val AmoledContainerHigh = Color(0xFF1A1A1A)
