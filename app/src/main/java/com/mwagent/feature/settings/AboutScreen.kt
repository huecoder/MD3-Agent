package com.mwagent.feature.settings

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Gavel
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Package_2
import com.composables.icons.materialsymbols.rounded.System_update
import com.mwagent.BuildConfig
import com.mwagent.R
import com.mwagent.data.updates.UpdateChecker
import com.mwagent.data.updates.UpdateStatus
import com.mwagent.ui.components.SettingsScaffold
import com.mwagent.ui.components.ShapeBadge
import com.mwagent.ui.components.appViewModel
import com.mwagent.ui.components.groupedListColors

@Composable
fun AboutScreen(onBack: () -> Unit, onSecret: () -> Unit) {
    val viewModel = appViewModel { AboutViewModel(it.updates) }
    val check by viewModel.check.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val updateUrl = ((check as? UpdateCheck.Done)?.status as? UpdateStatus.Available)?.let { it.apkUrl ?: it.pageUrl }
    val links = listOf(
        AboutLink(MaterialSymbols.Rounded.Code, R.string.about_source, R.string.about_source_summary, UpdateChecker.REPOSITORY_URL),
        AboutLink(MaterialSymbols.Rounded.Gavel, R.string.about_license, R.string.about_license_summary, "${UpdateChecker.REPOSITORY_URL}/blob/main/LICENSE"),
        AboutLink(MaterialSymbols.Rounded.Package_2, R.string.about_third_party, R.string.about_third_party_summary, "${UpdateChecker.REPOSITORY_URL}/blob/main/THIRD_PARTY.md"),
    )
    val total = links.size + 1

    SettingsScaffold(stringResource(R.string.settings_about), onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            AppIdentity(onSecret, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp))
            SegmentedListItem(
                onClick = { if (updateUrl != null) context.openUrl(updateUrl) else viewModel.checkForUpdates() },
                shapes = ListItemDefaults.segmentedShapes(0, total),
                colors = groupedListColors(),
                leadingContent = { ShapeBadge(MaterialSymbols.Rounded.System_update) },
                supportingContent = { Text(updateSummary(check)) },
                trailingContent = if (check == UpdateCheck.Running) {
                    { LoadingIndicator(Modifier.size(32.dp)) }
                } else {
                    null
                },
            ) {
                Text(
                    stringResource(if (updateUrl != null) R.string.about_update_download else R.string.about_check_updates),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            links.forEachIndexed { index, link ->
                SegmentedListItem(
                    onClick = { context.openUrl(link.url) },
                    shapes = ListItemDefaults.segmentedShapes(index + 1, total),
                    colors = groupedListColors(),
                    leadingContent = { ShapeBadge(link.icon) },
                    supportingContent = { Text(stringResource(link.summary)) },
                    trailingContent = { Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = null) },
                ) { Text(stringResource(link.title), style = MaterialTheme.typography.titleMediumEmphasized) }
            }
        }
    }
}

@Composable
private fun AppIdentity(onSecret: () -> Unit, modifier: Modifier = Modifier) {
    val taps = rememberSecretTaps(onSecret)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = taps.squish.value
                    scaleY = taps.squish.value
                }
                .clickable(interactionSource = null, indication = null, onClick = taps::tap)
                .size(LOGO_SIZE)
                .clip(RoundedCornerShape(LOGO_CORNER_PERCENT))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(LOGO_SIZE * ADAPTIVE_BLEED))
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMediumEmphasized)
        Text(
            taps.remaining?.let { stringResource(R.string.about_logo_taps_left, it) }
                ?: stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun updateSummary(check: UpdateCheck): String = when (check) {
    UpdateCheck.Idle -> stringResource(R.string.about_update_idle)
    UpdateCheck.Running -> stringResource(R.string.about_update_running)
    is UpdateCheck.Done -> when (val status = check.status) {
        is UpdateStatus.UpToDate -> stringResource(R.string.about_update_latest, status.latest)
        is UpdateStatus.Available -> stringResource(R.string.about_update_available, status.version)
        UpdateStatus.NotPublished -> stringResource(R.string.about_update_not_published)
        UpdateStatus.Unreachable -> stringResource(R.string.about_update_unreachable)
    }
}

private data class AboutLink(val icon: ImageVector, @StringRes val title: Int, @StringRes val summary: Int, val url: String)

private fun Context.openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

private val LOGO_SIZE = 96.dp
private const val LOGO_CORNER_PERCENT = 30
private const val ADAPTIVE_BLEED = 1.5f
