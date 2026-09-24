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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.conwic.mixmaster.ui.theme.Ok
import kotlinx.coroutines.delay

/** One trip to the mixer: what goes in, and whether it is the odd smaller one at the end. */
data class MixingStep(val amounts: List<ComponentAmount>, val isPartBatch: Boolean)

/** When a datasheet says nothing, two minutes — the figure most of them give. */
const val DefaultMixSeconds = 120

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

    val seconds = if (mixSeconds > 0) mixSeconds else DefaultMixSeconds
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

                val remaining = if (phase == MixPhase.RUNNING) {
                    ((deadline - now).coerceAtLeast(0L) / 1000.0).toInt()
                } else if (phase == MixPhase.DONE) {
                    0
                } else {
                    seconds
                }
                val fraction = if (seconds > 0) remaining.toFloat() / seconds.toFloat() else 0f
                TimerRing(
                    fraction = fraction,
                    label = clock(remaining),
                    running = phase == MixPhase.RUNNING,
                    done = phase == MixPhase.DONE,
                )

                when (phase) {
                    MixPhase.READY -> {
                        PrimaryButton(
                            text = stringResource(R.string.mix_start),
                            onClick = {
                                stepStartedAt = System.currentTimeMillis()
                                deadline = stepStartedAt + seconds * 1000L
                                now = stepStartedAt
                                phase = MixPhase.RUNNING
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    MixPhase.RUNNING -> {
                        Text(
                            text = stringResource(R.string.mix_running_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GhostButton(
                            text = stringResource(R.string.mix_stop_early),
                            onClick = {
                                phase = MixPhase.READY
                                recordAndAdvance()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    MixPhase.DONE -> {
                        Text(
                            text = stringResource(R.string.mix_ready),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        PrimaryButton(
                            text = if (stepIndex + 1 < steps.size) {
                                stringResource(R.string.mix_next_batch)
                            } else {
                                stringResource(R.string.mix_last_done)
                            },
                            onClick = {
                                phase = MixPhase.READY
                                recordAndAdvance()
                            },
                            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun TimerRing(fraction: Float, label: String, running: Boolean, done: Boolean) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (running || done) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val arc = if (done) Ok else MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
        Canvas(modifier = Modifier.size(220.dp).scale(scale)) {
            val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(
                color = arc,
                startAngle = -90f,
                // Wound down as the time goes, so what is left is what is drawn.
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }
        Text(text = label, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
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
