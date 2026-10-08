package dev.huecoder.md3agent.ui.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

private const val CLIP_LABEL = "MD3 Agent"

suspend fun Clipboard.copyText(text: String) = setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)))
