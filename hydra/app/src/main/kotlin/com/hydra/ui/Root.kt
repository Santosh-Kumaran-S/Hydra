package com.hydra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val tabs = listOf("Home", "Water Log", "Insights", "Profile", "Settings")

@Composable fun HydraRoot(vm: HydraViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = sub != null) { sub = null }
    val msg by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.clearMessage() } }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.Default.Home, Icons.Default.Add, Icons.Default.Info, Icons.Default.Person, Icons.Default.Settings)
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i; sub = null },
                        icon = { Icon(icons[i], contentDescription = label) }, label = { Text(label, maxLines = 1) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (sub == "wearable") WearableScreen(vm) { sub = null }
            else if (sub == "environment") EnvironmentScreen(vm) { sub = null }
            else when (tab) {
                0 -> HomeScreen(vm) { if (it == "profile") tab = 3 else sub = it }
                1 -> WaterLogScreen(vm)
                2 -> InsightsScreen(vm)
                3 -> ProfileScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
