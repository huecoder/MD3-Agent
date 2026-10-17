package com.mwagent.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `tags with a v prefix and suffix parse`() {
        assertEquals("0.7.0", AppVersion.parse("v0.7.0-beta")?.label)
    }

    @Test
    fun `numeric parts compare as numbers`() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.3")!!)
    }

    @Test
    fun `missing parts count as zero`() {
        assertEquals(AppVersion.parse("1.0"), AppVersion.parse("1.0.0"))
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(AppVersion.parse("nightly"))
    }
}
