package com.muslimpath.app

import com.muslimpath.app.data.Qibla
import org.junit.Assert.assertEquals
import org.junit.Test

class QiblaTest {
    @Test
    fun bearingMatchesKnownCities() {
        assertEquals(58.5, Qibla.bearing(40.7128, -74.0060), 0.5) // New York
        assertEquals(119.0, Qibla.bearing(51.5074, -0.1278), 0.5) // London
        assertEquals(295.2, Qibla.bearing(-6.2088, 106.8456), 0.5) // Jakarta
    }

    @Test
    fun turnTakesShortestWay() {
        assertEquals(20f, Qibla.turn(350f, 10f), 0.001f)
        assertEquals(-20f, Qibla.turn(10f, 350f), 0.001f)
        assertEquals(0f, Qibla.turn(90f, 90f), 0.001f)
    }
}
