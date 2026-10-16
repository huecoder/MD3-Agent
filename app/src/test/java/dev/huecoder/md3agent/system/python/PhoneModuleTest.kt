package dev.huecoder.md3agent.system.python

import org.junit.Test

class PhoneModuleTest {
    @Test
    fun `the phone module builds requests, reads replies and stops with the script`() = HostPythonSuite.run("test_phone.py")
}
