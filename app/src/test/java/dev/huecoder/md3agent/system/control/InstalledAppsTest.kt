package dev.huecoder.md3agent.system.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledAppsTest {
    private val apps = listOf(
        LaunchableApp("Telegram", "org.telegram.messenger", "Main"),
        LaunchableApp("Telegram X", "org.thunderdog.challegram", "Main"),
        LaunchableApp("Google Maps", "com.google.android.apps.maps", "Main"),
        LaunchableApp("Камера", "com.android.camera", "Main"),
    )

    @Test
    fun `exact label wins over longer prefix matches`() {
        assertEquals("org.telegram.messenger", InstalledApps.best(apps, "telegram")?.packageName)
    }

    @Test
    fun `partial label and package names match`() {
        assertEquals("Google Maps", InstalledApps.best(apps, "maps")?.label)
        assertEquals("Telegram X", InstalledApps.best(apps, "challegram")?.label)
        assertEquals("Камера", InstalledApps.best(apps, "камера")?.label)
    }

    @Test
    fun `blank and unknown queries find nothing`() {
        assertNull(InstalledApps.best(apps, "  "))
        assertNull(InstalledApps.best(apps, "spotify"))
    }
}
