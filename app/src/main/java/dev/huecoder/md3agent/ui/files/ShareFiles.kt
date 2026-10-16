package dev.huecoder.md3agent.ui.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.ViewableFile

fun Context.shareFiles(targets: List<ViewableFile>) {
    if (targets.isEmpty()) return
    val uris = try {
        targets.map { FileProvider.getUriForFile(this, "$packageName$FILE_PROVIDER_SUFFIX", it.file, it.name) }
    } catch (e: IllegalArgumentException) {
        Toast.makeText(this, R.string.files_error_share, Toast.LENGTH_SHORT).show()
        return
    }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    send.setType(commonMimeType(targets.map { it.mimeType }))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .clipData = clipOf(uris)
    val chooser = Intent.createChooser(send, getString(R.string.files_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.files_error_share, Toast.LENGTH_SHORT).show()
    }
}

internal fun commonMimeType(mimeTypes: List<String>): String {
    val distinct = mimeTypes.toSet()
    if (distinct.size == 1) return distinct.single()
    val families = distinct.map { it.substringBefore('/') }.toSet()
    return if (families.size == 1) "${families.single()}/*" else ANY_MIME
}

private fun clipOf(uris: List<Uri>): ClipData =
    ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }

private const val FILE_PROVIDER_SUFFIX = ".files"
private const val ANY_MIME = "*/*"
