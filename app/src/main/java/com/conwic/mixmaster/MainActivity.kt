package com.conwic.mixmaster

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
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.security.AppLockGate
import com.conwic.mixmaster.ui.theme.MixMasterTheme

/**
 * A FragmentActivity rather than a plain ComponentActivity: BiometricPrompt, which backs the
 * app lock, can only be hosted by one.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MixMasterApp).container

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

            MixMasterTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalAppContainer provides container) {
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
