package com.randallengineering.jokarztimeclock.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every tappable part must ripple AND squash slightly when pressed. The library buttons only ripple,
 * so UI code must use the Scaled* / Expressive* wrappers (ui/components/ExpressiveButtons.kt), and a
 * custom tappable surface must use `expressiveClickable` rather than a bare `clickable`.
 * Checked on the real sources (see [SourceTree]).
 */
class PressFeedbackEnforcementTest {

    @Test
    fun noBareLibraryButtonsOutsideTheWrappers() {
        val offenders = scan(BARE_BUTTON, exempt = BUTTONS_FILE)
        assertTrue("Use the Scaled*/Expressive* buttons (ripple + press-scale):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun noBareClickableOutsideTheMotionHelpers() {
        val offenders = scan(BARE_CLICKABLE, exempt = MOTION_FILE)
        assertTrue("Use Modifier.expressiveClickable (ripple + press-scale):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun detectorsSeeTheRightCalls() {
        listOf("Button(onClick = {}) {", "TextButton(onClick = x)", "IconButton(onClick = x)", "FilledTonalIconButton(", "OutlinedButton (", "AssistChip(onClick = f", "androidx.compose.material3.Button(onClick = f)")
            .forEach { assertTrue("missed $it", BARE_BUTTON.containsMatchIn(it)) }
        listOf("ScaledButton(onClick = x)", "RadioButton(selected = a)", "ToggleButton(", "ExpressiveButton(", "ScaledAssistChip(", "import androidx.compose.material3.Button")
            .forEach { assertEquals("false positive $it", false, BARE_BUTTON.containsMatchIn(it)) }
        listOf("Modifier.clickable { x() }", ".clickable(onClick = f)", "    .clickable {")
            .forEach { assertTrue("missed $it", BARE_CLICKABLE.containsMatchIn(it)) }
        assertEquals(false, BARE_CLICKABLE.containsMatchIn("Modifier.expressiveClickable { x() }"))
    }

    private fun scan(pattern: Regex, exempt: String): List<String> {
        val files = SourceTree.mainKotlinFiles()
        assertTrue("no sources scanned", files.isNotEmpty())
        return files.filterNot { it.relative == exempt }.flatMap { file ->
            file.codeLines().filter { (_, text) -> pattern.containsMatchIn(text) }
                .map { (line, text) -> "${file.relative}:$line: ${text.trim()}" }
        }
    }

    private companion object {
        const val BUTTONS_FILE = "com/randallengineering/jokarztimeclock/ui/components/ExpressiveButtons.kt"
        const val MOTION_FILE = "com/randallengineering/jokarztimeclock/ui/components/ExpressiveMotion.kt"

        val BARE_BUTTON = Regex(
            """(?<!\w)(Button|TextButton|FilledTonalButton|OutlinedButton|ElevatedButton|IconButton|FilledTonalIconButton|FilledIconButton|OutlinedIconButton|AssistChip)\s*\("""
        )
        val BARE_CLICKABLE = Regex("""\.clickable\s*[({]""")
    }
}
