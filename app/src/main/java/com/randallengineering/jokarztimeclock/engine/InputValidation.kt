package com.randallengineering.jokarztimeclock.engine

/**
 * Validation for every free-text number the owner types (pay rate, PTO hours, shift targets,
 * geofence coordinates). Pure, so it is unit tested; the dialogs show [Check.error] under the field
 * (M3 `isError` + supporting text) and keep Save disabled until every field is [Check.ok].
 *
 * Numbers accept a decimal point or a decimal comma ("10,5"), because the number keyboard on a
 * comma-locale phone offers the comma; nothing is ever silently clamped or dropped.
 */
object InputValidation {

    /** Either a parsed value or the message to show under the field. */
    data class Check<out T>(val value: T?, val error: String?) {
        val ok: Boolean get() = error == null
    }

    private fun <T> ok(value: T) = Check(value, null)
    private fun <T> bad(message: String) = Check<T>(null, message)

    /** Parses "10.5", "10,5", " 10 " — but not "1,000.5", "1.2.3", "" or "abc". */
    fun parseDecimal(text: String): Double? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val normalised = if (t.count { it == ',' } == 1 && '.' !in t) t.replace(',', '.') else t
        if (!Regex("""-?\d*\.?\d+|-?\d+\.""").matches(normalised)) return null
        return normalised.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Hourly pay rate: a positive amount (0 would silently zero every figure). */
    fun rate(text: String): Check<Double> {
        val v = parseDecimal(text) ?: return bad("Enter a rate, e.g. 32.50")
        return when {
            v <= 0.0 -> bad("The rate must be more than 0")
            v > MAX_RATE -> bad("That rate looks wrong (over $MAX_RATE/hr)")
            else -> ok(v)
        }
    }

    /** PTO / holiday hours for one day: more than 0, at most 24. */
    fun ptoHours(text: String): Check<Double> {
        val v = parseDecimal(text) ?: return bad("Enter the hours, e.g. 10")
        return when {
            v <= 0.0 -> bad("Hours must be more than 0")
            v > 24.0 -> bad("One day holds at most 24 hours")
            else -> ok(v)
        }
    }

    /** The standard shift length in hours: more than 0, at most 24. */
    fun standardShiftHours(text: String): Check<Double> {
        val v = parseDecimal(text) ?: return bad("Enter the standard shift length in hours")
        return when {
            v <= 0.0 -> bad("Must be more than 0 hours")
            v > 24.0 -> bad("A shift is at most 24 hours")
            else -> ok(v)
        }
    }

    /**
     * The overtime "cliff": hours after which a shift is overtime. At most 24, and never below the
     * standard shift (an OT target inside the standard shift would make every shift overtime).
     */
    fun cliffHours(text: String, standardHours: Double?): Check<Double> {
        val v = parseDecimal(text) ?: return bad("Enter the OT cliff in hours")
        return when {
            v <= 0.0 -> bad("Must be more than 0 hours")
            v > 24.0 -> bad("A shift is at most 24 hours")
            standardHours != null && v < standardHours -> bad("Must be at least the standard shift ($standardHours h)")
            else -> ok(v)
        }
    }

    /** Latitude in degrees, -90..90. Blank means "not set yet" (0.0, the app's existing default). */
    fun latitude(text: String): Check<Double> = coordinate(text, 90.0, "Latitude")

    /** Longitude in degrees, -180..180. Blank means "not set yet" (0.0). */
    fun longitude(text: String): Check<Double> = coordinate(text, 180.0, "Longitude")

    private fun coordinate(text: String, limit: Double, name: String): Check<Double> {
        if (text.isBlank()) return ok(0.0)
        val v = parseDecimal(text) ?: return bad("$name must be a number, e.g. ${if (limit == 90.0) "41.8781" else "-87.6298"}")
        return if (v < -limit || v > limit) bad("$name must be between -$limit and $limit") else ok(v)
    }

    const val MAX_RATE = 10_000.0
}
