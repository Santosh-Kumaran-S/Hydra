package com.hydra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydra.data.UserProfileEntity
import com.hydra.data.WaterIntakeEntity
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable fun WaterLogScreen(vm: HydraViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val intakes by vm.intakes.collectAsStateWithLifecycle()
    val zone = (profile ?: UserProfileEntity()).zone()
    val today = Instant.now().atZone(zone).toLocalDate()
    val byDay = intakes.groupBy { Instant.ofEpochMilli(it.atEpochMs).atZone(zone).toLocalDate() }
    var editing by remember { mutableStateOf<WaterIntakeEntity?>(null) }
    var deleting by remember { mutableStateOf<WaterIntakeEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Water Log", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }) { Text("+ Add") }
            }
        }
        item {
            val week = (0..6).sumOf { d -> byDay[today.minusDays(d.toLong())].orEmpty().sumOf { it.amountMl } }
            val month = (0..29).sumOf { d -> byDay[today.minusDays(d.toLong())].orEmpty().sumOf { it.amountMl } }
            SectionCard("Summaries") {
                Text("Today: ${byDay[today].orEmpty().sumOf { it.amountMl }} mL")
                Text("Last 7 days: $week mL")
                Text("Last 30 days: $month mL")
            }
        }
        item { Text("Today's timeline", style = MaterialTheme.typography.titleMedium) }
        val todays = byDay[today].orEmpty().sortedByDescending { it.atEpochMs }
        if (todays.isEmpty()) item { Text("No drinks logged today.") }
        items(todays, key = { it.id }) { e -> EntryRow(e, zone, timeFmt, { editing = e }, { deleting = e }) }
        item { Text("Earlier", style = MaterialTheme.typography.titleMedium) }
        byDay.filterKeys { it != today }.toSortedMap(reverseOrder()).forEach { (day, list) ->
            item(key = "h$day") { Text("$day · ${list.sumOf { it.amountMl }} mL", style = MaterialTheme.typography.labelLarge) }
            items(list.sortedByDescending { it.atEpochMs }, key = { it.id }) { e ->
                EntryRow(e, zone, timeFmt, { editing = e }, { deleting = e })
            }
        }
    }
    if (adding) AmountDialog("Add water", onConfirm = { vm.addWater(it, "CUSTOM"); adding = false }, onDismiss = { adding = false })
    editing?.let { e ->
        AmountDialog("Edit amount", e.amountMl.toString(), onConfirm = { vm.edit(e, it); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { e ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this entry?") },
            text = { Text("${e.amountMl} mL will be removed from your history.") },
            confirmButton = { TextButton(onClick = { vm.delete(e); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable private fun EntryRow(
    e: WaterIntakeEntity, zone: java.time.ZoneId, fmt: DateTimeFormatter, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${e.amountMl} mL", style = MaterialTheme.typography.titleMedium)
                Text("${Instant.ofEpochMilli(e.atEpochMs).atZone(zone).format(fmt)} · ${e.method.lowercase().replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
