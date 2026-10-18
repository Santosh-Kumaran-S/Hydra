package com.hydra.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hydra.HydraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch { try { block() } finally { pending.finish() } }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as HydraApp
        async { app.planner.onAlarmFired() }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as HydraApp
        async {
            when (intent.action) {
                Notifications.ACTION_LOG -> app.planner.confirmDrink(intent.getIntExtra(Notifications.EXTRA_ML, 250))
                Notifications.ACTION_SNOOZE -> app.planner.snooze()
                Notifications.ACTION_DISMISS -> app.planner.onDismissed()
            }
        }
    }
}

/** Re-arms the alarm after reboot, app update, time or time-zone change. Missed alerts are NOT replayed: at most one fires. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as HydraApp
        async { app.planner.recompute() }
    }
}
