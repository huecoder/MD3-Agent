package dev.huecoder.md3agent.tools.files

import dev.huecoder.md3agent.data.attachments.Attachment
import dev.huecoder.md3agent.data.attachments.AttachmentStore
import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.files.DeviceFiles
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalArray
import dev.huecoder.md3agent.tools.stringArrayProperty
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

class SendFilesTool(private val files: DeviceFiles, private val store: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Shows files from the phone to the user right in the chat: images as a picture, an album or a slider, " +
            "other files as cards the user can tap to open. Use it whenever the user wants to see or get a file instead of just naming the path. " +
            "You do not see the files yourself through this tool. " +
            "For pictures on the web, embed them in your reply as markdown ![description](https://...) instead.",
        parameters = objectSchema(required = listOf("paths")) {
            stringArrayProperty("paths", "File paths, up to $MAX_FILES, in the order to show them")
        },
    )

    override fun promptNote(settings: ToolSettings) = fileScopeNote(files, settings)

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val paths = arguments.optionalArray("paths").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        if (paths.isEmpty()) throw ToolArgumentException("Give at least one path in 'paths'")
        if (paths.size > MAX_FILES) throw ToolArgumentException("At most $MAX_FILES files per call")

        val sent = mutableListOf<Attachment>()
        val failures = mutableListOf<String>()
        for (path in paths) {
            val file = files.resolve(path)
            try {
                sent += files.withLocalCopy(file, settings.access.files, MAX_FILE_BYTES) { store.importLocal(it, forModel = false) }
            } catch (e: CancellationException) {
                store.delete(sent)
                throw e
            } catch (e: IllegalStateException) {
                failures += "${file.path}: ${e.message}"
            } catch (e: IOException) {
                failures += "${file.path}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        val report = buildList {
            if (sent.isNotEmpty()) add("Shown to the user: ${sent.joinToString { it.name }}")
            if (failures.isNotEmpty()) add("Could not send:\n" + failures.joinToString("\n"))
        }.joinToString("\n")
        return ToolOutcome(report, isError = sent.isEmpty(), attachments = sent)
    }

    companion object {
        const val NAME = "send_files"
        private const val MAX_FILES = 10
        private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    }
}
