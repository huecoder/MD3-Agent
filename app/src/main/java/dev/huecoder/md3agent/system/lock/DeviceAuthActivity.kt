package dev.huecoder.md3agent.system.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R

class DeviceAuthActivity : Activity() {
    private val lock get() = (application as App).container.deviceLock
    private var signal: CancellationSignal? = null
    private var concluded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        lock.attach(this)
        runCatching { if (intent.getBooleanExtra(EXTRA_UNLOCK, false)) requestUnlock() else confirm() }
            .onFailure { conclude(false) }
    }

    override fun onDestroy() {
        signal?.cancel()
        lock.detach(this)
        if (!concluded) lock.report(false)
        super.onDestroy()
    }

    private fun confirm() {
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.lock_auth_title))
            .setSubtitle(getString(R.string.lock_auth_subtitle))
            .allowingDeviceCredential()
            .build()
        val cancellation = CancellationSignal().also { signal = it }
        prompt.authenticate(
            cancellation,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = conclude(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = conclude(false)
            },
        )
    }

    private fun requestUnlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = conclude(true)

                override fun onDismissCancelled() = conclude(false)

                override fun onDismissError() = conclude(false)
            },
        )
    }

    private fun conclude(confirmed: Boolean) {
        if (concluded) return
        concluded = true
        lock.report(confirmed)
        finish()
    }

    companion object {
        private const val EXTRA_UNLOCK = "unlock"

        fun intent(context: Context, unlock: Boolean): Intent =
            Intent(context, DeviceAuthActivity::class.java)
                .putExtra(EXTRA_UNLOCK, unlock)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

@Suppress("DEPRECATION")
private fun BiometricPrompt.Builder.allowingDeviceCredential(): BiometricPrompt.Builder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    } else {
        setDeviceCredentialAllowed(true)
    }
