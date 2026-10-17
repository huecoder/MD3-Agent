package com.mwagent.tools.location

import com.mwagent.system.location.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationReportTest {
    @Test
    fun `fresh gps fix with place`() {
        val fix = LocationFix(56.83892, 60.60570, accuracyMeters = 11.6f, provider = "gps", ageMillis = 4_000)
        assertEquals(
            "Place: Lenina Avenue 1, Yekaterinburg\nCoordinates: 56.83892, 60.60570 (accuracy ±12 m, gps, fresh)",
            LocationReport.format(fix, "Lenina Avenue 1, Yekaterinburg"),
        )
    }

    @Test
    fun `cached network fix without accuracy or place`() {
        val fix = LocationFix(56.8, 60.6, accuracyMeters = null, provider = "network", ageMillis = 3 * 3_600_000L)
        assertEquals(
            "Place: address not found\nCoordinates: 56.80000, 60.60000 (network, 3 h old)",
            LocationReport.format(fix, null),
        )
    }

    @Test
    fun `age is shown in minutes below an hour`() {
        val fix = LocationFix(0.0, 0.0, accuracyMeters = null, provider = "gps", ageMillis = 7 * 60_000L)
        assertEquals(
            "Place: address not found\nCoordinates: 0.00000, 0.00000 (gps, 7 min old)",
            LocationReport.format(fix, null),
        )
    }
}
