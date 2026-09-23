package com.randallengineering.jokarztimeclock.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.randallengineering.jokarztimeclock.data.models.ThemeMode

private val SlateDarkColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = PurpleAccent,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    background = SlateBackground,
    onBackground = TextPrimaryDark,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateBorder,
    surfaceContainerLowest = SlateContainerLowest,
    surfaceContainerLow = SlateContainerLow,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = SlateContainerHigh,
    surfaceContainerHighest = SlateBorder,
    error = RoseError,
    onError = Color.White
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = PurpleAccent,
    surface = AmoledSurface,
    onSurface = TextPrimaryDark,
    background = AmoledBlack,
    onBackground = TextPrimaryDark,
    surfaceVariant = AmoledSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = AmoledBorder,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurface,
    surfaceContainer = AmoledContainer,
    surfaceContainerHigh = AmoledContainerHigh,
    surfaceContainerHighest = AmoledBorder,
    error = RoseError,
    onError = Color.White
)

private val EmeraldDarkColorScheme = darkColorScheme(
    primary = CyberEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFF6EE7B7),
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    background = SlateBackground,
    onBackground = TextPrimaryDark,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateBorder,
    surfaceContainerLowest = SlateContainerLowest,
    surfaceContainerLow = SlateContainerLow,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = SlateContainerHigh,
    surfaceContainerHighest = SlateBorder,
    error = RoseError,
    onError = Color.White
)

private val AmberDarkColorScheme = darkColorScheme(
    primary = AmberGlow,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFCD34D),
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    background = SlateBackground,
    onBackground = TextPrimaryDark,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateBorder,
    surfaceContainerLowest = SlateContainerLowest,
    surfaceContainerLow = SlateContainerLow,
    surfaceContainer = SlateSurface,
    surfaceContainerHigh = SlateContainerHigh,
    surfaceContainerHighest = SlateBorder,
    error = RoseError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D5FF),
    onPrimaryContainer = PurplePrimaryDark,
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE8EDF3),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    error = RoseError,
    onError = Color.White
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

    // Expressive-leaning shapes: generous, obviously rounded surfaces everywhere.
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp)
    )

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

    // Material 3 Expressive: same colours, typography and shapes, plus the expressive motion scheme
    // (MaterialTheme.motionScheme) that the hero's springs read.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        shapes = shapes,
        content = content
    )
}
