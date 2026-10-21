package dev.huecoder.md3agent.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val rows = listOf(
        SettingsSearchRow(SettingsPage.PHONE_USE, "Phone use", "Capabilities"),
        SettingsSearchRow(SettingsPage.PHONE_USE, "Cursor size", "Phone use › Cursor"),
        SettingsSearchRow(SettingsPage.APPEARANCE, "Vibration strength", "Appearance › Feedback"),
    )

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(SettingsSearch.filter(rows, "  ").isEmpty())
    }

    @Test
    fun matchesTitleCaseInsensitive() {
        assertEquals(listOf("Vibration strength"), SettingsSearch.filter(rows, "VIBRA").map { it.title })
    }

    @Test
    fun everyWordMustMatchTitleOrPath() {
        assertEquals(listOf("Cursor size"), SettingsSearch.filter(rows, "phone cursor").map { it.title })
    }

    @Test
    fun titleMatchesComeBeforePathMatches() {
        assertEquals(listOf("Phone use", "Cursor size"), SettingsSearch.filter(rows, "phone").map { it.title })
    }
}
