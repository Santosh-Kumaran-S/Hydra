package com.hydra.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

class ReminderScheduler(private val ctx: Context) {
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private fun pi() = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, ReminderReceiver::class.java).setAction(Notifications.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun canExact() = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun schedule(at: Instant) {
        val t = at.toEpochMilli()
        try {
            if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi())
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi())
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi())
        }
    }
    fun cancel() = am.cancel(pi())
}
