package com.randallengineering.jokarztimeclock.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {

    @Test
    fun decimalsAcceptPointOrCommaButNothingAmbiguous() {
        assertEquals(10.5, InputValidation.parseDecimal("10.5")!!, 0.0)
        assertEquals(10.5, InputValidation.parseDecimal("10,5")!!, 0.0)
        assertEquals(10.0, InputValidation.parseDecimal(" 10 ")!!, 0.0)
        assertEquals(0.5, InputValidation.parseDecimal(".5")!!, 0.0)
        assertEquals(-87.6298, InputValidation.parseDecimal("-87.6298")!!, 0.0)
        listOf("", "  ", "abc", "1.2.3", "1,000.5", "10h", "NaN", "Infinity", "-", ".").forEach {
            assertNull("accepted '$it'", InputValidation.parseDecimal(it))
        }
    }

    @Test
    fun rateMustBePositiveAndPlausible() {
        assertEquals(32.5, InputValidation.rate("32.50").value!!, 0.0)
        assertTrue(InputValidation.rate("32,50").ok)
        listOf("", "0", "-5", "abc", "20000").forEach {
            val c = InputValidation.rate(it)
            assertFalse("accepted '$it'", c.ok)
            assertNotNull(c.error)
            assertNull(c.value)
        }
    }

    @Test
    fun ptoHoursFitInOneDay() {
        assertEquals(10.0, InputValidation.ptoHours("10").value!!, 0.0)
        assertEquals(24.0, InputValidation.ptoHours("24").value!!, 0.0)
        listOf("", "0", "-1", "24.5", "ten").forEach { assertFalse("accepted '$it'", InputValidation.ptoHours(it).ok) }
    }

    @Test
    fun cliffCannotSitInsideTheStandardShift() {
        assertTrue(InputValidation.standardShiftHours("10").ok)
        assertFalse(InputValidation.standardShiftHours("0").ok)
        assertFalse(InputValidation.standardShiftHours("25").ok)

        assertTrue(InputValidation.cliffHours("12.5", standardHours = 10.0).ok)
        assertTrue(InputValidation.cliffHours("10", standardHours = 10.0).ok)
        val below = InputValidation.cliffHours("9.5", standardHours = 10.0)
        assertFalse(below.ok)
        assertTrue(below.error!!.contains("standard"))
        // With the standard field itself invalid, the cliff is judged on its own.
        assertTrue(InputValidation.cliffHours("9.5", standardHours = null).ok)
        assertFalse(InputValidation.cliffHours("30", standardHours = null).ok)
    }

    @Test
    fun coordinatesAreRangeCheckedAndBlankMeansUnset() {
        assertEquals(41.8781, InputValidation.latitude("41.8781").value!!, 0.0)
        assertEquals(-87.6298, InputValidation.longitude("-87.6298").value!!, 0.0)
        assertEquals(0.0, InputValidation.latitude("").value!!, 0.0)
        assertFalse(InputValidation.latitude("91").ok)
        assertFalse(InputValidation.longitude("-181").ok)
        assertFalse(InputValidation.latitude("north").ok)
        assertTrue(InputValidation.longitude("180").ok)
    }
}
