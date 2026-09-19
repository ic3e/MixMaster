package com.conwic.mixmaster.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.BuildConfig
import com.conwic.mixmaster.data.update.AppUpdates
import com.conwic.mixmaster.data.update.UpdateState
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel

/**
 * Checking for, fetching and installing a new build, without leaving the app.
 *
 * Android still shows its own install confirmation, and the phone has to allow this app to
 * install at all — neither can be skipped by anything short of a managed-device setup. What
 * this removes is finding the file in Downloads and opening it by hand.
 */
@Composable
fun UpdateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by AppUpdates.state.collectAsState()

    // Whether the phone lets this app install another is a live system setting, not something
    // Compose watches. Reading it once during composition meant that granting the permission
    // changed nothing on screen: the card stayed on "give permission" and the install button
    // never arrived, which looked like the update had simply died.
    var canInstall by remember { mutableStateOf(AppUpdates.canInstall(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = AppUpdates.canInstall(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The settings screen reports nothing back, so the result is ignored and the package
    // manager is asked again. If permission is now there and the APK is waiting, carry
    // straight on to the install rather than making them find the button.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        canInstall = AppUpdates.canInstall(context)
        val ready = AppUpdates.state.value as? UpdateState.ReadyToInstall
        if (canInstall && ready != null) AppUpdates.install(context, ready.file)
    }

    Column(modifier = modifier) {
        SectionLabel(text = "App updates")
        CardFlat {
            Text(
                text = "This phone is on ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
            )

            when (val current = state) {
                is UpdateState.Idle -> {
                    Hint("Checks on its own each time the app opens.")
                    GhostButton(
                        text = "Check for updates",
                        onClick = { AppUpdates.check(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.Checking -> Hint("Checking…")

                is UpdateState.UpToDate -> {
                    Hint("Nothing newer has been published.")
                    GhostButton(
                        text = "Check again",
                        onClick = { AppUpdates.check(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.Available -> {
                    Hint("${current.info.versionName} is ready to download.")
                    if (current.info.notes.isNotBlank()) Hint("What changed: ${current.info.notes}")
                    PrimaryButton(
                        text = "Download ${current.info.versionName}",
                        onClick = { AppUpdates.download(context, current.info) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.Downloading -> {
                    Hint("Downloading ${current.info.versionName} — ${current.percent}%")
                    LinearProgressIndicator(
                        progress = { current.percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    GhostButton(
                        text = "Cancel",
                        onClick = { AppUpdates.cancel() },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.ReadyToInstall -> {
                    if (canInstall) {
                        Hint("${current.info.versionName} is downloaded. Android will ask you to confirm.")
                        PrimaryButton(
                            text = "Install ${current.info.versionName}",
                            onClick = { AppUpdates.install(context, current.file) },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    } else {
                        // One-time system toggle. Without it the installer never opens, and the
                        // tap looks like it did nothing at all.
                        Hint(
                            "Android needs your permission for MixMaster to install updates. " +
                                "You only have to do this once.",
                        )
                        PrimaryButton(
                            text = "Give permission",
                            onClick = {
                                permissionLauncher.launch(AppUpdates.installPermissionIntent(context))
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }

                is UpdateState.Failed -> {
                    Text(
                        text = current.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    GhostButton(
                        text = "Try again",
                        onClick = { AppUpdates.check(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Shown on the home screen only when there's actually something to install, so an update
 * doesn't sit unnoticed in Settings waiting to be looked for.
 */
@Composable
fun UpdateBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by AppUpdates.state.collectAsState()
    val version = when (val current = state) {
        is UpdateState.Available -> current.info.versionName
        is UpdateState.ReadyToInstall -> current.info.versionName
        else -> return
    }
    CardFlat(modifier = modifier.fillMaxWidth()) {
        Text(text = "MixMaster $version is available", style = MaterialTheme.typography.titleMedium)
        Hint("You're on ${BuildConfig.VERSION_NAME}.")
        PrimaryButton(
            text = "Update",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}
