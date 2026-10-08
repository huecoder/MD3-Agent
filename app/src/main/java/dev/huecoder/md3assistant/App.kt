package dev.huecoder.md3assistant

import android.app.Application
import dev.huecoder.md3assistant.system.notifications.AppNotifications

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(container.visibility)
        AppNotifications.registerChannels(this)
    }
}
