package com.randallengineering.jokarztimeclock.data.csv

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.TimeZone

/**
 * Turns the bytes of a picked file into a [CsvParseResult.Success] or a plain-language refusal.
 * Pure JVM (takes a stream, not a Uri) so the refusals are unit-testable. It only reads: the
 * preview is built from its result, and nothing reaches the repository unless the owner applies.
 */
object CsvFileReader {

    /**
     * Largest file we will read into memory. His real export is ~75 bytes per shift, so even
     * decades of shifts are far below this; anything bigger is not a timesheet.
     */
    const val MAX_CSV_BYTES = 5 * 1024 * 1024

    sealed class Result {
        data class Ready(val parsed: CsvParseResult.Success) : Result()

        /** Nothing will be imported; [reason] is safe to show verbatim. */
        data class Refused(val reason: String) : Result()
    }

    /** [input] null means the file provider could not open the file. */
    fun read(input: InputStream?, tz: TimeZone = TimeZone.getDefault()): Result {
        if (input == null) return Result.Refused("The file could not be opened.")
        val buffer = ByteArray(MAX_CSV_BYTES + 1)
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        if (total > MAX_CSV_BYTES) {
            return Result.Refused("The file is too large to be a timesheet CSV. Nothing was imported.")
        }
        return decode(buffer.copyOf(total), tz)
    }

    fun decode(bytes: ByteArray, tz: TimeZone = TimeZone.getDefault()): Result {
        // Strict decoding: a lenient String(bytes, UTF_8) would silently turn unreadable bytes in
        // a note into '?' marks and import them as if that were what he wrote.
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            return Result.Refused("The file is not plain UTF-8 text, so it is not a CSV this app can read. Nothing was imported.")
        }
        return when (val parsed = LegacyCsvImporter.parse(text, tz)) {
            is CsvParseResult.Failure -> Result.Refused(parsed.reason)
            is CsvParseResult.Success ->
                // A header-only file parses, but under REPLACE it would empty his shift list for
                // nothing; there is no reason to offer an import of zero shifts.
                if (parsed.entries.isEmpty()) {
                    Result.Refused("The file has a header but no shifts. Nothing was imported.")
                } else {
                    Result.Ready(parsed)
                }
        }
    }
}
