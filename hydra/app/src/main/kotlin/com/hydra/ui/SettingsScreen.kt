package com.hydra.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydra.data.UserProfileEntity
import com.hydra.profile.Validation as V

@Composable fun SettingsScreen(vm: HydraViewModel) {
    val ctx = LocalContext.current
    val saved by vm.profile.collectAsStateWithLifecycle()
    val remindersOn by vm.remindersOn.collectAsStateWithLifecycle()
    val p = saved ?: UserProfileEntity()
    var interval by remember(saved) { mutableStateOf(p.baselineIntervalMin.toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsed = V.baseline(interval)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.setReminders(true) else vm.message.value = "Notification permission denied. Reminders stay off."
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.exportTo(uri) }
    fun turnOn() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setReminders(true)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        SectionCard("Reminders") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Water reminders")
                    Text("Notifications are needed so reminders can reach you.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(remindersOn, { if (it) turnOn() else vm.setReminders(false) })
            }
            if (remindersOn && !vm.canExact() && Build.VERSION.SDK_INT >= 31) {
                Text("Exact alarms are off, so reminders may arrive a few minutes late.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Allow exact timing") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quiet mode (no routine reminders during sleep)", Modifier.weight(1f))
                Switch(p.quietEnabled, { vm.saveProfile(p.copy(quietEnabled = it)) })
            }
            OutlinedTextField(interval, { interval = it }, label = { Text("Usual reminder interval (min)") }, singleLine = true,
                isError = parsed.error != null, supportingText = { parsed.error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
            Button(enabled = parsed.value != null, onClick = { vm.saveProfile(p.copy(baselineIntervalMin = parsed.value!!)) }) { Text("Save interval") }
            Text("Android or the phone maker may delay alerts if battery optimisation is strict. If reminders are late, set Hydra to " +
                "'Unrestricted' under Settings → Apps → Hydra → Battery. Reminders cannot override Do Not Disturb or a silenced channel.",
                style = MaterialTheme.typography.bodySmall)
        }
        SectionCard("Privacy") {
            Text("All data stays on this phone. Nothing is uploaded, and there are no ads or analytics.")
            Text("Hydra is a reminder and logging tool. It does not measure hydration, diagnose, or replace advice from a healthcare professional.")
        }
        SectionCard("Your data") {
            OutlinedButton(onClick = { exportLauncher.launch("hydra-export.json") }) { Text("Export my data (JSON)") }
            Button(onClick = { confirmDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Delete all data")
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete everything?") },
        text = { Text("Profile and all water entries will be permanently removed, and reminders turned off.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
