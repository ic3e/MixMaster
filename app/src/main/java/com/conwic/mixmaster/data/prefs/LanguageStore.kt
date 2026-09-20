package com.conwic.mixmaster.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.conwic.mixmaster.domain.AppLanguage
import java.util.Locale

/**
 * The chosen language, readable without suspending.
 *
 * DataStore is the app's settings store, but it can only be read from a coroutine — and the
 * language has to be known in attachBaseContext, before there is anywhere to suspend. So the
 * choice is mirrored into SharedPreferences, which reads synchronously. DataStore stays the
 * one the Settings screen observes; this is a cache of it.
 */
object LanguageStore {

    private const val FILE = "mixmaster_language"
    private const val KEY = "tag"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): AppLanguage? =
        runCatching { AppLanguage.fromTag(prefs(context).getString(KEY, null)) }.getOrNull()

    /** commit, not apply: the next thing that happens is the activity being recreated. */
    fun write(context: Context, language: AppLanguage) {
        runCatching { prefs(context).edit().putString(KEY, language.tag).commit() }
    }

    /**
     * What the phone itself is set to.
     *
     * Read from the system resources rather than Locale.getDefault(), because the app sets that
     * default to its own language — so asking Locale would eventually just return our own
     * answer back.
     */
    fun deviceLanguage(): AppLanguage =
        runCatching {
            AppLanguage.fromTag(Resources.getSystem().configuration.locales[0].language)
        }.getOrNull() ?: AppLanguage.ENGLISH

    fun chosenOrDevice(context: Context): AppLanguage = read(context) ?: deviceLanguage()

    /**
     * The base context an activity should run on, configured for the chosen language.
     *
     * Applied this low down so *everything* derived from the activity follows — including the
     * separate windows that popups, bottom sheets and dialogs live in, which build their own
     * composition and re-provide LocalContext from the activity. Overriding LocalContext higher
     * up left those windows in the phone's language.
     */
    fun wrap(base: Context): Context {
        val language = chosenOrDevice(base)
        Locale.setDefault(language.locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(language.locale)
        }
        return base.createConfigurationContext(config)
    }
}
