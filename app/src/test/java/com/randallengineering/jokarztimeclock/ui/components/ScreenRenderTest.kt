package com.randallengineering.jokarztimeclock.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.randallengineering.jokarztimeclock.MainActivity
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
 * screen's first, transient layout pass. The render is also saved to app/build/screen-render/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class ScreenRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun clockInButtonIsFullyPaintedOnAppOpen() {
        compose.waitForIdle()
        val bmp = render()
        File("build/screen-render").apply { mkdirs() }.resolve("main-screen.png")
            .outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val icon = compose.onAllNodes(hasContentDescription("Clock In"), useUnmergedTree = true)
            .fetchSemanticsNodes().single().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val x = icon.center.x.toInt()
        // Just above the play icon: inside the cookie, below where the sliver used to end.
        val y = (icon.top - 8 * density).toInt()
        // Same height, well outside the cookie: the bare stage.
        val stage = bmp.getPixel((icon.center.x - 110 * density).toInt(), y)
        val button = bmp.getPixel(x, y)
        assertNotEquals("clock-in button not painted above its icon (clipped?)", stage, button)
        // The play glyph (onPrimary) must be painted on the button too.
        val glyph = bmp.getPixel(x, icon.center.y.toInt())
        assertNotEquals("play icon not painted", button, glyph)
        assertNotEquals("play icon not painted", stage, glyph)
    }

    @Test
    fun activeShiftRendersAfterClockIn() {
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Clock In"), useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        File("build/screen-render").apply { mkdirs() }.resolve("active-shift.png")
            .outputStream().use { render().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNode(hasText("Clock Out"), useUnmergedTree = true).assertExists()
        // Leave the shared repository clocked out for the next test.
        compose.onNode(hasText("Clock Out"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun render(): Bitmap {
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }
}
