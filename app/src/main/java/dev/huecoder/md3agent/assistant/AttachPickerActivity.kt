package dev.huecoder.md3agent.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import dev.huecoder.md3agent.App
import java.io.File

enum class AttachSource { PHOTOS, CAMERA, FILES }

class AttachPickerActivity : ComponentActivity() {
    private var cameraUri: Uri? = null

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris -> finishWith(uris) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved -> finishWith(listOfNotNull(cameraUri.takeIf { saved })) }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> finishWith(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            cameraUri = BundleCompat.getParcelable(savedInstanceState, STATE_CAMERA_URI, Uri::class.java)
            return
        }
        when (intent.getStringExtra(EXTRA_SOURCE)?.let(AttachSource::valueOf)) {
            AttachSource.PHOTOS -> pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            AttachSource.CAMERA -> newCameraUri().also { cameraUri = it }.let(takePhoto::launch)
            AttachSource.FILES -> pickFiles.launch(ANY_FILE)
            null -> finishWith(emptyList())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CAMERA_URI, cameraUri)
    }

    private fun newCameraUri(): Uri {
        val folder = File(cacheDir, CAMERA_FOLDER).apply { mkdirs() }
        folder.listFiles()?.forEach(File::delete)
        val photo = File.createTempFile(PHOTO_PREFIX, PHOTO_SUFFIX, folder)
        return FileProvider.getUriForFile(this, "$packageName.files", photo)
    }

    private fun finishWith(uris: List<Uri>) {
        val presence = (applicationContext as App).container.assistant
        presence.deliver(uris)
        presence.pickerClosed()
        finish()
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        private const val STATE_CAMERA_URI = "camera-uri"
        private const val MAX_PHOTOS = 6
        private const val CAMERA_FOLDER = "camera"
        private const val PHOTO_PREFIX = "photo-"
        private const val PHOTO_SUFFIX = ".jpg"
        private val ANY_FILE = arrayOf("*/*")

        fun intent(context: Context, source: AttachSource): Intent =
            Intent(context, AttachPickerActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
