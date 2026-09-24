package com.conwic.mixmaster.ui.calculator

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.BatchPlan
import com.conwic.mixmaster.domain.ComponentAmount
import com.conwic.mixmaster.domain.MixPart
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.quantityFromGrams
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.theme.Accent2
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.Ok
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** One trip to the mixer: what goes in, and whether it is the odd smaller one at the end. */
data class MixingStep(val amounts: List<ComponentAmount>, val isPartBatch: Boolean)

/** When a datasheet says nothing, two minutes — the figure most of them give. */
const val DefaultMixSeconds = 120

/** What one press of the arrows is worth, and as far as the time can be taken. */
const val MixStepSeconds = 15
const val MaxMixSeconds = 900

/**
 * The batches of a plan, in the order they get mixed.
 *
 * A leftover too small for its own mixing was folded into the last full batch upstream, so it
 * is added to that batch here too rather than turning up as a trip of its own.
 */
fun mixingSteps(plan: BatchPlan): List<MixingStep> {
    val full = (0 until plan.batches).map { index ->
        val isLast = index == plan.batches - 1
        val extra = plan.lastBatchExtra
        val amounts = if (isLast && extra != null) {
            plan.perBatch.mapIndexed { part, amount ->
                amount.copy(grams = amount.grams + (extra.getOrNull(part)?.grams ?: 0.0))
            }
        } else {
            plan.perBatch
        }
        MixingStep(amounts, isPartBatch = false)
    }
    val remainder = plan.remainderBatch?.let { listOf(MixingStep(it, isPartBatch = true)) }.orEmpty()
    return full + remainder
}

/**
 * Mixing, one batch at a time, with the clock running.
 *
 * Every datasheet says to mix for two or three minutes and nobody does: the drill comes out
 * when the lumps go, which is early, and an under-mixed topping cures patchy. So the app holds
 * the timer — what goes in this batch, a countdown that cannot be talked down, an alarm loud
 * enough for a site, and a tally at the end of what was actually mixed and how long it took.
 */
@Composable
fun MixingSession(
    title: String,
    batchSize: String,
    steps: List<MixingStep>,
    parts: List<MixPart>,
    mixSeconds: Int,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalAppActivity.current

    var stepIndex by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf(MixPhase.READY) }
    var deadline by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var stepStartedAt by remember { mutableStateOf(0L) }
    val sessionStartedAt = remember { System.currentTimeMillis() }
    // Only what was actually mixed counts — a session ended early has fewer batches in it than
    // the plan, which is the whole point of being able to end it.
    val doneDurations = remember { mutableListOf<Long>() }
    var doneSteps by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var finishedAt by remember { mutableStateOf(0L) }

    // Starts at what the mix carries and can be nudged on the spot: a cold morning, a stiff
    // batch or a worn paddle all want another half minute, and that is decided at the mixer.
    var seconds by remember { mutableStateOf(if (mixSeconds > 0) mixSeconds else DefaultMixSeconds) }
    val step = steps.getOrNull(stepIndex)

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

    // Loud and long: a phone on a bucket in a room with a grinder going has to be noticed.
    val ringtone = remember { alarmSound(context) }
    LaunchedEffect(phase) {
        if (phase == MixPhase.DONE) {
            runCatching { ringtone?.play() }
            buzz(context)
        } else {
            runCatching { ringtone?.stop() }
        }
    }
    DisposableEffect(Unit) {
        // Hands full, gloves on, nobody is tapping the screen to keep it awake.
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            runCatching { ringtone?.stop() }
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val flashing = phase == MixPhase.DONE
        val flash = rememberInfiniteTransition(label = "flash")
        val flashAlpha by flash.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flashAlpha",
        )
        val background by animateColorAsState(
            targetValue = if (flashing) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + 0.5f * flashAlpha)
                    .compositeOn(MaterialTheme.colorScheme.background)
            } else {
                MaterialTheme.colorScheme.background
            },
            label = "background",
        )

        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (finished || step == null) {
                    MixingSummary(
                        title = title,
                        steps = doneSteps,
                        durations = doneDurations,
                        totalMillis = (if (finishedAt > 0L) finishedAt else System.currentTimeMillis()) - sessionStartedAt,
                        used = usedMaterial(steps.take(doneSteps), parts),
                        onClose = onClose,
                    )
                    return@Column
                }

                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (step.isPartBatch) {
                        stringResource(R.string.mix_part_batch_of, stepIndex + 1, steps.size)
                    } else {
                        stringResource(R.string.mix_batch_of, stepIndex + 1, steps.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                if (batchSize.isNotBlank()) {
                    Text(
                        text = batchSize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                                modifier = Modifier.padding(end = 8.dp),
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

                val remainingMillis = when (phase) {
                    MixPhase.RUNNING -> (deadline - now).coerceAtLeast(0L)
                    MixPhase.DONE -> 0L
                    MixPhase.READY -> seconds * 1000L
                }
                // Rounded up, so a run of two minutes opens on 2:00 and the last second is 0:01
                // rather than a zero that sits there while the drill is still turning.
                val shown = ceil(remainingMillis / 1000.0).toInt()
                // Keyed on the batch, so every one of them gets the wind-up rather than only
                // the first: the ring arriving is what says a new batch is up.
                key(stepIndex) {
                    TimerRing(
                        fraction = if (seconds > 0) remainingMillis / (seconds * 1000f) else 0f,
                        label = clock(shown),
                        running = phase == MixPhase.RUNNING,
                        done = phase == MixPhase.DONE,
                    )
                }

                // Only before it starts: a countdown that can be argued with while it runs is
                // not a countdown.
                if (phase == MixPhase.READY) {
                    TimeStepper(
                        label = stringResource(R.string.mix_time_label),
                        onLess = { seconds = (seconds - MixStepSeconds).coerceAtLeast(MixStepSeconds) },
                        onMore = { seconds = (seconds + MixStepSeconds).coerceAtMost(MaxMixSeconds) },
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
                            },
                        )
                    }
                    MixPhase.RUNNING -> {
                        Text(
                            text = stringResource(R.string.mix_running_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        // As big as the others so a glove can find it, but outlined rather than
                        // filled: ending the mixing early is an override, not the way through.
                        BigButton(
                            text = stringResource(R.string.mix_stop_early),
                            onClick = {
                                phase = MixPhase.READY
                                recordAndAdvance()
                            },
                            filled = false,
                        )
                    }
                    MixPhase.DONE -> {
                        Text(
                            text = stringResource(R.string.mix_ready),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        BigButton(
                            text = if (stepIndex + 1 < steps.size) {
                                stringResource(R.string.mix_next_batch)
                            } else {
                                stringResource(R.string.mix_last_done)
                            },
                            onClick = {
                                phase = MixPhase.READY
                                recordAndAdvance()
                            },
                        )
                    }
                }

                // Sometimes the floor is covered before the plan is: ending the run here counts
                // what was mixed rather than what was going to be.
                GhostButton(
                    text = stringResource(R.string.mix_finish),
                    onClick = {
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

/**
 * The countdown, drawn as light rather than as a band.
 *
 * A thick stroke has two hard edges and reads as a painted ring; what is wanted is something
 * that glows. So every arc here is laid down as a stack of strokes, widest and faintest first,
 * with a thin bright core on top — the edges fall away instead of stopping. Phones from
 * Android 12 get a real blur over the glow on top of that; older ones keep the stack, which is
 * soft enough on its own.
 */
@Composable
private fun TimerRing(fraction: Float, label: String, running: Boolean, done: Boolean) {
    val loop = rememberInfiniteTransition(label = "loop")
    val breathe by loop.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    // One soft arc drifting round the inside, so something is moving while the paddle is.
    val drift by loop.animateFloat(
        initialValue = -90f,
        targetValue = 270f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "drift",
    )
    val halo by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)),
        label = "halo",
    )

    // Winds up from nothing when a batch comes up, so the ring arrives rather than appears.
    var wound by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { wound = true }
    val sweep by animateFloatAsState(
        targetValue = if (wound) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = if (wound) tween(220, easing = LinearEasing) else tween(800, easing = FastOutSlowInEasing),
        label = "sweep",
    )

    // A spring on every second: the number lands rather than flicks over.
    val tick = remember { Animatable(1f) }
    LaunchedEffect(label, running) {
        if (running) {
            tick.snapTo(1.10f)
            tick.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val primary = MaterialTheme.colorScheme.primary
    // Runs warm as the time goes: full colour at the start, amber through the last third.
    val heat = (sweep / 0.34f).coerceIn(0f, 1f)
    val colour = if (done) Ok else lerp(Accent2, primary, heat)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
        // One layer, all of it soft: the countdown is light on the page, not a ring drawn on it.
        Canvas(modifier = Modifier.size(300.dp).blurCompat(6.dp)) {
            val core = 9.dp.toPx()
            val spread = 42.dp.toPx()
            inset((core + spread) / 2f) {
                val radius = size.minDimension / 2f
                if (done) {
                    // Rings of light going out, over and over, until somebody taps.
                    drawCircle(color = colour.copy(alpha = (1f - halo) * 0.22f), radius = radius * (1f + halo * 0.20f))
                    drawCircle(color = colour.copy(alpha = (1f - halo) * 0.11f), radius = radius * (1f + halo * 0.42f))
                }
                // The path the countdown runs on, barely there.
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = core * 0.8f, cap = StrokeCap.Round),
                )
                if (running) {
                    // One soft arc drifting round, so something is moving while the paddle is.
                    softArc(
                        colour = colour,
                        startAngle = drift,
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
                    sweepDegrees = 360f * sweep,
                    corePx = core,
                    spreadPx = spread,
                    coreAlpha = 0.70f,
                    glowAlpha = 0.26f * (if (running || done) breathe else 0.85f),
                )
            }
        }
        Text(
            text = label,
            // Read at arm's length, over a bucket, in daylight.
            fontSize = 74.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (done) Ok else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.scale(if (done) 1f + halo * 0.06f else tick.value),
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

/** A real blur where the platform has one, and nothing where it does not. */
private fun Modifier.blurCompat(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/** Nudges the mixing time before a batch starts. Big targets: this is done in gloves. */
@Composable
private fun TimeStepper(label: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperKey(add = false, onClick = onLess)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        StepperKey(add = true, onClick = onMore)
    }
}

@Composable
private fun StepperKey(add: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = if (add) Icons.Filled.Add else Icons.Filled.Remove,
            contentDescription = null,
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
private fun BigButton(text: String, onClick: () -> Unit, filled: Boolean = true) {
    Button(
        onClick = onClick,
        shape = CardShape,
        colors = if (filled) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
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
                    Text(text = material.label, style = MaterialTheme.typography.bodyMedium)
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
    PrimaryButton(
        text = stringResource(R.string.action_close),
        onClick = onClose,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun alarmSound(context: Context): Ringtone? = runCatching {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    RingtoneManager.getRingtone(context, uri)
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
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 500), -1))
    }
}

/** Lays one colour over another, since a translucent flash over a page has to land on something. */
private fun Color.compositeOn(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}
