package dev.huecoder.md3agent.system.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppVisibility : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private var overlayShown = false
    private val visible = MutableStateFlow(false)

    val isVisible: StateFlow<Boolean> = visible.asStateFlow()

    fun onOverlayShown(shown: Boolean) {
        overlayShown = shown
        publish()
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
        publish()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        publish()
    }

    private fun publish() {
        visible.value = startedActivities > 0 || overlayShown
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
