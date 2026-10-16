package dev.huecoder.md3agent.feature.chat.lightbox

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Download
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Share
import dev.huecoder.md3agent.App
import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.files.RemoteImages
import dev.huecoder.md3agent.data.files.ViewableFile
import dev.huecoder.md3agent.ui.files.openFile
import dev.huecoder.md3agent.ui.files.shareFiles
import kotlinx.coroutines.launch
import java.io.IOException

fun interface Lightbox {
    fun show(images: List<ViewableFile>, startIndex: Int)
}

val LocalLightbox = staticCompositionLocalOf<Lightbox?> { null }

private data class LightboxRequest(val images: List<ViewableFile>, val startIndex: Int)

@Composable
fun LightboxHost(content: @Composable () -> Unit) {
    var request by remember { mutableStateOf<LightboxRequest?>(null) }
    val lightbox = remember { Lightbox { images, index -> if (images.isNotEmpty()) request = LightboxRequest(images, index.coerceIn(images.indices)) } }
    CompositionLocalProvider(LocalLightbox provides lightbox, content = content)
    request?.let { ImageLightbox(it.images, it.startIndex, onDismiss = { request = null }) }
}

@Composable
private fun ImageLightbox(images: List<ViewableFile>, startIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as App).container.attachments
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex) { images.size }
    var chromeVisible by remember { mutableStateOf(true) }
    val current = images[pager.currentPage]

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = PAGE_SPACING, modifier = Modifier.fillMaxSize()) { page ->
                val zoom = rememberZoomState()
                AsyncImage(
                    model = images[page].url ?: images[page].file,
                    contentDescription = images[page].name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomable(zoom, onTap = { chromeVisible = !chromeVisible }),
                )
            }
            AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SCRIM)
                        .systemBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    ChromeButton(MaterialSymbols.Rounded.Close, stringResource(R.string.lightbox_close), onDismiss)
                    Text(
                        if (images.size > 1) "${pager.currentPage + 1} / ${images.size}" else current.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (current.canExport) {
                        ChromeButton(MaterialSymbols.Rounded.Share, stringResource(R.string.files_share)) {
                            scope.launch {
                                val local = stored(context, current)
                                if (local != null) context.shareFiles(listOf(local)) else Toast.makeText(context, R.string.lightbox_load_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ChromeButton(MaterialSymbols.Rounded.Open_in_new, stringResource(R.string.lightbox_open)) {
                            scope.launch {
                                val local = stored(context, current)
                                if (local != null) context.openFile(local) else Toast.makeText(context, R.string.lightbox_load_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                        ChromeButton(MaterialSymbols.Rounded.Download, stringResource(R.string.lightbox_download)) {
                            scope.launch {
                                val message = try {
                                    val local = stored(context, current)
                                    if (local == null) {
                                        R.string.lightbox_load_failed
                                    } else {
                                        store.exportToDownloads(local)
                                        R.string.lightbox_saved
                                    }
                                } catch (e: IOException) {
                                    R.string.lightbox_save_failed
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun stored(context: Context, target: ViewableFile): ViewableFile? =
    try {
        RemoteImages.materialize((context.applicationContext as App).container.httpClients.current(), target)
    } catch (e: IOException) {
        null
    }

@Composable
private fun ChromeButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = BUTTON_CONTAINER, contentColor = Color.White),
    ) { Icon(icon, contentDescription = label) }
}

private val SCRIM = Color.Black.copy(alpha = 0.45f)
private val BUTTON_CONTAINER = Color.White.copy(alpha = 0.14f)
private val PAGE_SPACING = 16.dp
