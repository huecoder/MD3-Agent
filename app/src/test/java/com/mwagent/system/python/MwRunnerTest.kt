package com.mwagent.system.python

import org.junit.Test

class MwRunnerTest {
    @Test
    fun `the runner stops a script on request and keeps outputs apart`() = HostPythonSuite.run("test_mw_runner.py")
}
