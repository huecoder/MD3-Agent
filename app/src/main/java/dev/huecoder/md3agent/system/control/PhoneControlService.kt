package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.huecoder.md3agent.App

class PhoneControlService : AccessibilityService() {
    private val control: PhoneControl get() = (application as App).container.phoneControl

    override fun onServiceConnected() {
        super.onServiceConnected()
        control.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        control.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        control.detach(this)
        super.onDestroy()
    }
}
