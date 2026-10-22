package dev.huecoder.md3agent.data.changes

import java.io.File

class RevisionBlobs(private val directory: File) {
    enum class Side(val suffix: String) { BEFORE(".before"), AFTER(".after") }

    fun write(id: String, side: Side, bytes: ByteArray) {
        directory.mkdirs()
        file(id, side).writeBytes(bytes)
    }

    fun read(id: String, side: Side): ByteArray? = file(id, side).takeIf { it.isFile }?.readBytes()

    fun delete(ids: Collection<String>) {
        ids.forEach { id -> Side.entries.forEach { side -> file(id, side).delete() } }
    }

    private fun file(id: String, side: Side) = File(directory, id + side.suffix)
}
