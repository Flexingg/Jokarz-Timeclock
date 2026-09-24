package com.randallengineering.jokarztimeclock.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Type and icon rules, checked on the real sources (see [SourceTree]):
 *  - no ad-hoc `fontSize = …` in UI code: text uses the M3 type roles (`MaterialTheme.typography.*`)
 *    or a named style from ui/theme/Type.kt;
 *  - no `FontFamily.*` outside the theme: the app font is Roboto (FontFamily.Default on Android) and
 *    the only deliberate exception, the monospace timer, lives in Type.kt as TimerDisplayStyle;
 *  - icons come from the Rounded set (`Icons.Rounded` / `Icons.AutoMirrored.Rounded`).
 */
class TypeAndIconEnforcementTest {

    @Test
    fun noAdHocFontSizesOutsideTheTheme() = assertNoMatch(
        Regex("""\bfontSize\s*="""),
        "Ad-hoc font sizes (use MaterialTheme.typography roles)"
    )

    @Test
    fun noFontFamilyOverridesOutsideTheTheme() = assertNoMatch(
        Regex("""\bFontFamily\."""),
        "Font family overrides (the app font is Roboto; styles live in Type.kt)"
    )

    @Test
    fun iconsComeFromTheRoundedSet() = assertNoMatch(
        Regex("""\bIcons\.(Filled|Outlined|Sharp|TwoTone|Default)\b|\bIcons\.AutoMirrored\.(Filled|Outlined|Sharp|TwoTone|Default)\b|material\.icons\.(filled|outlined|sharp|twotone)\."""),
        "Non-Rounded icons"
    )

    @Test
    fun everyTypeSlotIsRobotoAndOnlyTheTimerIsMonospace() {
        val t = Typography
        val slots = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall
        )
        slots.forEachIndexed { i, style -> assertEquals("slot $i", FontFamily.Default, style.fontFamily) }
        assertEquals(FontFamily.Default, FigureStyle.fontFamily)
        assertEquals(FontFamily.Default, SummaryFigureStyle.fontFamily)
        // The deliberate exception: the live timer (and its inline twin) keep monospace digits.
        assertEquals(FontFamily.Monospace, TimerDisplayStyle.fontFamily)
        assertEquals(FontFamily.Monospace, TimerInlineStyle.fontFamily)
    }

    private fun assertNoMatch(pattern: Regex, what: String) {
        val files = SourceTree.mainKotlinFiles()
        assertTrue("no sources scanned", files.isNotEmpty())
        val offenders = files
            .filterNot { it.relative.startsWith(ColorRoleEnforcementTest.THEME_DIR) }
            .flatMap { file ->
                file.codeLines().filter { (_, text) -> pattern.containsMatchIn(text) }
                    .map { (line, text) -> "${file.relative}:$line: ${text.trim()}" }
            }
        assertTrue("$what:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
