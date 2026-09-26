package com.conwic.mixmaster.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.conwic.mixmaster.domain.AppLanguage
import com.conwic.mixmaster.domain.AppLocale
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
     * The locale to run in — the phone's own, whenever it is already in the right language.
     *
     * A phone set to Estonian runs in et-EE. Applying a bare "et" over that is a different
     * configuration from the system's, and the framework relaunches the activity to reconcile
     * the two — whereupon attachBaseContext applies it again. That is a loop with no end, and
     * it is what left the app flicking between a blank screen and the lock screen instead of
     * opening. Keeping the country means the ordinary case, the app in the phone's own
     * language, overrides nothing at all.
     */
    private fun localeFor(language: AppLanguage, base: Context): Locale {
        val device = runCatching { base.resources.configuration.locales[0] }.getOrNull()
        return if (device != null && device.language == language.tag) device else language.locale
    }

    /**
     * The base context an activity should run on, configured for the chosen language.
     *
     * Applied this low down so *everything* derived from the activity follows — including the
     * separate windows that popups, bottom sheets and dialogs live in, which build their own
     * composition and re-provide LocalContext from the activity. Overriding LocalContext higher
     * up left those windows in the phone's language.
     */
    fun wrap(base: Context): Context {
        val locale = localeFor(chosenOrDevice(base), base)
        AppLocale.current = locale
        // Still set, for the formatting the app doesn't do itself. AppLocale is what the app's
        // own dates read, because the framework takes this default back on every config change.
        Locale.setDefault(locale)
        // The language and nothing else. A copy of the whole configuration pinned everything
        // in it along with the language — the size of the screen among them — and this activity
        // turns with the phone rather than being rebuilt, so after a turn to landscape every
        // dialog still measured itself against the portrait width: the mixing screen came up
        // as a strip down the middle with its list cut off at the bottom.
        val config = Configuration().apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /**
     * Brings [resources] round to the screen as it now is, keeping the app's language, should the
     * platform not have done it already. It does, for the language-only configuration above; this
     * is the fallback that keeps a phone which does not from showing dialogs a screen too narrow.
     */
    @Suppress("DEPRECATION")
    fun followScreen(resources: Resources, screen: Configuration, base: Context?) {
        val current = resources.configuration
        if (current.screenWidthDp == screen.screenWidthDp &&
            current.screenHeightDp == screen.screenHeightDp &&
            current.orientation == screen.orientation
        ) {
            return
        }
        val config = Configuration(screen).apply { setLocales(current.locales) }
        resources.updateConfiguration(config, base?.resources?.displayMetrics ?: resources.displayMetrics)
    }
}
