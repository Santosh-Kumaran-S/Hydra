package com.hydra.sensors

import android.content.Context
import com.hydra.data.EnvironmentReadingEntity
import com.hydra.data.HeartRateReadingEntity
import com.hydra.data.HydraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Pulls sensor data on demand (app open, reminder time). No polling loops, no background services. */
class SensorHub(ctx: Context, private val repo: HydraRepository) {
    private val prefs = ctx.getSharedPreferences("sensor_state", Context.MODE_PRIVATE)
    val hc = HealthConnectHeartRateSource(ctx)
    val hcState = MutableStateFlow<HcState?>(null)
    val city: String? get() = prefs.getString("cityLabel", null)

    /** Stores a newer reading if one exists. Returns true if the source is readable. */
    suspend fun refreshHeartRate(): Boolean = try {
        val st = hc.state(); hcState.value = st
        if (st != HcState.READY) false else {
            val r = hc.latest()
            val last = repo.latestHr()
            if (r != null && (last == null || r.measuredAt.toEpochMilli() > last.measuredAtMs))
                repo.saveHr(HeartRateReadingEntity(bpm = r.value, measuredAtMs = r.measuredAt.toEpochMilli(), sourceDevice = r.source, status = "SYNCED"))
            true
        }
    } catch (e: Exception) { false } // e.g. background read not permitted: fall back to stored data, which goes stale

    suspend fun setCity(name: String): String = try {
        val g = withContext(Dispatchers.IO) { OpenMeteo.parseGeocode(OpenMeteo.get(OpenMeteo.geocodeUrl(name))) }
            ?: return "City not found"
        prefs.edit().putString("cityLabel", g.label).putString("lat", g.lat.toString()).putString("lon", g.lon.toString()).apply()
        refreshWeather(force = true) ?: "Saved ${g.label}"
    } catch (e: Exception) { "Could not reach the weather service. Check your connection." }

    /** Returns a short status message, or null if nothing needed doing. At most one request per 15 min; observation reused for 60 min. */
    suspend fun refreshWeather(force: Boolean = false): String? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("lastTry", 0L) < 15 * 60_000) return null
        val latest = repo.latestEnv()
        if (!force && latest?.kind == "OBSERVATION" && now - latest.observedAtMs < 60 * 60_000) return null
        prefs.edit().putLong("lastTry", now).apply()
        return try {
            val obs = withTimeoutOrNull(6000) { withContext(Dispatchers.IO) { OpenMeteo.parseCurrent(OpenMeteo.get(OpenMeteo.currentUrl(lat, lon))) } }
                ?: return "Weather service returned no data"
            repo.saveEnv(EnvironmentReadingEntity(temperatureC = obs.tempC, humidityPct = obs.humidity,
                observedAtMs = obs.observedAtMs, source = "Open-Meteo: ${city ?: "saved city"}", kind = "OBSERVATION"))
            "Weather updated"
        } catch (e: Exception) { "Could not reach the weather service" }
    }

    suspend fun saveManual(tempC: Double, humidity: Double) = repo.saveEnv(EnvironmentReadingEntity(
        temperatureC = tempC, humidityPct = humidity, observedAtMs = System.currentTimeMillis(), source = "Manual entry", kind = "MANUAL"))
}
