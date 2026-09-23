package com.randallengineering.jokarztimeclock.engine

import com.randallengineering.jokarztimeclock.data.models.DayStats
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pins the Tasker contract. Wrong action strings or extra keys fail silently on a device (the
 * broadcast is just never matched), so the tests are the only place such a typo shows up.
 */
class TaskerContractTest {

    /**
     * Tasker's documented extra → local-variable rule, re-implemented here on purpose so the rule is
     * pinned by the test rather than by production code: lower-case, non-alphanumerics become `_`,
     * and an `a` is prefixed when the result does not start with a letter.
     */
    private fun taskerVar(key: String): String {
        val lower = key.lowercase(Locale.US).map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("")
        return "%" + if (lower.first() in 'a'..'z') lower else "a$lower"
    }

    private val documentedEventVars = mapOf(
        "JokarzEvent" to "%jokarzevent",
        "JokarzEventDetail" to "%jokarzeventdetail",
        "JokarzTimestamp" to "%jokarztimestamp"
    )

    private val documentedTotalVars = mapOf(
        "WorkTechHrsToday" to "%worktechhrstoday",
        "WorkActualHrsToday" to "%workactualhrstoday",
        "WorkActualGrossToday" to "%workactualgrosstoday",
        "WorkActualNetToday" to "%workactualnettoday",
        "WorkActualHrsPeriod" to "%workactualhrsperiod",
        "WorkActualGrossPeriod" to "%workactualgrossperiod",
        "WorkActualNetPeriod" to "%workactualnetperiod"
    )

    private fun dayStats(clocked: Double, payable: Double) = DayStats(
        dayStartMs = 0L, clockedMs = (clocked * 3_600_000).toLong(), clockedHours = clocked,
        workedHours = clocked, breakHours = 0.0, baseHours = payable, otHours = 0.0, ptoHours = 0.0,
        bankedHours = 0.0, payableHours = payable, systemInput = payable, type = "Workday", otMultiplier = 1.0
    )

    @Test
    fun `action strings are exactly the documented values`() {
        assertEquals("com.randallengineering.jokarztimeclock.EVENT", TaskerContract.ACTION_EVENT)
        assertEquals("com.randallengineering.jokarztimeclock.VARIABLES", TaskerContract.ACTION_VARIABLES)
        assertEquals("com.randallengineering.jokarztimeclock.ACTION_CLOCK_IN", TaskerContract.ACTION_CLOCK_IN)
        assertEquals("com.randallengineering.jokarztimeclock.ACTION_CLOCK_OUT", TaskerContract.ACTION_CLOCK_OUT)
        assertEquals("com.randallengineering.jokarztimeclock.engine.GeofenceBroadcastReceiver", TaskerContract.RECEIVER_CLASS)
        assertEquals("net.dinglisch.android.tasker.ACTION_TASK", TaskerContract.ACTION_TASKER_RUN_TASK)
        assertEquals("task_name", TaskerContract.EXTRA_TASKER_TASK_NAME)
        assertEquals("varNames", TaskerContract.EXTRA_TASKER_VAR_NAMES)
        assertEquals("varValues", TaskerContract.EXTRA_TASKER_VAR_VALUES)
        assertEquals("version_number", TaskerContract.EXTRA_TASKER_VERSION)
        assertEquals("1.1", TaskerContract.TASKER_VERSION_VALUE)
        assertEquals("net.dinglisch.android.tasker.PERMISSION_RUN_TASKS", TaskerContract.PERMISSION_TASKER_RUN_TASKS)
        assertEquals("net.dinglisch.android.taskerm", TaskerContract.TASKER_PACKAGE)
    }

    @Test
    fun `receiver actions are the contract actions`() {
        assertEquals(TaskerContract.ACTION_CLOCK_IN, GeofenceBroadcastReceiver.ACTION_CLOCK_IN)
        assertEquals(TaskerContract.ACTION_CLOCK_OUT, GeofenceBroadcastReceiver.ACTION_CLOCK_OUT)
    }

    @Test
    fun `event extras become the documented Tasker variables`() {
        val extras = TaskerContract.eventExtras("clock_in", "geofence", 1_700_000_000_000L)
        assertEquals(documentedEventVars.keys, extras.keys)
        for (key in extras.keys) assertValidKey(key, documentedEventVars.getValue(key))
        assertEquals("clock_in", extras["JokarzEvent"])
        assertEquals("geofence", extras["JokarzEventDetail"])
        assertEquals("1700000000000", extras["JokarzTimestamp"])
    }

    @Test
    fun `variable extras become the documented Tasker variables`() {
        val values = TaskerContract.variableValues(TimeclockState(), dayStats(8.0, 8.0), 40.0)
        assertEquals(documentedTotalVars.keys, values.keys)
        for (key in values.keys) assertValidKey(key, documentedTotalVars.getValue(key))
    }

    @Test
    fun `task variables use the Tasker local-variable names`() {
        val vars = TaskerContract.taskVariables("clock_out", "app", 5L)
        assertEquals(documentedEventVars.values.toSet(), vars.keys)
        assertEquals("clock_out", vars["%jokarzevent"])
        assertEquals("5", vars["%jokarztimestamp"])
    }

    @Test
    fun `production transform matches Tasker's rule including the a-prefix case`() {
        for (key in documentedEventVars.keys + documentedTotalVars.keys + listOf("%WorkTechHrsToday", "9lives", "a-b")) {
            assertEquals(taskerVar(key), TaskerContract.taskerVariableName(key))
        }
        // The pre-2.8.0 key, as Tasker actually exposed it.
        assertEquals("%a_worktechhrstoday", taskerVar("%WorkTechHrsToday"))
    }

    @Test
    fun `variable values keep the pre-2_8_0 formulas`() {
        val state = TimeclockState(grossRate = 62.0, netRate = 30.0)
        val today = dayStats(clocked = 11.25, payable = 10.5)
        val v = TaskerContract.variableValues(state, today, periodHours = 42.3)

        assertEquals("11.25", v["WorkTechHrsToday"])            // clockedHours
        assertEquals("10.50", v["WorkActualHrsToday"])          // payableHours
        assertEquals("651.00", v["WorkActualGrossToday"])       // 10.5 * 62
        assertEquals("315.00", v["WorkActualNetToday"])         // 10.5 * 30
        assertEquals("42.30", v["WorkActualHrsPeriod"])         // period payable hours
        assertEquals("2622.60", v["WorkActualGrossPeriod"])     // 42.3 * 62
        assertEquals("1269.00", v["WorkActualNetPeriod"])       // 42.3 * 30
    }

    @Test
    fun `variable values without today's stats report zero for the day`() {
        val v = TaskerContract.variableValues(TimeclockState(), null, 5.0)
        assertEquals("0.00", v["WorkTechHrsToday"])
        assertEquals("0.00", v["WorkActualGrossToday"])
        assertEquals("5.00", v["WorkActualHrsPeriod"])
    }

    @Test
    fun `setup steps name the actions, package and every documented variable`() {
        val text = TaskerContract.setupSteps().joinToString("\n")
        for (needle in listOf(
            "android.intent.action.VIEW",
            "jokarz://timeclock?action=clock_in",
            "jokarz://timeclock?action=clock_out",
            TaskerContract.ACTION_CLOCK_IN,
            TaskerContract.ACTION_CLOCK_OUT,
            "Package: com.randallengineering.jokarztimeclock",
            TaskerContract.ACTION_EVENT,
            TaskerContract.ACTION_VARIABLES,
            "Allow External Access",
            "not a Locale/Tasker plugin"
        ) + documentedEventVars.values + documentedTotalVars.values) {
            assertTrue("setup steps must mention `$needle`", text.contains(needle))
        }
        assertFalse("the undocumented import URI must be gone", text.contains("tasker://"))
    }

    private fun assertValidKey(key: String, documented: String) {
        assertFalse("$key must not start with %", key.startsWith("%"))
        assertTrue("$key must be purely alphanumeric", key.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })
        assertEquals("Tasker name for $key", documented, taskerVar(key))
    }
}
