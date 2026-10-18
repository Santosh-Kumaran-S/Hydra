package com.hydra.reminders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hydra.HydraApp
import com.hydra.ui.AmountDialog
import com.hydra.ui.HydraTheme
import com.hydra.ui.zone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin

class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HydraTheme { ReminderScreen(onClose = { finish() }) } }
    }
}

@Composable fun ReminderScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as HydraApp
    val scope = rememberCoroutineScope()
    var amount by remember { mutableIntStateOf(250) }
    var custom by remember { mutableStateOf(false) }
    var doneAt by remember { mutableStateOf<Instant?>(null) }
    var finished by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val next by app.planner.nextAt.collectAsState()
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase")
    val water = MaterialTheme.colorScheme.primary
    val glass = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    val fmt = DateTimeFormatter.ofPattern("EEE HH:mm")
    var zone by remember { mutableStateOf(java.time.ZoneId.systemDefault()) }
    LaunchedEffect(Unit) { app.repo.profileOnce()?.let { zone = it.zone() } }
    LaunchedEffect(finished) { if (finished) { delay(1600); onClose() } }

    Column(
        Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.size(180.dp).clip(CircleShape).background(glass)) {
            val level = size.height * 0.45f
            val path = Path().apply {
                moveTo(0f, size.height); lineTo(0f, level)
                var x = 0f
                while (x <= size.width) { lineTo(x, level + 8.dp.toPx() * sin(x / size.width * 2f * PI.toFloat() + phase)); x += 4f }
                lineTo(size.width, size.height); close()
            }
            drawPath(path, water)
        }
        Text("Time for your next water break", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("A routine reminder, not a medical assessment.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 150, 200, 250).forEach { ml ->
                if (amount == ml) Button(onClick = { amount = ml }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("$ml") }
                else OutlinedButton(onClick = { amount = ml }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("$ml") }
            }
        }
        TextButton(onClick = { custom = true }) { Text("Custom amount ($amount mL selected)") }
        AnimatedVisibility(doneAt != null) {
            Text("✓ Logged $amount mL. Next reminder ${next?.atZone(zone)?.format(fmt) ?: "—"}", style = MaterialTheme.typography.titleMedium)
        }
        Button(
            enabled = !busy && doneAt == null, modifier = Modifier.fillMaxWidth().height(64.dp),
            onClick = { busy = true; scope.launch { app.planner.confirmDrink(amount); doneAt = Instant.now(); finished = true } },
        ) { Text("I've Drunk Water — Done", style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            enabled = !busy && doneAt == null, modifier = Modifier.fillMaxWidth(),
            onClick = { busy = true; scope.launch { app.planner.snooze(); onClose() } },
        ) { Text("Snooze 10 min") }
        Text("Next scheduled: ${next?.atZone(zone)?.format(fmt) ?: "—"}", style = MaterialTheme.typography.bodySmall)
    }
    if (custom) AmountDialog("Custom amount", amount.toString(), onConfirm = { amount = it; custom = false }, onDismiss = { custom = false })
}
