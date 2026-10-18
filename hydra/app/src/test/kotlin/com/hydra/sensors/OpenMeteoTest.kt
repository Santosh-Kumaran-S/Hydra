package com.hydra.sensors

import org.junit.Assert.*
import org.junit.Test

class OpenMeteoTest {
    @Test fun parsesGeocode() {
        val g = OpenMeteo.parseGeocode("""{"results":[{"name":"Chennai","admin1":"Tamil Nadu","country":"India","latitude":13.08,"longitude":80.27}]}""")
        assertEquals("Chennai, Tamil Nadu, India", g!!.label); assertEquals(13.08, g.lat, 0.0)
    }
    @Test fun noResultsIsNull() { assertNull(OpenMeteo.parseGeocode("""{"generationtime_ms":0.1}""")) }
    @Test fun parsesCurrent() {
        val o = OpenMeteo.parseCurrent("""{"current":{"time":1760000000,"temperature_2m":31.2,"relative_humidity_2m":70}}""")
        assertEquals(31.2, o!!.tempC, 0.0); assertEquals(1760000000_000L, o.observedAtMs)
    }
    @Test fun incompleteCurrentIsNull() { assertNull(OpenMeteo.parseCurrent("""{"current":{"time":1}}""")) }
}
