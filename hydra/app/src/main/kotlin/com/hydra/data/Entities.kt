package com.hydra.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val dobEpochDay: Long? = null,
    val wakeMin: Int = 6 * 60 + 30,
    val sleepMin: Int = 22 * 60 + 30,
    val quietEnabled: Boolean = true,
    /** Clinician-provided only. null = unconfigured. */
    val targetMl: Int? = null,
    val clinicianMinGapMin: Int? = null,
    val baselineIntervalMin: Int = 90,
    val zoneId: String = java.time.ZoneId.systemDefault().id,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "water_intake", indices = [Index("atEpochMs")])
data class WaterIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMl: Int,
    val atEpochMs: Long,
    val method: String, // QUICK_ADD | CUSTOM | REMINDER_CONFIRM
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// Schema for later phases (no DAOs yet).
@Entity(tableName = "heart_rate_reading", indices = [Index("measuredAtMs")])
data class HeartRateReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bpm: Int, val measuredAtMs: Long, val sourceDevice: String, val status: String,
)

@Entity(tableName = "environment_reading", indices = [Index("observedAtMs")])
data class EnvironmentReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val temperatureC: Double, val humidityPct: Double, val observedAtMs: Long,
    val source: String, val kind: String, // MEASURED | FORECAST | CACHED | MANUAL
)

@Entity(tableName = "reminder_event", indices = [Index("scheduledAtMs")])
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledAtMs: Long, val notifiedAtMs: Long? = null,
    val status: String, val snoozeCount: Int = 0, val confirmedAmountMl: Int? = null,
    val reason: String, val algorithmVersion: String,
)
