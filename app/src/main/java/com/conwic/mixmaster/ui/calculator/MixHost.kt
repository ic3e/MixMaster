package com.conwic.mixmaster.ui.calculator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.conwic.mixmaster.data.prefs.MixRunStore
import com.conwic.mixmaster.ui.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Shows the mixing screen wherever the app happens to be, for as long as a run is under way.
 *
 * It sits above the whole navigation graph rather than inside the calculator, because a run
 * outlives the screen that started it: the worker walks to the shed, the app lock closes over
 * it, Android reclaims the app in a pocket, a new build gets installed — and the alarm brings
 * the app back. Whatever it comes back to, the batch is on top of it, which is the point.
 */
@Composable
fun MixingHost() {
    val context = LocalContext.current
    val run by MixRunStore.active(context).collectAsState()
    val current = run ?: return

    // Read once for this run, not on every change: after this, the screen is the one that knows
    // where the clock is, and reading its own writes back would start the batch again.
    val progress = remember(current) { MixRunStore.progress(context) }

    val container = LocalAppContainer.current
    MixingSession(
        run = current,
        progress = progress,
        onProgress = { MixRunStore.saveProgress(context, it) },
        onRecord = { amounts, batches ->
            // On the app's own scope, not this screen's: the next thing the worker does is
            // close the summary, which takes this composition with it.
            container.appScope.launch {
                container.projectRepository.recordMaterialUse(
                    projectId = current.projectId,
                    roomId = current.roomId,
                    solutionId = current.solutionId,
                    title = current.title,
                    jobLabel = current.jobLabel,
                    batches = batches,
                    amounts = amounts,
                )
            }
        },
        onClose = { MixRunStore.clear(context) },
    )
}
