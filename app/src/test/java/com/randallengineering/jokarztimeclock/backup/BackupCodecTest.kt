package com.randallengineering.jokarztimeclock.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.NOW
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.at
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.richState
import com.randallengineering.jokarztimeclock.backup.BackupFixtures.session
import com.randallengineering.jokarztimeclock.data.backup.BackupCodec
import com.randallengineering.jokarztimeclock.data.backup.BackupCodec.Decoded
import com.randallengineering.jokarztimeclock.data.models.PtoType
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest

/**
 * The backup format contract. Each rejection asserts its exact reason so that a broken guard makes the
 * test fail for the right reason instead of passing on some other Invalid.
 */
class BackupCodecTest {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun encodeRich(): String = BackupCodec.encode(richState(), "2.8.0", 12, NOW)

    private fun decode(text: String) = BackupCodec.decode(text, NOW)

    private fun valid(d: Decoded): Decoded.Valid = d as? Decoded.Valid ?: run {
        fail("Expected Valid but got $d"); throw AssertionError()
    }

    private fun invalidReason(d: Decoded): String = (d as? Decoded.Invalid)?.reason ?: run {
        fail("Expected Invalid but got $d"); throw AssertionError()
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun roundTripPreservesEveryField() {
        val original = richState()
        val decoded = valid(decode(encodeRich()))

        assertEquals(original, decoded.state)
        assertEquals(gson.toJson(original), gson.toJson(decoded.state))
        assertEquals(1, decoded.sourceVersion)
        assertEquals(NOW, decoded.exportedAtMs)
        assertEquals("2.8.0", decoded.appVersionName)
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test
    fun envelopeHasSchemaVersionAndPayloadOnlyChecksum() {
        val obj = JsonParser.parseString(encodeRich()).asJsonObject
        assertEquals(BackupCodec.SCHEMA, obj["schema"].asString)
        assertEquals(BackupCodec.CURRENT_VERSION, obj["version"].asInt)
        assertEquals(12, obj["appVersionCode"].asInt)
        // The checksum covers gson.toJson(state) only, so envelope metadata can never affect it.
        assertEquals(sha256(gson.toJson(richState())), obj["payloadSha256"].asString)
        val other = JsonParser.parseString(BackupCodec.encode(richState(), "9.9.9", 99, NOW + 1)).asJsonObject
        assertEquals(obj["payloadSha256"].asString, other["payloadSha256"].asString)
    }

    @Test
    fun oneChangedCharacterInThePayloadIsRejectedAsChecksumMismatch() {
        val text = encodeRich()
        val endValue = at("2026-09-01T20:30:00Z").toString()
        assertTrue(endValue.endsWith("0"))
        val needle = "\"end\": $endValue"
        assertEquals(1, text.split(needle).size - 1)
        val corrupted = text.replace(needle, "\"end\": ${endValue.dropLast(1)}1")

        assertEquals(
            "The backup is incomplete or damaged (checksum mismatch) — it will not be imported.",
            invalidReason(decode(corrupted))
        )
    }

    @Test
    fun editedEnvelopeMetadataDoesNotBreakTheChecksum() {
        val text = encodeRich().replace("\"appVersionName\": \"2.8.0\"", "\"appVersionName\": \"2.8.1\"")
        assertEquals("2.8.1", valid(decode(text)).appVersionName)
    }

    @Test
    fun truncatedFileIsRejected() {
        val text = encodeRich()
        val reason = invalidReason(decode(text.substring(0, text.length / 2)))
        assertEquals("The file is not a JSON backup (it may be truncated or the wrong file).", reason)
    }

    @Test
    fun emptyFileIsRejected() {
        assertEquals("The file is empty.", invalidReason(decode("")))
        assertEquals("The file is empty.", invalidReason(decode("  \n\t ")))
    }

    @Test
    fun nonObjectJsonIsRejected() {
        assertEquals(
            "The file is not a JSON backup (it may be truncated or the wrong file).",
            invalidReason(decode("[1, 2, 3]"))
        )
    }

    /** Byte-for-byte the shape v2.7.0's exportJson() wrote: a bare, pretty-printed TimeclockState. */
    private val v270Export = """
        {
          "isClockedIn": false,
          "isOnBreak": false,
          "accumulatedBreakMs": 0,
          "grossRate": 62.0,
          "netRate": 30.0,
          "displayMode": "GROSS",
          "sessions": [
            {
              "id": "sess_1788000000000_4821",
              "start": 1788253200000,
              "end": 1788291000000,
              "breakMs": 1800000,
              "note": "",
              "jobCode": "",
              "isPutInSystem": false
            }
          ],
          "ptoEntries": [
            {
              "id": "pto_1788000000000_1234",
              "date": 1788753600000,
              "hours": 10.0,
              "type": "HOLIDAY",
              "note": "Labor Day"
            }
          ],
          "settings": {
            "theme": "LIGHT",
            "paySchedule": "SEMI_MONTHLY",
            "biweeklyAnchorDate": "2026-01-05",
            "standardShiftHours": 10.0,
            "unpaidMealThreshold": 4.0,
            "unpaidMealDuration": 0.5,
            "cliffHours": 12.5,
            "otMultiplier": 1.0,
            "soundEnabled": true,
            "hapticEnabled": true,
            "notificationsEnabled": true,
            "liveNotificationEnabled": true,
            "hideMoneyAmounts": false,
            "autoBreakDeduction": true,
            "geofenceEnabled": false,
            "useTaskerFallback": false,
            "workLatitude": 0.0,
            "workLongitude": 0.0,
            "geofenceRadiusMeters": 150.0,
            "workAddressName": ""
          },
          "auditLog": [
            {
              "action": "CLOCK_OUT",
              "timestamp": 1788291000000,
              "payloadJson": "{\"start\": 1788253200000}"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun legacyV270BareStateExportIsAcceptedWithOneChecksumWarning() {
        val decoded = valid(decode(v270Export))

        assertEquals(0, decoded.sourceVersion)
        assertEquals(listOf(BackupCodec.LEGACY_WARNING), decoded.warnings)
        assertTrue(decoded.warnings.single().contains("checksum"))
        assertEquals(1, decoded.state.sessions.size)
        assertEquals("sess_1788000000000_4821", decoded.state.sessions[0].id)
        assertEquals(1_800_000L, decoded.state.sessions[0].breakMs)
        assertEquals(PtoType.HOLIDAY, decoded.state.ptoEntries[0].type)
        assertEquals(ThemeMode.LIGHT, decoded.state.settings.theme)
        // Fields added after v2.7.0 fall back to their defaults instead of null.
        assertEquals("", decoded.state.settings.taskerTaskName)
        assertFalse(decoded.state.settings.runTaskerTaskOnClock)
    }

    @Test
    fun newerVersionIsRejectedEvenWithACorrectChecksum() {
        val obj = JsonParser.parseString(encodeRich()).asJsonObject
        obj.addProperty("version", 99)
        val reason = invalidReason(decode(gson.toJson(obj)))
        assertEquals(
            "This backup was made by a newer version of the app (backup v99, this app understands v1). Update the app first.",
            reason
        )
        assertTrue(reason.contains("newer version"))
    }

    @Test
    fun foreignJsonIsRejected() {
        assertEquals("This is not a Jokarz Timeclock backup.", invalidReason(decode("""{"foo": 1, "bar": []}""")))
    }

    @Test
    fun foreignSchemaIsRejectedByName() {
        val obj = JsonParser.parseString(encodeRich()).asJsonObject
        obj.addProperty("schema", "someone-elses-backup")
        assertEquals(
            "This is not a Jokarz Timeclock backup (schema 'someone-elses-backup').",
            invalidReason(decode(gson.toJson(obj)))
        )
    }

    @Test
    fun wellFormedEnvelopeWithStopBeforeStartIsRejectedNamingTheSession() {
        val broken = richState().let { s ->
            s.copy(sessions = s.sessions + session("sess_bad", "2026-09-10T20:00:00Z", "2026-09-10T20:00:00Z"))
        }
        // Encoded by the real encoder, so the checksum is correct and only validation can refuse it.
        val reason = invalidReason(decode(BackupCodec.encode(broken, "2.8.0", 12, NOW)))

        assertTrue(reason, reason.startsWith("The backup contains invalid data:"))
        assertTrue(reason, reason.contains("Session #4 ("))
        assertTrue(reason, reason.contains("the stop is at or before the start"))
    }

    @Test
    fun legacyFileWithNullSessionsIsRejected() {
        val reason = invalidReason(decode("""{"isClockedIn": false, "sessions": null}"""))
        assertTrue(reason, reason.contains("\"sessions\" is null"))
    }
}
