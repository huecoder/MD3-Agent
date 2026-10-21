package dev.huecoder.md3agent.tools.phone

import android.content.Context
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.conversation.PromptAnswer
import dev.huecoder.md3agent.conversation.QuestionOption
import dev.huecoder.md3agent.conversation.UserPrompts
import dev.huecoder.md3agent.conversation.UserQuestion
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.control.PhoneControl
import dev.huecoder.md3agent.system.control.PhoneSay
import dev.huecoder.md3agent.system.control.PhoneUseSounds
import dev.huecoder.md3agent.system.control.WaitCondition
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolContext
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.booleanProperty
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalBoolean
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.requireString
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.coroutineContext

class PhoneHandoffTool(
    control: PhoneControl,
    private val prompts: UserPrompts,
    private val sounds: PhoneUseSounds,
    private val appContext: Context,
) : PhoneTool(control) {
    override val spec = ToolSpec(
        name = NAME,
        description = "Asks the user to do something on the phone that you cannot do yourself, like a fingerprint or face prompt, a PIN, unlocking the phone, a CAPTCHA or a code from a message, " +
            "and waits until they are done. The overlay stays on screen and lets their touches through. You get the screen as it is after they finish. " +
            "If it comes back saying they could not help, stop, tell them in one short line what is blocked and pass return_to_chat=true on your last phone call.",
        parameters = objectSchema(required = listOf(REASON)) {
            stringProperty(REASON, "What the user has to do, one short sentence in their language, shown on a card and in the notification", maxLength = MAX_REASON_CHARS)
            booleanProperty(URGENT, "true when nothing can go on without them (unlocking, biometrics): plays a ringtone instead of a notification sound")
            enumProperty(UNTIL, "Continue by itself when this happens, the user can always press Done. Leave out to wait for Done", UNTIL_VALUES)
            stringProperty("text", "Text for until=text_visible or text_gone")
            stringProperty("app", "App name or package for until=app")
            integerProperty(TIMEOUT, "How long to wait before giving up, default ${DEFAULT_TIMEOUT_MS} ms", MIN_TIMEOUT_MS.toInt(), MAX_TIMEOUT_MS.toInt())
            sayProperty(required = false)
        },
    )

    override val timeoutMs = MAX_TIMEOUT_MS + TIMEOUT_MARGIN_MS

    override fun promptNote(settings: ToolSettings) =
        "When the screen needs the user, like a fingerprint or face prompt, a PIN, an unlock, a CAPTCHA, a code from a message or a payment confirmation, call $NAME instead of skipping it, guessing or asking in the chat. " +
            "Never enter passwords or codes for them. After Done, read the screen it returns and carry on."

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome = ToolOutcome.error("This tool only works inside a chat")

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings, context: ToolContext): ToolOutcome {
        if (!control.isReady()) return ToolOutcome.error(PhoneControl.NOT_CONNECTED)
        control.session.begin(context.chatId)
        val reason = arguments.requireString(REASON).take(MAX_REASON_CHARS)
        val locked = control.isLocked()
        val timeout = (arguments.optionalInt(TIMEOUT)?.toLong() ?: DEFAULT_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        val done = appContext.getString(R.string.handoff_done)
        val cannot = appContext.getString(R.string.handoff_cannot)
        val question = UserQuestion(
            question = reason,
            header = appContext.getString(R.string.handoff_header),
            options = listOf(QuestionOption(done, null), QuestionOption(cannot, null)),
            multiSelect = false,
            allowCustom = false,
            handoff = true,
        )
        control.say(PhoneSay.clean(arguments.optionalString(SAY)) ?: appContext.getString(R.string.phone_use_handoff_say))
        if (settings.phoneUse.helpSound) sounds.alert((arguments.optionalBoolean(URGENT) ?: false) || locked)
        control.session.waiting(true)
        try {
            val resolution = withTimeoutOrNull(timeout) { waitForUser(context.chatId, question, done, watcher(arguments, locked, timeout)) }
            return when (resolution) {
                Resolution.Finished -> finished()
                Resolution.Declined -> ToolOutcome.error(DECLINED)
                null -> ToolOutcome.error(TIMED_OUT)
            }
        } finally {
            control.session.waiting(false)
        }
    }

    private suspend fun waitForUser(chatId: String, question: UserQuestion, done: String, watcher: (suspend () -> Unit)?): Resolution = coroutineScope {
        val answer = async { prompts.ask(chatId, question) }
        val condition = watcher?.let { watch -> async { watch(); Resolution.Finished } }
        val first = select<Resolution> {
            answer.onAwait { if (it is PromptAnswer.Chosen && it.chosen.singleOrNull() == done) Resolution.Finished else Resolution.Declined }
            condition?.onAwait { it }
        }
        coroutineContext.cancelChildren()
        first
    }

    private fun watcher(arguments: JsonObject, locked: Boolean, timeout: Long): (suspend () -> Unit)? {
        if (locked) return { while (control.isLocked()) delay(UNLOCK_POLL_MS) }
        val condition = condition(arguments) ?: return null
        return {
            if (control.awaitCondition(condition, timeout)?.met != true) awaitCancellation()
        }
    }

    private fun condition(arguments: JsonObject): WaitCondition? = when (val name = arguments.optionalString(UNTIL)) {
        null -> null
        "text_visible" -> WaitCondition.TextVisible(arguments.requireString("text"))
        "text_gone" -> WaitCondition.TextGone(arguments.requireString("text"))
        "app" -> WaitCondition.AppInFront(arguments.requireString("app"))
        "screen_changed" -> WaitCondition.ScreenChanged
        else -> throw ToolArgumentException("Unknown until '$name', use ${UNTIL_VALUES.joinToString()}")
    }

    private suspend fun finished(): ToolOutcome {
        delay(AFTER_HELP_SETTLE_MS)
        val screen = control.read(null, false, null)?.text ?: return ToolOutcome("The user says they are done.")
        return ToolOutcome("The user is done. The screen now:\n$screen")
    }

    private enum class Resolution { Finished, Declined }

    companion object {
        const val NAME = "phone_handoff"
        private const val REASON = "reason"
        private const val URGENT = "urgent"
        private const val UNTIL = "until"
        private const val TIMEOUT = "timeout_ms"
        private const val MAX_REASON_CHARS = 160
        private const val DEFAULT_TIMEOUT_MS = 120_000L
        private const val MIN_TIMEOUT_MS = 10_000L
        private const val MAX_TIMEOUT_MS = 600_000L
        private const val TIMEOUT_MARGIN_MS = 5_000L
        private const val UNLOCK_POLL_MS = 300L
        private const val AFTER_HELP_SETTLE_MS = 700L
        private val UNTIL_VALUES = listOf("text_visible", "text_gone", "app", "screen_changed")
        private const val DECLINED = "The user could not or did not want to help. Stop the task, tell them in one short line what is blocked, and pass return_to_chat=true on your last phone call."
        private const val TIMED_OUT = "The user did not answer in time. Stop the task, tell them in one short line what is blocked, and pass return_to_chat=true on your last phone call."
    }
}
