package com.mwagent.tools.phone

import com.mwagent.system.control.ElementPicker
import com.mwagent.system.control.PhoneControl
import com.mwagent.system.control.PhoneSay
import com.mwagent.system.control.ScreenElement
import com.mwagent.system.control.ScriptPhone
import com.mwagent.system.python.ScriptBridge
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PhoneScriptBridge(
    private val phone: ScriptPhone,
    private val chatId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScriptBridge {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val turn = Mutex()
    private val replies = ConcurrentHashMap<Int, String>()
    private val handles = AtomicInteger()

    fun begin(operation: String, payload: String): Int {
        val handle = handles.incrementAndGet()
        scope.launch {
            replies[handle] = try {
                turn.withLock { reply(operation, payload) }
            } catch (e: CancellationException) {
                CANCELLED_REPLY
            }
        }
        return handle
    }

    fun poll(handle: Int): String? = replies.remove(handle) ?: CANCELLED_REPLY.takeUnless { scope.isActive }

    override fun abort() = scope.cancel()

    private suspend fun reply(operation: String, payload: String): String = try {
        val arguments = Json.parseToJsonElement(payload) as? JsonObject ?: throw ToolArgumentException("The request is not an object")
        phone.begin(chatId)
        when (operation) {
            ACT -> act(arguments)
            SCREEN -> screen(arguments)
            SAY -> say(arguments)
            else -> throw ToolArgumentException("Unknown operation \"$operation\"")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ToolArgumentException) {
        failure(e.message.orEmpty())
    } catch (e: SerializationException) {
        failure("Unreadable request: ${e.message}")
    } catch (e: Exception) {
        failure(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun act(arguments: JsonObject): String {
        val report = phone.run(PhoneRequestParser.parse(arguments)) ?: return failure(PhoneControl.NOT_CONNECTED)
        return buildJsonObject {
            put("ok", !report.failed)
            put("text", report.render())
            report.screenChanged?.let { put("changed", it) }
            report.observed?.let { put("screen", it) }
        }.toString()
    }

    private suspend fun screen(arguments: JsonObject): String {
        val find = PhoneRequestParser.selector(arguments, FIND)
        val reading = phone.read(PhoneSay.clean(arguments.optionalString(SAY_KEY)), arguments.optionalBoolean(DETAILED) ?: false, find)
            ?: return failure(PhoneControl.NOT_CONNECTED)
        val snapshot = reading.snapshot
        val shown = if (find == null) snapshot.elements else ElementPicker.filter(find, snapshot.elements)
        return buildJsonObject {
            put("ok", true)
            put("app", snapshot.packageName)
            put("width", snapshot.screenWidth)
            put("height", snapshot.screenHeight)
            put("text", reading.text)
            putJsonArray("elements") { shown.forEach { add(describe(it)) } }
        }.toString()
    }

    private fun say(arguments: JsonObject): String {
        val caption = PhoneSay.clean(arguments.optionalString(TEXT)) ?: return failure("There is nothing to show")
        phone.say(caption)
        return buildJsonObject { put("ok", true) }.toString()
    }

    private fun describe(element: ScreenElement): JsonObject = buildJsonObject {
        put("number", element.number)
        put("role", element.role)
        put("label", element.label)
        put("x", element.bounds.centerX)
        put("y", element.bounds.centerY)
        put("left", element.bounds.left)
        put("top", element.bounds.top)
        put("right", element.bounds.right)
        put("bottom", element.bounds.bottom)
        put("clickable", element.clickable)
        put("editable", element.editable)
        put("scrollable", element.scrollable)
        put("checked", element.checked)
        put("focused", element.focused)
        put("id", element.viewId)
        put("partial", element.partial)
    }

    private fun failure(message: String): String = buildJsonObject {
        put("ok", false)
        put("error", message)
    }.toString()

    companion object {
        const val ACT = "act"
        const val SCREEN = "screen"
        const val SAY = "say"
        private const val FIND = "find"
        private const val DETAILED = "detailed"
        private const val TEXT = "text"
        private const val SAY_KEY = "say"
        private val CANCELLED_REPLY = buildJsonObject {
            put("ok", false)
            put("cancelled", true)
            put("error", "Stopped by the user")
        }.toString()
    }
}
