package com.mwagent.feature.phoneuse

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.mwagent.system.control.TouchArea

class OverlayWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val content: @Composable () -> Unit,
) {
    private var host: Host? = null

    val isShown: Boolean get() = host != null

    fun show() {
        if (host != null) return
        val created = Host(context, content)
        host = created
        windowManager.addView(created.view, params)
        created.resume()
    }

    fun hide() {
        val current = host ?: return
        host = null
        current.destroy()
        windowManager.removeViewImmediate(current.view)
    }

    fun update(change: WindowManager.LayoutParams.() -> Unit) {
        params.change()
        host?.let { windowManager.updateViewLayout(it.view, params) }
    }

    fun setVisible(visible: Boolean) {
        host?.view?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun setTouchable(touchable: Boolean) = update {
        flags = if (touchable) flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() else flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }

    fun bounds(): TouchArea? {
        val view = host?.view?.takeIf { it.isAttachedToWindow && it.width > 0 } ?: return null
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        return TouchArea(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }

    private class Host(context: Context, content: @Composable () -> Unit) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@Host)
            setViewTreeViewModelStoreOwner(this@Host)
            setViewTreeSavedStateRegistryOwner(this@Host)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }

        init {
            savedState.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            viewModelStore.clear()
        }
    }
}
