package com.randallengineering.jokarztimeclock.engine

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
 */
class NoPeriodicNotificationUpdateTest {

    private val guardedFiles = listOf("LiveShiftService.kt", "NotificationHelper.kt")

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
        Regex("""\bdelay\s*\(""") to "coroutine delay() tick loop",
        Regex("""\bticker\s*\(""") to "coroutine ticker channel",
        Regex("""setShortCriticalText""") to "static short-critical-text would replace the live chip timer",
        Regex("""setColorized\s*\(\s*true""") to "colorized notifications cannot be promoted",
        Regex("""setCustom\w*ContentView|RemoteViews""") to "custom views prevent promotion",
        Regex("""oplus""") to "fake oplus.* capsule extras (nothing reads them)"
    )

    @Test
    fun liveNotificationPathHasNoPeriodicUpdateMechanism() {
        val violations = mutableListOf<String>()
        for (name in guardedFiles) {
            val file = locate(name)
            stripComments(file.readText()).lines().forEachIndexed { index, line ->
                for ((pattern, reason) in forbidden) {
                    if (pattern.containsMatchIn(line)) {
                        violations += "$name:${index + 1}: [$reason] ${line.trim()}"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            fail("Forbidden periodic-update / anti-promotion code in the live notification path:\n" +
                violations.joinToString("\n"))
        }
    }

    @Test
    fun liveNotificationKeepsChipMechanism() {
        val code = stripComments(locate("LiveShiftService.kt").readText())
        for (required in listOf("setUsesChronometer(true)", "setRequestPromotedOngoing(true)", "ProgressStyle", "setOngoing(true)")) {
            assertTrue("LiveShiftService.kt must contain `$required`", code.contains(required))
        }
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
