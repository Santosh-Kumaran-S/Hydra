package com.hydra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydra.data.UserProfileEntity
import com.hydra.profile.Validation as V

@Composable fun ProfileScreen(vm: HydraViewModel) {
    val saved by vm.profile.collectAsStateWithLifecycle()
    val base = saved ?: UserProfileEntity()
    var name by remember(saved) { mutableStateOf(base.name) }
    var height by remember(saved) { mutableStateOf(base.heightCm?.toString() ?: "") }
    var weight by remember(saved) { mutableStateOf(base.weightKg?.toString() ?: "") }
    var wake by remember(saved) { mutableStateOf(V.fmt(base.wakeMin)) }
    var sleep by remember(saved) { mutableStateOf(V.fmt(base.sleepMin)) }
    var target by remember(saved) { mutableStateOf(base.targetMl?.toString() ?: "") }
    var gap by remember(saved) { mutableStateOf(base.clinicianMinGapMin?.toString() ?: "") }
    var zone by remember(saved) { mutableStateOf(base.zoneId) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    @Composable fun field(key: String, label: String, value: String, set: (String) -> Unit, help: String? = null) =
        OutlinedTextField(value, set, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = key in errors, supportingText = { (errors[key] ?: help)?.let { Text(it) } })

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        field("name", "Name or nickname", name, { name = it })
        field("height", "Height (cm)", height, { height = it })
        field("weight", "Weight (kg)", weight, { weight = it })
        field("wake", "Wake-up time (HH:mm)", wake, { wake = it })
        field("sleep", "Sleep time (HH:mm)", sleep, { sleep = it })
        field("zone", "Time zone", zone, { zone = it })
        field("target", "Daily fluid target (mL)", target, { target = it },
            "Enter only the amount your healthcare professional advised. Leave blank if none; nothing is calculated from height or weight.")
        field("gap", "Clinician minimum gap between reminders (min, optional)", gap, { gap = it })
        Button(Modifier.fillMaxWidth(), onClick = {
            val e = mutableMapOf<String, String>()
            val h = V.height(height); h.error?.let { e["height"] = it }
            val w = V.weight(weight); w.error?.let { e["weight"] = it }
            val wk = V.time(wake); wk.error?.let { e["wake"] = it }
            val sl = V.time(sleep); sl.error?.let { e["sleep"] = it }
            if (wk.value != null && sl.value != null) V.distinctTimes(wk.value, sl.value)?.let { e["sleep"] = it }
            val z = V.zone(zone); z.error?.let { e["zone"] = it }
            val t = V.target(target); t.error?.let { e["target"] = it }
            val g = V.minGap(gap); g.error?.let { e["gap"] = it }
            errors = e
            if (e.isEmpty()) vm.saveProfile(base.copy(
                name = name.trim(), heightCm = h.value, weightKg = w.value, wakeMin = wk.value!!, sleepMin = sl.value!!,
                zoneId = z.value!!, targetMl = t.value, clinicianMinGapMin = g.value,
            ))
        }) { Text("Save profile") }
    }
}
