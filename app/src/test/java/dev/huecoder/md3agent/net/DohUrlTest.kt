package dev.huecoder.md3agent.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DohUrlTest {
    @Test
    fun `bare host gets https scheme and the standard dns-query path`() {
        assertEquals("https://dns.example.com/dns-query", DohUrl.parse("dns.example.com").toString())
    }

    @Test
    fun `explicit path is kept`() {
        assertEquals("https://dns.example.com/custom", DohUrl.parse(" https://dns.example.com/custom ").toString())
    }

    @Test
    fun `plain http and garbage are rejected`() {
        assertNull(DohUrl.parse("http://dns.example.com/dns-query"))
        assertNull(DohUrl.parse("   "))
        assertNull(DohUrl.parse("not a host"))
    }
}
