package dev.huecoder.md3agent.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import dev.huecoder.md3agent.ui.haptics.clickHaptics
import dev.huecoder.md3agent.ui.theme.AssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AssistantSession(context: Context) :
    VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val container = (context.applicationContext as App).container

    private var activation = 0
    private var contentHost: FrameLayout? = null
    private var backHandler: (() -> Boolean)? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateContentView(): View {
        configureWindow()
        return FrameLayout(context).also { host ->
            host.viewTreeObserver.addOnDrawListener(SystemPanCanceller(host))
            contentHost = host
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        container.screenContext.clear()
        activation += 1
        window.window?.clearFlags(UNTOUCHABLE_FLAGS)
        mountContent(activation)
        container.visibility.onOverlayShown(true)
        container.assistant.onShown(close = ::hide, resume = ::restoreAfterPicker)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        captureScreenElements()
    }

    override fun onHide() {
        contentHost?.removeAllViews()
        window.window?.clearFlags(UNTOUCHABLE_FLAGS)
        backHandler = null
        container.assistant.onHidden()
        container.visibility.onOverlayShown(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onHide()
    }

    override fun onBackPressed() {
        if (backHandler?.invoke() != true) super.onBackPressed()
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

    private fun configureWindow() {
        window.window?.let { sessionWindow ->
            sessionWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(sessionWindow, false)
            sessionWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            sessionWindow.setGravity(Gravity.FILL)
            sessionWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            sessionWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            sessionWindow.decorView.let { decor ->
                decor.setViewTreeLifecycleOwner(this)
                decor.setViewTreeViewModelStoreOwner(this)
                decor.setViewTreeSavedStateRegistryOwner(this)
            }
        }
    }

    private fun mountContent(shownActivation: Int) {
        val host = contentHost ?: return
        host.removeAllViews()
        val overlay = ComposeView(context).apply {
            setContent {
                AssistantTheme {
                    ProvideAgentLook {
                        ProvideHaptics {
                            Box(Modifier.clickHaptics()) {
                                AssistantOverlay(
                                    activation = shownActivation,
                                    onDismiss = ::hide,
                                    onOpenInApp = ::openInApp,
                                    onPickAttachment = ::pickAttachment,
                                    onBackHandler = { backHandler = it },
                                )
                            }
                        }
                    }
                }
            }
        }
        host.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun captureScreenElements() {
        container.applicationScope.launch(Dispatchers.Default) {
            container.phoneControl.describeFrontScreen(SCREEN_ELEMENTS_CHARS)?.let { screen ->
                container.screenContext.recordElements(screen.packageName, screen.text)
            }
        }
    }

    private fun pickAttachment(source: AttachSource) {
        makeUntouchable()
        context.startActivity(AttachPickerActivity.intent(context, source))
    }

    private fun makeUntouchable() {
        window.window?.let {
            it.addFlags(UNTOUCHABLE_FLAGS)
            it.decorView.visibility = View.INVISIBLE
        }
    }

    private fun restoreAfterPicker() {
        window.window?.let {
            it.clearFlags(UNTOUCHABLE_FLAGS)
            it.decorView.visibility = View.VISIBLE
        }
    }

    private fun openInApp(chatId: String?) {
        startAssistantActivity(MainActivity.chatIntent(context, chatId))
        hide()
    }

    private class SystemPanCanceller(private val target: View) : ViewTreeObserver.OnDrawListener {
        private val location = IntArray(2)

        override fun onDraw() {
            target.getLocationInWindow(location)
            if (location[1] != 0) target.translationY -= location[1]
        }
    }

    private companion object {
        const val UNTOUCHABLE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        const val SCREEN_ELEMENTS_CHARS = 6_000
    }
}
