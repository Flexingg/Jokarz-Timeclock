package com.randallengineering.jokarztimeclock.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Source-level regression guard for the live shift notification.
 *
 * The status bar timer is the system chronometer (`setWhen` + `setUsesChronometer`); the app must
 * never re-post the notification on a timer (that is what broke the chip and drained the battery in
 * 2.6.x). This test reads the Kotlin sources and fails, naming the line, if a periodic update path
 * comes back — or if the chip mechanism itself (chronometer, promotion request, ProgressStyle) is
 * dropped. Comments are stripped first so documentation may still talk about what is forbidden.
 *
 * Since 2.8.2 one coarse app-side refresh is allowed (overtime money is a string, which SystemUI
 * cannot animate). `delay(` is therefore no longer banned outright: every occurrence must be one of
 * the approved >= 60 s forms in [approvedDelayArgs], and anything else — `delay(1000)`,
 * `delay(1_000L)`, `delay(16)`, `delay(someVariable)` — fails, naming the line.
 */
class NoPeriodicNotificationUpdateTest {

    private val guardedFiles = listOf(
        "LiveShiftService.kt", "NotificationHelper.kt",
        // The pure helpers the live notification is built from: no timers may hide in them either.
        "LiveChipText.kt", "LiveRefreshCadence.kt", "ShiftClockOutTarget.kt", "LiveOvertimePay.kt"
    )

    /** Pattern → why it is forbidden in the live-notification path. */
    private val forbidden = listOf(
        Regex("""postDelayed""") to "Handler.postDelayed re-post loop",
        Regex("""\bHandler\s*\(""") to "Handler (timer-driven re-posting)",
        Regex("""object\s*:\s*Handler""") to "Handler subclass",
        Regex("""\bTimer\s*\(""") to "java.util.Timer",
        Regex("""scheduleAtFixedRate|scheduleWithFixedDelay""") to "fixed-rate scheduling",
        Regex("""\bschedule\s*\(""") to "Timer/executor schedule()",
        Regex("""AlarmManager""") to "AlarmManager wakeups",
        Regex("""WorkManager""") to "WorkManager jobs",
        Regex("""setRepeating""") to "repeating alarm",
        Regex("""Thread\.sleep""") to "sleeping thread",
        Regex("""while\s*\(\s*true\s*\)""") to "infinite loop",
        Regex("""\brepeat\s*\(""") to "repeat {} tick loop",
        // `delay(` is checked separately by [delayViolations]: allowed only at a >= 60 s interval.
        Regex("""\bticker\s*\(""") to "coroutine ticker channel",
        Regex("""setShortCriticalText""") to "static short-critical-text would replace the live chip timer",
        Regex("""setColorized\s*\(\s*true""") to "colorized notifications cannot be promoted",
        Regex("""setCustom\w*ContentView|RemoteViews""") to "custom views prevent promotion",
        Regex("""oplus""") to "fake oplus.* capsule extras (nothing reads them)"
    )

    /**
     * The only arguments `delay(` may take in the guarded files. The two helpers are the
     * minute-boundary wakeups from [LiveRefreshCadence] (one per wall-clock minute, plus one at the
     * clock-out instant); their cadence is proven by `LiveRefreshCadenceTest`.
     */
    private val approvedDelayArgs = listOf(
        Regex("""^LiveRefreshCadence\.MIN_REFRESH_INTERVAL_MS$"""),
        Regex("""^LiveRefreshCadence\.nextWakeDelayMs\(.*\)$"""),
        Regex("""^LiveRefreshCadence\.delayUntilNextMinuteBoundary\(.*\)$""")
    )

    private val delayCall = Regex("""\bdelay\s*\(""")

    /** Every `delay(...)` in [code] whose argument is not an approved >= 60 s form, with its line. */
    private fun delayViolations(name: String, code: String): List<String> {
        val violations = mutableListOf<String>()
        code.lines().forEachIndexed { index, line ->
            for (match in delayCall.findAll(line)) {
                val arg = argumentAt(line, match.range.last + 1)
                if (!isApprovedDelayArg(arg)) {
                    violations += "$name:${index + 1}: [delay() shorter than " +
                        "${LiveRefreshCadence.MIN_REFRESH_INTERVAL_MS} ms or not provably >= 60 s] ${line.trim()}"
                }
            }
        }
        return violations
    }

    private fun isApprovedDelayArg(arg: String?): Boolean {
        if (arg == null) return false
        val trimmed = arg.trim()
        if (approvedDelayArgs.any { it.matches(trimmed) }) return true
        // Explicit numeric literal such as 60_000L or 120000.
        val literal = Regex("""^([0-9][0-9_]*)[lL]?$""").matchEntire(trimmed) ?: return false
        val value = literal.groupValues[1].replace("_", "").toLongOrNull() ?: return false
        return LiveRefreshCadence.isAllowedRefreshInterval(value)
    }

    /** Text between the paren opened just before [from] and its matching close, or null if unbalanced on the line. */
    private fun argumentAt(line: String, from: Int): String? {
        var depth = 1
        for (i in from until line.length) {
            when (line[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return line.substring(from, i)
            }
        }
        return null
    }

    @Test
    fun liveNotificationPathHasNoPeriodicUpdateMechanism() {
        val violations = mutableListOf<String>()
        for (name in guardedFiles) {
            val file = locate(name)
            val code = stripComments(file.readText())
            code.lines().forEachIndexed { index, line ->
                for ((pattern, reason) in forbidden) {
                    if (pattern.containsMatchIn(line)) {
                        violations += "$name:${index + 1}: [$reason] ${line.trim()}"
                    }
                }
            }
            violations += delayViolations(name, code)
        }
        if (violations.isNotEmpty()) {
            fail("Forbidden periodic-update / anti-promotion code in the live notification path:\n" +
                violations.joinToString("\n"))
        }
    }

    @Test
    fun liveNotificationKeepsChipMechanism() {
        val code = stripComments(locate("LiveShiftService.kt").readText())
        for (required in listOf(
            "setUsesChronometer(true)", "setRequestPromotedOngoing(true)", "ProgressStyle", "setOngoing(true)",
            // The clock-out countdown is system-drawn too: count-down flag plus the `when` instant.
            "setChronometerCountDown(true)", "setWhen("
        )) {
            assertTrue("LiveShiftService.kt must contain `$required`", code.contains(required))
        }
    }

    // ---------------------------------------------------------------- the delay() check itself

    /** The checker must fail — naming the line — on per-second and sub-minute delays. */
    @Test
    fun delayCheckRejectsSubMinuteDelaysAndNamesTheLine() {
        val source = listOf(
            "fun a() {",
            "    delay(1000)",
            "    delay(1_000L)",
            "    delay(16)",
            "    delay(59_999L)",
            "    delay(intervalMs)",
            "}"
        ).joinToString("\n")
        val violations = delayViolations("Sample.kt", source)
        assertEquals(violations.joinToString("\n"), 5, violations.size)
        for ((line, text) in listOf(2 to "delay(1000)", 3 to "delay(1_000L)", 4 to "delay(16)",
            5 to "delay(59_999L)", 6 to "delay(intervalMs)")) {
            assertTrue("expected a failure naming Sample.kt:$line", violations.any {
                it.startsWith("Sample.kt:$line:") && it.endsWith(text)
            })
        }
    }

    @Test
    fun delayCheckAcceptsOnlyTheApprovedMinuteForms() {
        val source = listOf(
            "delay(LiveRefreshCadence.MIN_REFRESH_INTERVAL_MS)",
            "delay(LiveRefreshCadence.nextWakeDelayMs(nowMs, clockOutAt))",
            "delay(LiveRefreshCadence.delayUntilNextMinuteBoundary(nowMs))",
            "delay(60_000L)",
            "delay(120000)"
        ).joinToString("\n")
        assertEquals(emptyList<String>(), delayViolations("Sample.kt", source))
    }

    /** The service really does use an approved delay form (so the check above is not vacuous). */
    @Test
    fun liveShiftServiceRefreshUsesTheApprovedCadence() {
        val code = stripComments(locate("LiveShiftService.kt").readText())
        assertTrue("LiveShiftService.kt should delay via LiveRefreshCadence",
            code.contains("delay(LiveRefreshCadence."))
        assertEquals(emptyList<String>(), delayViolations("LiveShiftService.kt", code))
    }

    @Test
    fun perSecondIntervalsAreNotAllowedRefreshIntervals() {
        assertFalse(LiveRefreshCadence.isAllowedRefreshInterval(1_000L))
        assertFalse(LiveRefreshCadence.isAllowedRefreshInterval(5_000L))
        assertFalse(LiveRefreshCadence.isAllowedRefreshInterval(59_999L))
        assertTrue(LiveRefreshCadence.isAllowedRefreshInterval(60_000L))
        assertTrue(LiveRefreshCadence.isAllowedRefreshInterval(3_600_000L))
    }

    /** Blanks comments while keeping line numbers intact, so reported lines match the file. */
    private fun stripComments(source: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(source) { m -> m.value.replace(Regex("[^\n]"), "") }
        return noBlock.lines().joinToString("\n") { it.replace(Regex("""//.*$"""), "") }
    }

    /**
     * Gradle runs unit tests with `app/` as the working directory; running from the repo root is
     * also supported. A guard that silently skips is useless, so a missing file is a failure.
     */
    private fun locate(name: String): File {
        val rel = "src/main/java/com/randallengineering/jokarztimeclock/engine/$name"
        val candidates = listOf(File(rel), File("app/$rel"))
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "Cannot find $name for the regression guard. Tried: " +
                    candidates.joinToString { it.absolutePath }
            )
    }
}
