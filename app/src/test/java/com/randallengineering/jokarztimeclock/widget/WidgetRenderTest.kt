package com.randallengineering.jokarztimeclock.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Inflates and draws both home-screen widgets the way the launcher does (RemoteViews.apply), at every
 * size bucket and in several themes, so a layout the launcher would refuse ("Can't load widget", e.g.
 * a plain View or a method that is not remotable) fails here instead. Renders are saved to
 * app/build/screen-render/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class WidgetRenderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repo get() = TimeclockRepository.get(ctx)

    private fun shot(v: RemoteViews, size: WidgetSize, name: String) {
        val d = ctx.resources.displayMetrics.density
        val view = v.apply(ctx, FrameLayout(ctx))
        val wp = (size.widthDp * d).toInt(); val hp = (size.heightDp * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(wp, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hp, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, wp, hp)
        val bmp = Bitmap.createBitmap(wp, hp, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(0xFF6B7F99.toInt()) }.also { view.draw(it) }
        File("build/screen-render").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun theme(mode: ThemeMode) = repo.updateSettings(repo.state.value.settings.copy(theme = mode))

    private val clockSizes = mapOf(
        "tiny" to WidgetSize(72, 90), "wide" to WidgetSize(260, 70), "wide-narrow" to WidgetSize(146, 70),
        "medium" to WidgetSize(146, 146), "large" to WidgetSize(146, 230)
    )
    private val statsSizes = mapOf(
        "compact" to WidgetSize(150, 130), "normal" to WidgetSize(300, 120), "large" to WidgetSize(300, 190)
    )

    @Test
    fun everySizeAndStateInflatesAndDraws() {
        theme(ThemeMode.AMOLED)
        for ((n, s) in clockSizes) shot(WidgetUpdater.buildClock(ctx, s), s, "w-clock-$n-idle")
        for ((n, s) in statsSizes) shot(WidgetUpdater.buildStats(ctx, s), s, "w-stats-$n")
        repo.clockIn()
        for ((n, s) in clockSizes) shot(WidgetUpdater.buildClock(ctx, s), s, "w-clock-$n-active")
        repo.toggleBreak()
        shot(WidgetUpdater.buildClock(ctx, clockSizes.getValue("medium")), clockSizes.getValue("medium"), "w-clock-medium-break")
        repo.clockOut()
    }

    @Test
    fun everyThemeDrawsInItsOwnColours() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.EMERALD, ThemeMode.AMBER, ThemeMode.DYNAMIC)) {
            theme(mode)
            val n = mode.name.lowercase()
            shot(WidgetUpdater.buildClock(ctx, clockSizes.getValue("medium")), clockSizes.getValue("medium"), "t-clock-$n")
            shot(WidgetUpdater.buildStats(ctx, statsSizes.getValue("normal")), statsSizes.getValue("normal"), "t-stats-$n")
        }
        theme(ThemeMode.LIGHT)
    }
}

