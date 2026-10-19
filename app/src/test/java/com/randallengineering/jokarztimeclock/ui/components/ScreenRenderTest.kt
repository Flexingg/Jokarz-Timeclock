package com.randallengineering.jokarztimeclock.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.randallengineering.jokarztimeclock.MainActivity
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real main screen (Robolectric native graphics) and checks the clock-in button is
 * painted in full on app open. Regression: v2.9.0-2.9.4 drew only a flat-bottomed sliver of it
 * (no icon, no label) until the first tap, because its pulse graphicsLayer kept the size of the
 * screen's first, transient layout pass. Renders are saved to app/build/screen-render/.
 *
 * Runs with "Reduce transparency" on: Robolectric cannot compile Haze's blur shaders, and the
 * opaque fallback is the path every pre-Android-12 phone takes anyway.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class ScreenRenderTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun launch(theme: ThemeMode): ActivityScenario<MainActivity> {
        val repo = TimeclockRepository.get(ApplicationProvider.getApplicationContext())
        repo.updateSettings(repo.state.value.settings.copy(theme = theme, reduceTransparency = true))
        return ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun clockInButtonIsFullyPaintedOnAppOpen() {
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.AMOLED)) {
            launch(theme).use { scenario ->
                compose.waitForIdle()
                val bmp = render(scenario, "main-${theme.name.lowercase()}")
                val icon = compose.onAllNodes(hasContentDescription("Clock In"), useUnmergedTree = true)
                    .fetchSemanticsNodes().single().boundsInRoot
                val density = bmp.density / 160f
                val x = icon.center.x.toInt()
                // Just above the play icon: inside the button, below where the sliver used to end.
                val y = (icon.top - 8 * density).toInt()
                // Same height, well outside the button: the bare stage.
                val stage = bmp.getPixel((icon.center.x - 110 * density).toInt(), y)
                val button = bmp.getPixel(x, y)
                assertNotEquals("$theme: clock-in button not painted above its icon (clipped?)", stage, button)
                // The play glyph must be painted on the button too.
                val glyph = bmp.getPixel(x, icon.center.y.toInt())
                assertNotEquals("$theme: play icon not painted", button, glyph)
                assertNotEquals("$theme: play icon not painted", stage, glyph)
            }
        }
    }

    @Test
    fun activeShiftRendersAfterClockIn() {
        launch(ThemeMode.AMOLED).use { scenario ->
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Clock In"), useUnmergedTree = true).performClick()
            compose.mainClock.advanceTimeBy(2000)
            compose.waitForIdle()
            render(scenario, "active-shift")
            compose.onNode(hasText("Clock Out"), useUnmergedTree = true).assertExists()
            // Leave the shared repository clocked out for the next test.
            compose.onNode(hasText("Clock Out"), useUnmergedTree = true).performClick()
            compose.waitForIdle()
        }
    }

    /** The whole page in one frame (a very tall window), for reviewing the cards below the fold. */
    @Test
    fun renderFullPage() {
        org.robolectric.RuntimeEnvironment.setQualifiers("+h2200dp")
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.AMOLED)) {
            launch(theme).use { scenario ->
                compose.waitForIdle()
                render(scenario, "full-${theme.name.lowercase()}")
            }
        }
    }

    private fun render(scenario: ActivityScenario<MainActivity>, name: String): Bitmap {
        lateinit var bmp: Bitmap
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            bmp.density = activity.resources.displayMetrics.densityDpi
            view.draw(Canvas(bmp))
        }
        File("build/screen-render").apply { mkdirs() }.resolve("$name.png")
            .outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }
}
