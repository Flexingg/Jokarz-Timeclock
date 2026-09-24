package com.randallengineering.jokarztimeclock.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every UI colour must come from a theme role (`MaterialTheme.colorScheme.<role>`). This walks the
 * real sources under app/src/main/java and fails on any raw colour literal — `Color(0x…)`,
 * `Color.White` / `.Black` / `.Transparent` / … — outside ui/theme/Color.kt, and on any direct use of a
 * Color.kt palette constant outside the theme package (those only reach the UI through a role).
 *
 * Plain java.io, no Android: it runs on the JVM in every `testDebugUnitTest`. Finding no sources
 * is a FAILURE, never a skip, so a moved source tree cannot turn this into a silent pass.
 */
class ColorRoleEnforcementTest {

    @Test
    fun noRawColourLiteralOutsideTheThemeColourFile() {
        val offenders = SourceTree.mainKotlinFiles()
            .filterNot { it.relative == COLOR_FILE }
            .flatMap { file -> findRawColourLiterals(file.code).map { "${file.relative}:${it.first}: ${it.second}" } }
        assertTrue(
            "Raw colour literals outside $COLOR_FILE (use MaterialTheme.colorScheme roles):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun paletteConstantsAreOnlyReadByTheTheme() {
        // `Transparent` is "no fill", not a colour: the brief has UI code reference it from Color.kt.
        val palette = paletteNames() - NEUTRAL_NO_FILL
        assertTrue("Could not read the palette from $COLOR_FILE", palette.size >= 25)
        val offenders = SourceTree.mainKotlinFiles()
            .filterNot { it.relative.startsWith(THEME_DIR) }
            .flatMap { file ->
                file.codeLines().flatMap { (line, text) ->
                    palette.filter { Regex("\\b$it\\b").containsMatchIn(text) }.map { "${file.relative}:$line: $it" }
                }
            }
        assertTrue(
            "Palette constants used outside $THEME_DIR (read a colorScheme role instead):\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /** Guards the guard: the detector must catch each literal form and ignore role reads. */
    @Test
    fun detectorCatchesEveryLiteralForm() {
        val caught = listOf(
            "val a = Color(0xFF6750A4)",
            "tint = Color.White",
            "color = Color.Black",
            "x = Color.Transparent",
            "Color.Red.copy(alpha = 0.5f)",
            "Color(red = 1f, green = 0f, blue = 0f)",
            "android.graphics.Color.parseColor(\"#fff\")",
            "Color.hsl(1f, 1f, 0.5f)",
            "tint = androidx.compose.ui.graphics.Color(0xFF000000)"
        )
        caught.forEach { assertEquals("missed: $it", 1, findRawColourLiterals(it).size) }

        val ignored = listOf(
            "color = MaterialTheme.colorScheme.primary",
            "fun f(c: Color): Color = c",
            "import androidx.compose.ui.graphics.Color",
            "cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)",
            "val x = animateColorAsState(targetValue = scheme.error)",
            "// Color.White in a comment",
            "/* Color(0xFF000000) in a block comment */",
            "Text(\"Tap Color.White or Color(0xFF000000)\")",
            "val quote = '\"'; val c = scheme.primary"
        )
        ignored.forEach { assertEquals("false positive: $it", 0, findRawColourLiterals(it).size) }
    }

    private fun paletteNames(): List<String> {
        val colorFile = SourceTree.mainKotlinFiles().firstOrNull { it.relative == COLOR_FILE }
            ?: fail("$COLOR_FILE not found").let { return emptyList() }
        return Regex("""^val\s+(\w+)\s*=""", RegexOption.MULTILINE).findAll(colorFile.code).map { it.groupValues[1] }.toList()
    }

    companion object {
        const val THEME_DIR = "com/randallengineering/jokarztimeclock/ui/theme/"
        const val COLOR_FILE = THEME_DIR + "Color.kt"
        private const val NEUTRAL_NO_FILL = "Transparent"

        private val literal = Regex(
            // Color(…) constructor call (not SolidColor / animateColorAsState, which contain it mid-word)
            """(?<!\w)Color\s*\(""" +
                // Any named Color companion value / factory: Color.White, Color.Transparent, Color.hsl(…), …
                """|(?<![\w])Color\.(?!Companion\b)[A-Za-z]\w*"""
        )

        /** (line number, matched text) for every raw colour literal in [code]; comments and string text excluded. */
        fun findRawColourLiterals(code: String): List<Pair<Int, String>> =
            SourceTree.stripComments(code).lines().flatMapIndexed { i, line ->
                literal.findAll(line).map { (i + 1) to it.value }.toList()
            }
    }
}

/** The app's main Kotlin sources, located from the repo root (never from a hard-coded path). */
internal object SourceTree {

    class KtFile(val relative: String, val code: String) {
        /** (1-based line, text) with comments removed. */
        fun codeLines(): List<Pair<Int, String>> = stripComments(code).lines().mapIndexed { i, l -> (i + 1) to l }
    }

    private val cached: List<KtFile> by lazy { load() }

    fun mainKotlinFiles(): List<KtFile> = cached

    private fun load(): List<KtFile> {
        val root = repoRoot()
        val src = File(root, "app/src/main/java")
        val files = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        // FAIL, don't skip: an empty scan would make every rule pass vacuously.
        if (files.size < 20) fail("Expected the app's Kotlin sources under $src, found ${files.size}")
        return files.map { f -> KtFile(f.relativeTo(src).invariantSeparatorsPath, f.readText()) }
    }

    /** Walks up from the test's working directory to the directory holding settings.gradle.kts. */
    fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        fail("No settings.gradle.kts above ${System.getProperty("user.dir")}")
        throw IllegalStateException()
    }

    /**
     * The code with comments removed and the contents of string / char literals blanked (quotes kept,
     * line breaks kept so line numbers stay right). UI text such as "AMOLED Black" is therefore never
     * mistaken for a colour, and a `//` inside a URL string is not taken for a comment.
     */
    fun stripComments(code: String): String {
        val out = StringBuilder(code.length)
        var i = 0
        fun at(k: Int) = if (k < code.length) code[k] else '\u0000'
        while (i < code.length) {
            val c = code[i]
            when {
                c == '/' && at(i + 1) == '/' -> {
                    while (i < code.length && code[i] != '\n') i++
                    continue
                }
                c == '/' && at(i + 1) == '*' -> {
                    i += 2
                    while (i < code.length && !(code[i] == '*' && at(i + 1) == '/')) {
                        if (code[i] == '\n') out.append('\n')
                        i++
                    }
                    i += 2
                    continue
                }
                c == '"' || c == '\'' -> {
                    val raw = c == '"' && at(i + 1) == '"' && at(i + 2) == '"'
                    val close = if (raw) "\"\"\"" else c.toString()
                    out.append(close)
                    i += close.length
                    while (i < code.length && !code.startsWith(close, i)) {
                        if (!raw && code[i] == '\\') { out.append(' '); i++ }
                        out.append(if (code[i] == '\n') '\n' else ' ')
                        i++
                    }
                    out.append(close)
                    i += close.length
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
