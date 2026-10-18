package com.hydra.reminders

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifications {
    const val CHANNEL = "water_reminders"
    const val ID = 1001
    const val ACTION_FIRE = "com.hydra.FIRE"
    const val ACTION_LOG = "com.hydra.LOG"
    const val ACTION_SNOOZE = "com.hydra.SNOOZE"
    const val ACTION_DISMISS = "com.hydra.DISMISS"
    const val EXTRA_ML = "ml"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL, "Water reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Routine prompts to drink water"
            enableVibration(true); vibrationPattern = longArrayOf(0, 250, 150, 250)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, ReminderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
        fun act(code: Int, action: String, ml: Int?) = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ActionReceiver::class.java).setAction(action).apply { if (ml != null) putExtra(EXTRA_ML, ml) }, flags)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time for your next water break")
            .setContentText("A routine reminder. Tap to log your drink.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDeleteIntent(act(12, Notifications.ACTION_DISMISS, null))
            .addAction(0, "Log 250 mL", act(11, ACTION_LOG, 250))
            .addAction(0, "Snooze 10 min", act(13, ACTION_SNOOZE, null))
            .build()
        nm.notify(ID, n)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}
