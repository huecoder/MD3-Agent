package dev.huecoder.md3agent.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.MainActivity
import dev.huecoder.md3agent.ui.avatar.ProvideAgentLook
import dev.huecoder.md3agent.ui.haptics.ProvideHaptics
import dev.huecoder.md3agent.ui.theme.AssistantTheme

class AssistantSession(context: Context) :
    VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val container = (context.applicationContext as App).container

    private val activation = mutableIntStateOf(0)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateContentView(): View {
        window.window?.let { sessionWindow ->
            sessionWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(sessionWindow, false)
            sessionWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            sessionWindow.setGravity(Gravity.FILL)
            sessionWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@AssistantSession))
            setContent {
                AssistantTheme {
                    ProvideAgentLook {
                        ProvideHaptics {
                            AssistantOverlay(
                                activation = activation.intValue,
                                onDismiss = ::hide,
                                onOpenInApp = ::openInApp,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        container.screenContext.clear()
        activation.intValue += 1
        container.visibility.onOverlayShown(true)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onHide() {
        container.visibility.onOverlayShown(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onHandleAssist(state: AssistState) {
        container.screenContext.recordStructure(state.assistStructure)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        container.screenContext.recordScreenshot(screenshot)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun openInApp(chatId: String?) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        chatId?.let { intent.putExtra(MainActivity.EXTRA_CHAT_ID, it) }
        startAssistantActivity(intent)
        hide()
    }
}
