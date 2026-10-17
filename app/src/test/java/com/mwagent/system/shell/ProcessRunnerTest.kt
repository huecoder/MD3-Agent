package com.mwagent.system.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessRunnerTest {
    @Test
    fun capturesBothStreamsAndExitCode() {
        val result = ProcessRunner.run(listOf("sh", "-c", "echo out; echo err >&2; exit 3"), timeoutMs = 5_000)
        assertEquals(3, result.exitCode)
        assertEquals("out\n", result.stdout)
        assertEquals("err\n", result.stderr)
    }

    @Test
    fun killsCommandsThatRunTooLong() {
        val result = ProcessRunner.run(listOf("sh", "-c", "sleep 5"), timeoutMs = 200)
        assertTrue(result.timedOut)
        assertNull(result.exitCode)
    }
}
