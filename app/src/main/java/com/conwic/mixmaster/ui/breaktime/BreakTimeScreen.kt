package com.conwic.mixmaster.ui.breaktime

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.pourday.game.GameScreen

/**
 * Pour Day: a first-person concrete shift, for the hours the slab takes to harden.
 *
 * The game itself lives in the :game module (see [GameScreen]), shared with the Pour Day app that
 * people outside the company can have without MixMaster. This only puts it on screen.
 *
 * Full screen: the status and navigation bars are hidden while it is open and come back with a
 * swipe from the edge. The way out is inside the game — its pause menu has "Back to MixMaster" —
 * and the phone's back gesture pauses rather than leaves.
 *
 * [appName] is who the game thinks it is running in. On a phone its company took off, MixMaster is
 * only the game, and it runs as Pour Day: "Quit" rather than "Back to MixMaster", and no company
 * sign on the van.
 */
@Composable
fun BreakTimeScreen(onExit: () -> Unit, appName: String = "MixMaster") {
    val exit by rememberUpdatedState(onExit)
    val context = LocalContext.current
    // the answer to a permission question goes to whoever asked it
    val permissionAnswer = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val then = permissionAnswer[0]
        permissionAnswer[0] = null
        then?.invoke(result.values.all { it })
    }
    val game = remember {
        GameScreen(
            context = context,
            appName = appName,
            onQuit = { exit() },
            askPermissions = { permissions, then ->
                permissionAnswer[0] = then
                askPermissions.launch(permissions)
            },
        )
    }

    BackHandler { game.back { exit() } }

    val activity = LocalAppActivity.current
    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose { }
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Paused with the app, and full screen again on the way back, which Android does not always
    // keep across a trip to another app.
    DisposableEffect(activity, game) {
        if (activity == null) return@DisposableEffect onDispose { }
        val lifecycle = activity.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> game.pause()
                Lifecycle.Event.ON_RESUME -> {
                    game.resume()
                    val window = activity.window
                    WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { game.view },
        onRelease = { game.close() },
        modifier = Modifier.fillMaxSize(),
    )
}
