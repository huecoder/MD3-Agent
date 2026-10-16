package dev.huecoder.md3agent.system.python

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit

internal object HostPythonSuite {
    private const val INTERPRETER = "python3"
    private const val SUITE_TIMEOUT_SECONDS = 90L
    private val runnerDirectory = File("src/main/python").absoluteFile

    fun run(suiteName: String) {
        assumeTrue("python3 is not installed", hasInterpreter())
        val suite = File("src/test/python/$suiteName").absoluteFile

        val process = ProcessBuilder(INTERPRETER, "-I", suite.path)
            .redirectErrorStream(true)
            .apply { environment()["MD3_RUNNER_DIR"] = runnerDirectory.path }
            .start()
        val report = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(SUITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals(report, true, finished)
        assertEquals(report, 0, process.exitValue())
    }

    private fun hasInterpreter(): Boolean = runCatching {
        ProcessBuilder(INTERPRETER, "--version").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)
}
