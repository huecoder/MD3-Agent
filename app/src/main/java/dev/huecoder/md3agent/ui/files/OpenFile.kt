package dev.huecoder.md3agent.ui.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.ViewableFile

fun Context.openFile(target: ViewableFile) {
    val uri = FileProvider.getUriForFile(this, "$packageName$FILE_PROVIDER_SUFFIX", target.file, target.name)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, target.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.chat_attachment_no_viewer, target.name), Toast.LENGTH_SHORT).show()
    }
}

private const val FILE_PROVIDER_SUFFIX = ".files"
