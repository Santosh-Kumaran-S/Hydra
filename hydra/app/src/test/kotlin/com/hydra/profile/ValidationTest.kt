package com.hydra.profile

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun rejectsZeroNegativeAndMalformed() {
        assertNotNull(Validation.height("0").error); assertNotNull(Validation.height("-5").error)
        assertNotNull(Validation.weight("abc").error); assertNotNull(Validation.weight("NaN").error)
    }
    @Test fun acceptsReasonable() { assertEquals(160.0, Validation.height("160").value!!, 0.0) }
    @Test fun blankTargetMeansUnconfigured() {
        val t = Validation.target(""); assertNull(t.value); assertNull(t.error)
    }
    @Test fun targetOutOfRangeRejected() { assertNotNull(Validation.target("50").error) }
    @Test fun amountBounds() {
        assertNotNull(Validation.amount("0").error); assertNotNull(Validation.amount("5000").error)
        assertEquals(250, Validation.amount("250").value)
    }
    @Test fun timeParsing() {
        assertEquals(22 * 60 + 30, Validation.time("22:30").value); assertNotNull(Validation.time("25:00").error)
        assertNotNull(Validation.distinctTimes(60, 60))
    }
    @Test fun badZoneRejected() { assertNotNull(Validation.zone("Mars/Base").error) }
    @Test fun envBounds() {
        assertNotNull(Validation.humidity("120").error); assertNotNull(Validation.temperature("99").error)
        assertEquals(31.5, Validation.temperature("31.5").value!!, 0.0)
    }
}
