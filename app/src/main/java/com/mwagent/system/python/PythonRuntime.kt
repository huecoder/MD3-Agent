package com.mwagent.system.python

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PythonResult(val output: String, val failed: Boolean, val timedOut: Boolean)

class PythonRuntime(private val context: Context) {
    private val interpreterThreads = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val version: String get() = PYTHON_VERSION

    suspend fun run(code: String, workdir: File, timeoutSeconds: Long, bridge: ScriptBridge? = null): PythonResult {
        val script = withContext(Dispatchers.IO) {
            workdir.mkdirs()
            interpreter().getModule(RUNNER_MODULE).callAttr(START_FUNCTION, code, workdir.absolutePath, bridge)
        }
        try {
            return awaitResult(script, timeoutSeconds, bridge)
        } finally {
            bridge?.abort()
        }
    }

    private suspend fun awaitResult(script: PyObject, timeoutSeconds: Long, bridge: ScriptBridge?): PythonResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            bridge?.abort()
            interpreterThreads.launch { script.callAttr(CANCEL_FUNCTION) }
        }
        interpreterThreads.launch {
            try {
                val result = script.callAttr(WAIT_FUNCTION, timeoutSeconds).asList()
                continuation.resume(
                    PythonResult(
                        output = result[OUTPUT_INDEX].toString(),
                        failed = result[FAILED_INDEX].toBoolean(),
                        timedOut = result[TIMED_OUT_INDEX].toBoolean(),
                    ),
                )
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    @Synchronized
    private fun interpreter(): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        return Python.getInstance()
    }

    private companion object {
        const val PYTHON_VERSION = "3.13"
        const val RUNNER_MODULE = "mw_runner"
        const val START_FUNCTION = "start"
        const val WAIT_FUNCTION = "wait"
        const val CANCEL_FUNCTION = "cancel"
        const val OUTPUT_INDEX = 0
        const val FAILED_INDEX = 1
        const val TIMED_OUT_INDEX = 2
    }
}
