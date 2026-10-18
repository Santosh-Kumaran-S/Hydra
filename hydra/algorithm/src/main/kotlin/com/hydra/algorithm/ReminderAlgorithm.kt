package com.hydra.algorithm

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Deterministic, UI-independent scheduler. A routine-reminder heuristic, not a hydration measurement. */
object ReminderAlgorithm {
    const val VERSION = "1.0.0"

    fun dedupe(entries: List<IntakeEntry>, cfg: AlgorithmConfig): List<IntakeEntry> {
        val out = mutableListOf<IntakeEntry>()
        for (e in entries.sortedBy { it.at }) {
            val prev = out.lastOrNull()
            val dup = prev != null && prev.amountMl == e.amountMl &&
                Duration.between(prev.at, e.at).seconds < cfg.duplicateWindowSec
            if (!dup) out += e
        }
        return out
    }

    fun isQuiet(t: ZonedDateTime, p: ProfileInput, cfg: AlgorithmConfig): Boolean {
        if (!p.quietModeEnabled) return false
        val start = p.sleepStart.minusMinutes(cfg.preSleepQuietMin)
        val wake = p.wakeTime
        if (start == wake) return false
        val now = t.toLocalTime()
        return if (start < wake) now >= start && now < wake else now >= start || now < wake
    }

    fun nextWake(from: ZonedDateTime, p: ProfileInput): ZonedDateTime {
        var c = from.toLocalDate().atTime(p.wakeTime).atZone(p.zone)
        if (!c.isAfter(from)) c = from.toLocalDate().plusDays(1).atTime(p.wakeTime).atZone(p.zone)
        return c
    }

    fun calculateNextReminderTime(
        profile: ProfileInput,
        intakes: List<IntakeEntry>,
        heartRate: Reading<Int>?,
        temperature: Reading<Double>?,
        humidity: Reading<Double>?,
        activityActive: Boolean?,
        recentSnoozeCount: Int,
        previousIntervalMin: Long?,
        now: Instant,
        cfg: AlgorithmConfig = AlgorithmConfig(),
    ): ReminderDecision {
        val zNow = now.atZone(profile.zone)
        val used = mutableSetOf<Signal>()
        val ignored = mutableMapOf<Signal, String>()
        val safety = mutableListOf<String>()

        // Step 1: validate
        val valid = dedupe(intakes.filter { !it.at.isAfter(now) && it.amountMl in 1..2000 }, cfg)
        val dayStart = zNow.toLocalDate().atStartOfDay(profile.zone).toInstant()
        val consumed = valid.filter { it.at >= dayStart }.sumOf { it.amountMl }
        val last = valid.maxByOrNull { it.at }
        if (last != null) used += Signal.INTAKE_HISTORY

        fun fresh(r: Reading<*>, minutes: Long) =
            !r.measuredAt.isAfter(now) && Duration.between(r.measuredAt, now).toMinutes() <= minutes

        val hr = heartRate?.let {
            when {
                it.value !in 30..220 -> { ignored[Signal.HEART_RATE] = "implausible value"; null }
                !fresh(it, cfg.hrFreshnessMin) -> { ignored[Signal.HEART_RATE] = "stale reading"; null }
                else -> it.value
            }
        } ?: run { if (heartRate == null) ignored[Signal.HEART_RATE] = "unavailable"; null }
        val temp = temperature?.let {
            when {
                it.value < -20.0 || it.value > 55.0 -> { ignored[Signal.TEMPERATURE] = "implausible value"; null }
                !fresh(it, cfg.envFreshnessMin) -> { ignored[Signal.TEMPERATURE] = "stale reading"; null }
                else -> it.value
            }
        } ?: run { if (temperature == null) ignored[Signal.TEMPERATURE] = "unavailable"; null }
        val hum = humidity?.let {
            when {
                it.value < 0.0 || it.value > 100.0 -> { ignored[Signal.HUMIDITY] = "implausible value"; null }
                !fresh(it, cfg.envFreshnessMin) -> { ignored[Signal.HUMIDITY] = "stale reading"; null }
                else -> it.value
            }
        } ?: run { if (humidity == null) ignored[Signal.HUMIDITY] = "unavailable"; null }

        // Clinician target reached: no more routine reminders today; never escalate.
        val target = profile.dailyTargetMl
        if (target != null && consumed >= target) {
            safety += "Daily target reached; no further routine reminders today."
            val next = nextWake(zNow.toLocalDate().plusDays(1).atStartOfDay(profile.zone), profile)
            return ReminderDecision(
                next.toInstant(), cfg.baselineIntervalMin, 0, false, consumed, used, ignored, safety,
                "Your configured daily target is reached, so the next routine reminder is after you wake tomorrow.",
            )
        }

        // Step 3: bounded, non-double-counted context signal (0..1)
        var heat = 0.0
        if (temp != null) {
            used += Signal.TEMPERATURE
            heat = ((temp - 26.0) / 12.0).coerceIn(0.0, 1.0)
            if (hum != null && temp >= 26.0) {
                used += Signal.HUMIDITY
                heat = min(1.0, heat + ((hum - 60.0) / 30.0).coerceIn(0.0, 1.0) * 0.3)
            } else if (hum != null) ignored[Signal.HUMIDITY] = "only considered when warm"
        }
        var hrSignal = 0.0
        if (hr != null) {
            when {
                activityActive == true -> ignored[Signal.HEART_RATE] = "activity in progress (heart rate not used)"
                heat >= 0.5 -> ignored[Signal.HEART_RATE] = "heat already counted (avoids double counting)"
                else -> { used += Signal.HEART_RATE; hrSignal = ((hr - 100.0) / 40.0).coerceIn(0.0, 1.0) * 0.5 }
            }
        }
        if (activityActive != null) used += Signal.ACTIVITY
        val strength = min(1.0, heat + hrSignal)

        var relax = 0.0
        if (recentSnoozeCount >= cfg.snoozeRelaxThreshold) {
            relax = cfg.snoozeRelaxFraction; used += Signal.SNOOZE_HISTORY
        }

        // Step 4: interval with hysteresis, step limit, bounds
        var interval = (cfg.baselineIntervalMin * (1.0 - cfg.maxAdjustFraction * strength + relax)).roundToInt().toLong()
        if (previousIntervalMin != null) {
            val d = interval - previousIntervalMin
            interval = if (abs(d) <= cfg.hysteresisMin) previousIntervalMin
            else previousIntervalMin + d.coerceIn(-cfg.maxStepMin, cfg.maxStepMin)
        }
        interval = interval.coerceIn(cfg.minIntervalMin, cfg.maxIntervalMin)
        profile.clinicianMinGapMin?.let {
            if (interval < it) { interval = it; safety += "Clinician minimum gap applied." }
        }

        val anchor = last?.at ?: zNow.toLocalDate().atTime(profile.wakeTime).atZone(profile.zone).toInstant()
        var candidate = maxOf(anchor.plus(Duration.ofMinutes(interval)), now)

        // Step 6: quiet hours — defer to wake, never replay missed alerts
        val zc = candidate.atZone(profile.zone)
        if (isQuiet(zc, profile, cfg)) {
            candidate = nextWake(zc, profile).toInstant()
            safety += "Deferred until wake time (quiet hours)."
        }

        val elapsed = Duration.between(anchor, now).toMinutes().coerceAtLeast(0)
        val score = (min(1.0, elapsed.toDouble() / interval) * 70 + strength * 30).roundToInt().coerceIn(0, 100)
        val adjusted = interval != cfg.baselineIntervalMin

        // Step 7: explanation uses only signals that were actually used
        val parts = mutableListOf<String>()
        if (Signal.TEMPERATURE in used && heat > 0.0) parts += "it is warmer than usual"
        if (Signal.HEART_RATE in used && hrSignal > 0.0) parts += "your recent heart rate is a little elevated"
        if (Signal.SNOOZE_HISTORY in used) parts += "you have snoozed several recent reminders"
        val why = when {
            !adjusted -> "Your next reminder follows your usual ${cfg.baselineIntervalMin}-minute routine."
            parts.isEmpty() -> "Your next reminder was adjusted slightly to keep changes gradual."
            else -> "Your next reminder is adjusted because ${parts.joinToString(" and ")}."
        } + " This is a routine prompt, not a medical assessment."

        return ReminderDecision(candidate, interval, score, adjusted, consumed, used, ignored, safety, why)
    }

    /**
     * Applied by the scheduler after a decision (and after a reminder has fired). Guarantees:
     * no re-fire sooner than one interval after the last notification, never in the past,
     * and never inside quiet hours (deferred to wake, one notification only).
     */
    fun scheduleAfterFiring(
        decisionNext: Instant, lastFiredAt: Instant?, intervalMin: Long, now: Instant,
        profile: ProfileInput, cfg: AlgorithmConfig = AlgorithmConfig(),
    ): Instant {
        var next = decisionNext
        if (lastFiredAt != null) next = maxOf(next, lastFiredAt.plus(Duration.ofMinutes(intervalMin)))
        next = maxOf(next, now.plusSeconds(5))
        val z = next.atZone(profile.zone)
        if (isQuiet(z, profile, cfg)) next = nextWake(z, profile).toInstant()
        return next
    }

    fun snoozeTime(now: Instant, snoozeMin: Long, profile: ProfileInput, cfg: AlgorithmConfig = AlgorithmConfig()): Instant {
        val z = now.plus(Duration.ofMinutes(snoozeMin)).atZone(profile.zone)
        return if (isQuiet(z, profile, cfg)) nextWake(z, profile).toInstant() else z.toInstant()
    }
}
