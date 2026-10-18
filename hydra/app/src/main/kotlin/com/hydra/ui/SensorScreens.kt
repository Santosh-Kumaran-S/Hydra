package com.hydra.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydra.profile.Validation as V
import com.hydra.sensors.HcState
import com.hydra.sensors.HealthConnectHeartRateSource
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

fun ageText(fromMs: Long, now: Instant): String {
    val m = Duration.between(Instant.ofEpochMilli(fromMs), now).toMinutes().coerceAtLeast(0)
    return if (m < 60) "$m min ago" else "${m / 60} h ${m % 60} min ago"
}

@Composable fun WearableScreen(vm: HydraViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hr by vm.latestHr.collectAsStateWithLifecycle()
    val state by vm.hcState.collectAsStateWithLifecycle()
    val now by produceState(Instant.now()) { while (true) { delay(30_000); value = Instant.now() } }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refreshSensors() }
    LaunchedEffect(Unit) { vm.refreshSensors() }
    val h = hr

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Smartwatch connection", style = MaterialTheme.typography.headlineMedium)
        SectionCard("Status") {
            Text("Integration: Health Connect (read-only heart rate)")
            Text(when (state) {
                null -> "Checking…"
                HcState.UNAVAILABLE -> "Health Connect is not available on this phone."
                HcState.UPDATE_REQUIRED -> "Health Connect needs an update."
                HcState.NEEDS_PERMISSION -> "Permission not granted yet."
                HcState.READY -> "Permission granted. Reading data written by other apps."
            })
            when (state) {
                HcState.NEEDS_PERMISSION -> Button(onClick = { launcher.launch(HealthConnectHeartRateSource.PERMS) }) { Text("Allow heart-rate access") }
                HcState.UPDATE_REQUIRED, HcState.UNAVAILABLE -> OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                }) { Text("Get Health Connect") }
                else -> OutlinedButton(onClick = { vm.refreshSensors() }) { Text("Refresh now") }
            }
        }
        SectionCard("Latest heart rate") {
            if (h == null) Text("No heart-rate data received.")
            else {
                val age = Duration.between(Instant.ofEpochMilli(h.measuredAtMs), now).toMinutes()
                Text("${h.bpm} bpm", style = MaterialTheme.typography.headlineSmall)
                Text("Measured ${ageText(h.measuredAtMs, now)} · written by ${h.sourceDevice}")
                Text(if (age <= 30) "Recent (synced, not live). Used only as a minor input."
                     else "Stale. Ignored by the reminder schedule.")
            }
        }
        SectionCard("Compatibility and troubleshooting") {
            Text("The boAt Wave Style is not a Wear OS watch and has no standard live heart-rate feed. Hydra can only use heart rate " +
                "if the boAt Crest app copies it into Health Connect. Whether it does is not confirmed.")
            Text("1. In boAt Crest, look for a Health Connect or Google Fit sync option and enable it.")
            Text("2. In Health Connect → App permissions, check that Crest appears and may write heart rate.")
            Text("3. Wear the watch, let Crest sync, then tap Refresh here. Readings arrive in batches, so expect delays.")
            Text("If nothing appears, Hydra still works as a tracker with routine reminders. Heart rate is never needed.")
            Text("Reminders do not read heart rate while the app is closed; the last synced value is used until it goes stale (30 min).",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun EnvironmentScreen(vm: HydraViewModel, onBack: () -> Unit) {
    val env by vm.latestEnv.collectAsStateWithLifecycle()
    val now by produceState(Instant.now()) { while (true) { delay(30_000); value = Instant.now() } }
    var city by remember { mutableStateOf(vm.city ?: "") }
    var temp by remember { mutableStateOf("") }
    var hum by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val e = env

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Temperature and humidity", style = MaterialTheme.typography.headlineMedium)
        SectionCard("Latest reading") {
            if (e == null) Text("None yet.")
            else {
                Text("${e.temperatureC} °C · ${e.humidityPct.toInt()} % humidity", style = MaterialTheme.typography.titleLarge)
                Text("${when (e.kind) { "MANUAL" -> "Manually entered"; "OBSERVATION" -> "Weather observation"; else -> e.kind }} · ${e.source}")
                Text("Observed ${ageText(e.observedAtMs, now)}")
                val stale = Duration.between(Instant.ofEpochMilli(e.observedAtMs), now).toMinutes() > 120
                Text(if (stale) "Stale. Ignored by the reminder schedule." else "Fresh enough to be used as a small input.")
            }
        }
        SectionCard("Weather for a city") {
            OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = city.isNotBlank(), onClick = { vm.lookupCity(city) }) { Text("Look up weather") }
            Text("Only the city name is sent (HTTPS, Open-Meteo, no account). Your phone's location is not used. " +
                "This is local weather, not the temperature right around her.", style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("Enter manually") {
            OutlinedTextField(temp, { temp = it }, label = { Text("Temperature (°C)") }, singleLine = true, isError = "t" in err,
                supportingText = { err["t"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(hum, { hum = it }, label = { Text("Humidity (%)") }, singleLine = true, isError = "h" in err,
                supportingText = { err["h"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val t = V.temperature(temp); val h = V.humidity(hum)
                err = buildMap { t.error?.let { put("t", it) }; h.error?.let { put("h", it) } }
                if (err.isEmpty()) vm.saveManualEnv(t.value!!, h.value!!)
            }) { Text("Save reading") }
            Text("Manual readings count as fresh for 2 hours.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
