package com.conwic.mixmaster.ui.calculator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.prefs.MixProgress
import com.conwic.mixmaster.data.prefs.SavedMixRun
import com.conwic.mixmaster.domain.MixPart
import com.conwic.mixmaster.domain.MixingStep
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.quantityFromGrams
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.data.db.entity.UsedAmount
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.motionOff
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.theme.Charcoal
import com.conwic.mixmaster.ui.theme.CardShape
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.min

/** When a datasheet says nothing, two minutes — the figure most of them give. */
const val DefaultMixSeconds = 120

/**
 * The two ends of the alert strobe: hi-vis amber against a pale wash of it.
 *
 * Fixed rather than themed, and always light, so the dark text over it reads the same whichever
 * way the phone is set — and so the colour is the one a site already reads as "look at me".
 */
private val Alert = Color(0xFFFFB300)
private val AlertPale = Color(0xFFFFF3D6)

/**
 * The curve the middle of the screen turns over on, and how long the turn takes.
 *
 * Leans into the movement and settles out of it, the way a card does when it is flicked over:
 * an even turn reads mechanical, and one that eases at both ends reads slow.
 */
private val FlipEase = CubicBezierEasing(0.45f, 0.05f, 0.2f, 1f)
private const val FlipMillis = 460

/**
 * What one press of the arrows is worth, and as far as the time can be taken.
 *
 * The ceiling is an hour rather than fifteen minutes: a datasheet asking for twenty would have
 * had the "more time" arrow clamp it back down to the limit, which is the one thing that button
 * must never do.
 */
const val MixStepSeconds = 15
const val MaxMixSeconds = 60 * 60

/**
 * Mixing, one batch at a time, with the clock running.
 *
 * Every datasheet says to mix for two or three minutes and nobody does: the drill comes out
 * when the lumps go, which is early, and an under-mixed topping cures patchy. So the app holds
 * the timer — what goes in this batch, a countdown that cannot be talked down, an alarm loud
 * enough for a site, and a tally at the end of what was actually mixed and how long it took.
 *
 * It is handed the run rather than owning it, and says where it has got to through [onProgress],
 * so the whole thing can be picked up again by whoever shows this screen next — including a
 * brand new process, opened by the alarm, with the calculator long gone. See [SavedMixRun].
 */
@Composable
fun MixingSession(
    run: SavedMixRun,
    progress: MixProgress,
    onProgress: (MixProgress) -> Unit,
    /** Writes what actually went in against the job the run was started for. */
    onRecord: (amounts: List<UsedAmount>, batches: Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalAppActivity.current
    val title = run.title
    val steps = run.steps
    val parts = run.parts

    // Asked for here rather than at startup: this is the one screen that needs it, and the
    // reason is on screen when it is asked. The answer is kept, because a "no" changes what
    // this screen is able to promise — see [alertWarning].
    var canNotify by remember { mutableStateOf(notificationsAllowed(context)) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted && notificationsAllowed(context)
    }
    LaunchedEffect(Unit) {
        if (!canNotify) {
            runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // Picked up from where the run had got to, which on a first start is the beginning. Nothing
    // here is remembered across a restart by this screen: the run on disk is the copy that
    // outlives it, and the deadline is a moment rather than a count, so a batch found again
    // half a minute later has half a minute less to go.
    var stepIndex by remember { mutableStateOf(progress.stepIndex) }
    var phase by remember { mutableStateOf(phaseNamed(progress.phase)) }
    var deadline by remember { mutableStateOf(progress.deadline) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var stepStartedAt by remember { mutableStateOf(progress.stepStartedAt) }
    val sessionStartedAt = remember { if (progress.startedAt > 0L) progress.startedAt else System.currentTimeMillis() }
    // Only what was actually mixed counts — a session ended early has fewer batches in it than
    // the plan, which is the whole point of being able to end it.
    val doneDurations = remember { progress.durations.toMutableList() }
    var doneSteps by remember { mutableStateOf(progress.doneSteps) }
    var finished by remember { mutableStateOf(progress.finished) }
    var finishedAt by remember { mutableStateOf(progress.finishedAt) }
    // Written down with the run, not just held here: a summary found again after the app was
    // taken apart would otherwise offer to record the same batches a second time.
    var recorded by remember { mutableStateOf(progress.recorded) }

    // Starts at what the mix carries and can be nudged on the spot: a cold morning, a stiff
    // batch or a worn paddle all want another half minute, and that is decided at the mixer.
    var seconds by remember {
        mutableStateOf(
            when {
                progress.seconds > 0 -> progress.seconds
                run.mixSeconds > 0 -> run.mixSeconds
                else -> DefaultMixSeconds
            },
        )
    }
    val step = steps.getOrNull(stepIndex)

    // Written down whenever something happens, and never on the tick: starting a batch, counting
    // one, nudging the time, ending the run. This is what somebody comes back to.
    LaunchedEffect(stepIndex, phase, deadline, doneSteps, seconds, finished, recorded) {
        onProgress(
            MixProgress(
                stepIndex = stepIndex,
                phase = phase.name,
                deadline = deadline,
                stepStartedAt = stepStartedAt,
                startedAt = sessionStartedAt,
                durations = doneDurations.toList(),
                doneSteps = doneSteps,
                seconds = seconds,
                finished = finished,
                finishedAt = finishedAt,
                recorded = recorded,
            ),
        )
    }

    // Found mid-batch after the app was taken apart: the booking may have gone with it — an app
    // being replaced takes its alarms — so it is made again. Same request, same moment, so where
    // it did survive nothing changes. And the app lock is told, since it was only ever told in
    // memory.
    LaunchedEffect(Unit) {
        if (phase == MixPhase.RUNNING && deadline > System.currentTimeMillis()) {
            MixAlarm.schedule(context, deadline, title)
            MixRun.started(deadline)
        }
    }

    // The clock, not the frames: a countdown built out of ticks drifts, and this one is being
    // trusted with whether a floor was mixed for long enough.
    LaunchedEffect(phase, stepIndex) {
        while (phase == MixPhase.RUNNING) {
            now = System.currentTimeMillis()
            if (now >= deadline) {
                phase = MixPhase.DONE
            }
            delay(100)
        }
    }

    // Whether this screen is the one in front of somebody, which decides who does the shouting.
    val lifecycleOwner = LocalLifecycleOwner.current
    var onScreen by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val watcher = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onScreen = true
                Lifecycle.Event.ON_PAUSE -> onScreen = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    // Loud and long: a phone on a bucket in a room with a grinder going has to be noticed. One
    // alarm at a time, though, and it follows whoever is there to hear it — the screen rings
    // while the screen is up, the notification takes it back the moment the app goes away, and
    // neither of them goes off alongside the other.
    val ringtone = remember { alarmSound(context) }
    // A batch that is up and has not been dealt with. Ending the run closes it too: the summary
    // used to come up with the alarm still going and the screen still flashing behind it.
    val alerting = phase == MixPhase.DONE && !finished
    LaunchedEffect(alerting, onScreen) {
        if (alerting && onScreen) {
            // The screen has it now, so the booking goes: cancelling only the notification left
            // the alarm standing in the system clock, and it would then go off again in the
            // middle of the next batch.
            MixAlarm.cancel(context)
            runCatching { ringtone?.play() }
            buzz(context)
        } else {
            runCatching { ringtone?.stop() }
            // Walked away from a batch that is up and not yet acknowledged: hand it back to the
            // notification, or the ringing stops and nobody is being told at all.
            if (alerting) MixAlarm.alert(context, title)
        }
    }
    DisposableEffect(Unit) {
        // Hands full, gloves on, nobody is tapping the screen to keep it awake.
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            runCatching { ringtone?.stop() }
            // The booking is deliberately left standing. This screen can go away for reasons
            // that have nothing to do with the mix — the system taking the app apart while it
            // is in the background, for one — and then the alarm is the only thing left that
            // can say the mix is ready; it opens the app again by itself. It is cancelled where
            // the run actually ends instead: [close], the early end, the last batch.
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // The way out, wherever it is taken from: nothing left ringing, nothing left booked, and
    // the app lock free to do its job again.
    val close = {
        MixAlarm.cancel(context)
        MixRun.ended()
        onClose()
    }

    fun recordAndAdvance() {
        if (stepStartedAt > 0L) {
            doneDurations += System.currentTimeMillis() - stepStartedAt
            doneSteps += 1
        }
        stepStartedAt = 0L
        if (stepIndex + 1 < steps.size) {
            stepIndex += 1
            phase = MixPhase.READY
        } else {
            finishedAt = System.currentTimeMillis()
            finished = true
        }
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val flashing = alerting
        // A phone told to keep still keeps still: somebody who has turned animations off in
        // accessibility settings is not going to thank a full-screen strobe for it, so the
        // alert holds a steady hi-vis screen instead of pulsing.
        val calm = remember { motionOff(context) }
        // Started when the batch goes off rather than when the screen opens, so the strobe
        // always begins at the pale end and eases both ways: coming in at whatever brightness
        // a loop running since the session opened happened to be at read as a glitch.
        val flashAlpha = if (flashing && !calm) {
            val flash = rememberInfiniteTransition(label = "flash")
            flash.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(560, easing = EaseInOut), RepeatMode.Reverse),
                label = "flashAlpha",
            )
        } else {
            null
        }
        // Hi-vis, and the same either way the phone is themed: a batch going off in a room with
        // a grinder running has to be caught out of the corner of an eye, and brown on cream is
        // not what catches it.
        val page = MaterialTheme.colorScheme.background
        // Dark text while the wash is on. The wash is always light, whatever the theme, so a
        // dark theme's pale text sat on it at around one and a half to one — unreadable at the
        // exact moment it matters most.
        val ink = if (flashing) Charcoal else MaterialTheme.colorScheme.onSurface
        val inkSoft = if (flashing) Charcoal.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
        val inkAccent = if (flashing) Charcoal else MaterialTheme.colorScheme.secondary

        Surface(
            color = Color.Transparent,
            // Painted in the draw pass rather than handed to the surface as a colour: the strobe
            // changes every frame, and as a parameter it recomposed the whole screen with it.
            modifier = Modifier.fillMaxSize().drawBehind {
                drawRect(
                    when {
                        !flashing -> page
                        flashAlpha == null -> Alert
                        else -> lerp(AlertPale, Alert, flashAlpha.value)
                    },
                )
            },
        ) {
            // One screenful, whatever the recipe holds: a header that stays, a middle that takes
            // whatever is left, and the controls on the floor of the screen. The whole page used
            // to scroll, so a mix of six parts pushed the start button off the bottom — which is
            // the one thing on here that has to be under a thumb.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (finished || step == null) {
                    // The summary is read once, at the end, and can be as long as the run was.
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    MixingSummary(
                        title = title,
                        steps = doneSteps,
                        durations = doneDurations,
                        totalMillis = (if (finishedAt > 0L) finishedAt else System.currentTimeMillis()) - sessionStartedAt,
                        used = usedMaterial(steps.take(doneSteps), parts),
                        jobLabel = run.jobLabel,
                        recorded = recorded,
                        onRecord = {
                            onRecord(mixedAmounts(steps.take(doneSteps)), doneSteps)
                            recorded = true
                        },
                        onClose = close,
                    )
                    }
                    return@Column
                }

                Text(text = title, style = MaterialTheme.typography.headlineSmall, color = ink)
                Text(
                    text = if (step.isPartBatch) {
                        stringResource(R.string.mix_part_batch_of, stepIndex + 1, steps.size)
                    } else {
                        stringResource(R.string.mix_batch_of, stepIndex + 1, steps.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = inkAccent,
                    fontWeight = FontWeight.Bold,
                )
                if (run.potLifeMinutes > 0) {
                    // The clock that matters after this one: how long what is in the drum stays
                    // workable. Said here rather than left on a datasheet in the van.
                    Text(
                        text = stringResource(R.string.mix_pot_life, run.potLifeMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (run.batchSize.isNotBlank()) {
                    Text(
                        text = run.batchSize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkSoft,
                    )
                }

                val total = seconds * 1000L
                // Read through a lambda so the clock lands inside the ring rather than up here:
                // ticking ten times a second in this scope recomposed the batch list, the cards
                // and the buttons along with it.
                val remaining = {
                    when (phase) {
                        MixPhase.RUNNING -> (deadline - now).coerceAtLeast(0L)
                        MixPhase.DONE -> 0L
                        MixPhase.READY -> total
                    }
                }

                // The middle of the screen holds one thing at a time, and it is always the thing
                // the worker is doing: what to weigh out before the drill starts, the ring while
                // it turns, and then the list again so the next batch can be weighed. Showing
                // both at once is what pushed everything else off the bottom.
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Square, and never taller than the room it has been left.
                    val side = minOf(maxWidth, maxHeight)
                    // The two of them are the two faces of one card, and pressing start turns it
                    // over: the list goes edge-on and the ring comes round behind it. One object
                    // changing its mind rather than two things swapping — which is what the
                    // screen is really doing, because it is the same batch either way.
                    val flip = animateFloatAsState(
                        targetValue = if (phase == MixPhase.RUNNING) 180f else 0f,
                        animationSpec = if (calm) snap() else tween(FlipMillis, easing = FlipEase),
                        label = "flip",
                    )
                    // Swapped at the halfway point, where the card is edge-on and neither face
                    // has anything to show. Derived rather than read straight, so the turn costs
                    // one recomposition at the halfway mark instead of one per frame — the angle
                    // itself is only ever read inside the layer block.
                    val showRing by remember(flip) { derivedStateOf { flip.value > 90f } }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            rotationY = flip.value
                            // Near enough for the turn to have some depth, far enough that the
                            // leading edge does not balloon across the screen on the way past.
                            cameraDistance = 16f * density
                        },
                    ) {
                        // The back face carried round the other way, so it lands the right way
                        // up rather than in mirror writing.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = if (showRing) 180f else 0f },
                        ) {
                            if (showRing) {
                                // Keyed on the batch, so every one of them gets the wind-up rather than
                                // only the first: the ring arriving is what says a new batch is up.
                                key(stepIndex) {
                                    TimerRing(
                                        remaining = remaining,
                                        total = total,
                                        running = true,
                                        done = false,
                                        calm = calm,
                                        modifier = Modifier.size(side),
                                    )
                                }
                            } else {
                                // Scrolls inside its own space rather than taking the page with it —
                                // a recipe of ten parts is rare, and when it happens the buttons stay
                                // where they are.
                                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                    CardFlat {
                                        SectionLabel(text = stringResource(R.string.mix_goes_in))
                                        step.amounts.forEach { amount ->
                                            val part = parts.firstOrNull { it.label == amount.label }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = amount.label,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                                )
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = quantityFromGrams(amount.grams).text,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                    )
                                                    packText(part, amount.grams)?.let { packs ->
                                                        Text(
                                                            text = packs,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                    }
                }

                // Only before it starts: a countdown that can be argued with while it runs is
                // not a countdown.
                if (phase == MixPhase.READY) {
                    TimeStepper(
                        label = stringResource(R.string.mix_time_label),
                        value = clock(seconds),
                        onLess = { seconds = (seconds - MixStepSeconds).coerceAtLeast(MixStepSeconds) },
                        // Never downwards: a mix that already asks for twenty minutes would
                        // otherwise have "more time" clamp it back to the ceiling.
                        onMore = { seconds = (seconds + MixStepSeconds).coerceAtMost(maxOf(MaxMixSeconds, seconds)) },
                    )
                }

                // Said plainly rather than promised quietly: the alarm off screen depends on
                // permissions the phone can refuse, and a worker who has turned notifications
                // down needs to know the screen has to stay open.
                alertWarning(canNotify, context)?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSoft,
                        textAlign = TextAlign.Center,
                    )
                }

                when (phase) {
                    MixPhase.READY -> {
                        BigButton(
                            text = stringResource(R.string.mix_start),
                            onClick = {
                                stepStartedAt = System.currentTimeMillis()
                                deadline = stepStartedAt + seconds * 1000L
                                now = stepStartedAt
                                phase = MixPhase.RUNNING
                                // Booked with the system clock as well as ticked here: a phone
                                // in a pocket, a call, a screen gone black — the batch is still
                                // up when it is up.
                                MixAlarm.schedule(context, deadline, title)
                                // And said out loud, so the app lock leaves the mix alone.
                                MixRun.started(deadline)
                            },
                        )
                    }
                    MixPhase.RUNNING -> {
                        Text(
                            text = stringResource(R.string.mix_running_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = inkSoft,
                            textAlign = TextAlign.Center,
                        )
                        // As big as the others so a glove can find it, but outlined rather than
                        // filled: ending the mixing early is an override, not the way through.
                        BigButton(
                            text = stringResource(R.string.mix_stop_early),
                            onClick = {
                                MixAlarm.cancel(context)
                                MixRun.ended()
                                recordAndAdvance()
                            },
                            filled = false,
                        )
                    }
                    MixPhase.DONE -> {
                        Text(
                            text = stringResource(R.string.mix_ready),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Charcoal,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        BigButton(
                            text = if (stepIndex + 1 < steps.size) {
                                stringResource(R.string.mix_next_batch)
                            } else {
                                stringResource(R.string.mix_last_done)
                            },
                            onClick = {
                                // Cancelled, not just taken off the shade: the booking outlives
                                // the batch it was made for otherwise.
                                MixAlarm.cancel(context)
                                MixRun.ended()
                                recordAndAdvance()
                            },
                            container = Charcoal,
                            content = Color.White,
                        )
                    }
                }

                // Sometimes the floor is covered before the plan is: ending the run here counts
                // what was mixed rather than what was going to be.
                GhostButton(
                    text = stringResource(R.string.mix_finish),
                    onClick = {
                        MixAlarm.cancel(context)
                        MixRun.ended()
                        finishedAt = System.currentTimeMillis()
                        finished = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private enum class MixPhase { READY, RUNNING, DONE }

/** The phase a saved run was left in. Anything unrecognised starts the batch over, not a crash. */
private fun phaseNamed(name: String): MixPhase =
    runCatching { MixPhase.valueOf(name) }.getOrDefault(MixPhase.READY)

/** How long the ring takes to wind up when a batch comes up, and how big the number is drawn. */
private const val WindUpMillis = 800

/**
 * The ring itself, and the square it is drawn in.
 *
 * They are not the same size on purpose. The rings of light going out when a batch is up need
 * somewhere to go, and what they do not have they used to take anyway — off the edge of the
 * canvas, which is a rectangle, so the pulse came out as a box around the clock. The square is
 * now as wide as the screen allows and the ring keeps its own size inside it.
 */
private val RingRadius = 130.dp
private val RingMax = 340.dp
private val CountdownSize = 74.sp

/** The four things that move on the ring, together, so they share one frame loop. */
private class RingMotion(
    val breathe: State<Float>,
    val drift: State<Float>,
    val halo: State<Float>,
    val pop: State<Float>,
)

/**
 * The ring's movement, or nothing at all when the phone has been told to keep still.
 *
 * Nothing at all, rather than animations that run and are then ignored: a loop nobody reads
 * still asks the phone for a frame sixty times a second, for as long as the batch takes.
 */
@Composable
private fun rememberRingMotion(calm: Boolean): RingMotion? {
    if (calm) return null
    val loop = rememberInfiniteTransition(label = "loop")
    return RingMotion(
        // The glow swelling and falling back while the paddle turns.
        breathe = loop.animateFloat(
            initialValue = 0.62f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        ),
        // One soft arc drifting round the inside, so something is moving while the paddle is.
        drift = loop.animateFloat(
            initialValue = -90f,
            targetValue = 270f,
            animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
            label = "drift",
        ),
        // Rings going out when a batch is up: each one starts again from the middle, so this
        // one runs one way only.
        halo = loop.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)),
            label = "halo",
        ),
        // The number swelling under them, which has to come back down the way it went up:
        // driven off the rings, it snapped back to nothing every time they started again.
        pop = loop.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pop",
        ),
    )
}

/**
 * The countdown, drawn as light rather than as a band.
 *
 * A thick stroke has two hard edges and reads as a painted ring; what is wanted is something
 * that glows. So every arc here is laid down as a stack of strokes, widest and faintest first,
 * with a thin bright core on top — the edges fall away instead of stopping. There is no blur
 * over it: the stack is soft enough on its own, and a blur means a layer, and a layer has four
 * corners for the glow to be cut on.
 *
 * Everything that moves is read inside the draw pass rather than while composing: this runs for
 * up to an hour with the screen held awake, and a countdown that recomposes the screen sixty
 * times a second is a countdown that warms the phone in somebody's pocket.
 */
@Composable
private fun TimerRing(
    remaining: () -> Long,
    total: Long,
    running: Boolean,
    done: Boolean,
    calm: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = rememberRingMotion(calm)
    val breathe = motion?.breathe
    val drift = motion?.drift
    val halo = motion?.halo
    val pop = motion?.pop

    val millis = remaining()
    // Rounded up, so a run of two minutes opens on 2:00 and the last second is 0:01 rather than
    // a zero that sits there while the drill is still turning.
    val label = clock(ceil(millis / 1000.0).toInt())
    val fraction = if (total > 0L) (millis / total.toFloat()).coerceIn(0f, 1f) else 0f

    // Winds up from nothing when a batch comes up, so the ring arrives rather than appears.
    var wound by remember { mutableStateOf(false) }
    // A spec chosen on the same value that sets the target is a spec that never runs: the
    // wind-up was replaced by the countdown's own short step in the very recomposition that
    // started it, and the arrival played as a fifth of a second of nothing. So the short one
    // waits until the long one has had its turn.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        wound = true
        if (!calm) delay(WindUpMillis.toLong())
        settled = true
    }
    val sweep = animateFloatAsState(
        targetValue = if (wound) fraction else 0f,
        animationSpec = when {
            calm -> snap()
            settled -> tween(220, easing = LinearEasing)
            else -> tween(WindUpMillis, easing = FastOutSlowInEasing)
        },
        label = "sweep",
    )

    // A spring on every second: the number lands rather than flicks over. Stiff enough to be
    // finished well inside the second it belongs to — a soft one never came to rest before the
    // next second knocked it again, and the countdown throbbed the whole way down.
    val tick = remember { Animatable(1f) }
    LaunchedEffect(label, running, calm) {
        if (running && !calm) {
            tick.snapTo(1.08f)
            tick.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        } else {
            tick.snapTo(1f)
        }
    }

    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
        // All of it soft: the countdown is light on the page, not a ring drawn on it. Drawn
        // straight onto the page rather than into a layer of its own — a layer has edges, and
        // anything reaching them comes back with corners.
        Canvas(modifier = modifier.widthIn(max = RingMax)) {
            val core = 14.dp.toPx()
            val spread = 26.dp.toPx()
            // A square to draw in, whatever shape the box ended up, and a ring of its own size
            // inside it — smaller only where the screen is too narrow to hold it.
            val half = size.minDimension / 2f
            val edge = (core + spread) / 2f
            val radius = min(RingRadius.toPx(), half - edge)
            // What is left over outside the ring, as a share of it: how far the rings going out
            // may go, so that they fade out in open space rather than run into an edge.
            val room = ((half - radius - 2.dp.toPx()) / radius).coerceIn(0f, 0.25f)
            inset(horizontal = size.width / 2f - radius, vertical = size.height / 2f - radius) {
                // Runs warm as the time goes: the brand colour for most of the run, hi-vis
                // amber through the last third. Amber rather than the second accent, which in a
                // dark theme is the primary itself — the ring read the same all the way down.
                // Once the batch is up the screen behind has gone hi-vis, so the ring goes dark
                // to sit on it.
                val heat = (sweep.value / 0.34f).coerceIn(0f, 1f)
                val colour = if (done) Charcoal else lerp(Alert, primary, heat)
                if (done) {
                    // Rings of light going out, over and over, until somebody taps. Held
                    // half-way out, once, when the phone is keeping still.
                    val out = halo?.value ?: 0.30f
                    drawCircle(color = colour.copy(alpha = (1f - out) * 0.22f), radius = radius * (1f + out * room * 0.45f))
                    drawCircle(color = colour.copy(alpha = (1f - out) * 0.11f), radius = radius * (1f + out * room))
                }
                // The path the countdown runs on, barely there.
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = core * 0.8f, cap = StrokeCap.Round),
                )
                if (running && drift != null) {
                    // One soft arc drifting round, so something is moving while the paddle is.
                    softArc(
                        colour = colour,
                        startAngle = drift.value,
                        sweepDegrees = 46f,
                        corePx = core * 0.5f,
                        spreadPx = spread * 0.55f,
                        coreAlpha = 0.20f,
                        glowAlpha = 0.14f,
                    )
                }
                softArc(
                    colour = colour,
                    startAngle = -90f,
                    // Wound down as the time goes, so what is left on the ring is what is left.
                    sweepDegrees = 360f * sweep.value,
                    corePx = core,
                    spreadPx = spread,
                    coreAlpha = 0.92f,
                    glowAlpha = 0.22f * if (running || done) (breathe?.value ?: 0.85f) else 0.85f,
                )
            }
        }
        Text(
            text = label,
            // Read at arm's length, over a bucket, in daylight — and given a line to stand in
            // that is taller than the digits, because the style's own is shorter than this size
            // and cropped the tops and tails off the numbers.
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = CountdownSize,
                lineHeight = CountdownSize * 1.14f,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = if (done) Charcoal else onSurface,
            // Scaled in a layer rather than by remeasuring: a spring that runs every second has
            // no business laying the screen out again.
            modifier = Modifier.graphicsLayer {
                val scale = when {
                    done -> 1f + (pop?.value ?: 0f) * 0.06f
                    else -> tick.value
                }
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

/**
 * An arc laid down as light: widest and faintest underneath, narrowing and brightening inwards.
 *
 * Ten passes is enough for the edge to fall away rather than stop, and cheap enough to redraw
 * at sixty frames while a countdown runs.
 */
private fun DrawScope.softArc(
    colour: Color,
    startAngle: Float,
    sweepDegrees: Float,
    corePx: Float,
    spreadPx: Float,
    coreAlpha: Float,
    glowAlpha: Float,
    layers: Int = 10,
) {
    if (sweepDegrees <= 0f) return
    for (layer in layers downTo 1) {
        val out = layer / layers.toFloat()
        drawArc(
            color = colour.copy(alpha = glowAlpha * (1f - out) * (1f - out)),
            startAngle = startAngle,
            sweepAngle = sweepDegrees,
            useCenter = false,
            style = Stroke(width = corePx + spreadPx * out, cap = StrokeCap.Round),
        )
    }
    if (coreAlpha > 0f) {
        drawArc(
            color = colour.copy(alpha = coreAlpha),
            startAngle = startAngle,
            sweepAngle = sweepDegrees,
            useCenter = false,
            style = Stroke(width = corePx, cap = StrokeCap.Round),
        )
    }
}

/** Nudges the mixing time before a batch starts. Big targets: this is done in gloves. */
@Composable
private fun TimeStepper(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperKey(add = false, onClick = onLess)
        // The figure lives here now. It used to be read off the ring, which is not on screen
        // before the drill starts — leaving two keys either side of a word and no number.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                // headlineLarge, not a Material default: the theme only defines the styles the
                // app uses, and anything else falls back to Roboto in the middle of Manrope.
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        StepperKey(add = true, onClick = onMore)
    }
}

@Composable
private fun StepperKey(add: Boolean, onClick: () -> Unit) {
    // Named, not just drawn: a plus on its own tells a screen reader nothing about what it adds.
    val what = stringResource(if (add) R.string.mix_time_more else R.string.mix_time_less)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = what, onClick = onClick),
    ) {
        Icon(
            imageVector = if (add) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = what,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(34.dp),
        )
    }
}

/**
 * The one button that matters on this screen.
 *
 * Tall and full width because it is pressed with a gloved thumb, at the mixer, in the seconds
 * between one batch going out and the next going in.
 */
@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = true,
    container: Color? = null,
    content: Color? = null,
) {
    Button(
        onClick = onClick,
        shape = CardShape,
        colors = if (filled) {
            ButtonDefaults.buttonColors(
                containerColor = container ?: MaterialTheme.colorScheme.primary,
                contentColor = content ?: MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.outlinedButtonColors(contentColor = content ?: MaterialTheme.colorScheme.onSurface)
        },
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(88.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MixingSummary(
    title: String,
    steps: Int,
    durations: List<Long>,
    totalMillis: Long,
    used: List<UsedMaterial>,
    /** The job this was mixed for, or blank where the run was started without one. */
    jobLabel: String,
    recorded: Boolean,
    onRecord: () -> Unit,
    onClose: () -> Unit,
) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall)
    Text(
        text = stringResource(R.string.mix_summary),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Bold,
    )
    CardFlat {
        SummaryRow(stringResource(R.string.mix_mixings), "$steps")
        SummaryRow(stringResource(R.string.mix_total_time), clock((totalMillis / 1000).toInt()))
        SummaryRow(
            stringResource(R.string.mix_average_step),
            clock(if (durations.isEmpty()) 0 else (durations.average() / 1000).toInt()),
        )
    }
    if (used.isNotEmpty()) {
        CardFlat {
            SectionLabel(text = stringResource(R.string.mix_used))
            used.forEach { material ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(modifier = Modifier.weight(1f, fill = false), text = material.label, style = MaterialTheme.typography.bodyMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = material.amount, style = MaterialTheme.typography.titleMedium)
                        material.packs?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    // What was mixed, written against the job. Offered here because this is the one moment the
    // figures are the ones that went in rather than the ones that were planned — and once, so a
    // summary read twice doesn't put the same batches on the project twice.
    if (jobLabel.isBlank()) {
        Text(
            text = stringResource(R.string.mix_record_no_job),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    } else if (recorded) {
        CardFlat {
            Text(
                text = stringResource(R.string.mix_recorded, jobLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    } else {
        GhostButton(
            text = stringResource(R.string.mix_record),
            onClick = onRecord,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.mix_record_note, jobLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    PrimaryButton(
        text = stringResource(R.string.action_close),
        onClick = onClose,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * What actually went in over the batches that were done, per part.
 *
 * Kept apart from [usedMaterial], which is the same figures worded for the screen: this one is
 * what gets written down, so it stays in grams and keeps the product each part came from.
 */
private fun mixedAmounts(done: List<MixingStep>): List<UsedAmount> {
    val totals = linkedMapOf<String, UsedAmount>()
    done.forEach { step ->
        step.amounts.forEach { amount ->
            // By product where there is one; a part with no product of its own — water on an
            // old recipe — is still one line rather than one per batch.
            val key = if (amount.productId > 0L) "p${amount.productId}" else "l${amount.label}"
            val running = totals[key]
            totals[key] = UsedAmount(
                productId = amount.productId,
                label = amount.label,
                grams = (running?.grams ?: 0.0) + amount.grams,
            )
        }
    }
    return totals.values.toList()
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.weight(1f, fill = false), text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** What went in over the whole run, per part. */
data class UsedMaterial(val label: String, val amount: String, val packs: String?)

@Composable
private fun usedMaterial(done: List<MixingStep>, parts: List<MixPart>): List<UsedMaterial> {
    val grams = linkedMapOf<String, Double>()
    done.forEach { step ->
        step.amounts.forEach { amount ->
            grams[amount.label] = (grams[amount.label] ?: 0.0) + amount.grams
        }
    }
    return grams.map { (label, total) ->
        val part = parts.firstOrNull { it.label == label }
        UsedMaterial(
            label = label,
            amount = quantityFromGrams(total).text,
            packs = packText(part, total),
        )
    }
}

/** "2 bag" — what a figure comes to in the containers it is carried in. */
@Composable
private fun packText(part: MixPart?, grams: Double): String? {
    if (part == null || part.packageSize <= 0.0) return null
    val kg = grams / 1000.0
    val amount = when {
        part.packageUnit == "kg" -> kg
        part.densityKgPerL > 0.0 -> kg / part.densityKgPerL
        else -> return null
    }
    val packs = amount / part.packageSize
    if (packs <= 0.0) return null
    return stringResource(R.string.mix_packs, formatDecimal(packs, 2), part.packageType)
}

/** m:ss, the way a countdown is read. */
private fun clock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * The same sound the notification uses, so the screen and the shade ring alike.
 *
 * Null where the alert has been set to silent in Settings — the buzz still goes.
 */
private fun alarmSound(context: Context): Ringtone? = runCatching {
    MixAlarm.alarmSoundUri(context)?.let { RingtoneManager.getRingtone(context, it) }
}.getOrNull()

private fun buzz(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        // The same long-short-long as the notification channel's.
        vibrator?.vibrate(VibrationEffect.createWaveform(MixAlarm.VibratePattern, -1))
    }
}

/**
 * Whether this phone will let the app put an alarm on the shade at all.
 *
 * The runtime permission is one half; the switch in the phone's own settings is the other, and
 * either one being off means the off-screen alarm is not going to happen.
 */
private fun notificationsAllowed(context: Context): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return granted && runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)
}

/**
 * What this screen has to admit it cannot do, or null when it can do all of it.
 *
 * The off-screen alarm rests on three things the phone can each refuse: notifications at all,
 * putting one over the lock screen, and landing it to the second. Whichever is missing, the
 * worker is told here — a timer that quietly cannot ring is worse than one that says so.
 */
@Composable
private fun alertWarning(canNotify: Boolean, context: Context): String? = when {
    !canNotify -> stringResource(R.string.mix_alert_off)
    !MixAlarm.canAlertOverLockScreen(context) -> stringResource(R.string.mix_alert_shade_only)
    !MixAlarm.canBeExact(context) -> stringResource(R.string.mix_alert_inexact)
    else -> null
}



