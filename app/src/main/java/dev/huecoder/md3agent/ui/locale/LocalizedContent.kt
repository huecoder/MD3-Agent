package dev.huecoder.md3agent.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources

@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, language) { LocalizedContext(base, language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

private class LocalizedContext(base: Context, language: AppLanguage) : ContextWrapper(base) {
    private val localizedResources: Resources =
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(language.locale) }).resources

    override fun getResources(): Resources = localizedResources
}
