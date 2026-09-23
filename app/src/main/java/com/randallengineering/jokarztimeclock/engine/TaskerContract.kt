package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.DayStats
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.util.Locale

/**
 * Every string the Tasker integration depends on, in one place and free of Android classes so the
 * JVM tests can pin them. A typo in an action or extra name is invisible at runtime: the broadcast
 * is simply never matched, which is exactly how the pre-2.8.0 integration failed.
 *
 * Tasker interfaces used (and only these):
 *  - "Intent Received" event: a runtime IntentFilter, so it only sees *implicit* broadcasts. Each
 *    extra becomes a local variable named by [taskerVariableName]'s rule, e.g. `JokarzEvent` →
 *    `%jokarzevent`.
 *  - "Send Intent" action: Tasker → this app, either the `jokarz://` deep link or an explicit
 *    (Package-restricted) broadcast to [RECEIVER_CLASS].
 *  - TaskerIntent external API ([ACTION_TASKER_RUN_TASK]): opt-in "run a named task".
 *
 * This app is not a Locale/Tasker plugin and deliberately implements no plugin protocol.
 */
object TaskerContract {

    const val PACKAGE_NAME = "com.randallengineering.jokarztimeclock"
    const val RECEIVER_CLASS = "$PACKAGE_NAME.engine.GeofenceBroadcastReceiver"
    const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"

    // ── Tasker → app ────────────────────────────────────────────────────────────────────────────
    const val ACTION_CLOCK_IN = "$PACKAGE_NAME.ACTION_CLOCK_IN"
    const val ACTION_CLOCK_OUT = "$PACKAGE_NAME.ACTION_CLOCK_OUT"
    /** Optional String extra on [ACTION_CLOCK_OUT]; stored as the note of the finished entry. */
    const val EXTRA_NOTE = "note"
    const val DEEP_LINK_BASE = "jokarz://timeclock?action="

    // ── App → Tasker (implicit broadcasts for an "Intent Received" profile) ─────────────────────
    const val ACTION_EVENT = "$PACKAGE_NAME.EVENT"
    const val ACTION_VARIABLES = "$PACKAGE_NAME.VARIABLES"

    const val EXTRA_EVENT = "JokarzEvent"
    const val EXTRA_EVENT_DETAIL = "JokarzEventDetail"
    const val EXTRA_TIMESTAMP = "JokarzTimestamp"

    /** Values of [EXTRA_EVENT]; [EXTRA_EVENT_DETAIL] carries the source (see `SOURCE_*`). */
    const val EVENT_CLOCK_IN = "clock_in"
    const val EVENT_CLOCK_OUT = "clock_out"
    const val SOURCE_APP = "app"
    const val SOURCE_TASKER = "tasker"
    const val SOURCE_GEOFENCE = "geofence"
    const val SOURCE_NOTIFICATION = "notification"

    val VARIABLE_KEYS = listOf(
        "WorkTechHrsToday",
        "WorkActualHrsToday",
        "WorkActualGrossToday",
        "WorkActualNetToday",
        "WorkActualHrsPeriod",
        "WorkActualGrossPeriod",
        "WorkActualNetPeriod"
    )

    // ── App → Tasker, opt-in: official TaskerIntent "run task" API ──────────────────────────────
    const val ACTION_TASKER_RUN_TASK = "net.dinglisch.android.tasker.ACTION_TASK"
    const val EXTRA_TASKER_TASK_NAME = "task_name"
    const val EXTRA_TASKER_VAR_NAMES = "varNames"
    const val EXTRA_TASKER_VAR_VALUES = "varValues"
    const val EXTRA_TASKER_VERSION = "version_number"
    const val TASKER_VERSION_VALUE = "1.1"
    const val PERMISSION_TASKER_RUN_TASKS = "net.dinglisch.android.tasker.PERMISSION_RUN_TASKS"

    fun eventExtras(event: String, detail: String, timestampMs: Long): Map<String, String> = linkedMapOf(
        EXTRA_EVENT to event,
        EXTRA_EVENT_DETAIL to detail,
        EXTRA_TIMESTAMP to timestampMs.toString()
    )

    /**
     * Local variables handed to the owner's task by [ACTION_TASKER_RUN_TASK]: the same values as
     * [eventExtras] under the same names the Intent Received profile would see (`%jokarzevent`, ...).
     */
    fun taskVariables(event: String, detail: String, timestampMs: Long): Map<String, String> =
        eventExtras(event, detail, timestampMs).mapKeys { (key, _) -> taskerVariableName(key) }

    /**
     * Hour and money totals. The maths is unchanged from the pre-2.8.0 bridge (only the key names
     * lost their `%`), so the owner's existing Tasker calculations keep working. Tasker extras are
     * strings here, hence the `%.2f` formatting. Without a [DayStats] for today the day values are 0.
     */
    fun variableValues(state: TimeclockState, todayStats: DayStats?, periodHours: Double): Map<String, String> {
        val clockedToday = todayStats?.clockedHours ?: 0.0
        val payableToday = todayStats?.payableHours ?: 0.0
        val values = listOf(
            clockedToday,
            payableToday,
            payableToday * state.grossRate,
            payableToday * state.netRate,
            periodHours,
            periodHours * state.grossRate,
            periodHours * state.netRate
        )
        return VARIABLE_KEYS.zip(values) { key, value -> key to String.format(Locale.US, "%.2f", value) }
            .toMap(LinkedHashMap())
    }

    /** Tasker's documented extra-key → local-variable rule, used for the names we document. */
    fun taskerVariableName(extraKey: String): String {
        val name = extraKey.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "_")
        return "%" + if (name.firstOrNull()?.isLetter() == true) name else "a$name"
    }

    /** The complete setup recipe, shown/copied in the app and mirrored by the README. */
    fun setupSteps(): List<String> {
        val eventVars = listOf(EXTRA_EVENT, EXTRA_EVENT_DETAIL, EXTRA_TIMESTAMP).joinToString(", ") { taskerVariableName(it) }
        val totalVars = VARIABLE_KEYS.joinToString(", ") { taskerVariableName(it) }
        return listOf(
            "DIRECTION 1 — Tasker clocks you in/out (recommended: the deep link)",
            "1. Tasker ▸ TASKS ▸ + ▸ name it \"Clock In\"",
            "2. Add action ▸ System ▸ Send Intent",
            "3. Action: android.intent.action.VIEW",
            "4. Data: ${DEEP_LINK_BASE}clock_in  (use clock_out, toggle or break for the others)",
            "5. Target: Activity; leave Package and Class empty",
            "6. Back ▸ the task is ready. Repeat for \"Clock Out\" with ${DEEP_LINK_BASE}clock_out",
            "7. Wire these tasks to whatever profile you like (location, WiFi near, NFC, time)",
            "",
            "DIRECTION 2 (alternative) — broadcast straight to the receiver",
            "1. Send Intent with Action $ACTION_CLOCK_IN (or $ACTION_CLOCK_OUT)",
            "2. Target: Broadcast Receiver",
            "3. Package: $PACKAGE_NAME — MANDATORY. Without it the broadcast is implicit and Android 8+ " +
                "will not deliver it to this app, so nothing happens and nothing is logged. " +
                "(Class $RECEIVER_CLASS is for reference; Tasker does not need it.)",
            "4. Extras: none required. On clock out, $EXTRA_NOTE:<text> (String) is optional and is stored " +
                "as the note of the finished entry.",
            "",
            "DIRECTION 3 — the app tells Tasker what happened",
            "1. Tasker ▸ PROFILES ▸ + ▸ Event ▸ System ▸ Intent Received",
            "2. Action: $ACTION_EVENT (or $ACTION_VARIABLES for the hour totals)",
            "3. Your task then sees $eventVars " +
                "($EVENT_CLOCK_IN / $EVENT_CLOCK_OUT; detail = $SOURCE_APP, $SOURCE_TASKER, $SOURCE_GEOFENCE " +
                "or $SOURCE_NOTIFICATION), and for the variables broadcast $totalVars",
            "",
            "DIRECTION 4 (opt-in) — the app runs one of your Tasker tasks",
            "1. Tasker ▸ Preferences ▸ Misc ▸ Allow External Access → on",
            "2. In the app: Settings ▸ Tasker ▸ enable \"Run Tasker task on clock in/out\" and type the exact task name",
            "3. When asked to allow the app to run Tasker tasks, accept. The task receives $eventVars",
            "",
            "Note: this app is not a Locale/Tasker plugin; it uses only the intents above."
        )
    }
}
