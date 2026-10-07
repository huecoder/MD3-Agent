package dev.huecoder.md3assistant.ui.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

private const val CLIP_LABEL = "MD3 Assistant"

suspend fun Clipboard.copyText(text: String) = setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)))
