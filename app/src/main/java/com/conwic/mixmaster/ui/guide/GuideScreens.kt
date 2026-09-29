package com.conwic.mixmaster.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.FieldLabel
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.packCount
import com.conwic.mixmaster.ui.components.packLabel
import com.conwic.mixmaster.ui.components.packsName
import com.conwic.mixmaster.ui.theme.Accent
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.Charcoal
import com.conwic.mixmaster.ui.theme.ChipShape
import com.conwic.mixmaster.ui.theme.Danger
import com.conwic.mixmaster.ui.theme.FieldShape
import com.conwic.mixmaster.ui.theme.Ok
import com.conwic.mixmaster.ui.theme.TextOnDark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.IsoFields

/*
 * The guide's pictures: the app's screens as the guide shows them.
 *
 * Built from the app's own cards, buttons and words, so they are in the phone's language and look
 * like the real thing — but they are pictures. Nothing in them does anything, and none of them
 * reads the phone's data: the guide shows the same job on every phone, a new one included. Each
 * takes the moment of its scene (what has been typed, what is lit, where the finger is) and the
 * player steps them through it.
 *
 * Laid out on a phone 360 × 720 dp; the player scales that to whatever room it has.
 */

internal val StageWidth = 360.dp
internal val StageHeight = 720.dp

// Names in the pictures: a job, its client, a site and a worker. The same in every language.
private const val JOB = "Villa Saimaa"
private const val CLIENT = "Mikko & Heidi Laine"
private const val ADDRESS = "Rantatie 41, Savonlinna"
private const val OWNER = "Tanel"
private const val PERSON = "Mart"
private const val CODE = "7QK4-9XF2-M3"
private const val ARCHITOP = "Architop®"

// ---- What every picture is made of -------------------------------------------------------------

/** The finger: a dot where the guide is tapping, with a ring going out from it. */
@Composable
private fun Modifier.tap(on: Boolean): Modifier {
    if (!on) return this
    val ring = rememberInfiniteTransition(label = "tap").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "ring",
    )
    return drawWithContent {
        drawContent()
        val r = 12.dp.toPx()
        val grow = ring.value
        drawCircle(color = Accent.copy(alpha = 0.45f * (1f - grow)), radius = r + 22.dp.toPx() * grow, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r, center = center)
        drawCircle(color = Accent, radius = r, center = center, style = Stroke(width = 3.dp.toPx()))
    }
}

/** A ring round what the caption is talking about. */
@Composable
private fun Modifier.lit(on: Boolean): Modifier {
    val alpha by animateFloatAsState(if (on) 1f else 0f, tween(350), label = "lit")
    return drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val pad = 4.dp.toPx()
            drawRoundRect(
                color = Accent.copy(alpha = alpha),
                topLeft = Offset(-pad, -pad),
                size = Size(size.width + 2 * pad, size.height + 2 * pad),
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private val TabIcons = listOf(Icons.Filled.Home, Icons.Filled.Warehouse, Icons.Filled.Inventory2, Icons.Filled.CalendarMonth, Icons.Filled.Settings)
private val TabNames = listOf(R.string.nav_home, R.string.nav_warehouse, R.string.nav_products, R.string.nav_projects, R.string.nav_settings)

/** One screen of the app: the page, and the tab bar when the page is one of the five. */
@Composable
private fun Page(tab: Int? = null, litTab: Int = -1, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = if (tab != null) 84.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        if (tab != null) TabBar(selected = tab, lit = litTab, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TabBar(selected: Int, lit: Int, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(26.dp)).background(Charcoal).padding(6.dp),
    ) {
        TabIcons.forEachIndexed { index, icon ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (index == selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                    .tap(index == lit)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = TextOnDark, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(TabNames[index]),
                    color = TextOnDark,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Slides in when its moment comes, as a line does on the real screen. */
@Composable
private fun ColumnScope.Show(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + expandVertically(tween(400)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(250)),
    ) {
        content()
    }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Column {
        Text(text = text, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) {
            Text(text = sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A field being typed into: [active] puts the cursor in it. */
@Composable
private fun Field(label: String, value: String, active: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        FieldLabel(text = label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (active) 2.dp else 1.dp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = FieldShape,
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Text(
                text = value + if (active) "|" else "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A name on the left and a figure on the right, with a note under the name. */
@Composable
private fun Line(name: String, value: String, sub: String? = null, valueColor: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Text(text = sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** An amount and the packs it comes to, as the calculator lists a part. */
@Composable
private fun PartLine(name: String, amount: String, packs: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(text = amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (packs != null) {
                Text(text = packs, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A link-like action in the page's own colour — "+ Add room", "Open ↗". */
@Composable
private fun Action(text: String, tapped: Boolean = false, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.tap(tapped).padding(vertical = 4.dp),
    )
}

@Composable
private fun Chips(labels: List<String>, selected: Int) {
    ChipRow(options = labels.mapIndexed { index, label -> ChipOption(label = label, selected = index == selected, onClick = {}) })
}

@Composable
private fun dateText(daysFromToday: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return LocalDate.now().plusDays(daysFromToday).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

/** "Architop® · Coat 1": a coat of the mix, as the calculator names it. */
private fun coat(label: String) = "$ARCHITOP · $label"

// ---- Home ---------------------------------------------------------------------------------------

internal enum class HomeSpot { None, Calc, Warehouse, Week, Today, Tick, AddTask }

@Composable
internal fun HomeMock(spot: HomeSpot = HomeSpot.None, ticked: Boolean = false, litTab: Int = -1) {
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val monday = today.with(DayOfWeek.MONDAY)
    Page(tab = 0, litTab = litTab) {
        Text(
            text = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().lit(spot == HomeSpot.Calc).clip(ChipShape).background(Charcoal)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.home_quick_calculate), style = MaterialTheme.typography.titleMedium, color = TextOnDark)
                Text(
                    text = stringResource(R.string.home_quick_calculate_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextOnDark.copy(alpha = 0.7f),
                )
            }
            Text(text = "›", style = MaterialTheme.typography.titleLarge, color = TextOnDark, modifier = Modifier.tap(spot == HomeSpot.Calc))
        }
        Column(
            modifier = Modifier.fillMaxWidth().lit(spot == HomeSpot.Warehouse).clip(CardShape).background(Charcoal).padding(14.dp),
        ) {
            Text(text = stringResource(R.string.home_check_warehouse), style = MaterialTheme.typography.titleMedium, color = TextOnDark)
            Text(
                text = stringResource(R.string.home_check_warehouse_sub, JOB),
                style = MaterialTheme.typography.labelSmall,
                color = TextOnDark.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ShortOnDark("Marble aggregate Nero Ebano 5/8 mm", "509.4 kg")
            ShortOnDark("Marble aggregate Nero Ebano 8/12 mm", "509.4 kg")
            ShortOnDark("Colour-Mix Antracite", "111.5 kg")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.home_projects), "3", Modifier.weight(1f))
            Stat(stringResource(R.string.home_open_today), "2", Modifier.weight(1f))
            Stat(stringResource(R.string.home_products), "42", Modifier.weight(1f))
        }
        Column(modifier = Modifier.fillMaxWidth().lit(spot == HomeSpot.Week)) {
            Text(
                text = stringResource(R.string.home_week, today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                (0L..6L).forEach { offset ->
                    val day = monday.plusDays(offset)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (day == today) Accent else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (day == today) TextOnDark else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(5.dp).clip(ChipShape).background(Accent))
            Box(modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(5.dp).clip(ChipShape).background(Ok))
        }
        CardFlat(modifier = Modifier.lit(spot == HomeSpot.Today)) {
            TaskLine(stringResource(R.string.guide_mock_task1), JOB, done = ticked, tapped = spot == HomeSpot.Tick)
            TaskLine(stringResource(R.string.guide_mock_task2), JOB, done = false, tapped = false)
            Action(text = "+ " + stringResource(R.string.task_add), tapped = spot == HomeSpot.AddTask, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ShortOnDark(name: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = TextOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.wh_short_by, amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = TextOnDark,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    CardFlat(modifier = modifier, contentPadding = 10.dp) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TaskLine(title: String, job: String, done: Boolean, tapped: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (done) Ok else Color.Transparent)
                .border(2.dp, if (done) Ok else MaterialTheme.colorScheme.outline, CircleShape)
                .tap(tapped),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Text(text = "✓", color = TextOnDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(text = job, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- The calculator and the mixing screen ------------------------------------------------------

internal enum class CalcSpot { None, Product, Area, Mixer, Start }

@Composable
internal fun CalcMock(chosen: Boolean, area: String, parts: Int, mixing: Boolean, spot: CalcSpot = CalcSpot.None) {
    val firstCoat = coat(stringResource(R.string.solution_coat_number, 1))
    Page {
        Title(stringResource(R.string.calc_title))
        Field(
            label = stringResource(R.string.calc_product),
            value = if (chosen) firstCoat else stringResource(R.string.calc_choose_product),
            active = spot == CalcSpot.Product,
            modifier = Modifier.tap(spot == CalcSpot.Product),
        )
        Field(label = stringResource(R.string.calc_area), value = if (area.isEmpty()) "" else "$area m²", active = spot == CalcSpot.Area)
        Show(parts > 0) {
            CardFlat {
                SectionLabel(text = stringResource(R.string.calc_youll_need))
                Show(parts >= 1) { PartLine("Colour Hardener", "85 kg", packCount(4, "bucket")) }
                Show(parts >= 2) { PartLine("Architop® Catalyst", "20.4 kg", packCount(1, "canister")) }
                Show(parts >= 3) { PartLine("Archi-Go", "1.7 kg", packCount(1, "canister")) }
                Show(parts >= 4) { PartLine("Colour Pack-C Beige Grey", "571.2 g", packCount(2, "tub")) }
                Show(parts >= 4) { Line(name = stringResource(R.string.calc_total_mix), value = "107.1 kg") }
            }
        }
        Show(mixing) {
            CardFlat(modifier = Modifier.lit(spot == CalcSpot.Mixer)) {
                SectionLabel(text = stringResource(R.string.calc_mixing))
                Line(name = stringResource(R.string.calc_mixer_size), value = "120 L")
                Text(text = pluralStringResource(R.plurals.calc_mixings, 4, 4), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.calc_to_get_through, "107.1 kg"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Show(mixing) {
            PrimaryButton(
                text = stringResource(R.string.calc_start_mixing),
                onClick = {},
                modifier = Modifier.fillMaxWidth().tap(spot == CalcSpot.Start),
            )
        }
    }
}

internal enum class MixState { Loading, Running, Ready }

@Composable
internal fun MixingMock(batch: Int, of: Int, ring: Float, state: MixState, tapButton: Boolean = false) {
    val shown by animateFloatAsState(ring, tween(900, easing = LinearEasing), label = "timer")
    val ready = state == MixState.Ready
    Page {
        Title(stringResource(R.string.mix_batch_of, batch, of), coat(stringResource(R.string.solution_coat_number, 1)))
        CardFlat {
            SectionLabel(text = stringResource(R.string.mix_goes_in))
            PartLine("Colour Hardener", "21.3 kg")
            PartLine("Architop® Catalyst", "5.1 kg")
            PartLine("Archi-Go", "425 g")
            PartLine("Colour Pack-C Beige Grey", "142.8 g")
        }
        Box(modifier = Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.outlineVariant
            val done = if (ready) Ok else Accent
            Canvas(modifier = Modifier.size(180.dp)) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                drawArc(color = done, startAngle = -90f, sweepAngle = 360f * shown, useCenter = false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val left = ((1f - ring) * 120).toInt()
                Text(text = "%d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = stringResource(if (ready) R.string.mix_ready else R.string.mix_time_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ready) Ok else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (state) {
            MixState.Loading -> PrimaryButton(stringResource(R.string.mix_start), {}, Modifier.fillMaxWidth().tap(tapButton))
            MixState.Running -> GhostButton(stringResource(R.string.mix_stop_early), {}, Modifier.fillMaxWidth())
            MixState.Ready -> PrimaryButton(stringResource(R.string.mix_next_batch), {}, Modifier.fillMaxWidth().tap(tapButton))
        }
    }
}

@Composable
internal fun SummaryMock(recorded: Boolean, tapRecord: Boolean) {
    Page {
        Title(stringResource(R.string.mix_summary), coat(stringResource(R.string.solution_coat_number, 1)))
        CardFlat {
            Line(name = stringResource(R.string.mix_mixings), value = "4")
            Line(name = stringResource(R.string.mix_total_time), value = "38 min")
            Line(name = stringResource(R.string.mix_average_step), value = "2 min")
        }
        CardFlat {
            SectionLabel(text = stringResource(R.string.mix_used))
            PartLine("Colour Hardener", "85 kg")
            PartLine("Architop® Catalyst", "20.4 kg")
            PartLine("Archi-Go", "1.7 kg")
            PartLine("Colour Pack-C Beige Grey", "571.2 g")
        }
        if (recorded) {
            CardFlat(edge = Ok) {
                Text(text = stringResource(R.string.mix_recorded, JOB), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            PrimaryButton(stringResource(R.string.mix_record), {}, Modifier.fillMaxWidth().tap(tapRecord))
            Text(
                text = stringResource(R.string.mix_record_note, JOB),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Products and recipes -----------------------------------------------------------------------

@Composable
internal fun ProductsMock(tab: Int, litRow: Int = -1, tapRow: Int = -1) {
    Page(tab = 2) {
        Title(stringResource(R.string.products_title))
        Chips(listOf(stringResource(R.string.products_tab_products), stringResource(R.string.products_tab_solutions)), tab)
        if (tab == 0) {
            ItemCard(0, litRow, tapRow, "Colour Hardener", "Ideal Work · " + packLabel(25.0, "kg", "bucket"))
            ItemCard(1, litRow, tapRow, "Architop® Catalyst", "Ideal Work · " + packLabel(25.0, "kg", "canister"))
            ItemCard(2, litRow, tapRow, "Lixio® Powder", "Ideal Work · " + packLabel(18.75, "kg", "bag"))
            ItemCard(3, litRow, tapRow, "Primer SN Part A", "Mapei · " + packLabel(16.0, "kg", "bucket"))
            ItemCard(4, litRow, tapRow, "Fibermesh 90 g/m²", packLabel(4.5, "kg", "roll"))
            ItemCard(5, litRow, tapRow, "Colour Pack-C Beige Grey", "Ideal Work · " + packLabel(0.5, "kg", "tub"))
        } else {
            val twoCoats = stringResource(R.string.solution_coat_count, 2)
            ItemCard(0, litRow, tapRow, ARCHITOP, "Ideal Work · $twoCoats", "25:6:0.5")
            ItemCard(1, litRow, tapRow, "Lixio®", "Ideal Work", "25:18.75:6.25")
            ItemCard(2, litRow, tapRow, "Lixio® Plus Neutro / Botticino", "Ideal Work", "25:75:75:50:25:1")
            ItemCard(3, litRow, tapRow, "IdealPU-WB-Easy", "Ideal Work · $twoCoats", "10:2:1.2")
            ItemCard(4, litRow, tapRow, "Primer SN", "Mapei", "80:20")
        }
    }
}

@Composable
private fun ItemCard(index: Int, litRow: Int, tapRow: Int, name: String, sub: String, ratio: String? = null) {
    CardFlat(modifier = Modifier.lit(index == litRow).tap(index == tapRow), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ratio != null) RatioBadge(text = ratio, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
internal fun ProductMock(tapSheet: Boolean) {
    Page(tab = 2) {
        Title("Colour Hardener", "Ideal Work · Architop")
        CardFlat {
            SectionLabel(text = stringResource(R.string.product_how_sold))
            Line(name = stringResource(R.string.product_pack_size), value = packLabel(25.0, "kg", "bucket"))
        }
        CardFlat(modifier = Modifier.lit(tapSheet)) {
            SectionLabel(text = stringResource(R.string.product_sheets))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.product_technical_sheet), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "ARCHITOP_TEC_EN.pdf", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Action(text = stringResource(R.string.product_sheet_open), tapped = tapSheet)
            }
            Line(name = stringResource(R.string.product_safety_sheet), value = stringResource(R.string.product_sheet_none))
        }
    }
}

@Composable
internal fun RecipeMock(coat: Int, colour: Boolean, tapCoat: Int = -1) {
    Page(tab = 2) {
        Title(ARCHITOP, "Ideal Work")
        SectionLabel(text = stringResource(R.string.solution_coats))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1).forEach { n ->
                Text(
                    text = stringResource(R.string.solution_coat_number, n + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (n == coat) TextOnDark else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(ChipShape)
                        .background(if (n == coat) Charcoal else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ChipShape)
                        .tap(n == tapCoat)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        CardFlat {
            SectionLabel(text = stringResource(R.string.solution_whats_in_it))
            if (coat == 0) {
                PartLine("Colour Hardener", "25")
                PartLine("Architop® Catalyst", "6")
                PartLine("Archi-Go", "0.5")
            } else {
                PartLine("Colour Hardener", "25")
                PartLine("Architop® Catalyst", "4")
                PartLine("Archi-Go", "0.33")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                RatioBadge(text = if (coat == 0) "25:6:0.5" else "25:4:2:0.33")
                Text(
                    text = stringResource(R.string.prj_buildup_rate, if (coat == 0) "2520" else "1985") + " " + stringResource(R.string.calc_per_coat),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Show(colour) {
            CardFlat(edge = Accent) {
                SectionLabel(text = stringResource(R.string.calc_colour_additives))
                Line(name = "Colour Pack-C Beige Grey", value = "28 g / 1 kg")
                Text(
                    text = stringResource(R.string.calc_measured_against) + ": Architop® Catalyst",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- A project --------------------------------------------------------------------------------

@Composable
private fun ProjectTabs(selected: Int) {
    Chips(
        listOf(
            stringResource(R.string.tab_overview),
            stringResource(R.string.tab_layout),
            stringResource(R.string.tab_materials),
            stringResource(R.string.tab_tasks),
            stringResource(R.string.tab_notes),
        ),
        selected,
    )
}

@Composable
internal fun NewProjectMock(filled: Int, tapSave: Boolean) {
    Page(tab = 3) {
        Title(stringResource(R.string.projects_new))
        Field(stringResource(R.string.project_name), if (filled >= 1) JOB else "", active = filled == 1)
        Field(stringResource(R.string.project_client_optional), if (filled >= 2) CLIENT else "", active = filled == 2)
        Field(stringResource(R.string.project_site_optional), if (filled >= 3) ADDRESS else "", active = filled == 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.prj_start_date), if (filled >= 4) dateText(7) else "", modifier = Modifier.weight(1f))
            Field(stringResource(R.string.prj_target_finish), if (filled >= 4) dateText(31) else "", modifier = Modifier.weight(1f))
        }
        PrimaryButton(stringResource(R.string.prj_save), {}, Modifier.fillMaxWidth().tap(tapSave))
    }
}

internal enum class LayoutSpot { None, AddRoom, AddCoat, Colour, Mix }

@Composable
internal fun LayoutMock(rooms: Int, coats: Int, colour: Boolean, spot: LayoutSpot) {
    val first = coat(stringResource(R.string.solution_coat_number, 1))
    val second = coat(stringResource(R.string.solution_coat_number, 2))
    val buildUp = listOf(
        "IW-Epoxy Coat primer" to "400",
        "Quartz sand 0.7–1.2 mm" to "2500",
        first to "2520",
        second to "1985",
        "Primer WB Max" to "52.5",
        "IdealPU-WB-Easy" to "50",
    )
    Page(tab = 3) {
        Title(JOB, CLIENT)
        ProjectTabs(1)
        SectionLabel(text = stringResource(R.string.prj_floors_rooms))
        CardFlat {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.guide_mock_floor), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Action(text = stringResource(R.string.prj_add_room), tapped = spot == LayoutSpot.AddRoom)
            }
            Show(rooms >= 1) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Line(name = stringResource(R.string.guide_mock_living), value = "38 m²")
                    buildUp.forEachIndexed { index, (name, rate) ->
                        Show(coats > index) {
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val tinted = colour && (index == 2 || index == 3)
                                    Text(
                                        text = stringResource(R.string.prj_buildup_rate, rate) + if (tinted) " · Tortora" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (tinted) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (index == 2) {
                                    Action(text = stringResource(R.string.prj_set_colour), tapped = spot == LayoutSpot.Colour, modifier = Modifier.padding(end = 10.dp))
                                    Action(text = stringResource(R.string.prj_mix_coat), tapped = spot == LayoutSpot.Mix)
                                }
                            }
                        }
                    }
                    Action(text = stringResource(R.string.prj_add_coat), tapped = spot == LayoutSpot.AddCoat, modifier = Modifier.padding(start = 10.dp, top = 4.dp))
                }
            }
            Show(rooms >= 2) {
                Line(name = stringResource(R.string.guide_mock_kitchen), value = "16 m²", modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
internal fun MaterialsMock(lines: Int, tapPickup: Boolean) {
    val nothing = stringResource(R.string.prj_nothing_to_order)
    val rows = listOf(
        listOf("Colour Hardener", "193.5 kg", "750 kg", nothing),
        listOf("Architop® Catalyst", "39.6 kg", "186 kg", nothing),
        listOf("Nero Ebano 5/8 mm", "559.4 kg", "50 kg", packCount(21, "bag")),
        listOf("Nero Ebano 8/12 mm", "559.4 kg", "50 kg", packCount(21, "bag")),
        listOf("Colour-Mix Antracite", "186.5 kg", "75 kg", packCount(5, "bag")),
    )
    Page(tab = 3) {
        Title(JOB, CLIENT)
        ProjectTabs(2)
        CardFlat {
            Row {
                Text(text = stringResource(R.string.prj_need), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text(text = stringResource(R.string.prj_in_stock), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                Text(text = stringResource(R.string.prj_to_order), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
            }
            rows.forEachIndexed { index, row ->
                Show(lines > index) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(text = row[0], style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = row[1], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(text = row[2], style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.8f))
                        Text(
                            text = row[3],
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (row[3] == nothing) Ok else Danger,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }
            }
        }
        Show(lines >= 5) {
            CardFlat(modifier = Modifier.lit(tapPickup).tap(tapPickup)) {
                Text(text = stringResource(R.string.prj_pickup_list), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.prj_pickup_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal enum class NotesSpot { None, Blueprints, Photos, Note, Report }

@Composable
internal fun JobNotesMock(spot: NotesSpot) {
    Page(tab = 3) {
        Title(JOB, CLIENT)
        ProjectTabs(4)
        Column(modifier = Modifier.lit(spot == NotesSpot.Blueprints)) {
            SectionLabel(text = stringResource(R.string.prj_blueprints, 2))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { Plan() }
            }
        }
        Column(modifier = Modifier.lit(spot == NotesSpot.Photos)) {
            SectionLabel(text = stringResource(R.string.prj_photos, 3))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Photo(Color(0xFF9A948C), Color(0xFFCFC9BF))
                Photo(Color(0xFF2E2E30), Color(0xFF6D6D70))
                Photo(Color(0xFFB9B1A5), Color(0xFFE2DDD4))
            }
        }
        Column(modifier = Modifier.lit(spot == NotesSpot.Note)) {
            SectionLabel(text = stringResource(R.string.prj_notes, 1))
            CardFlat(contentPadding = 12.dp) {
                Text(
                    text = "$OWNER · " + stringResource(R.string.note_by_employer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = stringResource(R.string.guide_mock_note), style = MaterialTheme.typography.bodyMedium)
            }
        }
        PrimaryButton(stringResource(R.string.prj_generate_report), {}, Modifier.fillMaxWidth().tap(spot == NotesSpot.Report))
    }
}

@Composable
private fun Plan() {
    Canvas(modifier = Modifier.width(100.dp).height(70.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F4E8C))) {
        val line = Color.White.copy(alpha = 0.85f)
        val w = size.width
        val h = size.height
        drawRect(color = line, topLeft = Offset(w * 0.1f, h * 0.15f), size = Size(w * 0.8f, h * 0.7f), style = Stroke(2.dp.toPx()))
        drawLine(color = line, start = Offset(w * 0.55f, h * 0.15f), end = Offset(w * 0.55f, h * 0.85f), strokeWidth = 2.dp.toPx())
        drawLine(color = line, start = Offset(w * 0.1f, h * 0.55f), end = Offset(w * 0.55f, h * 0.55f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun Photo(dark: Color, light: Color) {
    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(light, dark))))
}

// ---- The warehouse ----------------------------------------------------------------------------

internal enum class WhSpot { None, Shelf, ToOrder, TapLine }

@Composable
internal fun WarehouseMock(spot: WhSpot) {
    Page(tab = 1) {
        Title(stringResource(R.string.wh_title))
        CardFlat(modifier = Modifier.lit(spot == WhSpot.ToOrder), edge = Danger) {
            SectionLabel(text = stringResource(R.string.wh_to_order))
            Text(text = stringResource(R.string.wh_to_order_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Line("Marble aggregate Nero Ebano 5/8 mm", stringResource(R.string.wh_short_by, "509.4 kg"), valueColor = Danger, modifier = Modifier.tap(spot == WhSpot.TapLine))
            Line("Marble aggregate Nero Ebano 8/12 mm", stringResource(R.string.wh_short_by, "509.4 kg"), valueColor = Danger)
            Line("Colour-Mix Antracite", stringResource(R.string.wh_short_by, "111.5 kg"), valueColor = Danger)
        }
        SectionLabel(text = stringResource(R.string.wh_on_the_shelf))
        CardFlat(modifier = Modifier.lit(spot == WhSpot.Shelf)) {
            ShelfLine("Colour Hardener", "750 kg", "193.5 kg", "556.5 kg")
            ShelfLine("Architop® Catalyst", "186 kg", "39.6 kg", "146.4 kg")
            ShelfLine("Lixio® Powder", "525 kg", "482 kg", "43 kg")
            ShelfLine("Quartz sand 0.7–1.2 mm", "700 kg", "658.3 kg", "41.7 kg")
        }
    }
}

@Composable
private fun ShelfLine(name: String, onHand: String, booked: String, free: String) {
    Line(
        name = name,
        value = stringResource(R.string.wh_in_stock, onHand),
        sub = stringResource(R.string.wh_booked_total) + " " + booked + " · " + stringResource(R.string.wh_free) + " " + free,
    )
}

@Composable
internal fun OrderMock(filled: Int, tapSave: Boolean) {
    Page(tab = 1) {
        Title("Nero Ebano 5/8 mm", stringResource(R.string.wh_short_by, "509.4 kg"))
        CardFlat {
            SectionLabel(text = stringResource(R.string.wh_mark_ordered))
            Field(stringResource(R.string.wh_order_packs_field, packsName("bag")), if (filled >= 1) "21" else "", active = filled == 1)
            Field(stringResource(R.string.wh_order_due), if (filled >= 2) dateText(6) else "", active = filled == 2, modifier = Modifier.padding(top = 8.dp))
            Field(stringResource(R.string.wh_order_note), if (filled >= 3) "Ideal Work Finland · IW-26-0431" else "", active = filled == 3, modifier = Modifier.padding(top = 8.dp))
        }
        PrimaryButton(stringResource(R.string.wh_order_save), {}, Modifier.fillMaxWidth().tap(tapSave))
    }
}

@Composable
internal fun ArrivedMock(asked: Boolean, arrived: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        Page(tab = 1) {
            Title(stringResource(R.string.wh_title))
            SectionLabel(text = stringResource(R.string.wh_on_order_title, if (arrived) 0 else 1))
            if (arrived) {
                CardFlat(edge = Ok) {
                    Line("Marble aggregate Nero Ebano 5/8 mm", stringResource(R.string.wh_in_stock, "575 kg"))
                }
            } else {
                CardFlat(modifier = Modifier.lit(!asked).tap(!asked)) {
                    Line("Marble aggregate Nero Ebano 5/8 mm", packCount(21, "bag"))
                    Text(text = stringResource(R.string.wh_order_expected, dateText(0)), style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = stringResource(R.string.wh_order_marked_by, dateText(-6), OWNER),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (asked && !arrived) {
            Popup {
                Text(text = stringResource(R.string.wh_arrived_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.wh_arrived_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                PrimaryButton(stringResource(R.string.wh_arrived_yes), {}, Modifier.fillMaxWidth().tap(true))
                GhostButton(stringResource(R.string.wh_arrived_not_yet), {}, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

/** A dialog drawn inside the picture: a real one would open over the guide itself. */
@Composable
private fun Popup(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface).padding(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun CountMock(packs: Int, counted: Int, tapMore: Boolean) {
    Page(tab = 1) {
        Title(stringResource(R.string.sc_title), stringResource(R.string.sc_progress, counted, 41))
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(ChipShape).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(modifier = Modifier.fillMaxWidth(counted / 41f).height(6.dp).clip(ChipShape).background(Accent))
        }
        CardFlat(modifier = Modifier.lit(true)) {
            Text(text = "Colour Hardener", style = MaterialTheme.typography.titleMedium)
            Text(text = "Ideal Work", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.sc_full_packs, packsName("bucket")), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StepperBox("−", false)
                Text(text = packs.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp))
                StepperBox("+", tapMore)
            }
            Field(stringResource(R.string.sc_open_pack, "kg"), "11", modifier = Modifier.padding(top = 10.dp))
            GhostButton(stringResource(R.string.sc_same), {}, Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        CardFlat {
            Text(text = "Architop® Catalyst", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepperBox(sign: String, tapped: Boolean) {
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant).tap(tapped),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = sign, style = MaterialTheme.typography.titleLarge)
    }
}

// ---- Tasks ------------------------------------------------------------------------------------

@Composable
internal fun TaskMock(filled: Int, tapSave: Boolean) {
    Page(tab = 0) {
        Title(stringResource(R.string.task_new))
        Field(stringResource(R.string.task_what), if (filled >= 1) stringResource(R.string.guide_mock_task1) else "", active = filled == 1)
        Column {
            FieldLabel(text = stringResource(R.string.task_due))
            Chips(
                listOf(stringResource(R.string.task_today), stringResource(R.string.task_tomorrow), stringResource(R.string.task_next_monday)),
                if (filled >= 2) 1 else -1,
            )
        }
        Column {
            FieldLabel(text = stringResource(R.string.task_priority))
            Chips(
                listOf(stringResource(R.string.priority_high), stringResource(R.string.priority_medium), stringResource(R.string.priority_low)),
                if (filled >= 3) 0 else 1,
            )
        }
        Field(stringResource(R.string.task_project), if (filled >= 4) JOB else stringResource(R.string.task_none_project))
        PrimaryButton(stringResource(R.string.task_save), {}, Modifier.fillMaxWidth().tap(tapSave))
    }
}

// ---- The company ------------------------------------------------------------------------------

/** Two phones in one company, and a change going from one to the other. */
@Composable
internal fun SyncMock(step: Int) {
    Page {
        Title(stringResource(R.string.co_title))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniPhone(OWNER, fresh = step >= 1)
            Travel(moving = step >= 2)
            MiniPhone(PERSON, fresh = step >= 3)
        }
    }
}

@Composable
private fun MiniPhone(name: String, fresh: Boolean) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .height(250.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(3.dp, Charcoal, RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Show(fresh) {
            Text(
                text = stringResource(R.string.guide_mock_task2),
                style = MaterialTheme.typography.labelSmall,
                color = TextOnDark,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Accent).padding(6.dp),
            )
        }
        repeat(4) { row ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (row % 2 == 0) 1f else 0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun Travel(moving: Boolean) {
    val go = rememberInfiniteTransition(label = "travel").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dot",
    )
    val line = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.width(50.dp).height(24.dp)) {
        val y = size.height / 2
        drawLine(color = line, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 2.dp.toPx())
        if (moving) drawCircle(color = Accent, radius = 6.dp.toPx(), center = Offset(size.width * go.value, y))
    }
}

@Composable
internal fun PersonMock(perms: Int, codeShown: Boolean, tapShare: Boolean) {
    val names = listOf(R.string.co_perm_catalogue, R.string.co_perm_projects, R.string.co_perm_warehouse, R.string.co_perm_site)
    Page {
        Title(stringResource(R.string.co_add_person))
        CardFlat {
            Text(text = PERSON, style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.co_role_worker), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CardFlat {
            names.forEachIndexed { index, name ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = perms > index, onCheckedChange = null, modifier = Modifier.tap(perms == index + 1))
                }
            }
        }
        Show(codeShown) {
            CardFlat(edge = Accent) {
                Text(text = stringResource(R.string.co_code_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = CODE, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            }
        }
        Show(codeShown) {
            PrimaryButton(stringResource(R.string.co_share), {}, Modifier.fillMaxWidth().tap(tapShare))
        }
    }
}

@Composable
internal fun JoinMock(tapJoin: Boolean) {
    Page {
        Title(stringResource(R.string.co_title), PERSON)
        CardFlat {
            SectionLabel(text = stringResource(R.string.co_join_title))
            Text(text = stringResource(R.string.co_join_body), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(stringResource(R.string.co_code_label), CODE, active = !tapJoin, modifier = Modifier.padding(top = 10.dp))
            PrimaryButton(stringResource(R.string.co_join), {}, Modifier.fillMaxWidth().padding(top = 12.dp).tap(tapJoin))
        }
    }
}

@Composable
internal fun RemoveMock(step: Int) {
    Page {
        Title(stringResource(R.string.co_title))
        SectionLabel(text = stringResource(R.string.co_people, 2))
        CardFlat {
            Line(name = OWNER, value = stringResource(R.string.co_role_owner))
            Line(name = PERSON, value = stringResource(R.string.co_role_worker), modifier = Modifier.lit(step >= 1))
        }
        Show(step >= 1) {
            Action(text = stringResource(R.string.co_remove), tapped = step == 1, color = Danger)
        }
        Show(step >= 2) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .height(250.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Charcoal)
                        .border(3.dp, Color.Black, RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null, tint = TextOnDark, modifier = Modifier.size(40.dp))
                    Text(text = stringResource(R.string.break_time_title), style = MaterialTheme.typography.titleMedium, color = TextOnDark)
                    Text(text = PERSON, style = MaterialTheme.typography.labelSmall, color = TextOnDark.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ---- Settings ---------------------------------------------------------------------------------

internal enum class SettingsSpot { None, Backup, Lock, Look, Demo, Empty }

@Composable
internal fun SettingsMock(spot: SettingsSpot) {
    val locale = LocalConfiguration.current.locales[0]
    Page(tab = 4) {
        Title(stringResource(R.string.nav_settings))
        CardFlat(modifier = Modifier.lit(spot == SettingsSpot.Backup), contentPadding = 12.dp) {
            SectionLabel(text = stringResource(R.string.settings_backup))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(stringResource(R.string.settings_export), {}, Modifier.weight(1f).tap(spot == SettingsSpot.Backup))
                GhostButton(stringResource(R.string.settings_restore), {}, Modifier.weight(1f))
            }
        }
        CardFlat(modifier = Modifier.lit(spot == SettingsSpot.Lock), contentPadding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_lock), style = MaterialTheme.typography.titleSmall)
                    Text(text = stringResource(R.string.settings_lock_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = spot == SettingsSpot.Lock, onCheckedChange = null, modifier = Modifier.tap(spot == SettingsSpot.Lock))
            }
        }
        CardFlat(modifier = Modifier.lit(spot == SettingsSpot.Look), contentPadding = 12.dp) {
            Text(text = stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            Chips(
                listOf("English", "Eesti", "Suomi"),
                when (locale.language) {
                    "et" -> 1
                    "fi" -> 2
                    else -> 0
                },
            )
            Text(text = stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            Chips(listOf(stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_auto)), 0)
        }
        CardFlat(modifier = Modifier.lit(spot == SettingsSpot.Demo), contentPadding = 12.dp) {
            SectionLabel(text = stringResource(R.string.settings_demo))
            Action(text = stringResource(R.string.settings_demo_load), tapped = spot == SettingsSpot.Demo)
        }
        CardFlat(modifier = Modifier.lit(spot == SettingsSpot.Empty), contentPadding = 12.dp) {
            SectionLabel(text = stringResource(R.string.settings_empty))
            Action(text = stringResource(R.string.settings_empty_action), tapped = spot == SettingsSpot.Empty, color = Danger)
        }
    }
}

/** Empty all's question, with the word being typed into it letter by letter. */
@Composable
internal fun EmptyMock(stage: Int) {
    val word = stringResource(R.string.settings_empty_word)
    val typed = when (stage) {
        0 -> ""
        1 -> word.take((word.length + 1) / 2)
        else -> word
    }
    val armed = typed == word
    Box(modifier = Modifier.fillMaxSize()) {
        SettingsMock(SettingsSpot.None)
        Popup {
            Text(text = stringResource(R.string.settings_empty_confirm_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.settings_empty_confirm_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            Field(stringResource(R.string.confirm_type_word, word), typed, active = !armed)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                Action(text = stringResource(R.string.action_cancel), modifier = Modifier.padding(end = 18.dp))
                Action(
                    text = stringResource(R.string.settings_empty_confirm),
                    tapped = armed && stage >= 3,
                    color = if (armed) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Break time --------------------------------------------------------------------------------

/** Pour Day's title card: a fresh slab under an evening sky, with a trowel's shine going over it. */
@Composable
internal fun PourDayMock() {
    val sheen = rememberInfiniteTransition(label = "slab").animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sheen",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF4A6FA5), Color(0xFFF2B179)), endY = size.height * 0.55f))
            drawRect(color = Color(0xFF6E6A63), topLeft = Offset(0f, size.height * 0.55f), size = Size(size.width, size.height * 0.45f))
            val slabTop = size.height * 0.62f
            drawRect(color = Color(0xFFA7A39B), topLeft = Offset(size.width * 0.08f, slabTop), size = Size(size.width * 0.84f, size.height * 0.25f))
            val x = size.width * sheen.value
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                    startX = x - size.width * 0.15f,
                    endX = x + size.width * 0.15f,
                ),
                topLeft = Offset(size.width * 0.08f, slabTop),
                size = Size(size.width * 0.84f, size.height * 0.25f),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 90.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            Text(text = stringResource(R.string.break_time_title), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                text = stringResource(R.string.break_time_note),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
