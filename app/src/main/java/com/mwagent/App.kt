package com.mwagent

import android.app.Application
import com.mwagent.system.notifications.AppNotifications
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(container.visibility)
        AppNotifications.registerChannels(this)
        container.applicationScope.launch { container.chats.purgeExpiredTrash() }
    }
}
