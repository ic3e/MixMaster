package com.conwic.mixmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.MixMasterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MixMasterApp).container

        setContent {
            MixMasterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalAppContainer provides container) {
                        // Onboarding hasn't been seen yet on a fresh install -> start at Sign in;
                        // otherwise jump straight to Home. Resolved once before the NavHost mounts.
                        val onboardingSeen by container.userPrefs.onboardingSeen.collectAsState(initial = null)
                        when (onboardingSeen) {
                            null -> Unit // still loading the preference — avoid flashing the wrong start screen
                            else -> {
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
