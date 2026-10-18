package com.hydra.algorithm

import org.junit.Assert.*
import org.junit.Test

class InsightsMathTest {
    @Test fun snoozesAreNotOutcomes() {
        val s = InsightsMath.reminderStats(listOf("SNOOZED", "CONFIRMED", "DISMISSED", "NOTIFIED"))
        assertEquals(3, s.resolved); assertEquals(1.0 / 3, s.acknowledgementRate!!, 1e-9); assertEquals(1, s.snoozed)
    }
    @Test fun noRemindersMeansNoRate() { assertNull(InsightsMath.reminderStats(emptyList()).acknowledgementRate) }
    @Test fun fewRemindersNotEnoughData() { assertFalse(InsightsMath.reminderStats(listOf("CONFIRMED")).enoughData) }
    @Test fun completeness() { assertEquals(57, InsightsMath.completenessPercent(4)); assertEquals(100, InsightsMath.completenessPercent(9)) }
}
