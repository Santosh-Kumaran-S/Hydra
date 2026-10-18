package com.hydra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydra.HydraApp
import com.hydra.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HydraViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as HydraApp).repo
    private val planner = (app as HydraApp).planner
    val remindersOn = planner.remindersOn
    val nextAt = planner.nextAt
    fun canExact() = planner.scheduler.canExact()
    fun setReminders(on: Boolean) { viewModelScope.launch { planner.setEnabled(on) } }
    private val hub = (app as HydraApp).hub
    val hcState = hub.hcState
    val latestHr = repo.latestHrFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val latestEnv = repo.latestEnvFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val city get() = hub.city
    fun refreshSensors() { viewModelScope.launch { hub.refreshHeartRate(); hub.refreshWeather(); planner.recompute() } }
    fun lookupCity(name: String) { viewModelScope.launch { message.value = hub.setCity(name); planner.recompute() } }
    fun saveManualEnv(t: Double, h: Double) { viewModelScope.launch { hub.saveManual(t, h); planner.recompute(); message.value = "Saved manual reading" } }
    init { refreshSensors() } // refresh sensors and re-arm whenever the app opens
    val profile: StateFlow<UserProfileEntity?> =
        repo.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val intakes: StateFlow<List<WaterIntakeEntity>> =
        repo.intakes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)

    fun addWater(ml: Int, method: String) = viewModelScope.launch {
        message.value = when (repo.addWater(ml, method)) {
            AddResult.ADDED -> { com.hydra.reminders.Notifications.cancel(getApplication()); planner.recompute(); "Logged $ml mL" }
            AddResult.DUPLICATE -> "Looks like a duplicate — not logged"
            AddResult.INVALID -> "Amount must be 1–2000 mL"
        }
    }
    fun edit(e: WaterIntakeEntity, ml: Int) = viewModelScope.launch {
        message.value = if (repo.editAmount(e, ml)) { planner.recompute(); "Updated" } else "Invalid amount"
    }
    fun delete(e: WaterIntakeEntity) = viewModelScope.launch { repo.delete(e); planner.recompute(); message.value = "Deleted" }
    fun saveProfile(p: UserProfileEntity) = viewModelScope.launch { repo.saveProfile(p); planner.recompute(); message.value = "Saved" }
    fun deleteAll() = viewModelScope.launch { repo.deleteAllData(); planner.setEnabled(false); message.value = "All data deleted" }
    val reminderStatuses = repo.reminderStatuses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun exportTo(uri: android.net.Uri) = viewModelScope.launch {
        message.value = try {
            val json = repo.exportJson()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            "Exported"
        } catch (e: Exception) { "Export failed" }
    }
    fun clearMessage() { message.value = null }
}
