package com.randallengineering.jokarztimeclock.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

/** Where the exported Tasker profile ended up, or why it did not. */
sealed interface TaskerExportResult {
    /** @param location human-readable, shown to the owner. */
    data class Written(val location: String, val bytes: Int) : TaskerExportResult
    data class Failed(val reason: String) : TaskerExportResult
}

/**
 * Writes [TaskerProfileExport.profileXml] to the phone's **Downloads** folder.
 *
 * Tasker's import picker only browses public storage, so an app-private file is invisible to it:
 * the pre-2.8.1 "import" never wrote a file at all, it just fired an undocumented
 * `tasker://import?file=<asset name>` URI. Downloads is reachable from
 * `Long press PROFILES tab ▸ Import Profile ▸ <file>` with no permission on Android 10+ (scoped
 * storage) and with `WRITE_EXTERNAL_STORAGE` on 9 and below.
 */
object TaskerProfileWriter {

    private const val TAG = "TaskerProfileWriter"

    fun write(context: Context, xml: String): TaskerExportResult = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeViaMediaStore(context, xml)
        else writeToLegacyDownloads(xml)
    } catch (t: Throwable) {
        Log.w(TAG, "Could not write ${TaskerProfileExport.FILE_NAME}", t)
        TaskerExportResult.Failed(t.message ?: t.javaClass.simpleName)
    }

    /** Android 10+ (scoped storage): no permission is needed for our own MediaStore entry. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(context: Context, xml: String): TaskerExportResult {
        val bytes = xml.toByteArray(Charsets.UTF_8)
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        // Replace an earlier export rather than littering Downloads_1, _2, ... files.
        resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(TaskerProfileExport.FILE_NAME), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                resolver.delete(Uri.withAppendedPath(collection, id.toString()), null, null)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, TaskerProfileExport.FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, TaskerProfileExport.MIME_TYPE)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return TaskerExportResult.Failed("Downloads refused a new file entry")

        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: return TaskerExportResult.Failed("Could not open $uri for writing")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return TaskerExportResult.Written(publicLocation(), bytes.size)
    }

    /** Android 9 and below: the public Downloads directory, needs WRITE_EXTERNAL_STORAGE. */
    @Suppress("DEPRECATION")
    private fun writeToLegacyDownloads(xml: String): TaskerExportResult {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null) return TaskerExportResult.Failed("This device has no public Downloads directory")
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            return TaskerExportResult.Failed(
                "Cannot create ${dir.absolutePath} — grant the Storage permission and try again"
            )
        }
        val file = File(dir, TaskerProfileExport.FILE_NAME)
        file.writeText(xml, Charsets.UTF_8)
        return TaskerExportResult.Written(publicLocation(), file.readBytes().size)
    }

    fun publicLocation(): String = "Downloads/${TaskerProfileExport.FILE_NAME}"
}
