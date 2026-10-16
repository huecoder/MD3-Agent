package dev.huecoder.md3agent.tools.system

import android.content.ContextWrapper
import dev.huecoder.md3agent.data.settings.PhoneUseSettings
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.system.control.ElementSelector
import dev.huecoder.md3agent.system.control.PhoneReport
import dev.huecoder.md3agent.system.control.PhoneRequest
import dev.huecoder.md3agent.system.control.ScreenReading
import dev.huecoder.md3agent.system.control.ScriptPhone
import dev.huecoder.md3agent.system.python.PythonRuntime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RunPythonToolTest {
    private val phone = object : ScriptPhone {
        override fun begin(chatId: String) = Unit

        override fun say(caption: String) = Unit

        override suspend fun run(request: PhoneRequest): PhoneReport? = null

        override suspend fun read(say: String?, detailed: Boolean, find: ElementSelector?): ScreenReading? = null
    }
    private val tool = RunPythonTool(PythonRuntime(ContextWrapper(null)), File("/workspace"), phone)

    @Test
    fun `scripts that import the phone module are recognised`() {
        listOf(
            "import phone\nphone.tap('x')",
            "import os, phone",
            "from phone import tap",
            "def go():\n    import phone\n",
            "x = __import__('phone')",
            "importlib.import_module(\"phone\")",
            "a = 1; import phone",
        ).forEach { assertTrue("should be recognised: $it", RunPythonTool.usesPhone(it)) }
    }

    @Test
    fun `a call is judged by its code argument`() {
        assertTrue(RunPythonTool.usesPhone(buildJsonObject { put("code", "import phone\nphone.press('back')") }))
        assertFalse(RunPythonTool.usesPhone(buildJsonObject { put("code", "print(1)") }))
        assertFalse(RunPythonTool.usesPhone(buildJsonObject { put("timeout_seconds", 5) }))
    }

    @Test
    fun `other uses of the word phone are not`() {
        listOf(
            "phone = '+1 555'\nprint(phone.strip())",
            "import phonenumbers",
            "from phones import x",
            "print(contact.phone)",
            "",
        ).forEach { assertFalse("should not be recognised: $it", RunPythonTool.usesPhone(it)) }
    }

    @Test
    fun `the phone notes appear only when python may control the phone`() {
        assertNull(tool.promptNote(ToolSettings()))

        val note = tool.promptNote(ToolSettings(phoneUse = PhoneUseSettings(pythonControl = true)))

        assertNotNull(note)
        assertTrue(note!!.contains("routines/<name>.py"))
    }

    @Test
    fun `the notes only name things the phone module really has`() {
        val source = File("src/main/python/phone.py").readText()
        val defined = Regex("""(?m)^(?:def|class)\s+(\w+)""").findAll(source).map { it.groupValues[1] }.toSet() + "type"
        val note = tool.promptNote(ToolSettings(phoneUse = PhoneUseSettings(pythonControl = true)))!!

        val mentioned = Regex("""phone\.(\w+)""").findAll(note).map { it.groupValues[1] }.toSet()

        assertTrue(mentioned.isNotEmpty())
        assertEquals("the notes mention names the module lacks", emptySet<String>(), mentioned - defined)
    }

    @Test
    fun `the tool only works inside a chat`() = runBlocking {
        val outcome = tool.execute(buildJsonObject { put("code", "print(1)") }, ToolSettings())

        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("inside a chat"))
    }
}
