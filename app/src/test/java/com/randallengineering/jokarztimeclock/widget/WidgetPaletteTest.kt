package com.randallengineering.jokarztimeclock.widget

import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import com.randallengineering.jokarztimeclock.ui.theme.presetColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widgets wear exactly the colours the app resolves for the current UI theme. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetPaletteTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun everyThemeMapsToItsOwnSchemeRoles() {
        for (mode in ThemeMode.values()) {
            if (mode == ThemeMode.DYNAMIC) continue // wallpaper colours: covered by the render test
            val scheme = presetColorScheme(mode, darkTheme = false)
            val palette = WidgetPalette.of(ctx, mode)
            assertEquals("$mode primary", scheme.primary.toArgb(), palette.primary)
            assertEquals("$mode onSurface", scheme.onSurface.toArgb(), palette.onSurface)
            assertEquals("$mode error", scheme.error.toArgb(), palette.error)
            assertEquals("$mode tertiary", scheme.tertiary.toArgb(), palette.tertiary)
        }
    }

    @Test
    fun themesActuallyDiffer() {
        val light = WidgetPalette.of(ctx, ThemeMode.LIGHT)
        val amoled = WidgetPalette.of(ctx, ThemeMode.AMOLED)
        val emerald = WidgetPalette.of(ctx, ThemeMode.EMERALD)
        assertTrue(!light.dark && amoled.dark)
        assertNotEquals(light.card, amoled.card)
        assertNotEquals(amoled.primary, emerald.primary)
        // Text always contrasts with the card it sits on.
        assertNotEquals(light.onSurface, light.card)
        assertNotEquals(amoled.onSurface, amoled.card)
    }
}
