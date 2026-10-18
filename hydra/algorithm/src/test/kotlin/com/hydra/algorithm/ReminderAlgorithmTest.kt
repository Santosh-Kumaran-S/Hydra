package com.hydra.algorithm

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderAlgorithmTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val profile = ProfileInput(LocalTime.of(6, 30), LocalTime.of(22, 30), zone)
    private fun at(h: Int, m: Int = 0) = LocalDate.of(2026, 10, 17).atTime(h, m).atZone(zone).toInstant()
    private fun calc(
        p: ProfileInput = profile, intakes: List<IntakeEntry> = emptyList(),
        hr: Reading<Int>? = null, t: Reading<Double>? = null, h: Reading<Double>? = null,
        act: Boolean? = null, snoozes: Int = 0, prev: Long? = null, now: Instant = at(10),
    ) = ReminderAlgorithm.calculateNextReminderTime(p, intakes, hr, t, h, act, snoozes, prev, now)

    @Test fun noWaterYet_followsBaseline_notAdjusted() {
        val d = calc()
        assertEquals(90, d.intervalMin); assertFalse(d.adjustedFromBaseline)
        assertFalse(d.nextReminder.isBefore(at(10)))
    }
    @Test fun staleHeartRate_isIgnored() {
        val d = calc(hr = Reading(130, at(8), "test"))
        assertEquals("stale reading", d.ignored[Signal.HEART_RATE]); assertEquals(90, d.intervalMin)
    }
    @Test fun implausibleHeartRate_isIgnored() {
        val d = calc(hr = Reading(400, at(10), "test"))
        assertEquals("implausible value", d.ignored[Signal.HEART_RATE])
    }
    @Test fun heat_shortensButStaysBounded() {
        val d = calc(t = Reading(38.0, at(9, 30), "w"), h = Reading(80.0, at(9, 30), "w"))
        assertTrue(d.intervalMin in 60..89); assertTrue(d.intervalMin >= 45)
    }
    @Test fun exercise_heartRateDoesNotTriggerEarlierReminder() {
        val d = calc(hr = Reading(150, at(10), "w"), act = true, t = Reading(20.0, at(10), "w"))
        assertEquals(90, d.intervalMin)
    }
    @Test fun heartRateNotDoubleCountedWithHeat() {
        val d = calc(hr = Reading(130, at(10), "w"), t = Reading(38.0, at(10), "w"))
        assertTrue(d.ignored.containsKey(Signal.HEART_RATE))
    }
    @Test fun sleepBeforeNextReminder_defersToWake() {
        val d = calc(intakes = listOf(IntakeEntry(1, 250, at(21, 30))), now = at(21, 45))
        assertEquals(LocalDate.of(2026, 10, 18).atTime(6, 30).atZone(zone).toInstant(), d.nextReminder)
    }
    @Test fun quietCrossesMidnight() {
        assertTrue(ReminderAlgorithm.isQuiet(at(2).atZone(zone), profile, AlgorithmConfig()))
        assertFalse(ReminderAlgorithm.isQuiet(at(12).atZone(zone), profile, AlgorithmConfig()))
    }
    @Test fun duplicateIntakeCountedOnce() {
        val d = calc(intakes = listOf(IntakeEntry(1, 250, at(9)), IntakeEntry(2, 250, at(9).plusSeconds(10))))
        assertEquals(250, d.consumedTodayMl)
    }
    @Test fun targetReached_noMoreRemindersToday() {
        val p = profile.copy(dailyTargetMl = 500)
        val d = calc(p = p, intakes = listOf(IntakeEntry(1, 300, at(8)), IntakeEntry(2, 300, at(9))))
        assertEquals(LocalDate.of(2026, 10, 18).atTime(6, 30).atZone(zone).toInstant(), d.nextReminder)
    }
    @Test fun smallChange_hysteresisKeepsPreviousInterval() {
        val d = calc(t = Reading(27.0, at(10), "w"), prev = 90)
        assertEquals(90, d.intervalMin)
    }
    @Test fun repeatedSnoozes_relaxSchedule() {
        assertTrue(calc(snoozes = 3).intervalMin > 90)
    }
    @Test fun clinicianMinGap_isRespectedEvenWhenHot() {
        val p = profile.copy(clinicianMinGapMin = 120)
        val d = calc(p = p, t = Reading(38.0, at(10), "w"))
        assertTrue(d.intervalMin >= 120)
    }
    @Test fun missingEnvironment_fallsBackToBaseline() {
        assertEquals(90, calc(t = null, h = null).intervalMin)
    }

    @Test fun afterFiring_neverRefiresImmediately() {
        val next = ReminderAlgorithm.scheduleAfterFiring(at(10), at(10), 90, at(10), profile)
        assertEquals(at(11, 30), next)
    }
    @Test fun afterFiring_neverInPast() {
        val next = ReminderAlgorithm.scheduleAfterFiring(at(8), null, 90, at(10), profile)
        assertTrue(next.isAfter(at(10)))
    }
    @Test fun afterFiring_quietDeferredToWake() {
        val next = ReminderAlgorithm.scheduleAfterFiring(at(22, 45), at(21), 90, at(21), profile)
        assertEquals(LocalDate.of(2026, 10, 18).atTime(6, 30).atZone(zone).toInstant(), next)
    }
    @Test fun snooze_withinDay() { assertEquals(at(10, 10), ReminderAlgorithm.snoozeTime(at(10), 10, profile)) }
    @Test fun snooze_intoSleepDeferredToWake() {
        val next = ReminderAlgorithm.snoozeTime(at(22, 25), 10, profile)
        assertEquals(LocalDate.of(2026, 10, 18).atTime(6, 30).atZone(zone).toInstant(), next)
    }

    @Test fun freshHeartRate_modestBoundedEffect() {
        val d = calc(hr = Reading(140, at(10), "hc"))
        assertTrue(d.intervalMin in 78..80); assertTrue(Signal.HEART_RATE in d.used)
    }
    @Test fun staleEnvironment_isIgnored() {
        val d = calc(t = Reading(38.0, at(6), "w"), h = Reading(80.0, at(6), "w"))
        assertEquals("stale reading", d.ignored[Signal.TEMPERATURE]); assertEquals(90, d.intervalMin)
    }
}
