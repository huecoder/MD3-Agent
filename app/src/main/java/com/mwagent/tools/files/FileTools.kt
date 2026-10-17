package com.mwagent.tools.files

import com.mwagent.data.attachments.AttachmentStore
import com.mwagent.data.attachments.ByteSize
import com.mwagent.data.attachments.TextFormats
import com.mwagent.data.settings.FileScope
import com.mwagent.data.settings.ToolPermission
import com.mwagent.data.settings.ToolSettings
import com.mwagent.llm.ToolSpec
import com.mwagent.system.files.DeviceFiles
import com.mwagent.system.files.FileAccessException
import com.mwagent.tools.Tool
import com.mwagent.tools.ToolArgumentException
import com.mwagent.tools.ToolOutcome
import com.mwagent.tools.booleanProperty
import com.mwagent.tools.integerProperty
import com.mwagent.tools.objectSchema
import com.mwagent.tools.optionalBoolean
import com.mwagent.tools.optionalInt
import com.mwagent.tools.optionalString
import com.mwagent.tools.requireString
import com.mwagent.tools.stringProperty
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun fileScopeNote(files: DeviceFiles, settings: ToolSettings): String {
    val base = "File tools (${ListFilesTool.NAME}, ${ReadFileTool.NAME}, ${WriteFileTool.NAME}, ${SendFilesTool.NAME}) take absolute paths; " +
        "relative paths resolve inside the app workspace ${files.workspace.path}. Files the user attached to the chat live in ${files.attachments.path} and are readable. " +
        "The user's own folder from the Files tab is ${files.uploads.path}, readable as well."
    val reach = when (settings.access.files) {
        FileScope.APP -> "Only the workspace, attachments and the user's folder are reachable, the rest of the device is not."
        FileScope.SHARED_STORAGE -> "The phone's shared storage ${files.sharedStoragePath} (Download, DCIM, Pictures, Documents and so on) is reachable too, without root."
        FileScope.ROOT -> "Any path on the device is reachable through root."
    }
    return "$base $reach"
}

class ListFilesTool(private val files: DeviceFiles) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Lists a folder on the phone: subfolders first, then files with size and modification time.",
        parameters = objectSchema(required = emptyList()) {
            stringProperty("path", "Folder path, defaults to the app workspace")
        },
    )

    override fun promptNote(settings: ToolSettings) = fileScopeNote(files, settings)

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val folder = files.resolve(arguments.optionalString("path").orEmpty())
        val entries = files.list(folder, settings.access.files).sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        if (entries.isEmpty()) return ToolOutcome("${folder.path} is empty")
        val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.ROOT)
        val lines = entries.take(MAX_ENTRIES).map { entry ->
            if (entry.isDirectory) "${entry.name}/" else "${entry.name}  ${ByteSize.describe(entry.sizeBytes)}  ${dateFormat.format(Date(entry.modifiedAt))}"
        }
        val more = if (entries.size > MAX_ENTRIES) listOf("… ${entries.size - MAX_ENTRIES} more entries not shown") else emptyList()
        return ToolOutcome((listOf("${folder.path} (${entries.size} entries)") + lines + more).joinToString("\n"))
    }

    companion object {
        const val NAME = "list_files"
        private const val MAX_ENTRIES = 200
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
    }
}

class ReadFileTool(private val files: DeviceFiles, private val store: AttachmentStore) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Reads a file on the phone. Text files come back as text, in parts for long files: " +
            "when the result says it is truncated, call again with the given offset. Images are shown to you so you can look at them. " +
            "Other binary files cannot be read here.",
        parameters = objectSchema(required = listOf("path")) {
            stringProperty("path", "File path")
            integerProperty("offset", "Character offset to continue reading text from, default 0", minimum = 0, maximum = Int.MAX_VALUE)
        },
    )

    override fun promptNote(settings: ToolSettings) = fileScopeNote(files, settings)

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val file = files.resolve(arguments.requireString("path"))
        val offset = (arguments.optionalInt("offset") ?: 0).coerceAtLeast(0)
        val mimeType = TextFormats.guessMimeType(file.name)
        return if (mimeType.startsWith("image/")) {
            val image = files.withLocalCopy(file, settings.access.files, MAX_IMAGE_BYTES) { store.importLocal(it, forModel = true) }
            ToolOutcome("${file.path} is attached below as an image", attachments = listOf(image))
        } else {
            val bytes = files.withLocalCopy(file, settings.access.files, MAX_TEXT_BYTES) { it.readBytes() }
            if (bytes.take(BINARY_SNIFF_BYTES).any { it == NUL }) {
                throw FileAccessException("${file.name} is a binary file ($mimeType, ${ByteSize.describe(bytes.size.toLong())}) and cannot be read as text")
            }
            ToolOutcome(textPage(file, bytes.decodeToString(), offset))
        }
    }

    private fun textPage(file: File, text: String, offset: Int): String {
        if (text.isEmpty()) return "${file.path} is empty"
        if (offset >= text.length) throw ToolArgumentException("Offset $offset is past the end of the file (${text.length} characters)")
        val end = minOf(offset + MAX_TEXT_CHARS, text.length)
        val tail = if (end < text.length) "\n…[truncated at $end of ${text.length} characters, continue with offset=$end]" else ""
        return text.substring(offset, end) + tail
    }

    companion object {
        const val NAME = "read_file"
        private const val MAX_TEXT_CHARS = 16_000
        private const val MAX_TEXT_BYTES = 4L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        private const val BINARY_SNIFF_BYTES = 8 * 1024
        private const val NUL: Byte = 0
    }
}

class WriteFileTool(private val files: DeviceFiles) : Tool {
    override val defaultPermission = ToolPermission.ASK

    override val spec = ToolSpec(
        name = NAME,
        description = "Writes text to a file on the phone, creating missing folders. Replaces the file unless append is true.",
        parameters = objectSchema(required = listOf("path", "content")) {
            stringProperty("path", "File path")
            stringProperty("content", "Text to write")
            booleanProperty("append", "Add to the end of the file instead of replacing it")
        },
    )

    override fun promptNote(settings: ToolSettings) = fileScopeNote(files, settings)

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val file = files.resolve(arguments.requireString("path"))
        val content = (arguments["content"] as? JsonPrimitive)?.contentOrNull
            ?: throw ToolArgumentException("A string parameter 'content' is required")
        val append = arguments.optionalBoolean("append") ?: false
        val bytes = content.encodeToByteArray()
        files.write(file, settings.access.files, bytes, append)
        val verb = if (append) "Appended" else "Wrote"
        return ToolOutcome("$verb ${ByteSize.describe(bytes.size.toLong())} to ${file.path}")
    }

    companion object {
        const val NAME = "write_file"
    }
}
