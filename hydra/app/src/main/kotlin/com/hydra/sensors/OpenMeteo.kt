package com.hydra.sensors

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Geo(val label: String, val lat: Double, val lon: Double)
data class Obs(val tempC: Double, val humidity: Double, val observedAtMs: Long)

/** Open-Meteo: HTTPS, no API key. Only a city name and its coordinates are sent; the phone's location is never read. */
object OpenMeteo {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 4000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    fun geocodeUrl(name: String) =
        "https://geocoding-api.open-meteo.com/v1/search?count=1&name=${java.net.URLEncoder.encode(name.trim(), "UTF-8")}"
    fun currentUrl(lat: Double, lon: Double) =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m&timeformat=unixtime"

    fun parseGeocode(json: String): Geo? {
        val arr = JSONObject(json).optJSONArray("results") ?: return null
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        val label = listOf("name", "admin1", "country").mapNotNull { o.optString(it).ifBlank { null } }.joinToString(", ")
        return Geo(label, o.getDouble("latitude"), o.getDouble("longitude"))
    }

    fun parseCurrent(json: String): Obs? {
        val c = JSONObject(json).optJSONObject("current") ?: return null
        if (!c.has("temperature_2m") || !c.has("relative_humidity_2m") || !c.has("time")) return null
        return Obs(c.getDouble("temperature_2m"), c.getDouble("relative_humidity_2m"), c.getLong("time") * 1000)
    }
}
