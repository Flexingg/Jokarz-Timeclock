package com.randallengineering.jokarztimeclock.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Golden-file + linkage tests for the Tasker profile the app exports.
 *
 * The file Tasker refused (`Error details: Missing event type`) was structurally wrong in ways that
 * only show up on a phone, so everything checkable off-device is checked here against the DOM:
 *
 *  - the document is well-formed and matches the committed golden file byte for byte;
 *  - it contains **exactly one** `<Profile>` linked to a `<Task>` in the same file, which is what
 *    makes it a Profile file that "Import Profile" will list at all;
 *  - the trigger's `<code>` is a real **Profile Event** code (`599`, Intent Received) and its
 *    `arg0` is the exact intent action the app broadcasts;
 *  - the task's action codes are real **Task Action** codes with the right typed arguments;
 *  - the action string is the same one `TaskerBridge` sends, verified against its source.
 *
 * The schema and the source of every constant are in `docs/TASKER-FORMAT.md`.
 */
class TaskerProfileXmlTest {

    /** Injected timestamp, so the golden file is a byte-exact comparison. */
    private val goldenStamp = 1_724_658_000_000L

    private val goldenPath = "/tasker/Jokarz_Timeclock.prf.xml"

    private fun generated(): String = TaskerProfileExport.profileXml(goldenStamp)

    private fun golden(): String {
        val stream = javaClass.getResourceAsStream(goldenPath)
        if (stream == null) {
            fail("Missing golden file on the test classpath: $goldenPath")
            return ""
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        try {
            return factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
                .documentElement
        } catch (e: Exception) {
            throw AssertionError("the exported Tasker XML is not well-formed: ${e.message}", e)
        }
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }

    private fun Element.child(tag: String): Element? = children(tag).firstOrNull()

    private fun Element.text(tag: String): String? = child(tag)?.textContent?.trim()

    /**
     * A context's or action's arguments are `<Str>`/`<Int>` elements named by their `sr` attribute,
     * not by their tag name: `<Str sr="arg0" ve="3">…</Str>`. Reading them by tag returns null.
     * A `<Str>` carries its value as text, an `<Int>` in its `val` attribute (`textContent` is empty).
     */
    private fun Element.arg(name: String): String? {
        val element = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
            .firstOrNull { it.getAttribute("sr") == name }
            ?: return null
        return if (element.tagName == "Int") element.getAttribute("val") else element.textContent?.trim()
    }

    // ── golden file ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the generated profile matches the committed golden file byte for byte`() {
        assertEquals(
            "The exported Tasker XML changed. If that is deliberate, re-record " +
                "app/src/test/resources$goldenPath and update docs/TASKER-FORMAT.md.",
            golden(),
            generated()
        )
    }

    @Test
    fun `the golden file parses and its root is TaskerData`() {
        val root = parse(golden())
        assertEquals("TaskerData", root.tagName)
        assertTrue("TaskerData must carry dvi", root.getAttribute("dvi").isNotEmpty())
        assertTrue("TaskerData must carry tv", root.getAttribute("tv").isNotEmpty())
        // dmetric is only needed when the file has a Scene; a profile file must not need one.
        assertTrue("a profile file needs no <dmetric>", root.children("dmetric").isEmpty())
    }

    @Test
    fun `the file is a Tasker Profile file, not a Data Backup`() {
        val root = parse(generated())
        assertEquals(
            "exactly one <Profile> node: two would make this a Data Backup, which " +
                "'Import Profile' never lists",
            1,
            root.children("Profile").size
        )
        assertTrue("a profile file may not contain a project node", root.children("Project").isEmpty())
        assertTrue(
            "the file must be named for the Profile export type",
            TaskerProfileExport.FILE_NAME.endsWith(".prf.xml")
        )
    }

    // ── the trigger ───────────────────────────────────────────────────────────────────────────

    /** Tasker's Profile Events table (docs/TASKER-FORMAT.md, source S1). */
    private val profileEventCodes = setOf(
        2, 4, 6, 7, 8, 134, 135, 136, 201, 203, 205, 206, 208, 210, 215, 216, 220, 222, 224, 226, 228,
        230, 300, 302, 303, 304, 305, 306, 307, 309, 411, 413, 422, 424, 425, 426, 427, 428, 429, 444,
        445, 446, 447, 448, 450, 451, 453, 460, 461, 462, 463, 464, 599, 1000, 2000, 2003, 2005, 2010,
        2050, 2075, 2076, 2077, 2078, 2079, 2080, 2081, 2083, 2084, 2085, 2088, 2091, 2092, 2093, 2094,
        2095, 2096, 3000, 3001, 3050, 3060, 3071
    )

    @Test
    fun `the trigger is an Intent Received event, not a task-action code`() {
        val profile = parse(generated()).children("Profile").single()
        val event = profile.child("Event")
        if (event == null) {
            fail("the profile has no <Event> trigger — this is exactly the 'Missing event type' failure")
            return
        }

        assertEquals("the trigger must be the only context on the profile", 1, profile.children("Event").size)
        assertEquals("Event sr must number the context", "con0", event.getAttribute("sr"))
        assertTrue("Event must carry ve", event.getAttribute("ve").isNotEmpty())

        val code = event.text("code")?.toIntOrNull()
        assertNotNull("the <Event> must carry a numeric <code>: without it Tasker has no event type", code)
        val resolved = requireNotNull(code)
        assertTrue(
            "code $resolved is not in Tasker's Profile Events table, so Tasker reports " +
                "'Missing event type'. (331 — what the pre-2.8.1 export wrote — is the Task Action 'Auto-Sync'.)",
            profileEventCodes.contains(resolved)
        )
        assertEquals(TaskerProfileExport.CODE_EVENT_INTENT_RECEIVED, resolved)
        assertFalse("331 must never come back as an event code", resolved == 331)
    }

    @Test
    fun `the trigger listens for the exact action the app broadcasts`() {
        val event = parse(generated()).children("Profile").single().child("Event")
        assertNotNull("the profile needs an <Event> trigger", event)
        val trigger = requireNotNull(event)

        val action = trigger.arg("arg0")
        assertEquals("arg0 of an Intent Received event is the intent action", TaskerContract.ACTION_EVENT, action)
        assertEquals("com.randallengineering.jokarztimeclock.EVENT", action)
        assertFalse(
            "the pre-2.8.1 export put a package name in arg0",
            action == TaskerContract.TASKER_PACKAGE
        )

        // The rest of the Intent Received argument set, as in the known-good exports.
        assertEquals("arg1 is the priority", "0", trigger.arg("arg1"))
        assertEquals("arg2 is the stop-event flag", "0", trigger.arg("arg2"))
        assertTrue("arg3 (category) may be empty", trigger.arg("arg3") == "")
        assertTrue("arg4 (data filter) may be empty", trigger.arg("arg4") == "")
    }

    // ── the link to the task ──────────────────────────────────────────────────────────────────

    @Test
    fun `the profile links to a task that exists in the same file and has actions`() {
        val root = parse(generated())
        val profile = root.children("Profile").single()
        val taskId = profile.text("mid0")
        if (taskId == null) {
            fail("the profile has no <mid0>, so it runs no task")
            return
        }
        assertNull("this export has no exit task", profile.text("mid1"))

        val tasks = root.children("Task")
        assertEquals(1, tasks.size)
        val task = tasks.single()
        assertEquals("mid0 must point at the id of the task in this file", taskId, task.text("id"))
        assertTrue("the linked task must have at least one <Action>", task.children("Action").isNotEmpty())
    }

    @Test
    fun `the task's actions use task-action codes`() {
        val task = parse(generated()).children("Task").single()
        val actionCodes = setOf(547, 877, 410, 129, 548, 30, 123, 130, 523)
        for (action in task.children("Action")) {
            val code = action.text("code")?.toIntOrNull()
            assertNotNull("every <Action> needs a numeric <code>", code)
            val resolved = requireNotNull(code)
            assertTrue(
                "action code $resolved must be in Tasker's Task Actions table (the pre-2.8.1 export used " +
                    "130 = Perform Task with a shell command in arg0)",
                actionCodes.contains(resolved)
            )
            assertTrue(
                "Action must carry sr and ve",
                action.getAttribute("sr").isNotEmpty() && action.getAttribute("ve").isNotEmpty()
            )
        }
        assertEquals(
            "the starter task writes the two event variables",
            listOf("547", "547"),
            task.children("Action").map { it.text("code") }
        )
    }

    @Test
    fun `typed argument elements follow the attribute rules Tasker reads`() {
        val xml = generated()
        // <Int> carries its value in `val`; `dvi` is an element-format attribute, never a value one.
        for (match in Regex("""<Int\b[^>]*>""").findAll(xml)) {
            val tag = match.value
            assertTrue("$tag must carry val=", tag.contains("val=\""))
            assertFalse("$tag must not use dvi= as a value attribute", tag.contains("dvi="))
        }
        for (match in Regex("""<Str\b[^>]*>""").findAll(xml)) {
            val tag = match.value
            assertTrue("$tag must carry sr= and ve=", tag.contains("sr=\"") && tag.contains("ve=\""))
            assertFalse("$tag must not use val= (Str carries its value as text)", tag.contains("val="))
        }
    }

    @Test
    fun `the task reads the same variable names the app's extras produce`() {
        val task = parse(generated()).children("Task").single()
        val written = task.children("Action").map { it.arg("arg1") }

        // Tasker exposes each intent extra as a local variable named by the documented rule.
        val expected = TaskerContract.eventExtras("clock_in", "geofence", goldenStamp).keys
            .map { TaskerContract.taskerVariableName(it) }
        assertTrue(
            "the task reads an event variable the app never sends: $written",
            expected.containsAll(written)
        )
        assertEquals(listOf("%jokarzevent", "%jokarzeventdetail"), written)
        assertEquals(TaskerProfileExport.eventVariable(), written[0])
        assertEquals(TaskerProfileExport.eventDetailVariable(), written[1])

        for (action in task.children("Action")) {
            val name = action.arg("arg0")
            assertNotNull("a Variable Set needs a name in arg0", name)
            assertTrue("a Variable Set name must start with %: $name", requireNotNull(name).startsWith("%"))
        }
    }

    // ── the sender ────────────────────────────────────────────────────────────────────────────

    /**
     * Gradle runs unit tests with `app/` (or the repo root) as the working directory — the same
     * lookup the live-notification guard uses. A guard that silently skips is useless.
     */
    private fun sourceFile(name: String): File {
        val rel = "src/main/java/com/randallengineering/jokarztimeclock/engine/$name"
        return listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile }
            ?: throw AssertionError("Cannot find $name for the linkage check")
    }

    @Test
    fun `TaskerBridge sends the same action the exported profile listens for`() {
        val code = sourceFile("TaskerBridge.kt").readText().lines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString("\n")

        assertTrue(
            "TaskerBridge must broadcast Intent(TaskerContract.ACTION_EVENT) — the action the exported " +
                "profile's Intent Received trigger matches",
            Regex("""Intent\(\s*TaskerContract\.ACTION_EVENT\s*\)""").containsMatchIn(code)
        )
        assertFalse(
            "TaskerBridge must not go back to Tasker's own namespace " +
                "(net.dinglisch.android.tasker.ACTION_EVENT): Tasker never broadcasts it, so the profile " +
                "would be syntactically valid and never fire",
            code.contains("net.dinglisch.android.tasker.ACTION_EVENT")
        )

        // The action literal lives in one place and the exporter reads it from there, so the
        // trigger and the sender cannot drift apart.
        val triggerAction = parse(generated()).children("Profile").single().child("Event")?.arg("arg0")
        assertEquals(
            "the profile's trigger must be built from the same constant TaskerBridge sends",
            TaskerContract.ACTION_EVENT,
            triggerAction
        )
    }
}
