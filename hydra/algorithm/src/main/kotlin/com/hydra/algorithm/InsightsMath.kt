package com.hydra.algorithm

/** Plain counting. Describes what happened with reminders; says nothing about hydration or causes. */
object InsightsMath {
    data class ReminderStats(val confirmed: Int, val snoozed: Int, val dismissed: Int, val ignored: Int) {
        /** Reminders with a final outcome. Snoozes postpone a reminder, so they are not counted as outcomes. */
        val resolved get() = confirmed + dismissed + ignored
        val acknowledgementRate: Double? get() = if (resolved == 0) null else confirmed.toDouble() / resolved
        /** Too few reminders to show a rate without being misleading. */
        val enoughData get() = resolved >= 5
    }

    fun reminderStats(statuses: List<String>) = ReminderStats(
        statuses.count { it == "CONFIRMED" }, statuses.count { it == "SNOOZED" },
        statuses.count { it == "DISMISSED" }, statuses.count { it == "NOTIFIED" })

    fun completenessPercent(daysWithEntries: Int, totalDays: Int = 7): Int =
        if (totalDays <= 0) 0 else (daysWithEntries.coerceIn(0, totalDays) * 100.0 / totalDays).toInt()
}
