package dev.huecoder.md3agent.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import dev.huecoder.md3agent.App
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class AttachSource { PHOTOS, CAMERA, FILES }

class AttachPickerActivity : ComponentActivity() {
    private var cameraUri: Uri? = null
    private val presence get() = (applicationContext as App).container.assistant

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachPicking.MAX_PHOTOS)) { uris -> finishWith(uris) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved -> finishWith(listOfNotNull(cameraUri.takeIf { saved })) }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> finishWith(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            cameraUri = BundleCompat.getParcelable(savedInstanceState, STATE_CAMERA_URI, Uri::class.java)
            return
        }
        try {
            when (intent.getStringExtra(EXTRA_SOURCE)?.let(AttachSource::valueOf)) {
                AttachSource.PHOTOS -> pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                AttachSource.CAMERA -> AttachPicking.newCameraUri(this).also { cameraUri = it }.let(takePhoto::launch)
                AttachSource.FILES -> pickFiles.launch(AttachPicking.ANY_FILE)
                null -> finishWith(emptyList())
            }
        } catch (e: ActivityNotFoundException) {
            finishWith(emptyList())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CAMERA_URI, cameraUri)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) presence.pickerClosed()
    }

    private fun finishWith(uris: List<Uri>) {
        presence.pickerClosed()
        val picked = PickedFiles(uris)
        if (!presence.deliver(picked)) return finish()
        lifecycleScope.launch {
            withTimeoutOrNull(IMPORT_TIMEOUT_MS) { picked.awaitImported() }
            finish()
        }
    }

    companion object {
        private const val EXTRA_SOURCE = "source"
        private const val STATE_CAMERA_URI = "camera-uri"
        private const val IMPORT_TIMEOUT_MS = 20_000L

        fun intent(context: Context, source: AttachSource): Intent =
            Intent(context, AttachPickerActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
