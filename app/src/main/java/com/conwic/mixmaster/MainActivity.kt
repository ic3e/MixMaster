package com.conwic.mixmaster

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.conwic.mixmaster.data.update.AppUpdates
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import com.conwic.mixmaster.data.prefs.LanguageStore
import com.conwic.mixmaster.domain.AppLanguage
import com.conwic.mixmaster.ui.calculator.MixAlarm
import com.conwic.mixmaster.ui.calculator.MixingHost
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.security.AppLockGate
import com.conwic.mixmaster.ui.theme.MixMasterTheme

/**
 * A FragmentActivity rather than a plain ComponentActivity: BiometricPrompt, which backs the
 * app lock, can only be hosted by one.
 */
class MainActivity : FragmentActivity() {

    /** What this activity was built for, so a later change can be noticed. */
    private lateinit var startedInLanguage: AppLanguage

    /**
     * The language is applied here rather than through LocalContext.
     *
     * Popups, bottom sheets and dialogs each live in their own window and build their own
     * composition, which re-provides LocalContext from the activity — so overriding it higher
     * up left every menu and sheet in the phone's language while the screen behind them
     * followed the setting. Configuring the activity's own base context reaches all of them.
     */
    /** Held so it can be handed back on the way out — see [ActivityBaseContext]. */
    private var unwrappedBase: Context? = null

    override fun attachBaseContext(newBase: Context) {
        startedInLanguage = LanguageStore.chosenOrDevice(newBase)
        // Kept before the wrapper goes on: the wrapped context belongs to no activity, and
        // there are platform services that will not take one of those.
        unwrappedBase = newBase
        ActivityBaseContext.set(newBase)
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        wakeForAlarm(intent)
    }

    /**
     * Opened by the mixing alarm: over the lock screen, with the screen lit.
     *
     * Asked for only when the alarm is what brought the app up, so the app does not sit over
     * the keyguard the rest of the time — it has a lock of its own for a reason. The extra is
     * taken off the intent once it has been acted on: the intent is kept by the activity, and
     * a sticky one would put the app back over the lock screen every time it came round again.
     */
    private fun wakeForAlarm(intent: Intent?) {
        if (intent?.getBooleanExtra(MixAlarm.EXTRA_FROM_ALARM, false) != true) return
        intent.removeExtra(MixAlarm.EXTRA_FROM_ALARM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    /**
     * Handed back to the keyguard on the way out.
     *
     * By the time the app is off screen the alarm has been seen, and an app that keeps the
     * right to show itself over a lock screen for the rest of the day is a hole in the lock.
     */
    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
    }

    override fun onDestroy() {
        unwrappedBase?.let { ActivityBaseContext.clear(it) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeForAlarm(intent)
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

            // Changing the language rebuilds the activity, which is what lets attachBaseContext
            // apply it to the popup windows too.
            val language by container.userPrefs.language.collectAsState(initial = null)
            LaunchedEffect(language) {
                // Guarded so this can only ever fire once: attachBaseContext builds the next
                // activity from the mirrored choice, so unless that already says something
                // else, recreating would put back the very activity we are standing in.
                val next = language
                if (next != null &&
                    next != startedInLanguage &&
                    LanguageStore.chosenOrDevice(this@MainActivity) == next
                ) {
                    recreate()
                }
            }

            MixMasterTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (language == null) return@Surface
                    CompositionLocalProvider(
                        LocalAppContainer provides container,
                        LocalAppActivity provides this@MainActivity,
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
                                // Above the whole app, not inside the calculator: a mix in the
                                // mixer is the most important thing on the phone, and it has to
                                // still be there when the app is opened again by its own alarm.
                                MixingHost()
                            }
                        }
                    }
                }
            }
        }
    }
}
