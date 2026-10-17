package com.mwagent.ui.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

private const val CLIP_LABEL = "MW AGENT"

suspend fun Clipboard.copyText(text: String) = setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)))
