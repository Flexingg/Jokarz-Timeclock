package com.randallengineering.jokarztimeclock.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.DayStats
import com.randallengineering.jokarztimeclock.data.models.TimeclockState

private const val TAG = "TaskerBridge"

/**
 * App → Tasker. Every call is best-effort: a Tasker failure must never break a clock in/out.
 * Names and values come from [TaskerContract].
 */
class TaskerBridge(private val context: Context) {

    /**
     * Implicit broadcast for a Tasker "Intent Received" profile on [TaskerContract.ACTION_EVENT].
     * Deliberately no setPackage(): Tasker cannot receive an intent that names a package.
     * Also runs the owner's Tasker task when that opt-in setting is on.
     */
    fun sendEvent(event: String, detail: String, settings: AppSettings) {
        val now = System.currentTimeMillis()
        try {
            val intent = Intent(TaskerContract.ACTION_EVENT)
            TaskerContract.eventExtras(event, detail, now).forEach { (key, value) -> intent.putExtra(key, value) }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (settings.runTaskerTaskOnClock) {
            runTaskerTask(settings.taskerTaskName, TaskerContract.taskVariables(event, detail, now))
        }
    }

    /** Implicit broadcast of the hour/money totals on [TaskerContract.ACTION_VARIABLES]. */
    fun pushData(todayStats: DayStats?, totalActualHrsPeriod: Double, state: TimeclockState) {
        if (todayStats == null) return
        try {
            val intent = Intent(TaskerContract.ACTION_VARIABLES)
            TaskerContract.variableValues(state, todayStats, totalActualHrsPeriod)
                .forEach { (key, value) -> intent.putExtra(key, value) }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Opt-in: asks Tasker to run [taskName] through Tasker's official external API (TaskerIntent),
     * passing [variables] as the task's local variables. Needs Tasker installed, Tasker's
     * "Allow External Access" preference on and [TaskerContract.PERMISSION_TASKER_RUN_TASKS] granted.
     * Returns false (and logs why) when a precondition is missing; the app works without it.
     */
    fun runTaskerTask(taskName: String, variables: Map<String, String>): Boolean {
        if (taskName.isBlank()) {
            Log.w(TAG, "Run Tasker task is enabled but no task name is set.")
            return false
        }
        if (!isTaskerInstalled(context)) {
            Log.w(TAG, "Tasker (${TaskerContract.TASKER_PACKAGE}) is not installed; task '$taskName' not run.")
            return false
        }
        val granted = context.checkPermission(
            TaskerContract.PERMISSION_TASKER_RUN_TASKS, Process.myPid(), Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "${TaskerContract.PERMISSION_TASKER_RUN_TASKS} not granted; task '$taskName' not run.")
            return false
        }
        return try {
            val intent = Intent(TaskerContract.ACTION_TASKER_RUN_TASK).apply {
                // Explicit to Tasker so Android 8+ delivers it even if Tasker's receiver is manifest-declared.
                setPackage(TaskerContract.TASKER_PACKAGE)
                putExtra(TaskerContract.EXTRA_TASKER_VERSION, TaskerContract.TASKER_VERSION_VALUE)
                putExtra(TaskerContract.EXTRA_TASKER_TASK_NAME, taskName)
                putStringArrayListExtra(TaskerContract.EXTRA_TASKER_VAR_NAMES, ArrayList(variables.keys))
                putStringArrayListExtra(TaskerContract.EXTRA_TASKER_VAR_VALUES, ArrayList(variables.values))
            }
            context.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not ask Tasker to run '$taskName'.", e)
            false
        }
    }

    companion object {
        /** Relies on the manifest's <queries> entry for Tasker (package visibility, API 30+). */
        fun isTaskerInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(TaskerContract.TASKER_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
