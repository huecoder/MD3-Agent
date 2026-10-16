package dev.huecoder.md3agent.system.control

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.provider.Settings
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.system.shell.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneControl(private val context: Context, shell: suspend () -> Shell?) : ScriptPhone {
    private val service = MutableStateFlow<AccessibilityService?>(null)
    private val screenshots = PhoneScreenshots()
    private val reader = ScreenReader(context)
    val session = PhoneUseSession()
    private val executor = PhoneExecutor(context, session, reader, PhoneGestures(), InputInjector(shell))

    val connected: StateFlow<AccessibilityService?> = service.asStateFlow()

    fun attach(connected: AccessibilityService) {
        service.value = connected
    }

    fun detach(disconnected: AccessibilityService) {
        service.compareAndSet(disconnected, null)
    }

    fun isEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(context, PhoneControlService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    fun isReady(): Boolean = service.value != null

    override fun begin(chatId: String) = session.begin(chatId)

    override fun say(caption: String) = session.say(caption)

    override suspend fun read(say: String?, detailed: Boolean, find: ElementSelector?): ScreenReading? {
        val active = service.value ?: return null
        session.say(say ?: context.getString(R.string.phone_use_looking))
        return executor.read(active, detailed, find)
    }

    suspend fun screenshot(region: ScreenRegion, marks: Boolean, say: String? = null): ScreenshotResult {
        val active = service.value ?: return ScreenshotResult.Failed(NOT_CONNECTED)
        session.say(say ?: context.getString(R.string.phone_use_screenshot))
        val numbered = if (marks) reader.snapshot(active).elements else emptyList()
        val area = when (region) {
            ScreenRegion.Full -> null
            is ScreenRegion.Area -> Rect(region.x, region.y, region.x + region.width, region.y + region.height)
            is ScreenRegion.Element -> {
                val element = reader.last?.elements?.getOrNull(region.number - 1)
                    ?: return ScreenshotResult.Failed("No element [${region.number}] on the last screen, read it again with phone_screen")
                element.bounds.toRect().apply { inset(-ELEMENT_CROP_PADDING_PX, -ELEMENT_CROP_PADDING_PX) }
            }
        }
        return session.withOverlayHidden { screenshots.capture(active, area, numbered) }
    }

    override suspend fun run(request: PhoneRequest): PhoneReport? {
        val active = service.value ?: return null
        return executor.run(active, request)
    }

    companion object {
        private const val ELEMENT_CROP_PADDING_PX = 16
        const val NOT_CONNECTED = "Phone control is off. Ask the user to enable MD3 Agent in Settings > Accessibility."
    }
}
