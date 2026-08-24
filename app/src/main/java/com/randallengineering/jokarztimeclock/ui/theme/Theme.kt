package com.randallengineering.jokarztimeclock.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
    error = RoseError,
    onError = Color.White
)

@Composable
fun JokarzTimeclockTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        ThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) SlateDarkColorScheme else LightColorScheme
            }
        }
        ThemeMode.DARK -> SlateDarkColorScheme
        ThemeMode.AMOLED -> AmoledDarkColorScheme
        ThemeMode.EMERALD -> EmeraldDarkColorScheme
        ThemeMode.AMBER -> AmberDarkColorScheme
        ThemeMode.LIGHT -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (themeMode == ThemeMode.LIGHT)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
