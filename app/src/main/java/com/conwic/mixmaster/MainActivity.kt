package com.conwic.mixmaster

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.conwic.mixmaster.data.update.AppUpdates
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.security.AppLockGate
import com.conwic.mixmaster.ui.theme.MixMasterTheme
import java.util.Locale

/**
 * The activity, serving resources in the chosen language.
 *
 * A wrapper *around the activity* rather than the context createConfigurationContext hands
 * back: that one is a fresh ContextImpl with no link to the activity, and several Compose APIs
 * find what they need by walking the ContextWrapper chain up from LocalContext.
 * rememberLauncherForActivityResult is one — given a context with no activity in its chain it
 * throws "No ActivityResultRegistryOwner was provided", which is what closed the app on
 * opening Settings. Wrapping keeps the chain intact and still answers getResources() in the
 * right language, which is all stringResource asks for.
 */
private class LocalizedContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
    private val localized: Resources = base.createConfigurationContext(configuration).resources
    override fun getResources(): Resources = localized
}

/**
 * A FragmentActivity rather than a plain ComponentActivity: BiometricPrompt, which backs the
 * app lock, can only be hosted by one.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MixMasterApp).container

        // Looks once per app start, and stays quiet unless there's something newer — a failed
        // check on a site with no signal isn't news.
        AppUpdates.checkOnStart(this)

        setContent {
            // The theme setting used to be saved and then ignored — the app always followed the
            // system. "Light" and "Dark" now mean what they say; "Auto" follows the phone.
            val themeSetting by container.userPrefs.theme.collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeSetting) {
                "Dark" -> true
                "Light" -> false
                else -> systemDark
            }

            val activityWindow = this@MainActivity.window
            SideEffect {
                // Dark app, light status-bar icons, and the other way round.
                WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
                    .isAppearanceLightStatusBars = !dark
            }

            // The chosen language, applied by handing every reader a context configured for it.
            // That is what makes stringResource resolve in the right language and, just as
            // importantly, what makes java.time format dates in it — the two used to disagree,
            // so "TODAY" sat above an Estonian weekday on the same screen.
            val language by container.userPrefs.language.collectAsState(initial = null)
            val baseContext = LocalContext.current
            val localeConfig = remember(language, baseContext) {
                language?.let {
                    Configuration(baseContext.resources.configuration).apply { setLocale(it.locale) }
                }
            }
            val localizedContext = remember(localeConfig) {
                localeConfig?.let { LocalizedContext(this@MainActivity, it) }
            }

            MixMasterTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (language == null || localizedContext == null || localeConfig == null) {
                        return@Surface
                    }
                    // java.time reads the JVM default, so the setting has to reach it too or
                    // the dates go on following the phone while the words follow the setting.
                    // A SideEffect, because composition is not the place to mutate globals.
                    val chosen = language!!.locale
                    SideEffect { Locale.setDefault(chosen) }
                    CompositionLocalProvider(
                        LocalAppContainer provides container,
                        LocalAppActivity provides this@MainActivity,
                        LocalContext provides localizedContext,
                        LocalConfiguration provides localeConfig,
                    ) {
                        val appLockEnabled by container.userPrefs.appLockEnabled.collectAsState(initial = null)
                        // Onboarding hasn't been seen yet on a fresh install -> start at Sign in;
                        // otherwise jump straight to Home. Resolved once before the NavHost mounts.
                        val onboardingSeen by container.userPrefs.onboardingSeen.collectAsState(initial = null)

                        // Both preferences are read before anything is drawn: showing the app for
                        // a frame and then locking it would defeat the lock.
                        if (themeSetting != null && onboardingSeen != null && appLockEnabled != null) {
                            AppLockGate(enabled = appLockEnabled == true) {
                                val start = if (onboardingSeen == true) Routes.HOME else Routes.SIGN_IN
                                com.conwic.mixmaster.ui.navigation.MixMasterNavGraph(startDestination = start)
                            }
                        }
                    }
                }
            }
        }
    }
}
