package com.randallengineering.jokarztimeclock.engine

/**
 * Builds the Tasker **profile file** (`.prf.xml`) this app exports.
 *
 * Tasker decides what an XML file is from the nodes it contains: one `<Profile>` plus the tasks it
 * links to is a *Profile file* and is imported through "Long press PROFILES tab ▸ Import Profile".
 * That is why this generator emits exactly one `<Profile>` and its one linked `<Task>` — a second
 * profile would make the file a Data Backup instead, which the Profile import menu never lists.
 *
 * Every element, attribute and code below is pinned to a known-good Tasker export; the sources are
 * named in `docs/TASKER-FORMAT.md`, and `TaskerProfileXmlTest` fails if any of them drifts. In
 * particular the trigger's `<code>` must be a **Profile Event** code: the pre-2.8.1 asset wrote
 * `331`, which is the *Task Action* `Auto-Sync`, and Tasker answered with "Missing event type".
 *
 * Pure Kotlin on purpose: no Android classes, so it is exercised by JVM unit tests.
 */
object TaskerProfileExport {

    /** Tasker only lists files whose suffix matches the export type. */
    const val FILE_NAME = "Jokarz_Timeclock.prf.xml"
    const val MIME_TYPE = "text/xml"

    /** Shown in Tasker's PROFILES list. Importing a second profile with the same name fails. */
    const val PROFILE_NAME = "Jokarz Timeclock Events"
    const val TASK_NAME = "Jokarz Timeclock Event"

    /**
     * `tv` is the Tasker version the file is written for; Tasker uses it only for compatibility
     * messaging. 6.5.11 is taken from a real, recent export (see docs/TASKER-FORMAT.md, source S3)
     * so the file never claims to be newer than the Tasker reading it.
     */
    const val TASKER_FILE_VERSION = "6.5.11"

    /** Profile Events table: 599 = Intent Received. (331, the old value, is not an event at all.) */
    const val CODE_EVENT_INTENT_RECEIVED = 599

    /** Task Actions table: 547 = Variable Set. */
    const val CODE_ACTION_VARIABLE_SET = 547

    /** Profile / context / element format versions, all as seen in real exports. */
    const val VE_PROFILE = 2
    const val VE_EVENT = 2
    const val VE_STR = 3
    const val VE_ACTION = 7

    const val PROFILE_ID = 1
    const val TASK_ID = 1

    /** Global variables the linked task writes, so the owner can see the trigger really fired. */
    const val OUT_EVENT_VARIABLE = "%JokarzLastEvent"
    const val OUT_DETAIL_VARIABLE = "%JokarzLastEventDetail"

    /**
     * The whole profile file.
     *
     * @param timestampMs written into `<cdate>`/`<edate>`; injectable so the golden-file test can
     *   compare a byte-exact document.
     */
    fun profileXml(timestampMs: Long = System.currentTimeMillis()): String = buildString {
        val stamp = timestampMs.toString()
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<TaskerData sr=\"\" dvi=\"1\" tv=\"$TASKER_FILE_VERSION\">\n")

        // ── the profile: trigger + link to the task with the same numeric id ────────────────────
        append("\t<Profile sr=\"prof$PROFILE_ID\" ve=\"$VE_PROFILE\">\n")
        append("\t\t<cdate>$stamp</cdate>\n")
        append("\t\t<edate>$stamp</edate>\n")
        append("\t\t<id>$PROFILE_ID</id>\n")
        append("\t\t<mid0>$TASK_ID</mid0>\n")     // entry task; without it the profile has no task
        append("\t\t<nme>${escape(PROFILE_NAME)}</nme>\n")

        // The trigger. arg0 is the *intent action*; arg1..arg4 are priority/stop-event/category/data.
        append("\t\t<Event sr=\"con0\" ve=\"$VE_EVENT\">\n")
        append("\t\t\t<code>$CODE_EVENT_INTENT_RECEIVED</code>\n")
        append("\t\t\t<Str sr=\"arg0\" ve=\"$VE_STR\">${escape(TaskerContract.ACTION_EVENT)}</Str>\n")
        append("\t\t\t<Int sr=\"arg1\" val=\"0\"/>\n")
        append("\t\t\t<Int sr=\"arg2\" val=\"0\"/>\n")
        append("\t\t\t<Str sr=\"arg3\" ve=\"$VE_STR\"/>\n")
        append("\t\t\t<Str sr=\"arg4\" ve=\"$VE_STR\"/>\n")
        append("\t\t</Event>\n")
        append("\t</Profile>\n")

        // ── the linked task ────────────────────────────────────────────────────────────────────
        append("\t<Task sr=\"task$TASK_ID\">\n")
        append("\t\t<cdate>$stamp</cdate>\n")
        append("\t\t<edate>$stamp</edate>\n")
        append("\t\t<id>$TASK_ID</id>\n")
        append("\t\t<nme>${escape(TASK_NAME)}</nme>\n")
        append("\t\t<pri>6</pri>\n")
        append(variableSetAction("act0", OUT_EVENT_VARIABLE, eventVariable()))
        append(variableSetAction("act1", OUT_DETAIL_VARIABLE, eventDetailVariable()))
        append("\t</Task>\n")

        append("</TaskerData>\n")
    }

    /** `<Action>` `Variable Set`: arg0 = name (with `%`), arg1 = value, arg2 = do-maths, arg3 = 0. */
    private fun variableSetAction(sr: String, variable: String, value: String): String = buildString {
        append("\t\t<Action sr=\"$sr\" ve=\"$VE_ACTION\">\n")
        append("\t\t\t<code>$CODE_ACTION_VARIABLE_SET</code>\n")
        append("\t\t\t<Str sr=\"arg0\" ve=\"$VE_STR\">${escape(variable)}</Str>\n")
        append("\t\t\t<Str sr=\"arg1\" ve=\"$VE_STR\">${escape(value)}</Str>\n")
        append("\t\t\t<Int sr=\"arg2\" val=\"0\"/>\n")
        append("\t\t\t<Int sr=\"arg3\" val=\"0\"/>\n")
        append("\t\t</Action>\n")
    }

    /** The local variable the task sees. Tasker makes each extra of the received intent available as
     * a local variable named by [TaskerContract.taskerVariableName], so `JokarzEvent` arrives as
     * `%jokarzevent`. Both the extra key ([TaskerContract.EXTRA_EVENT]) and the rule live in
     * [TaskerContract], so the profile and the sender cannot disagree.
     */
    fun eventVariable(): String = TaskerContract.taskerVariableName(TaskerContract.EXTRA_EVENT)

    fun eventDetailVariable(): String = TaskerContract.taskerVariableName(TaskerContract.EXTRA_EVENT_DETAIL)

    /** Where the file has to be for Tasker's picker to find it. */
    fun publicLocation(): String = "Downloads/$FILE_NAME"

    /**
     * The numbered phone-side recipe. Shown in the app after an export and mirrored by the README,
     * so the two cannot drift; `TaskerProfileXmlTest` fails if a step stops naming the real file,
     * profile name or action.
     */
    fun importSteps(): List<String> = listOf(
        "1. In this app: ⋯ Settings ▸ TASKER ▸ \"Export Tasker profile\".",
        "2. The file is written to ${publicLocation()} — the dialog tells you the exact path.",
        "3. Open Tasker ▸ long-press the PROFILES tab ▸ \"Import Profile\".",
        "4. Pick $FILE_NAME. There must be no error dialog.",
        "5. The list now shows \"$PROFILE_NAME\" with the trigger \"Intent Received\" and the " +
            "action ${TaskerContract.ACTION_EVENT}.",
        "6. Clock in (or out) in this app. Tasker's task sets %JOKARZLASTEVENT and " +
            "%JOKARZLASTEVENTDETAIL, which proves the trigger fired.",
        "7. Edit that task to do whatever you want on a clock in/out.",
        "Note: Tasker refuses a second profile with the same name — rename or delete \"$PROFILE_NAME\" " +
            "before importing it again."
    )

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(ch)
        }
    }
}
