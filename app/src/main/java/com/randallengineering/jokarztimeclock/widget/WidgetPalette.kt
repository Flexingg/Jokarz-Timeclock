package com.randallengineering.jokarztimeclock.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import com.randallengineering.jokarztimeclock.ui.theme.presetColorScheme

/**
 * The widgets' colours: the very same [ColorScheme] the app resolves for its current UI theme
 * (Light / Slate / AMOLED / Emerald / Amber, or Material You wallpaper colours for Dynamic, which
 * also follows the system's light / dark), turned into ARGB ints for RemoteViews. Nothing here names
 * a palette colour: every value is a scheme role, so a new theme reaches the widgets for free.
 */
internal class WidgetPalette(
    val dark: Boolean,
    /** The card's base, the same step the app's cards use ([fluidCardColor]). */
    val card: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    /** Accent pills (labels, chips) use the container pair, like the app's chips: readable in every theme. */
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val error: Int,
    val onError: Int,
    val secondary: Int,
    val tertiary: Int,
    val outline: Int,
    /** The contour glow's tint: a light edge on dark cards, an outline-variant edge on light ones. */
    val edge: Int,
    /** The top highlight's tint (visible on dark cards; a no-op white on light ones). */
    val sheen: Int
) {
    /** The card base's opacity (0..255): a little see-through so the wallpaper shows, like the app's glass. */
    val cardAlpha: Int get() = if (dark) 224 else 238

    /** The accent glow's strength (0..255 of its baked-in alpha). */
    val glowAlpha: Int get() = if (dark) 255 else 120

    companion object {
        fun of(context: Context, theme: ThemeMode): WidgetPalette = from(scheme(context, theme))

        fun scheme(context: Context, theme: ThemeMode): ColorScheme {
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return if (theme == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                presetColorScheme(theme, systemDark)
            }
        }

        fun from(s: ColorScheme): WidgetPalette {
            val dark = s.background.luminance() < 0.5f
            return WidgetPalette(
                dark = dark,
                card = (if (dark) s.surfaceContainer else s.surfaceContainerLowest).toArgb(),
                onSurface = s.onSurface.toArgb(),
                onSurfaceVariant = s.onSurfaceVariant.toArgb(),
                primary = s.primary.toArgb(),
                onPrimary = s.onPrimary.toArgb(),
                primaryContainer = s.primaryContainer.toArgb(),
                onPrimaryContainer = s.onPrimaryContainer.toArgb(),
                error = s.error.toArgb(),
                onError = s.onError.toArgb(),
                secondary = s.secondary.toArgb(),
                tertiary = s.tertiary.toArgb(),
                outline = s.outline.toArgb(),
                edge = (if (dark) s.onSurface else s.outlineVariant).toArgb(),
                sheen = (if (dark) s.onSurface else s.surfaceContainerLowest).toArgb()
            )
        }
    }
}
