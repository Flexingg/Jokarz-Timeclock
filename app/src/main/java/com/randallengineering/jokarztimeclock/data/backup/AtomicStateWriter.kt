package com.randallengineering.jokarztimeclock.data.backup

import java.io.File
import java.io.FileOutputStream

object AtomicStateWriter {
    /** Writes [json] to [target] via a sibling temp file + rename, so a crash mid-write can never
     *  leave a half-written file. Returns false and leaves [target] untouched on any failure. */
    // Synchronized because the UI, the live-shift service and the geofence receiver all persist through
    // the same repository: two concurrent writers would otherwise share (and clobber) one temp file.
    @Synchronized
    fun write(target: File, json: String): Boolean {
        val temp = File(target.parentFile, target.name + ".tmp")
        return try {
            if (temp.exists() && !temp.delete()) return false
            FileOutputStream(temp).use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
                // Without fsync the rename can reach disk before the data, and a power cut would leave
                // an empty file under the real name — exactly what the rename is meant to prevent.
                out.fd.sync()
            }
            if (temp.renameTo(target)) {
                true
            } else {
                temp.delete()
                false
            }
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }
}
