package com.randallengineering.jokarztimeclock.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.randallengineering.jokarztimeclock.data.repository.TimeclockRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
/**
 * Inflates and draws both home-screen widgets the way the launcher does (RemoteViews.apply), so a
 * layout the launcher would refuse ("Can't load widget", e.g. a plain View) fails here instead.
 * Renders are saved to app/build/screen-render/.
 */
class WidgetRenderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun shot(v: android.widget.RemoteViews, w: Int, h: Int, name: String) {
        val d = ctx.resources.displayMetrics.density
        val parent = FrameLayout(ctx)
        val view = v.apply(ctx, parent)
        val wp = (w * d).toInt(); val hp = (h * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(wp, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hp, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, wp, hp)
        val bmp = Bitmap.createBitmap(wp, hp, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { drawColor(0xFF6B7F99.toInt()) }.also { view.draw(it) }
        File("build/screen-render").apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun widgetsInflateAndDrawInEveryState() {
        shot(WidgetUpdater.buildClock(ctx), 180, 180, "w-clock-idle")
        shot(WidgetUpdater.buildStats(ctx), 340, 130, "w-stats")
        TimeclockRepository.get(ctx).clockIn()
        shot(WidgetUpdater.buildClock(ctx), 180, 180, "w-clock-active")
        TimeclockRepository.get(ctx).clockOut()
    }
}
