package dev.huecoder.md3agent.system.python

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class Md3RunnerTest {
    private val interpreter = "python3"
    private val runnerDirectory = File("src/main/python").absoluteFile
    private val suite = File("src/test/python/test_md3_runner.py").absoluteFile

    @Test
    fun `the runner stops a script on request and keeps outputs apart`() {
        assumeTrue("python3 is not installed", hasInterpreter())

        val process = ProcessBuilder(interpreter, "-I", suite.path)
            .redirectErrorStream(true)
            .apply { environment()["MD3_RUNNER_DIR"] = runnerDirectory.path }
            .start()
        val report = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(SUITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals(report, true, finished)
        assertEquals(report, 0, process.exitValue())
    }

    private fun hasInterpreter(): Boolean = runCatching {
        ProcessBuilder(interpreter, "--version").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    private companion object {
        const val SUITE_TIMEOUT_SECONDS = 60L
    }
}
