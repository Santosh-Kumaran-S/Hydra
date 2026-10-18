package com.hydra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydra.data.UserProfileEntity
import com.hydra.profile.Validation
import com.hydra.algorithm.IntakeEntry
import com.hydra.algorithm.ProfileInput
import java.time.*

fun UserProfileEntity.zone(): ZoneId = try { ZoneId.of(zoneId) } catch (e: Exception) { ZoneId.systemDefault() }
fun UserProfileEntity.toInput() = ProfileInput(
    wakeTime = LocalTime.of(wakeMin / 60, wakeMin % 60), sleepStart = LocalTime.of(sleepMin / 60, sleepMin % 60),
    zone = zone(), quietModeEnabled = quietEnabled, dailyTargetMl = targetMl,
    clinicianMinGapMin = clinicianMinGapMin?.toLong(),
)
fun com.hydra.data.WaterIntakeEntity.toEntry() = IntakeEntry(id, amountMl, Instant.ofEpochMilli(atEpochMs))

@Composable fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable fun AmountDialog(title: String, initial: String = "", onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val parsed = Validation.amount(text)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text("Amount (mL)") }, singleLine = true,
                isError = text.isNotEmpty() && parsed.error != null,
                supportingText = { if (text.isNotEmpty() && parsed.error != null) Text(parsed.error!!) })
        },
        confirmButton = { TextButton(enabled = parsed.value != null, onClick = { onConfirm(parsed.value!!) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
