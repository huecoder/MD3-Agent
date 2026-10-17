package com.mwagent.system.shell

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class TermuxShell(private val context: Context) : Shell {
    private val requestCodes = AtomicInteger()

    fun status(): AccessStatus = when {
        !isInstalled() -> AccessStatus.NOT_INSTALLED
        ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED -> AccessStatus.NEEDS_PERMISSION
        else -> AccessStatus.READY
    }

    override suspend fun run(command: String, timeoutMs: Long): ShellResult {
        when (status()) {
            AccessStatus.NOT_INSTALLED -> error("Termux is not installed")
            AccessStatus.NEEDS_PERMISSION -> error("Termux RUN_COMMAND permission is not granted to the assistant")
            else -> Unit
        }
        return withTimeoutOrNull(timeoutMs) { awaitResult(command) } ?: ShellResult(null, "", "", timedOut = true)
    }

    private suspend fun awaitResult(command: String): ShellResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val action = "${context.packageName}.TERMUX_RESULT.${UUID.randomUUID()}"
            val registered = AtomicBoolean(true)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (registered.getAndSet(false)) context.unregisterReceiver(this)
                    continuation.resume(parseResult(intent.getBundleExtra(EXTRA_RESULT)))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation { if (registered.getAndSet(false)) context.unregisterReceiver(receiver) }
            val callback = PendingIntent.getBroadcast(
                context,
                requestCodes.incrementAndGet(),
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
            try {
                context.startService(runIntent(command, callback))
            } catch (e: IllegalStateException) {
                context.startForegroundService(runIntent(command, callback))
            } catch (e: SecurityException) {
                if (registered.getAndSet(false)) context.unregisterReceiver(receiver)
                continuation.resumeWith(Result.failure(IllegalStateException("Termux refused the command: ${e.message}")))
            }
        }
    }

    private fun runIntent(command: String, callback: PendingIntent) = Intent(ACTION_RUN_COMMAND)
        .setClassName(PACKAGE, RUN_COMMAND_SERVICE)
        .putExtra(EXTRA_PATH, BASH)
        .putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
        .putExtra(EXTRA_WORKDIR, HOME)
        .putExtra(EXTRA_BACKGROUND, true)
        .putExtra(EXTRA_PENDING_INTENT, callback)

    private fun parseResult(bundle: Bundle?): ShellResult {
        if (bundle == null) return ShellResult(null, "", "Termux returned no result")
        val error = bundle.getInt(RESULT_ERR, RESULT_OK)
        val errorMessage = bundle.getString(RESULT_ERRMSG).orEmpty()
        val stderr = bundle.getString(RESULT_STDERR).orEmpty()
        return ShellResult(
            exitCode = if (bundle.containsKey(RESULT_EXIT_CODE)) bundle.getInt(RESULT_EXIT_CODE) else null,
            stdout = bundle.getString(RESULT_STDOUT).orEmpty(),
            stderr = if (error == RESULT_OK || errorMessage.isEmpty()) stderr else listOf(stderr, errorMessage).filter(String::isNotEmpty).joinToString("\n"),
        )
    }

    private fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val PACKAGE = "com.termux"
        const val PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val ENABLE_EXTERNAL_APPS = "mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings"
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val EXTRA_RESULT = "result"
        private const val RESULT_STDOUT = "stdout"
        private const val RESULT_STDERR = "stderr"
        private const val RESULT_EXIT_CODE = "exitCode"
        private const val RESULT_ERR = "err"
        private const val RESULT_ERRMSG = "errmsg"
        private const val RESULT_OK = -1
        private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val HOME = "/data/data/com.termux/files/home"
    }
}
