package com.conwic.mixmaster.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.R
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.theme.Accent
import com.conwic.mixmaster.ui.theme.CharcoalDeep
import com.conwic.mixmaster.ui.theme.TextOnDark
import kotlin.math.max
import kotlin.math.min

/**
 * The guide, played like a video: the app's screens on a phone in the middle, a finger going
 * through them, and a caption under it read out by the phone's own voice.
 *
 * It plays by itself and can be stopped at any point — a tap on the picture pauses it, as on a
 * video — stepped back and forward a shot at a time, or jumped to a chapter. Each shot stays up
 * for as long as its caption takes to read, and never cuts the voice off mid-sentence.
 *
 * A new phone gets it straight after sign-in, with "Skip" in the corner. From then on it is under
 * Settings → Help, whole or by the chapter.
 */
@Composable
fun GuideScreen(startChapter: Int, firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val voice = remember { GuideVoice(context, locale) }
    DisposableEffect(voice) { onDispose { voice.close() } }

    var chapter by rememberSaveable { mutableStateOf(startChapter.coerceIn(0, Guide.lastIndex)) }
    var scene by rememberSaveable { mutableStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var soundOn by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    // Milliseconds into the shot. Read by the picture only through the beat it is on, and by the
    // progress bar only while drawing, so the page is not recomposed every frame.
    val elapsed = remember { mutableStateOf(0L) }

    val shot = Guide[chapter].scenes[scene]
    val caption = stringResource(shot.caption)
    // As long as the caption takes to read, and time for the picture to get through its moments.
    val duration = max(max(4500L, caption.length * 62L + 1800L), shot.beats * 1300L)
    val beat by remember(chapter, scene) {
        derivedStateOf { ((elapsed.value.toFloat() / duration) * shot.beats).toInt().coerceIn(0, shot.beats - 1) }
    }

    fun go(toChapter: Int, toScene: Int) {
        chapter = toChapter
        scene = toScene
        elapsed.value = 0L
        finished = false
    }

    fun next() {
        when {
            scene + 1 < Guide[chapter].scenes.size -> go(chapter, scene + 1)
            chapter + 1 < Guide.size -> go(chapter + 1, 0)
            else -> {
                finished = true
                voice.stop()
            }
        }
    }

    fun previous() {
        when {
            // a few seconds in, "back" goes to the start of this shot, as on a player
            elapsed.value > 2500L -> go(chapter, scene)
            scene > 0 -> go(chapter, scene - 1)
            chapter > 0 -> go(chapter - 1, Guide[chapter - 1].scenes.lastIndex)
            else -> go(0, 0)
        }
    }

    // The caption read out at the start of each shot, and again when playing resumes.
    LaunchedEffect(chapter, scene, playing, soundOn, voice.available, finished) {
        if (playing && soundOn && voice.available && !finished) voice.say(caption) else voice.stop()
    }

    // The clock: on to the next shot when this one's time is up and the voice has finished.
    LaunchedEffect(chapter, scene, playing, finished) {
        if (!playing || finished) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed.value += now - last
            last = now
            val voiceDone = !(soundOn && voice.available) || !voice.speaking
            // a voice that never says it has finished must not hold the guide for good
            if (elapsed.value >= duration && (voiceDone || elapsed.value >= duration * 3)) {
                next()
                break
            }
        }
    }

    // Paused with the app; the screen kept on while it plays, as a video player does.
    val activity = LocalAppActivity.current
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                playing = false
                voice.stop()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    BackHandler {
        if (menu) menu = false else onDone()
    }

    val progress = remember(chapter, scene) {
        { chapterIndex: Int ->
            when {
                chapterIndex < chapter -> 1f
                chapterIndex > chapter -> 0f
                else -> {
                    val scenes = Guide[chapter].scenes.size
                    (scene + min(1f, elapsed.value.toFloat() / duration)) / scenes
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CharcoalDeep)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
            val wide = maxWidth > maxHeight
            when {
                finished -> EndCard(
                    firstRun = firstRun,
                    onAgain = {
                        go(0, 0)
                        playing = true
                    },
                    onDone = onDone,
                )
                wide -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stage(chapter, scene, beat, onTap = { playing = !playing }, modifier = Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier.width(maxWidth * 0.42f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Header(chapter, firstRun, onDone)
                        ProgressBar(progress)
                        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) { Caption(caption) }
                        Controls(
                            playing = playing,
                            sound = soundOn && voice.available,
                            soundAvailable = voice.available,
                            onMenu = { menu = true },
                            onPrevious = { previous() },
                            onPlay = { playing = !playing },
                            onNext = { next() },
                            onSound = { soundOn = !soundOn },
                        )
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Header(chapter, firstRun, onDone)
                    ProgressBar(progress)
                    Stage(chapter, scene, beat, onTap = { playing = !playing }, modifier = Modifier.weight(1f).fillMaxWidth())
                    Caption(caption)
                    Controls(
                        playing = playing,
                        sound = soundOn && voice.available,
                        soundAvailable = voice.available,
                        onMenu = { menu = true },
                        onPrevious = { previous() },
                        onPlay = { playing = !playing },
                        onNext = { next() },
                        onSound = { soundOn = !soundOn },
                    )
                }
            }
        }
        if (menu) {
            ChapterMenu(
                current = chapter,
                onPick = { picked ->
                    go(picked, 0)
                    playing = true
                    menu = false
                },
                onClose = { menu = false },
            )
        }
    }
}

@Composable
private fun Header(chapter: Int, firstRun: Boolean, onDone: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.guide_chapter_of, chapter + 1, Guide.size),
                style = MaterialTheme.typography.labelLarge,
                color = TextOnDark.copy(alpha = 0.7f),
            )
            Text(text = stringResource(Guide[chapter].title), style = MaterialTheme.typography.titleLarge, color = TextOnDark)
        }
        if (firstRun) {
            TextButton(onClick = onDone) {
                Text(text = stringResource(R.string.guide_skip), color = TextOnDark, fontWeight = FontWeight.Bold)
            }
        } else {
            IconButton(onClick = onDone) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.guide_close), tint = TextOnDark)
            }
        }
    }
}

/** One bar per chapter, filling as it plays. */
@Composable
private fun ProgressBar(progress: (Int) -> Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
        val gap = 4.dp.toPx()
        val count = Guide.size
        val width = (size.width - gap * (count - 1)) / count
        repeat(count) { index ->
            val x = index * (width + gap)
            drawRoundRect(color = Color.White.copy(alpha = 0.22f), topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(size.height))
            val filled = progress(index)
            if (filled > 0f) {
                drawRoundRect(color = Accent, topLeft = Offset(x, 0f), size = Size(width * filled, size.height), cornerRadius = CornerRadius(size.height))
            }
        }
    }
}

/**
 * The phone the guide plays on: the pictures are laid out on a 360 × 720 screen and scaled to the
 * room there is. Nothing in them is a button — a tap anywhere pauses or plays.
 */
@Composable
private fun Stage(chapter: Int, scene: Int, beat: Int, onTap: () -> Unit, modifier: Modifier) {
    val tap by rememberUpdatedState(onTap)
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = min(maxWidth / StageWidth, maxHeight / StageHeight)
        Box(
            modifier = Modifier
                .size(StageWidth * scale, StageHeight * scale)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            Crossfade(targetState = chapter to scene, animationSpec = tween(450), label = "shot") { (c, s) ->
                val shown = Guide[c].scenes[s]
                Box(
                    modifier = Modifier
                        .requiredSize(StageWidth, StageHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    // the shot fading out stays on its last moment
                    shown.picture(if (c == chapter && s == scene) beat else shown.beats - 1)
                }
            }
            Box(modifier = Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { tap() } })
        }
    }
}

@Composable
private fun Caption(text: String) {
    Crossfade(targetState = text, animationSpec = tween(350), label = "caption") { shown ->
        Text(
            text = shown,
            style = MaterialTheme.typography.titleMedium,
            color = TextOnDark,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        )
    }
}

@Composable
private fun Controls(
    playing: Boolean,
    sound: Boolean,
    soundAvailable: Boolean,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onSound: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Control(Icons.Filled.Menu, stringResource(R.string.guide_chapters), onMenu)
        Control(Icons.Filled.SkipPrevious, stringResource(R.string.guide_previous), onPrevious)
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Accent).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.guide_pause else R.string.guide_play),
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
        Control(Icons.Filled.SkipNext, stringResource(R.string.guide_next), onNext)
        Control(
            if (sound) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            stringResource(if (soundAvailable) R.string.guide_voice else R.string.guide_no_voice),
            onSound,
            enabled = soundAvailable,
        )
    }
}

@Composable
private fun Control(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = label, tint = if (enabled) TextOnDark else TextOnDark.copy(alpha = 0.35f))
    }
}

@Composable
private fun ChapterMenu(current: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(CharcoalDeep)
                // a tap between the rows is the card's, not the dark round it that closes the menu
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.guide_chapters),
                style = MaterialTheme.typography.titleLarge,
                color = TextOnDark,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Guide.forEachIndexed { index, chapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .background(if (index == current) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = chapter.icon, contentDescription = null, tint = if (index == current) Accent else TextOnDark)
                    Text(
                        text = "${index + 1}. " + stringResource(chapter.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextOnDark,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EndCard(firstRun: Boolean, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.guide_end_title), style = MaterialTheme.typography.headlineMedium, color = TextOnDark)
        Text(
            text = stringResource(R.string.guide_end_text),
            style = MaterialTheme.typography.bodyLarge,
            color = TextOnDark.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
        PrimaryButton(
            text = stringResource(if (firstRun) R.string.guide_end_start else R.string.guide_end_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onAgain, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = stringResource(R.string.guide_again), color = TextOnDark, fontWeight = FontWeight.Bold)
        }
    }
}
