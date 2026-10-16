package dev.huecoder.md3agent.tools.phone

import dev.huecoder.md3agent.system.control.GlobalKey
import dev.huecoder.md3agent.system.control.Observe
import dev.huecoder.md3agent.system.control.SwipeDirection
import dev.huecoder.md3agent.tools.ToolArgumentException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionSchemaTest {
    private val schema = PhoneActionSchema.parameters()
    private val properties = schema.getValue("properties").jsonObject
    private val steps = properties.getValue("steps").jsonObject
    private val stepProperties = steps.getValue("items").jsonObject.getValue("properties").jsonObject

    private fun JsonObject.names(key: String): List<String> = getValue(key).let { (it as JsonArray).map { item -> (item as JsonPrimitive).content } }

    private fun JsonObject.enumOf(property: String): List<String> = getValue(property).jsonObject.names("enum")

    @Test
    fun `the action list is exactly what the parser understands`() {
        assertEquals(PhoneRequestParser.ACTION_NAMES, properties.enumOf("action"))
        assertEquals(PhoneRequestParser.ACTION_NAMES, stepProperties.enumOf("action"))
    }

    @Test
    fun `every advertised action reaches a parser branch`() {
        PhoneRequestParser.ACTION_NAMES.forEach { name ->
            val failure = runCatching { PhoneRequestParser.parse(buildJsonObject { put("action", name) }) }.exceptionOrNull()

            assertTrue("$name is unknown to the parser", failure == null || (failure is ToolArgumentException && failure.message?.startsWith("Unknown action") == false))
        }
    }

    @Test
    fun `a step takes every action field and none of the batch controls`() {
        val batchControls = setOf("steps", "observe", "on_error")

        assertEquals(properties.keys - batchControls, stepProperties.keys)
    }

    @Test
    fun `a step has to name its action and the batch is bounded like the parser`() {
        assertEquals(listOf("action"), steps.getValue("items").jsonObject.names("required"))
        assertEquals(PhoneRequestParser.MAX_STEPS, steps.getValue("maxItems").let { (it as JsonPrimitive).int })
        assertTrue(schema.names("required").isEmpty())
    }

    @Test
    fun `enums match the values the parser accepts`() {
        assertEquals(Observe.entries.map { it.key }, properties.enumOf("observe"))
        assertEquals(GlobalKey.entries.map { it.key }, properties.enumOf("key"))
        assertEquals(SwipeDirection.entries.map { it.key }, properties.enumOf("direction"))
        assertEquals(PhoneActionSchema.ERROR_POLICIES, properties.enumOf("on_error"))
        assertEquals(PhoneActionSchema.WAIT_CONDITIONS, properties.enumOf("condition"))
    }

    @Test
    fun `every advertised wait condition and key parses`() {
        PhoneActionSchema.WAIT_CONDITIONS.forEach { condition ->
            val request = buildJsonObject {
                put("action", "wait_for")
                put("condition", condition)
                put("text", "x")
                put("app", "x")
                put("timeout_ms", 1_000)
            }

            assertEquals(1, PhoneRequestParser.parse(request).steps.size)
        }
        GlobalKey.entries.forEach { key ->
            assertEquals(1, PhoneRequestParser.parse(buildJsonObject { put("action", "press"); put("key", key.key) }).steps.size)
        }
    }

    @Test
    fun `a target describes its own fields`() {
        val target = properties.getValue("target").jsonObject.getValue("properties").jsonObject

        assertEquals(setOf("text", "id", "role", "exact", "index"), target.keys)
        assertNotNull(stepProperties.getValue("target").jsonObject["properties"])
        assertFalse(stepProperties.getValue("target").jsonObject.containsKey("description"))
    }

    @Test
    fun `say is capped in the schema`() {
        assertEquals(60, (properties.getValue("say").jsonObject.getValue("maxLength") as JsonPrimitive).int)
    }
}
