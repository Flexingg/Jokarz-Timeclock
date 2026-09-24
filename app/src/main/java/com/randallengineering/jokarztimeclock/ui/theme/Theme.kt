package com.randallengineering.jokarztimeclock.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.randallengineering.jokarztimeclock.data.models.ThemeMode

/**
 * The shared role wiring for the dark presets. Accents always travel through scheme roles, so a
 * composable never names a palette colour: success = `tertiary`, break / banking = `secondary`,
 * overtime / destructive = `error`.
 */
private fun presetDarkScheme(
    primary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    surface: Color,
    background: Color,
    outline: Color,
    containerLowest: Color,
    containerLow: Color,
    container: Color,
    containerHigh: Color,
    containerHighest: Color
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = AmberWarning,
    onSecondary = Black,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = EmeraldSuccess,
    onTertiary = Black,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOnContainer,
    surface = surface,
    onSurface = TextPrimaryDark,
    background = background,
    onBackground = TextPrimaryDark,
    surfaceVariant = surface,
    onSurfaceVariant = TextSecondaryDark,
    outline = outline,
    outlineVariant = SlateOutlineVariant,
    surfaceContainerLowest = containerLowest,
    surfaceContainerLow = containerLow,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = containerHighest,
    error = RoseError,
    onError = White,
    errorContainer = RoseErrorContainer,
    onErrorContainer = RoseOnErrorContainer
)

private val SlateDarkColorScheme = presetDarkScheme(
    primary = PurplePrimary,
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = PurpleAccent,
    surface = SlateSurface,
    background = SlateBackground,
    outline = SlateBorder,
    containerLowest = SlateContainerLowest,
    containerLow = SlateContainerLow,
    container = SlateSurface,
    containerHigh = SlateContainerHigh,
    containerHighest = SlateBorder
)

private val AmoledDarkColorScheme = presetDarkScheme(
    primary = PurplePrimary,
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = PurpleAccent,
    surface = AmoledSurface,
    background = AmoledBlack,
    outline = AmoledBorder,
    containerLowest = AmoledBlack,
    containerLow = AmoledSurface,
    container = AmoledContainer,
    containerHigh = AmoledContainerHigh,
    containerHighest = AmoledBorder
)

private val EmeraldDarkColorScheme = presetDarkScheme(
    primary = CyberEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    surface = SlateSurface,
    background = SlateBackground,
    outline = SlateBorder,
    containerLowest = SlateContainerLowest,
    containerLow = SlateContainerLow,
    container = SlateSurface,
    containerHigh = SlateContainerHigh,
    containerHighest = SlateBorder
)

private val AmberDarkColorScheme = presetDarkScheme(
    primary = AmberGlow,
    primaryContainer = AmberContainer,
    onPrimaryContainer = AmberOnContainer,
    surface = SlateSurface,
    background = SlateBackground,
    outline = SlateBorder,
    containerLowest = SlateContainerLowest,
    containerLow = SlateContainerLow,
    container = SlateSurface,
    containerHigh = SlateContainerHigh,
    containerHighest = SlateBorder
)

/** The canonical Material 3 light scheme from the owner's brief (every value pinned by ThemeSchemeTest). */
internal val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceBright = LightSurface,
    surfaceDim = LightSurfaceDim,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

/**
 * The built-in palettes. DYNAMIC resolves here only below Android 12 (no wallpaper colours), where it
 * falls back to Slate dark / light. Split out of [JokarzTimeclockTheme] so the timer-contrast test can
 * check every preset.
 */
internal fun presetColorScheme(themeMode: ThemeMode, darkTheme: Boolean): ColorScheme = when (themeMode) {
    ThemeMode.DYNAMIC -> if (darkTheme) SlateDarkColorScheme else LightColorScheme
    ThemeMode.DARK -> SlateDarkColorScheme
    ThemeMode.AMOLED -> AmoledDarkColorScheme
    ThemeMode.EMERALD -> EmeraldDarkColorScheme
    ThemeMode.AMBER -> AmberDarkColorScheme
    ThemeMode.LIGHT -> LightColorScheme
}

/** The app's motion scheme: standard (no bounce, no overshoot). */
internal val AppMotionScheme: MotionScheme = MotionScheme.standard()

@Composable
fun JokarzTimeclockTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (themeMode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        presetColorScheme(themeMode, darkTheme)
    }

    val view = LocalView.current
    val useLightStatusBarIcons = colorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            // Derived from the resolved scheme, so DYNAMIC + a light wallpaper also gets dark icons.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = useLightStatusBarIcons
        }
    }

    // Material 3 Expressive colours, type and shapes on the STANDARD motion scheme: the owner wants
    // smooth, non-overshooting motion, so nothing here may use MotionScheme.expressive() (whose
    // spatial springs bounce). ThemeMotionTest pins this.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = AppMotionScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
