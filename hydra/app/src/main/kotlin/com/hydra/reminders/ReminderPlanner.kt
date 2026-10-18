package com.hydra.reminders

import android.content.Context
import com.hydra.algorithm.AlgorithmConfig
import com.hydra.algorithm.ReminderAlgorithm
import com.hydra.algorithm.Reading
import com.hydra.data.AddResult
import com.hydra.data.HydraRepository
import com.hydra.data.UserProfileEntity
import com.hydra.ui.toEntry
import com.hydra.ui.toInput
import com.hydra.ui.zone
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Glue between the pure algorithm, the database and AlarmManager. */
class ReminderPlanner(private val ctx: Context, private val repo: HydraRepository, private val hub: com.hydra.sensors.SensorHub) {
    private val prefs = ctx.getSharedPreferences("reminder_state", Context.MODE_PRIVATE)
    val scheduler = ReminderScheduler(ctx)
    val remindersOn = MutableStateFlow(prefs.getBoolean("on", false))
    val nextAt = MutableStateFlow<Instant?>(null)

    suspend fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("on", on).apply(); remindersOn.value = on
        if (on) recompute() else { scheduler.cancel(); Notifications.cancel(ctx); nextAt.value = null }
    }

    /** Recalculate and (re)arm the single pending alarm. Safe to call often. */
    suspend fun recompute(now: Instant = Instant.now()): Instant? {
        if (!remindersOn.value) return null
        val p = repo.profileOnce() ?: UserProfileEntity()
        val input = p.toInput()
        val cfg = AlgorithmConfig(baselineIntervalMin = p.baselineIntervalMin.toLong())
        val hrRow = repo.latestHr()
        val envRow = repo.latestEnv()
        val hr = hrRow?.let { Reading(it.bpm, Instant.ofEpochMilli(it.measuredAtMs), it.sourceDevice) }
        val envAt = envRow?.let { Instant.ofEpochMilli(it.observedAtMs) }
        val temp = envRow?.let { Reading(it.temperatureC, envAt!!, it.source) }
        val hum = envRow?.let { Reading(it.humidityPct, envAt!!, it.source) }
        val d = ReminderAlgorithm.calculateNextReminderTime(
            input, repo.intakesOnce().map { it.toEntry() }, hr, temp, hum, null,
            prefs.getInt("snoozes", 0), prefs.getLong("interval", 0L).takeIf { it > 0 }, now, cfg)
        val lastFired = prefs.getLong("lastFired", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        val next = ReminderAlgorithm.scheduleAfterFiring(d.nextReminder, lastFired, d.intervalMin, now, input, cfg)
        prefs.getLong("eventId", 0L).takeIf { it > 0 }?.let { repo.supersede(it) }
        val id = repo.newEvent(next.toEpochMilli(), d.explanation, ReminderAlgorithm.VERSION)
        prefs.edit().putLong("eventId", id).putLong("interval", d.intervalMin).apply()
        scheduler.schedule(next); nextAt.value = next
        return next
    }

    suspend fun onAlarmFired(now: Instant = Instant.now()) {
        if (!remindersOn.value) return
        hub.refreshWeather(); hub.refreshHeartRate() // best effort; failures just leave older (possibly stale, ignored) data
        val p = repo.profileOnce() ?: UserProfileEntity()
        if (ReminderAlgorithm.isQuiet(now.atZone(p.zone()), p.toInput(), AlgorithmConfig())) { recompute(now); return }
        val id = prefs.getLong("eventId", 0L)
        if (id > 0) {
            repo.setEventState(id, "NOTIFIED", now.toEpochMilli(), prefs.getInt("snoozes", 0))
            prefs.edit().putLong("firedEventId", id).apply()
        }
        prefs.edit().putLong("lastFired", now.toEpochMilli()).apply()
        Notifications.show(ctx)
        recompute(now) // arms the NEXT reminder; an ignored reminder never repeats sooner than one interval
    }

    /** Only called after an explicit user confirmation. Never from merely opening a notification. */
    suspend fun confirmDrink(amountMl: Int): Instant? {
        val r = repo.addWater(amountMl, "REMINDER_CONFIRM")
        if (r == AddResult.INVALID) return null
        prefs.getLong("firedEventId", 0L).takeIf { it > 0 }?.let {
            repo.setEventState(it, "CONFIRMED", null, prefs.getInt("snoozes", 0), if (r == AddResult.ADDED) amountMl else null)
        }
        prefs.edit().putInt("snoozes", 0).putLong("firedEventId", 0L).apply()
        Notifications.cancel(ctx)
        return recompute()
    }

    suspend fun snooze(now: Instant = Instant.now(), minutes: Long = 10): Instant? {
        Notifications.cancel(ctx)
        val n = prefs.getInt("snoozes", 0) + 1
        prefs.edit().putInt("snoozes", n).apply()
        prefs.getLong("firedEventId", 0L).takeIf { it > 0 }?.let { repo.setEventState(it, "SNOOZED", null, n, null) }
        if (n > MAX_CHAINED_SNOOZES) return recompute(now) // stop chaining; normal schedule continues
        val p = repo.profileOnce() ?: UserProfileEntity()
        val at = ReminderAlgorithm.snoozeTime(now, minutes, p.toInput())
        val id = repo.newEvent(at.toEpochMilli(), "Snoozed by you", ReminderAlgorithm.VERSION)
        prefs.edit().putLong("eventId", id).apply()
        scheduler.schedule(at); nextAt.value = at
        return at
    }

    suspend fun onDismissed() {
        prefs.getLong("firedEventId", 0L).takeIf { it > 0 }?.let { repo.markDismissed(it) } // no water is logged
    }

    companion object { const val MAX_CHAINED_SNOOZES = 3 }
}
