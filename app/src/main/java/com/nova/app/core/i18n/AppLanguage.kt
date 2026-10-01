package com.nova.app.core.i18n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/** Languages the app ships translations for. [tag] matches the `values-<tag>` resource folder. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    English("en", "English"),
    Vietnamese("vi", "Tiếng Việt"),
    Chinese("zh", "中文"),
    Japanese("ja", "日本語"),
    Korean("ko", "한국어");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /** Saved choice, or the device language when it is supported, otherwise English. */
        fun saved(context: Context): AppLanguage =
            fromTag(AppLanguageStore.load(context))
                ?: fromTag(Locale.getDefault().language)
                ?: English
    }
}

object AppLanguageStore {
    private const val PREFS_NAME = "nova_app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun load(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply()
    }
}

class AppLanguageController(
    val current: AppLanguage,
    val select: (AppLanguage) -> Unit,
)

val LocalAppLanguage = staticCompositionLocalOf { AppLanguageController(AppLanguage.English) {} }

/**
 * Re-provides [LocalContext], [LocalConfiguration] and [LocalResources] with the chosen locale so
 * every `stringResource` below switches language instantly, without recreating the activity.
 */
@Composable
fun ProvideAppLanguage(content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    var language by rememberSaveable { mutableStateOf(AppLanguage.saved(baseContext)) }

    val localizedContext = remember(language, baseContext, baseConfiguration) {
        Locale.setDefault(language.locale)
        val config = Configuration(baseConfiguration).apply { setLocales(LocaleList(language.locale)) }
        LocalizedContext(baseContext, baseContext.createConfigurationContext(config).resources)
    }
    val controller = remember(language) {
        AppLanguageController(language) { selected ->
            AppLanguageStore.save(baseContext, selected)
            language = selected
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalResources provides localizedContext.resources,
        LocalAppLanguage provides controller,
        content = content,
    )
}

/** Keeps the activity as base context (needed by pickers/credential UI) but swaps resources. */
private class LocalizedContext(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

/** Walks the wrapper chain back to the hosting activity. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Context whose resources use the language chosen in the app (for notifications/services). */
fun Context.withAppLanguage(): Context {
    val language = AppLanguage.saved(this)
    val config = Configuration(resources.configuration).apply { setLocales(LocaleList(language.locale)) }
    return createConfigurationContext(config)
}
