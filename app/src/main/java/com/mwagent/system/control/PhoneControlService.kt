package com.mwagent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.mwagent.App
import com.mwagent.feature.phoneuse.PhoneUseOverlay

class PhoneControlService : AccessibilityService() {
    private val container get() = (application as App).container
    private val control: PhoneControl get() = container.phoneControl
    private var overlay: PhoneUseOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        control.attach(this)
        overlay = PhoneUseOverlay(this, container).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onDisplayChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        releaseOverlay()
        control.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseOverlay()
        control.detach(this)
        super.onDestroy()
    }

    private fun releaseOverlay() {
        overlay?.stop()
        overlay = null
    }
}
