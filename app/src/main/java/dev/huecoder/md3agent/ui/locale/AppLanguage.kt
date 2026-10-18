package dev.huecoder.md3agent.ui.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    fun next(step: Int): AppLanguage = entries[(ordinal + step).mod(entries.size)]

    companion object {
        fun of(locale: Locale): AppLanguage = entries.firstOrNull { it.tag == locale.language } ?: ENGLISH
    }
}

object AppLocale {
    fun current(context: Context): AppLanguage = AppLanguage.of(context.resources.configuration.locales[0])

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = preferences(base).getString(KEY_LANGUAGE, null) ?: return base
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    }

    fun apply(activity: Activity, language: AppLanguage) {
        if (current(activity) == language) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            preferences(activity).edit().putString(KEY_LANGUAGE, language.tag).apply()
            activity.recreate()
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "app_language"
    private const val KEY_LANGUAGE = "tag"
}
