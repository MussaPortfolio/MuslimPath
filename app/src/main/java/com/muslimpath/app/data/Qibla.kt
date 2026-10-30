package com.muslimpath.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Qibla {
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    /** Initial great-circle bearing to the Kaaba, in degrees clockwise from true north (0..360). */
    fun bearing(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** Shortest signed turn between two headings, in -180..180; positive means clockwise. */
    fun turn(from: Float, to: Float): Float {
        val d = (to - from) % 360
        return when {
            d > 180 -> d - 360
            d < -180 -> d + 360
            else -> d
        }
    }
}
