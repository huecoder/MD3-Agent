package dev.huecoder.md3agent.assistant

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object AttachPicking {
    const val MAX_PHOTOS = 6
    val ANY_FILE = arrayOf("*/*")

    private const val CAMERA_FOLDER = "camera"
    private const val PHOTO_PREFIX = "photo-"
    private const val PHOTO_SUFFIX = ".jpg"

    fun newCameraUri(context: Context): Uri {
        val folder = File(context.cacheDir, CAMERA_FOLDER).apply { mkdirs() }
        folder.listFiles()?.forEach(File::delete)
        val photo = File.createTempFile(PHOTO_PREFIX, PHOTO_SUFFIX, folder)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", photo)
    }
}
