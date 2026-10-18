package com.hydra

import android.app.Application
import com.hydra.data.HydraDatabase
import com.hydra.data.HydraRepository
import com.hydra.reminders.Notifications
import com.hydra.reminders.ReminderPlanner

class HydraApp : Application() {
    val repo by lazy { HydraRepository(HydraDatabase.build(this)) }
    val hub by lazy { com.hydra.sensors.SensorHub(this, repo) }
    val planner by lazy { ReminderPlanner(this, repo, hub) }
    override fun onCreate() { super.onCreate(); Notifications.createChannel(this) }
}
