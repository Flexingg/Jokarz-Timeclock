package com.randallengineering.jokarztimeclock.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import java.security.MessageDigest

/**
 * The backup file format: a versioned envelope around the Gson shape of [TimeclockState], with a
 * SHA-256 of the payload so a truncated or edited file is refused instead of imported.
 *
 * The payload *is* the model's Gson serialisation, so renaming a field of [TimeclockState] (or of
 * anything it contains) silently breaks every backup already on people's phones.
 */
object BackupCodec {
    const val SCHEMA = "jokarz-timeclock-backup"
    const val CURRENT_VERSION = 1

    const val LEGACY_WARNING = "Legacy backup without a checksum — accepted, but it cannot be verified."

    // Same style as TimeclockRepository, so a v2.7.0 export and a payload look alike.
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    data class Envelope(
        val schema: String = SCHEMA,
        val version: Int = CURRENT_VERSION,
        val appVersionName: String,
        val appVersionCode: Int,
        val exportedAtMs: Long,
        val payloadSha256: String,
        val state: TimeclockState
    )

    sealed interface Decoded {
        data class Valid(
            val state: TimeclockState,
            val sourceVersion: Int,
            val exportedAtMs: Long,
            val warnings: List<String>,
            /** Null for legacy files, which did not record the app version. */
            val appVersionName: String? = null
        ) : Decoded

        data class Invalid(val reason: String) : Decoded
    }

    fun encode(state: TimeclockState, appVersionName: String, appVersionCode: Int, exportedAtMs: Long): String {
        val envelope = Envelope(
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            exportedAtMs = exportedAtMs,
            payloadSha256 = sha256Hex(gson.toJson(state)),
            state = state
        )
        return gson.toJson(envelope)
    }

    fun decode(text: String, nowMs: Long = System.currentTimeMillis()): Decoded {
        return try {
            decodeOrThrow(text, nowMs)
        } catch (e: Exception) {
            Decoded.Invalid("The file could not be read as a Jokarz Timeclock backup.")
        }
    }

    private fun decodeOrThrow(text: String, nowMs: Long): Decoded {
        if (text.isBlank()) return Decoded.Invalid("The file is empty.")

        val root = try {
            JsonParser.parseString(text)
        } catch (e: Exception) {
            null
        }
        if (root == null || !root.isJsonObject) {
            return Decoded.Invalid("The file is not a JSON backup (it may be truncated or the wrong file).")
        }
        val obj = root.asJsonObject

        if (!obj.has("schema")) return decodeLegacy(obj, nowMs)

        val schema = obj.get("schema").let { if (it.isJsonPrimitive) it.asString else it.toString() }
        if (schema != SCHEMA) return Decoded.Invalid("This is not a Jokarz Timeclock backup (schema '$schema').")

        val version = intOrNull(obj.get("version"))
            ?: return Decoded.Invalid("The backup header is damaged (missing or invalid version).")
        if (version > CURRENT_VERSION) {
            return Decoded.Invalid(
                "This backup was made by a newer version of the app (backup v$version, this app understands " +
                    "v$CURRENT_VERSION). Update the app first."
            )
        }
        if (version < 1) return Decoded.Invalid("The backup header is damaged (missing or invalid version).")

        // Hash the payload exactly as it sits in the file (re-serialised from the JSON tree, which
        // reproduces gson.toJson(state) byte for byte), not a re-serialised model: adding a field to
        // TimeclockState later must not invalidate every existing v1 backup.
        val stateElement = obj.get("state")
        val storedHash = obj.get("payloadSha256")?.takeIf { it.isJsonPrimitive }?.asString
        if (stateElement == null || !stateElement.isJsonObject || storedHash == null ||
            !storedHash.equals(sha256Hex(gson.toJson(stateElement)), ignoreCase = true)
        ) {
            return Decoded.Invalid("The backup is incomplete or damaged (checksum mismatch) — it will not be imported.")
        }

        val state = gson.fromJson(stateElement, TimeclockState::class.java)
        val problems = BackupValidator.validate(state, nowMs)
        if (problems.isNotEmpty()) return invalidData(problems)

        return Decoded.Valid(
            state = state,
            sourceVersion = version,
            exportedAtMs = longOrNull(obj.get("exportedAtMs")) ?: 0L,
            warnings = emptyList(),
            appVersionName = obj.get("appVersionName")?.takeIf { it.isJsonPrimitive }?.asString
        )
    }

    /** v2.7.0's exportJson() wrote the bare state with no envelope; those files must keep importing. */
    private fun decodeLegacy(obj: JsonObject, nowMs: Long): Decoded {
        if (!obj.has("sessions") && !obj.has("isClockedIn")) {
            return Decoded.Invalid("This is not a Jokarz Timeclock backup.")
        }
        val state = gson.fromJson(obj, TimeclockState::class.java)
        val problems = BackupValidator.validate(state, nowMs)
        if (problems.isNotEmpty()) return invalidData(problems)
        return Decoded.Valid(state = state, sourceVersion = 0, exportedAtMs = 0L, warnings = listOf(LEGACY_WARNING))
    }

    private fun invalidData(problems: List<String>) =
        Decoded.Invalid("The backup contains invalid data:\n" + problems.joinToString("\n") { "• $it" })

    private fun intOrNull(e: JsonElement?): Int? =
        e?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun longOrNull(e: JsonElement?): Long? =
        e?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
