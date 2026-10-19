package com.randallengineering.jokarztimeclock.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

    /**
     * Dialogs are separate windows that Robolectric cannot draw, so this renders the dialog's
     * styled parts (glass container + contour, title, pill switches, text field, buttons) on the
     * page, with the same tokens ExpressiveAlertDialog uses.
     */
    @Test
    fun renderDialogLook() {
        for (theme in listOf(ThemeMode.AMOLED, ThemeMode.LIGHT)) {
            launch(theme).use { scenario ->
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    val root = activity.window.decorView as android.view.ViewGroup
                    val host = androidx.compose.ui.platform.ComposeView(activity).apply {
                        setContent {
                            com.randallengineering.jokarztimeclock.ui.theme.JokarzTimeclockTheme(themeMode = theme, reduceTransparency = true) {
                                DialogLookPreview()
                            }
                        }
                    }
                    root.addView(host, android.view.ViewGroup.LayoutParams(-1, -1))
                }
                compose.waitForIdle()
                render(scenario, "dialog-look-${theme.name.lowercase()}")
            }
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


@androidx.compose.runtime.Composable
private fun DialogLookPreview() {
    val s = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = androidx.compose.ui.Modifier.fillMaxSize().background(s.scrim.copy(alpha = 0.55f))
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
            color = fluidGlassFill(com.randallengineering.jokarztimeclock.ui.theme.FluidGlass.thick),
            border = fluidContour(),
            modifier = androidx.compose.ui.Modifier.padding(24.dp)
        ) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(24.dp)) {
                androidx.compose.material3.Text("App Settings & Geofence", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(16.dp))
                for ((label, on) in listOf("Privacy Mode (Hide Dollar Amounts)" to true, "Haptic Vibration Feedback" to false)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        FluidSwitch(checked = on, onCheckedChange = {})
                    }
                }
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(value = "10.0", onValueChange = {}, label = { androidx.compose.material3.Text("Standard shift hours") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(20.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.End)) {
                    ScaledTextButton(onClick = {}) { androidx.compose.material3.Text("Cancel") }
                    ScaledButton(onClick = {}) { androidx.compose.material3.Text("Save") }
                }
            }
        }
    }
}
