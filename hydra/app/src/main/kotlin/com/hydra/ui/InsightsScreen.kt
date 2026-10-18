package com.hydra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydra.data.UserProfileEntity
import java.time.Duration
import java.time.Instant

@Composable fun InsightsScreen(vm: HydraViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val intakes by vm.intakes.collectAsStateWithLifecycle()
    val statuses by vm.reminderStatuses.collectAsStateWithLifecycle()
    val stats = com.hydra.algorithm.InsightsMath.reminderStats(statuses)
    val zone = (profile ?: UserProfileEntity()).zone()
    val today = Instant.now().atZone(zone).toLocalDate()
    val daily = (6 downTo 0).map { d ->
        val day = today.minusDays(d.toLong())
        day to intakes.filter { Instant.ofEpochMilli(it.atEpochMs).atZone(zone).toLocalDate() == day }.sumOf { it.amountMl }
    }
    val hourly = IntArray(24).also { arr ->
        intakes.forEach { arr[Instant.ofEpochMilli(it.atEpochMs).atZone(zone).hour] += it.amountMl }
    }
    val times = intakes.map { it.atEpochMs }.sorted()
    val avgGap = if (times.size >= 5) times.zipWithNext { a, b -> b - a }.average().let { Duration.ofMillis(it.toLong()).toMinutes() } else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Insights", style = MaterialTheme.typography.headlineMedium)
        SectionCard("Intake by day (last 7 days)") {
            Bars(daily.map { it.second.toFloat() }); Text(daily.joinToString("  ") { "${it.first.dayOfMonth}" }, style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Intake by hour of day (all logged data)") {
            Bars(hourly.map { it.toFloat() }); Text("0h ──────── 12h ──────── 23h", style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Average time between drinks") {
            Text(avgGap?.let { "$it min" } ?: "Not enough data yet (needs at least 5 entries)")
        }
        SectionCard("Reminders") {
            Text(if (stats.enoughData) "Acknowledged: ${(stats.acknowledgementRate!! * 100).toInt()}% of ${stats.resolved} reminders"
                 else "Acknowledgement rate appears after 5 reminders (so far ${stats.resolved}).")
            Text("Confirmed ${stats.confirmed} · snoozed ${stats.snoozed} · swiped away ${stats.dismissed} · no response ${stats.ignored}")
        }
        SectionCard("Data completeness") {
            val withData = daily.count { it.second > 0 }
            Text("Drinks were logged on $withData of the last 7 days (${com.hydra.algorithm.InsightsMath.completenessPercent(withData)}%).")
            Text("Heart-rate and weather trend charts are not shown: they need more history than this early version collects, " +
                "and they could not show cause and effect anyway.", style = MaterialTheme.typography.bodySmall)
        }
        Text("These charts show what was logged. They do not measure hydration and cannot show causes.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Bars(values: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v / maxV)
            drawRect(color, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}
