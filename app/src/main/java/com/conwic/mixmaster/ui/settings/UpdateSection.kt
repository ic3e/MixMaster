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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.BuildConfig
import com.conwic.mixmaster.R
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
        SectionLabel(text = stringResource(R.string.upd_section))
        CardFlat {
            Text(
                text = stringResource(R.string.upd_this_phone, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.titleMedium,
            )

            when (val current = state) {
                is UpdateState.Idle -> {
                    Hint(stringResource(R.string.upd_auto_note))
                    GhostButton(
                        text = stringResource(R.string.upd_check),
                        onClick = { AppUpdates.check(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.Checking -> Hint(stringResource(R.string.upd_checking))

                is UpdateState.UpToDate -> {
                    Hint(stringResource(R.string.upd_up_to_date))
                    GhostButton(
                        text = stringResource(R.string.upd_check_again),
                        onClick = { AppUpdates.check(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.Available -> {
                    Hint(stringResource(R.string.upd_ready, current.info.versionName))
                    if (current.info.notes.isNotBlank()) Hint(stringResource(R.string.upd_what_changed, current.info.notes))
                    PrimaryButton(
                        text = stringResource(R.string.upd_download, current.info.versionName),
                        onClick = { AppUpdates.download(context, current.info) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.Downloading -> {
                    Hint(stringResource(R.string.upd_downloading, current.info.versionName, current.percent))
                    LinearProgressIndicator(
                        progress = { current.percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    GhostButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { AppUpdates.cancel() },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }

                is UpdateState.ReadyToInstall -> {
                    if (canInstall) {
                        Hint(stringResource(R.string.upd_downloaded, current.info.versionName))
                        PrimaryButton(
                            text = stringResource(R.string.upd_install, current.info.versionName),
                            onClick = { AppUpdates.install(context, current.file) },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    } else {
                        // One-time system toggle. Without it the installer never opens, and the
                        // tap looks like it did nothing at all.
                        Hint(stringResource(R.string.upd_permission_note))
                        PrimaryButton(
                            text = stringResource(R.string.upd_give_permission),
                            onClick = {
                                permissionLauncher.launch(AppUpdates.installPermissionIntent(context))
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }

                is UpdateState.Failed -> {
                    Text(
                        text = stringResource(current.reasonRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    GhostButton(
                        text = stringResource(R.string.upd_try_again),
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
        Text(text = stringResource(R.string.upd_available, version), style = MaterialTheme.typography.titleMedium)
        Hint(stringResource(R.string.upd_youre_on, BuildConfig.VERSION_NAME))
        PrimaryButton(
            text = stringResource(R.string.upd_update),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}
