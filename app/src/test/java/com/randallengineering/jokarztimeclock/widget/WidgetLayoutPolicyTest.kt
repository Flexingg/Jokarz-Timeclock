package com.randallengineering.jokarztimeclock.widget

import com.randallengineering.jokarztimeclock.widget.WidgetLayoutPolicy.Clock
import com.randallengineering.jokarztimeclock.widget.WidgetLayoutPolicy.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutPolicyTest {

    @Test
    fun clockLayoutFollowsSize() {
        assertEquals(Clock.TINY, WidgetLayoutPolicy.clock(WidgetSize(60, 60)))      // 1x1
        assertEquals(Clock.TINY, WidgetLayoutPolicy.clock(WidgetSize(70, 250)))     // 1 wide, however tall
        assertEquals(Clock.WIDE, WidgetLayoutPolicy.clock(WidgetSize(146, 70)))     // 2x1
        assertEquals(Clock.WIDE, WidgetLayoutPolicy.clock(WidgetSize(300, 70)))     // 4x1
        assertEquals(Clock.MEDIUM, WidgetLayoutPolicy.clock(WidgetSize(146, 146)))  // 2x2
        assertEquals(Clock.MEDIUM, WidgetLayoutPolicy.clock(WidgetSize(300, 146)))  // 4x2
        assertEquals(Clock.LARGE, WidgetLayoutPolicy.clock(WidgetSize(146, 220)))   // 2x3
        assertEquals(Clock.LARGE, WidgetLayoutPolicy.clock(WidgetSize(300, 300)))   // 4x4
    }

    @Test
    fun statsLayoutFollowsSize() {
        assertEquals(Stats.COMPACT, WidgetLayoutPolicy.stats(WidgetSize(146, 146)))
        assertEquals(Stats.COMPACT, WidgetLayoutPolicy.stats(WidgetSize(100, 300)))
        assertEquals(Stats.NORMAL, WidgetLayoutPolicy.stats(WidgetSize(300, 70)))
        assertEquals(Stats.NORMAL, WidgetLayoutPolicy.stats(WidgetSize(300, 146)))
        assertEquals(Stats.LARGE, WidgetLayoutPolicy.stats(WidgetSize(300, 220)))
    }

    @Test
    fun everySizeGetsALayoutAndGrowingNeverDropsToTheTinyOne() {
        for (w in 40..500 step 5) for (h in 40..500 step 5) {
            WidgetLayoutPolicy.clock(WidgetSize(w, h))
            WidgetLayoutPolicy.stats(WidgetSize(w, h))
        }
        // Widening a widget never makes it TINY again.
        for (h in 40..500 step 10) {
            var seenNonTiny = false
            for (w in 40..500 step 5) {
                val tiny = WidgetLayoutPolicy.clock(WidgetSize(w, h)) == Clock.TINY
                if (!tiny) seenNonTiny = true
                assertTrue("tiny again at $w x $h", !(seenNonTiny && tiny))
            }
        }
    }

    @Test
    fun theButtonLabelShrinksWithTheRoom() {
        // Idle, a 2x2 card: the whole word. Clocked in beside the break button: the short one.
        assertEquals("Clock In", WidgetLayoutPolicy.actionLabel(false, 146, 18, 0))
        assertEquals("Out", WidgetLayoutPolicy.actionLabel(true, 200, 18, 48))
        assertEquals("Clock Out", WidgetLayoutPolicy.actionLabel(true, 260, 16, 50))
        assertEquals(null, WidgetLayoutPolicy.actionLabel(true, 146, 18, 48))
        // Too narrow for any word: icon only.
        assertEquals(null, WidgetLayoutPolicy.actionLabel(true, 140, 20, 56))
    }

    @Test
    fun timerTextShrinksToFitSevenCharacters() {
        assertEquals(34, WidgetLayoutPolicy.timerSp(400, 18, 0, 22, 34))   // capped
        assertEquals(22, WidgetLayoutPolicy.timerSp(100, 18, 0, 22, 34))   // floored
        // A 2x1 card with an icon-only button leaves room for ~22 sp.
        assertEquals(18, WidgetLayoutPolicy.timerSp(146, 16, 52, 18, 30))
        // Always larger as the widget grows.
        var last = 0
        for (w in 100..500 step 10) {
            val sp = WidgetLayoutPolicy.timerSp(w, 20, 0, 24, 52)
            assertTrue(sp >= last); last = sp
        }
    }
}
