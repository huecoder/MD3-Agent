package dev.huecoder.md3agent.data.files

import java.io.File

data class ViewableFile(val file: File, val name: String, val mimeType: String, val url: String? = null) {
    val isImage: Boolean get() = mimeType.startsWith("image/")

    val canExport: Boolean get() = url == null || url.startsWith("http://") || url.startsWith("https://")
}
