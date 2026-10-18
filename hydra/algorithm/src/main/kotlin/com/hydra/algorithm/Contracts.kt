package com.hydra.algorithm

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** A sensor/weather value with the time it was MEASURED (never "now") and its origin. */
data class Reading<T>(val value: T, val measuredAt: Instant, val source: String)

data class IntakeEntry(val id: Long, val amountMl: Int, val at: Instant)

/** Weight/height are stored in the app profile but deliberately NOT used to compute any fluid target. */
data class ProfileInput(
    val wakeTime: LocalTime,
    val sleepStart: LocalTime,
    val zone: ZoneId,
    val quietModeEnabled: Boolean = true,
    /** Clinician-provided only. Null = unconfigured; the app must label it as such. */
    val dailyTargetMl: Int? = null,
    /** Clinician-provided minimum gap between reminders, if any. */
    val clinicianMinGapMin: Long? = null,
)

data class AlgorithmConfig(
    val baselineIntervalMin: Long = 90,
    val minIntervalMin: Long = 45,
    val maxIntervalMin: Long = 180,
    /** Context signals can shorten the baseline by at most this fraction. */
    val maxAdjustFraction: Double = 0.25,
    val hrFreshnessMin: Long = 30,
    val envFreshnessMin: Long = 120,
    val hysteresisMin: Long = 5,
    val maxStepMin: Long = 10,
    val duplicateWindowSec: Long = 60,
    val snoozeRelaxThreshold: Int = 3,
    val snoozeRelaxFraction: Double = 0.15,
    val preSleepQuietMin: Long = 0,
)

enum class Signal { HEART_RATE, TEMPERATURE, HUMIDITY, ACTIVITY, INTAKE_HISTORY, SNOOZE_HISTORY }

data class ReminderDecision(
    val nextReminder: Instant,
    val intervalMin: Long,
    val contextScore: Int,            // 0..100 scheduling heuristic, NOT a hydration measure
    val adjustedFromBaseline: Boolean,
    val consumedTodayMl: Int,
    val used: Set<Signal>,
    val ignored: Map<Signal, String>, // signal -> why
    val safetyNotes: List<String>,
    val explanation: String,
    val algorithmVersion: String = ReminderAlgorithm.VERSION,
)

/** Implemented by a real source only (Health Connect, BLE...). Never emits random/simulated values in production. */
interface HeartRateDataSource {
    suspend fun requestAccess(): Boolean
    /** Latest reading with its true measurement time, or null if unavailable. */
    suspend fun latest(): Reading<Int>?
    fun disconnect()
}
