package com.hydra.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydra.algorithm.ReminderAlgorithm
import com.hydra.algorithm.AlgorithmConfig
import com.hydra.data.UserProfileEntity
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeScreen(vm: HydraViewModel, onOpen: (String) -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val intakes by vm.intakes.collectAsStateWithLifecycle()
    val remindersOn by vm.remindersOn.collectAsStateWithLifecycle()
    val scheduled by vm.nextAt.collectAsStateWithLifecycle()
    val hrRow by vm.latestHr.collectAsStateWithLifecycle()
    val envRow by vm.latestEnv.collectAsStateWithLifecycle()
    val p = profile ?: UserProfileEntity()
    val zone = p.zone()
    val now by produceState(Instant.now()) { while (true) { delay(30_000); value = Instant.now() } }
    val zNow = now.atZone(zone)
    val dayStart = zNow.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val today = intakes.filter { it.atEpochMs >= dayStart }
    val consumed = today.sumOf { it.amountMl }
    val last = intakes.firstOrNull()
    var custom by remember { mutableStateOf(false) }

    val decision = remember(p, intakes, now) {
        ReminderAlgorithm.calculateNextReminderTime(
            p.toInput(), intakes.map { it.toEntry() }, null, null, null, null, 0, null, now,
            AlgorithmConfig(baselineIntervalMin = p.baselineIntervalMin.toLong()),
        )
    }
    val fraction = p.targetMl?.let { (consumed.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val anim by animateFloatAsState(fraction, tween(700), label = "ring")
    val greeting = when (zNow.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello" }
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$greeting${if (p.name.isNotBlank()) ", ${p.name}" else ""}", style = MaterialTheme.typography.headlineMedium)
        Text(zNow.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.bodyLarge)

        if (profile == null) SectionCard("Welcome") {
            Text("Set up her profile first: sleep times, reminder interval and, if her doctor gave one, the daily fluid target.")
            Button(onClick = { onOpen("profile") }) { Text("Set up profile") }
        }
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val s = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
                val inset = 11.dp.toPx()
                val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
                drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), sz, style = s)
                drawArc(ringColor, -90f, 360f * anim, false, Offset(inset, inset), sz, style = s)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$consumed mL", style = MaterialTheme.typography.headlineLarge)
                Text(
                    p.targetMl?.let { "of $it mL · ${maxOf(0, it - consumed)} mL left" } ?: "No clinician target set",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (p.targetMl == null) Text("Tracking only. Enter the fluid target from her healthcare professional in Profile.",
            style = MaterialTheme.typography.bodySmall)

        SectionCard("Add water") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 150, 200, 250).forEach { ml ->
                    FilledTonalButton(onClick = { vm.addWater(ml, "QUICK_ADD") }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("$ml") }
                }
            }
            Button(onClick = { custom = true }, Modifier.fillMaxWidth()) { Text("+ Add Water (custom amount)") }
        }

        SectionCard("Last drink") {
            if (last == null) Text("Nothing logged yet")
            else {
                val t = Instant.ofEpochMilli(last.atEpochMs)
                val mins = Duration.between(t, now).toMinutes().coerceAtLeast(0)
                Text("${last.amountMl} mL at ${t.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))} (manual entry)")
                Text("${mins / 60} h ${mins % 60} min ago")
            }
        }

        SectionCard(if (remindersOn) "Next reminder" else "Next reminder (preview)") {
            Text((if (remindersOn) scheduled ?: decision.nextReminder else decision.nextReminder).atZone(zone).format(DateTimeFormatter.ofPattern("EEE HH:mm")), style = MaterialTheme.typography.titleLarge)
            Text(decision.explanation)
            Text(if (remindersOn) "Reminders are on." else "Reminders are off. Turn them on in Settings.", style = MaterialTheme.typography.bodySmall)
            Text("Quiet mode: ${if (p.quietEnabled) "on (${com.hydra.profile.Validation.fmt(p.sleepMin)}–${com.hydra.profile.Validation.fmt(p.wakeMin)})" else "off"}")
        }

        SectionCard("Sensors") {
            val h = hrRow; val e = envRow
            val hrFresh = h != null && Duration.between(Instant.ofEpochMilli(h.measuredAtMs), now).toMinutes() <= 30
            val envFresh = e != null && Duration.between(Instant.ofEpochMilli(e.observedAtMs), now).toMinutes() <= 120
            Text(when {
                h == null -> "Heart rate: no data"
                hrFresh -> "Heart rate: ${h.bpm} bpm, synced ${ageText(h.measuredAtMs, now)} (not live)"
                else -> "Heart rate: last ${h.bpm} bpm ${ageText(h.measuredAtMs, now)}. Stale, not used"
            })
            if (h != null) Text("Source: ${h.sourceDevice}", style = MaterialTheme.typography.bodySmall)
            Text(if (e == null) "Temperature / humidity: not set" else
                "${e.temperatureC} °C · ${e.humidityPct.toInt()}% · ${if (e.kind == "MANUAL") "manual" else "weather observation"} · ${ageText(e.observedAtMs, now)}" +
                    if (envFresh) "" else " (stale, not used)")
            if (e != null) Text("Source: ${e.source}", style = MaterialTheme.typography.bodySmall)
            Text("Data quality: " + when {
                hrFresh && envFresh -> "good (watch + environment)"
                hrFresh || envFresh -> "partial"
                else -> "baseline schedule only"
            }, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpen("wearable") }) { Text("Smartwatch") }
                OutlinedButton(onClick = { onOpen("environment") }) { Text("Weather") }
            }
        }
    }
    if (custom) AmountDialog("Add water", onConfirm = { vm.addWater(it, "CUSTOM"); custom = false }, onDismiss = { custom = false })
}
