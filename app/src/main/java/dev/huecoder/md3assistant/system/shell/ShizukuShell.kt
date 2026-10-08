package dev.huecoder.md3assistant.system.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.coroutines.resume
import kotlin.system.exitProcess

class ShizukuShell(context: Context) : Shell {
    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellUserService::class.java.name))
        .daemon(false)
        .processNameSuffix(PROCESS_SUFFIX)
        .version(SERVICE_VERSION)
    private val connectLock = Mutex()
    @Volatile private var service: IShellService? = null

    fun status(): AccessStatus = when {
        !Shizuku.pingBinder() -> AccessStatus.NOT_RUNNING
        Shizuku.isPreV11() -> AccessStatus.UNAVAILABLE
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> AccessStatus.NEEDS_PERMISSION
        else -> AccessStatus.READY
    }

    suspend fun requestPermission(): Boolean {
        if (status() != AccessStatus.NEEDS_PERMISSION) return status() == AccessStatus.READY
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != PERMISSION_REQUEST) return
                        Shizuku.removeRequestPermissionResultListener(this)
                        continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
                Shizuku.requestPermission(PERMISSION_REQUEST)
            }
        }
    }

    override suspend fun run(command: String, timeoutMs: Long): ShellResult {
        when (status()) {
            AccessStatus.NOT_RUNNING -> error("Shizuku is not running. Start it in the Shizuku app")
            AccessStatus.NEEDS_PERMISSION -> error("Shizuku permission is not granted to the assistant")
            AccessStatus.UNAVAILABLE -> error("This Shizuku version is too old")
            else -> Unit
        }
        val remote = connect()
        return runInterruptible(Dispatchers.IO) { ShellResult.fromBundle(remote.run(command, timeoutMs)) }
    }

    private suspend fun connect(): IShellService = connectLock.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        withTimeout(BIND_TIMEOUT_MS) { bind() }.also { service = it }
    }

    private suspend fun bind(): IShellService = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (continuation.isActive) continuation.resume(IShellService.Stub.asInterface(binder))
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    service = null
                }
            }
            Shizuku.bindUserService(serviceArgs, connection)
        }
    }

    private companion object {
        const val PROCESS_SUFFIX = "shell"
        const val SERVICE_VERSION = 1
        const val PERMISSION_REQUEST = 4201
        const val BIND_TIMEOUT_MS = 15_000L
    }
}

class ShellUserService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun run(command: String, timeoutMs: Long) =
        ProcessRunner.run(listOf(SYSTEM_SH, "-c", command), timeoutMs, workdir = SHELL_HOME).toBundle()

    private companion object {
        val SHELL_HOME = File("/data/local/tmp")
    }
}
