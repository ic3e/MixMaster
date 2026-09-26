package com.conwic.mixmaster.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.ui.calculator.MixRun
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.ui.components.ConwicLockup
import com.conwic.mixmaster.ui.components.PrimaryButton

/** How long the app can sit in the background before it asks again. Long enough that picking a
 * photo or answering a call isn't a re-authentication, short enough to be worth something. */
private const val RelockAfterMillis = 2 * 60 * 1000L

/**
 * Which authenticators this phone can actually offer, or null if it can offer none.
 *
 * Wrapped because this asks the system on behalf of a context the app builds itself for the
 * language setting. A lock that can't be checked has to mean "don't lock", never "don't open".
 */
private fun allowedAuthenticators(context: Context): Int? = runCatching {
    val manager = BiometricManager.from(context)
    val withCredential = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (manager.canAuthenticate(withCredential) == BiometricManager.BIOMETRIC_SUCCESS) return@runCatching withCredential
    if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
        return@runCatching BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
    null
}.getOrNull()

/** True when this phone has a fingerprint, face or screen lock the app can check against. */
fun canLockApp(context: Context): Boolean = allowedAuthenticators(context) != null

/**
 * Holds [content] behind the phone's own fingerprint, face or screen lock while [enabled].
 *
 * The setting used to be stored and never acted on, which is the worst way for a security toggle
 * to behave. If the phone has no lock set up there's nothing to check against, so the app opens
 * rather than shutting someone out of their own job sheets.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Handed down rather than cast out of LocalContext: that is a locale wrapper, not the
    // activity, and a null here silently means "never lock".
    val activity = LocalAppActivity.current ?: (context as? FragmentActivity)
    val authenticators = remember { allowedAuthenticators(context) }
    val guarding = enabled && activity != null && authenticators != null

    // Locked from the start when the setting was already on at launch. Switching it on later in
    // Settings doesn't throw a prompt up mid-session.
    var unlocked by remember { mutableStateOf(!guarding) }
    var prompting by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf<String?>(null) }
    var leftAt by remember { mutableStateOf(0L) }

    // Coming back after a couple of minutes away asks again; a quick hop out to the camera or the
    // file picker does not.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, guarding) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> leftAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START ->
                    // Not while a batch is in the mixer. Locking again drops everything composed
                    // behind the prompt, and that is the running mix — its clock, its place in
                    // the batch list and what it has counted so far. A phone that spent those
                    // two minutes on a bucket next to the person mixing is not the phone this
                    // lock is for.
                    if (guarding &&
                        leftAt != 0L &&
                        System.currentTimeMillis() - leftAt > RelockAfterMillis &&
                        !MixRun.underWay()
                    ) {
                        unlocked = false
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun prompt() {
        if (prompting || activity == null || authenticators == null) return
        prompting = true
        lastMessage = null

        // Fail open if the prompt can't be built or shown at all — a lock that won't open is
        // worse than no lock, and the data is local to this phone either way.
        val biometricPrompt = runCatching {
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        prompting = false
                        unlocked = true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        prompting = false
                        lastMessage = errString.toString()
                    }

                    override fun onAuthenticationFailed() {
                        // Wrong finger — the prompt stays up and handles the retry itself.
                    }
                },
            )
        }.getOrElse {
            prompting = false
            unlocked = true
            return
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_unlock_title))
            .setSubtitle(context.getString(R.string.lock_unlock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .apply {
                // A negative button is required unless the phone's own screen lock is offered as
                // the fallback, and forbidden when it is.
                if (authenticators == BiometricManager.Authenticators.BIOMETRIC_WEAK) {
                    setNegativeButtonText(context.getString(R.string.action_cancel))
                }
            }
            .build()

        runCatching { biometricPrompt.authenticate(info) }.onFailure {
            prompting = false
            unlocked = true
        }
    }

    LaunchedEffect(unlocked, guarding) {
        if (guarding && !unlocked) prompt()
    }

    if (unlocked || !guarding) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ConwicLockup(height = 34.dp)
            Text(
                text = stringResource(R.string.lock_locked),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 28.dp),
            )
            lastMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.lock_unlock),
                onClick = { prompt() },
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
