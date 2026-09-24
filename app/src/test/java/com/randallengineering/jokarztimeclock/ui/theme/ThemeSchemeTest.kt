package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the owner's Material 3 light scheme value for value, so a silent palette edit (or a "rounded"
 * hex) fails the build. The pre-Android-12 DYNAMIC fallback in light mode must be the same scheme.
 */
class ThemeSchemeTest {

    /** The 25 values of the brief, verbatim. */
    private val brief: List<Pair<String, (ColorScheme) -> Color>> = listOf(
        "primary #6750A4" to { it.primary },
        "onPrimary #FFFFFF" to { it.onPrimary },
        "primaryContainer #EADDFF" to { it.primaryContainer },
        "onPrimaryContainer #21005D" to { it.onPrimaryContainer },
        "secondary #635A75" to { it.secondary },
        "secondaryContainer #E8DEF8" to { it.secondaryContainer },
        "onSecondaryContainer #1D192B" to { it.onSecondaryContainer },
        "tertiaryContainer #FFD8E4" to { it.tertiaryContainer },
        "onTertiaryContainer #31111D" to { it.onTertiaryContainer },
        "surface #FEF7FF" to { it.surface },
        "surfaceContainerLow #F7F2FA" to { it.surfaceContainerLow },
        "surfaceContainer #F3EDF7" to { it.surfaceContainer },
        "surfaceContainerHigh #ECE6F0" to { it.surfaceContainerHigh },
        "surfaceContainerHighest #E6E0E9" to { it.surfaceContainerHighest },
        "onSurface #1D1B20" to { it.onSurface },
        "onSurfaceVariant #49454F" to { it.onSurfaceVariant },
        "outline #79747E" to { it.outline },
        "outlineVariant #CAC4D0" to { it.outlineVariant },
        "inverseSurface #322F35" to { it.inverseSurface },
        "inverseOnSurface #F5EFF7" to { it.inverseOnSurface },
        "inversePrimary #D0BCFF" to { it.inversePrimary },
        "error #B3261E" to { it.error },
        "onError #FFFFFF" to { it.onError },
        "errorContainer #F9DEDC" to { it.errorContainer },
        "onErrorContainer #410E0B" to { it.onErrorContainer }
    )

    @Test
    fun briefHasExactlyTwentyFiveValues() {
        assertEquals(25, brief.size)
        assertEquals(25, brief.map { it.first.substringBefore(' ') }.toSet().size)
    }

    @Test
    fun lightSchemeMatchesTheBriefExactly() {
        assertMatchesBrief(presetColorScheme(ThemeMode.LIGHT, darkTheme = false), "LIGHT")
    }

    @Test
    fun dynamicFallbackInLightModeIsTheSameScheme() {
        // Below Android 12 there are no wallpaper colours, so DYNAMIC + light falls back here.
        assertMatchesBrief(presetColorScheme(ThemeMode.DYNAMIC, darkTheme = false), "DYNAMIC(light)")
    }

    @Test
    fun lightPresetIgnoresTheSystemDarkFlag() {
        assertMatchesBrief(presetColorScheme(ThemeMode.LIGHT, darkTheme = true), "LIGHT(system dark)")
    }

    /**
     * The UI draws its success / break / overtime accents through `tertiary` / `secondary` / `error`
     * (never a palette constant), so in every preset those roles must be readable as text on `surface`
     * (WCAG 3:1, the large / bold text floor) and distinct from each other.
     */
    @Test
    fun accentRolesAreReadableAndDistinctInEveryPreset() {
        val failures = mutableListOf<String>()
        for (mode in ThemeMode.values()) for (dark in listOf(false, true)) {
            val s = presetColorScheme(mode, dark)
            val accents = mapOf("tertiary" to s.tertiary, "secondary" to s.secondary, "error" to s.error)
            accents.forEach { (role, c) ->
                val ratio = contrast(c, s.surface)
                if (ratio < 3.0) failures += "$mode dark=$dark $role on surface %.2f:1".format(ratio)
            }
            if (accents.values.map { it.toArgb() }.toSet().size != 3) failures += "$mode dark=$dark accents collide"
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    private fun assertMatchesBrief(scheme: ColorScheme, name: String) {
        val mismatches = brief.mapNotNull { (label, role) ->
            val want = label.substringAfter('#').toLong(16).toInt() or (0xFF shl 24)
            val got = role(scheme).toArgb()
            if (got != want) "$label but was #%06X".format(got and 0xFFFFFF) else null
        }
        assertTrue("$name differs from the brief: ${mismatches.joinToString()}", mismatches.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
