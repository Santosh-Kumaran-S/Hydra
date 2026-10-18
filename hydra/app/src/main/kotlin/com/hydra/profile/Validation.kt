package com.hydra.profile

import java.time.LocalTime
import java.time.ZoneId

data class Parsed<T>(val value: T? = null, val error: String? = null)

object Validation {
    private fun num(s: String, label: String, lo: Double, hi: Double): Parsed<Double> {
        val v = s.trim().toDoubleOrNull() ?: return Parsed(error = "$label must be a number")
        return if (v.isNaN() || v < lo || v > hi) Parsed(error = "$label must be between $lo and $hi") else Parsed(v)
    }
    fun temperature(s: String) = num(s, "Temperature (°C)", -20.0, 55.0)
    fun humidity(s: String) = num(s, "Humidity (%)", 0.0, 100.0)
    fun height(s: String) = num(s, "Height (cm)", 50.0, 250.0)
    fun weight(s: String) = num(s, "Weight (kg)", 20.0, 300.0)

    /** Blank = not configured (null value, no error). */
    fun optionalInt(s: String, label: String, lo: Int, hi: Int): Parsed<Int?> {
        if (s.isBlank()) return Parsed(null)
        val v = s.trim().toIntOrNull() ?: return Parsed(error = "$label must be a whole number")
        return if (v !in lo..hi) Parsed(error = "$label must be between $lo and $hi") else Parsed(v)
    }
    fun target(s: String) = optionalInt(s, "Daily target (mL)", 250, 6000)
    fun minGap(s: String) = optionalInt(s, "Minimum gap (min)", 15, 360)
    fun baseline(s: String): Parsed<Int?> {
        val r = optionalInt(s, "Reminder interval (min)", 30, 240)
        return if (r.error == null && r.value == null) Parsed(error = "Reminder interval is required") else r
    }

    fun amount(s: String): Parsed<Int> {
        val v = s.trim().toIntOrNull() ?: return Parsed(error = "Enter a whole number of mL")
        return if (v !in 1..2000) Parsed(error = "Amount must be 1–2000 mL") else Parsed(v)
    }
    fun time(s: String): Parsed<Int> {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(s.trim()) ?: return Parsed(error = "Use HH:mm (24-hour)")
        return Parsed(m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt())
    }
    fun distinctTimes(wakeMin: Int, sleepMin: Int) =
        if (wakeMin == sleepMin) "Wake and sleep times must differ" else null
    fun zone(s: String): Parsed<String> =
        try { Parsed(ZoneId.of(s.trim()).id) } catch (e: Exception) { Parsed(error = "Unknown time zone (e.g. Asia/Kolkata)") }

    fun fmt(min: Int): String = LocalTime.of(min / 60, min % 60).toString()
}
