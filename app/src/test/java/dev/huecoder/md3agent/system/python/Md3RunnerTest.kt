package dev.huecoder.md3agent.system.python

import org.junit.Test

class Md3RunnerTest {
    @Test
    fun `the runner stops a script on request and keeps outputs apart`() = HostPythonSuite.run("test_md3_runner.py")
}
