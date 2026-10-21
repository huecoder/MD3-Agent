package dev.huecoder.md3agent.feature.chat

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.huecoder.md3agent.assistant.AttachPicking
import dev.huecoder.md3agent.assistant.AttachSource

@Composable
fun rememberAttachPicker(onPicked: (List<Uri>) -> Unit): (AttachSource) -> Unit {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachPicking.MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        cameraUri.takeIf { saved }?.let { onPicked(listOf(it)) }
    }
    return { source ->
        try {
            when (source) {
                AttachSource.PHOTOS -> photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                AttachSource.CAMERA -> camera.launch(AttachPicking.newCameraUri(context).also { cameraUri = it })
                AttachSource.FILES -> files.launch(AttachPicking.ANY_FILE)
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can handle $source", e)
        }
    }
}

private const val TAG = "AttachPicker"
