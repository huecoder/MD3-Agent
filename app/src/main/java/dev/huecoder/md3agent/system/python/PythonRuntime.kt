package dev.huecoder.md3agent.system.python

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File

data class PythonResult(val output: String, val failed: Boolean, val timedOut: Boolean)

class PythonRuntime(private val context: Context) {
    val version: String get() = PYTHON_VERSION

    suspend fun run(code: String, workdir: File, timeoutSeconds: Long): PythonResult = runInterruptible(Dispatchers.IO) {
        workdir.mkdirs()
        val runner = interpreter().getModule(RUNNER_MODULE)
        val result = runner.callAttr(RUN_FUNCTION, code, workdir.absolutePath, timeoutSeconds).asList()
        PythonResult(
            output = result[OUTPUT_INDEX].toString(),
            failed = result[FAILED_INDEX].toBoolean(),
            timedOut = result[TIMED_OUT_INDEX].toBoolean(),
        )
    }

    @Synchronized
    private fun interpreter(): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        return Python.getInstance()
    }

    private companion object {
        const val PYTHON_VERSION = "3.13"
        const val RUNNER_MODULE = "md3_runner"
        const val RUN_FUNCTION = "run"
        const val OUTPUT_INDEX = 0
        const val FAILED_INDEX = 1
        const val TIMED_OUT_INDEX = 2
    }
}
