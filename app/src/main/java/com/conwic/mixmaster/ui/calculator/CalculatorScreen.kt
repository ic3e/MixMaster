package com.conwic.mixmaster.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.ChipRow
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.prefs.MixRunStore
import com.conwic.mixmaster.data.prefs.SavedMixRun
import com.conwic.mixmaster.domain.BatchProblem
import com.conwic.mixmaster.domain.BatchSize
import com.conwic.mixmaster.domain.BatchBasis
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.data.db.entity.coatLabel
import com.conwic.mixmaster.data.db.entity.familyId
import com.conwic.mixmaster.domain.doseDecimals
import com.conwic.mixmaster.domain.doseStep
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.snapDose
import com.conwic.mixmaster.domain.snapToDoseStep
import com.conwic.mixmaster.domain.openableUrl
import com.conwic.mixmaster.domain.mixingSteps
import com.conwic.mixmaster.domain.quantityFromGrams
import com.conwic.mixmaster.domain.quantityFromLitres
import com.conwic.mixmaster.domain.quantityOf
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FieldWeightWide
import com.conwic.mixmaster.ui.components.FieldWeightNarrow
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.ProductIdentity
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.Stepper
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.navigation.navigateToTopLevel
import com.conwic.mixmaster.ui.theme.CardShape
import java.time.LocalDate
import kotlin.random.Random
import com.conwic.mixmaster.domain.toNumberOrNull
import com.conwic.mixmaster.ui.components.packName
import com.conwic.mixmaster.ui.components.packLabel
import com.conwic.mixmaster.domain.batchPartIndex
import com.conwic.mixmaster.ui.components.byThePack
import com.conwic.mixmaster.ui.components.onePack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import com.conwic.mixmaster.ui.theme.FieldShape

private fun productLabel(brand: String, name: String) = "$brand — $name"

@StringRes
private fun perLabelRes(mode: DosingMode?): Int? = when (mode) {
    DosingMode.COATS -> R.string.calc_per_coat
    DosingMode.POUR -> R.string.calc_per_pour
    DosingMode.MM -> R.string.calc_per_mm
    null -> null
}

/** The "per coat" / "per pour" wording, empty when the product has no mode yet. */
@Composable
private fun perLabel(mode: DosingMode?): String =
    perLabelRes(mode)?.let { stringResource(it) } ?: ""

@Composable
fun CalculatorScreen(
    navController: NavHostController,
    solutionId: Long = 0L,
    handover: CoatHandover = CoatHandover.None,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val viewModel: CalculatorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CalculatorViewModel(
                    container.solutionRepository,
                    container.productRepository,
                    container.projectRepository,
                    container.userPrefs,
                    solutionId,
                    handover,
                )
            }
        },
    )
    val allSolutions by viewModel.solutions.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val showMixingReminders by viewModel.showMixingReminders.collectAsState()
    val mixesById by viewModel.mixesById.collectAsState()
    val jobRooms by viewModel.jobRooms.collectAsState()
    var jobPickOpen by remember { mutableStateOf(false) }
    // Which job the figures on screen belong to: handed in by whoever opened the screen, or
    // chosen here. Kept so it survives the rotation that happens when the phone is put down.
    var job by rememberSaveable { mutableStateOf(handover.jobLabel) }
    // Kept alongside the words: what gets mixed is written back against these.
    var jobProjectId by rememberSaveable { mutableStateOf(handover.projectId) }
    var jobRoomId by rememberSaveable { mutableStateOf(handover.roomId) }

    val today = remember { LocalDate.now() }
    var brandFilter by remember { mutableStateOf("All") }
    var loggedToast by remember { mutableStateOf(false) }

    val brands = listOf("All") + allSolutions.map { it.brand }.filter { it.isNotBlank() }.distinct().sorted()
    // The dropdown lists mixes, not coats: a second coat is a recipe of the same mix, and
    // picking between them is the next question, not part of the same one.
    val visibleSolutions = allSolutions
        .filter { it.parentId == 0L }
        .filter { brandFilter == "All" || it.brand == brandFilter }
    // Every coat of whatever is selected, first coat first.
    val chosen = state.selectedSolution?.solution
    val coats = chosen?.let { current ->
        allSolutions.filter { it.familyId == current.familyId }.sortedBy { it.id }
    }.orEmpty()
    val head = coats.firstOrNull { it.parentId == 0L } ?: chosen

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Opened from a product now rather than from the bottom bar, so it needs its own way
        // back to the one you came from.
        item {
            MixMasterTopBar(
                title = stringResource(R.string.calc_title),
                onBack = { navController.popBackStack() },
            )
        }

        // Which room's job this is. Said out loud because the fields below came up filled in,
        // and the figures in them are that room's rather than the last job's.
        if (job.isNotBlank()) {
            item {
                CardFlat {
                    Text(
                        text = stringResource(R.string.calc_for_job, job),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.calc_for_job_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            // Side by side, as on the Products screen. Dropdowns rather than chip rows for the
            // same reason: the lists grow with the catalogue, and whatever is off the right
            // edge may as well not exist. The product gets the wider half — its name is a
            // brand and a product run together, where the brand filter is usually one word.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DropdownField(
                    label = stringResource(R.string.filter_brand),
                    selected = brandFilter,
                    options = brands,
                    onSelect = { brandFilter = it },
                    modifier = Modifier.weight(FieldWeightNarrow),
                )
                DropdownField(
                    label = stringResource(R.string.calc_product),
                    selected = head
                        ?.let { productLabel(it.brand, it.name) }
                        ?: stringResource(R.string.calc_choose_product),
                    options = visibleSolutions.map { productLabel(it.brand, it.name) },
                    onSelect = { label ->
                        visibleSolutions.firstOrNull { productLabel(it.brand, it.name) == label }
                            ?.let { viewModel.selectSolution(it.id) }
                    },
                    modifier = Modifier.weight(FieldWeightWide),
                )
            }
        }

        // The other way in. Until this line the calculator knew nothing about the projects:
        // the area was read off the Layout tab and typed in here, and the coat's rate with it.
        item {
            ActionLink(text = stringResource(R.string.calc_pick_job), onClick = { jobPickOpen = true })
        }

        // Which coat is being mixed. Only asked where a mix has more than one, because most
        // do not, and a question with one answer is a question not worth asking. Each says what
        // goes in it: the coats of one mix often differ only by the water, and "Coat 1" and
        // "Coat 2" alone left that to memory.
        if (coats.size > 1) {
            item {
                val shareOfRest = stringResource(R.string.solution_line_percent)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    coats.forEach { coat ->
                        val parts = mixesById[coat.id]?.parts.orEmpty()
                        CoatChoice(
                            name = coat.coatName.ifBlank { coat.name },
                            ratio = coat.ratioLabel,
                            parts = parts.map { part ->
                                part.label to if (part.percentOfRest > 0.0) {
                                    formatDecimal(part.percentOfRest, 2) + shareOfRest
                                } else {
                                    formatDecimal(part.ratioParts, 2)
                                }
                            },
                            selected = coat.id == chosen?.id,
                            onClick = { viewModel.selectSolution(coat.id) },
                        )
                    }
                }
            }
        }

        val data = state.selectedSolution
        val product = data?.solution
        if (product != null) {
            item {
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        ProductIdentity(
                            name = product.name,
                            brand = product.brand,
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        RatioBadge(text = product.ratioLabel)
                    }
                    Text(
                        text = stringResource(
                            R.string.calc_datasheet_typical_full,
                            formatDecimal(
                                product.typicalDoseGramsPerM2,
                                doseDecimals(doseStep(product.typicalDoseGramsPerM2)),
                            ),
                            perLabel(product.dosingMode),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    // Only offered when it's something the system can actually open — the field is
                    // free text, and handing "test datasheet" to the browser took the app down.
                    val datasheetLink = openableUrl(product.datasheetUrl)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // The card sat there saying what the mix is and gave no way to go and
                        // look at it. A ratio that reads wrong on site is the moment somebody
                        // wants the recipe, and the only way in was back out to Products.
                        ActionLink(
                            text = stringResource(R.string.calc_open_recipe),
                            onClick = { navController.navigate(Routes.solutionEdit(product.id)) },
                        )
                        if (datasheetLink != null) {
                            ActionLink(
                                text = stringResource(R.string.calc_view_datasheet),
                                onClick = { runCatching { uriHandler.openUri(datasheetLink) } },
                            )
                        }
                    }
                }
            }

            item {
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel(text = stringResource(R.string.calc_area))
                        Text(
                            text = state.areaInput.toNumberOrNull()?.let { "${formatArea(it)} m²" } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Stepper(
                        value = state.areaInput,
                        onValueChange = viewModel::setArea,
                        step = 1.0,
                        minValue = 0.0,
                        decimals = 2,
                        suffix = "m²",
                    )

                    if (product.dosingMode != DosingMode.POUR) {
                        val isThickness = product.dosingMode == DosingMode.MM
                        SectionLabel(
                            text = stringResource(if (isThickness) R.string.calc_thickness else R.string.calc_coats),
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Stepper(
                            value = state.quantityInput,
                            onValueChange = viewModel::setQuantity,
                            step = if (isThickness) 0.5 else 1.0,
                            minValue = if (isThickness) 0.5 else 1.0,
                            decimals = if (isThickness) 1 else 0,
                            suffix = if (isThickness) "mm" else "",
                        )
                    }
                }
            }

            item {
                // The drag is tracked locally so the thumb tracks the finger; the mix is only
                // recomputed when the finger lifts.
                var dragCoverage by remember(product.id) { mutableStateOf<Float?>(null) }
                // Snapped to the same step it is shown in, so the rate on screen is the rate the
                // mix below was worked out from. The step comes from the figure being set, so a
                // 2700 g/m² pour moves in fifties and a 6 g/m² primer in tenths.
                val live = dragCoverage?.toDouble() ?: state.coverageValue
                val step = doseStep(live)
                val decimals = doseDecimals(step)
                val typical = product.typicalDoseGramsPerM2
                // The datasheet figure is a stop of its own, even where it does not sit on the
                // step grid: the marker has to land on the number it is pointing at.
                val snapped = if (typical > 0.0 && abs(live - typical) <= step / 2.0) {
                    typical
                } else {
                    snapDose(live, step)
                }
                // Kept inside the range the slider was given: snapping can land a hair past the
                // end of it, and a product with no range at all has nothing to clamp to.
                val shownCoverage = if (product.maxDoseGramsPerM2 > product.minDoseGramsPerM2) {
                    snapped.coerceIn(product.minDoseGramsPerM2, product.maxDoseGramsPerM2)
                } else {
                    snapped
                }

                CardFlat {
                    SectionLabel(text = stringResource(R.string.calc_coverage_rate, perLabel(product.dosingMode)))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatDecimal(shownCoverage, decimals),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            text = "g/m²",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 5.dp, bottom = 3.dp),
                        )
                    }

                    if (product.maxDoseGramsPerM2 > product.minDoseGramsPerM2) {
                        // Where the datasheet says to be, marked on the track and tappable:
                        // once the slider has been nudged for a porous slab, finding the way
                        // back to the recommended figure was a matter of dragging and squinting.
                        if (typical > 0.0) {
                            val span = product.maxDoseGramsPerM2 - product.minDoseGramsPerM2
                            val fraction = ((typical - product.minDoseGramsPerM2) / span)
                                .coerceIn(0.0, 1.0)
                                .toFloat()
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val markerWidth = 56.dp
                                val x = (maxWidth * fraction - markerWidth / 2)
                                    .coerceIn(0.dp, (maxWidth - markerWidth).coerceAtLeast(0.dp))
                                Column(
                                    modifier = Modifier
                                        .width(markerWidth)
                                        .offset(x = x)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            dragCoverage = null
                                            viewModel.setCoverage(typical)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = formatDecimal(typical, doseDecimals(doseStep(typical))),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = stringResource(R.string.calc_back_to_typical),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        Slider(
                            value = shownCoverage.toFloat(),
                            onValueChange = { dragCoverage = it },
                            onValueChangeFinished = {
                                dragCoverage?.let { viewModel.setCoverage(snapToDoseStep(it.toDouble())) }
                            },
                            valueRange = product.minDoseGramsPerM2.toFloat()..product.maxDoseGramsPerM2.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = stringResource(R.string.calc_datasheet_typical, formatDecimal(product.typicalDoseGramsPerM2, decimals)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.siteAverageDose
                                    ?.let { stringResource(R.string.calc_your_average, formatDecimal(it, decimals)) }
                                    ?: stringResource(R.string.calc_no_logged),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = stringResource(R.string.calc_range_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.calc_no_range_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        val result = state.result
        if (result != null && data != null) {
            item { SectionLabel(text = stringResource(R.string.calc_youll_need)) }

            result.components.forEachIndexed { index, amount ->
                item {
                    val part = data.parts.getOrNull(index)
                    val parts = part?.ratioParts
                    val pack = state.packNeeds.getOrNull(index)
                    CardFlat {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = amount.label, style = MaterialTheme.typography.titleLarge)
                                // Said the way the datasheet says it: a part measured off the
                                // others reads as the percentage it was written as, not as the
                                // 1.2 parts that comes to.
                                val ratioLine = when {
                                    part != null && part.percentOfRest > 0.0 ->
                                        stringResource(
                                            R.string.calc_ratio_percent,
                                            formatDecimal(part.percentOfRest, 2),
                                        )
                                    parts != null ->
                                        stringResource(R.string.calc_ratio_parts, formatDecimal(parts, 2))
                                    else -> null
                                }
                                if (ratioLine != null) {
                                    Text(
                                        text = ratioLine,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            val shown = quantityFromGrams(amount.grams)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = shown.amount, style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = shown.unit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                                )
                            }
                        }
                        if (pack != null) {
                            Text(
                                text = when {
                                    pack.packs != null ->
                                        stringResource(R.string.calc_packs, pack.packs, packLabel(pack.packSize, pack.packUnit, pack.packType)) +
                                            (pack.amountInPackUnit?.takeIf { pack.packUnit == "L" }
                                                ?.let { " · " + quantityFromLitres(it).text } ?: "")
                                    pack.packSize > 0.0 && pack.packUnit == "L" ->
                                        stringResource(R.string.calc_add_density_to_count)
                                    else -> stringResource(R.string.calc_set_pack_to_count)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pack.packs != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (pack.packs != null) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, CardShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CardShape)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = stringResource(R.string.calc_total_mix),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val total = quantityFromGrams(result.totalGrams)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = total.amount,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = total.unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                }
            }

            // What the recipe itself carries. Which colour a floor gets is decided on the
            // room, not here — the calculator works out a mix, it does not choose the job.
            if (state.addOnNeeds.isNotEmpty()) {
                item { SectionLabel(text = stringResource(R.string.calc_colour_additives), modifier = Modifier.padding(top = 4.dp)) }
                items(state.addOnNeeds, key = { it.productId }) { need ->
                    CardFlat {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = need.name,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f).padding(end = 10.dp),
                            )
                            val dose = quantityOf(need.amount, need.unit)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = dose.amount, style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = dose.unit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                R.string.calc_addon_rate,
                                stringResource(
                                    R.string.addon_rate_label,
                                    need.rateAmount,
                                    need.rateUnit,
                                    need.againstLabel,
                                ),
                                quantityFromGrams(need.againstKg * 1000).text,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        need.packs?.let { packs ->
                            Text(
                                text = stringResource(R.string.calc_packs, packs, need.packLabel ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            item { SectionLabel(text = stringResource(R.string.calc_mixing), modifier = Modifier.padding(top = 4.dp)) }

            if (state.implausibleDensities.isNotEmpty()) {
                item {
                    CardFlat {
                        // Worded here rather than in the view model: the subject inflects with
                        // how many there are, and neither is known where the check runs.
                        // map is inline so stringResource is still in composable scope here;
                        // joinToString is not, which is why the wording happens first.
                        val named = state.implausibleDensities
                            .map { stringResource(R.string.stored_density_item, it.label, it.densityKgPerL) }
                            .joinToString(", ")
                        val subject = pluralStringResource(
                            R.plurals.stored_density_subject,
                            state.implausibleDensities.size,
                        )
                        Text(
                            text = stringResource(R.string.stored_density_warning, subject, named),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            item {
                // Named for the pack the batch is counted in — "By the bottle" for a mix whose
                // biggest part comes in bottles. "By the bag" said bag whatever it was.
                val batchPack = state.selectedSolution?.parts
                    ?.let { parts -> batchPartIndex(parts)?.let { parts[it].packageType } }
                val byThe = byThePack(batchPack)
                CardFlat {
                    ChipRow(
                        options = listOf(
                            BatchBasis.ONE_PACKAGE to byThe,
                            BatchBasis.MIXER_VOLUME to stringResource(R.string.calc_mixer_size),
                            BatchBasis.MAX_WEIGHT to stringResource(R.string.calc_max_kg),
                        ).map { (basis, label) ->
                            ChipOption(
                                label = label,
                                selected = state.batchBasis == basis,
                                onClick = { viewModel.setBatchBasis(basis) },
                            )
                        },
                    )

                    when (state.batchBasis) {
                        BatchBasis.MIXER_VOLUME -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(modifier = Modifier.weight(1f, fill = false), text = stringResource(R.string.calc_mixer_bucket), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${formatDecimal(state.mixerLitres, 0)} L", style = MaterialTheme.typography.titleMedium)
                            }
                            Stepper(
                                value = formatDecimal(state.mixerLitres, 0),
                                onValueChange = { viewModel.setMixerLitres(it.toNumberOrNull() ?: 0.0) },
                                step = 5.0,
                                minValue = 5.0,
                                decimals = 0,
                                suffix = "L",
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(modifier = Modifier.weight(1f, fill = false), text = stringResource(R.string.calc_keep_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = stringResource(R.string.calc_headroom_value, formatDecimal(state.headroomPercent, 0), formatDecimal(state.usableLitres, 1)),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Slider(
                                value = state.headroomPercent.toFloat(),
                                onValueChange = { viewModel.setHeadroomPercent(it.toDouble()) },
                                valueRange = 10f..70f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                            Text(
                                // The quoted bit is the control's own label, so it stays in step
                                // with the chip above it in whichever language is on.
                                text = stringResource(
                                    R.string.calc_headroom_note,
                                    "\"" + stringResource(R.string.calc_keep_empty) + "\"",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        BatchBasis.MAX_WEIGHT -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(modifier = Modifier.weight(1f, fill = false), text = stringResource(R.string.calc_max_per_batch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${formatDecimal(state.maxBatchKg, 1)} kg", style = MaterialTheme.typography.titleMedium)
                            }
                            Stepper(
                                value = formatDecimal(state.maxBatchKg, 1),
                                onValueChange = { viewModel.setMaxBatchKg(it.toNumberOrNull() ?: 0.0) },
                                step = 5.0,
                                minValue = 1.0,
                                decimals = 1,
                                suffix = "kg",
                            )
                            Text(
                                text = stringResource(R.string.calc_max_batch_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        BatchBasis.ONE_PACKAGE -> {
                            Text(
                                text = stringResource(R.string.calc_by_bag_note, onePack(batchPack)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }

                    val plan = state.batchPlan
                    if (plan?.problem != null) {
                        Text(
                            text = batchProblemText(plan.problem),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else if (plan != null) {
                        Text(
                            text = pluralStringResource(R.plurals.calc_mixings, plan.totalMixes, plan.totalMixes),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            text = stringResource(R.string.calc_to_get_through, quantityFromGrams(result.totalGrams).text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (plan.remainderBatch != null && plan.batches > 0) {
                                stringResource(R.string.calc_batches_split, plan.batches, batchSizeText(plan.batchSize))
                            } else {
                                batchSizeText(plan.batchSize)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (plan.overflows && plan.perBatchLitres != null) {
                            Text(
                                text = stringResource(R.string.calc_too_big, formatDecimal(plan.perBatchLitres, 1), formatDecimal(state.usableLitres, 1)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else if (plan.perBatchLitres != null) {
                            // When the batch size is already given in litres above, repeating it
                            // here just looks like the app disagreeing with itself.
                            val spare = formatDecimal(state.usableLitres - plan.perBatchLitres, 1)
                            Text(
                                text = if (state.batchBasis == BatchBasis.MIXER_VOLUME) {
                                    stringResource(
                                        R.string.calc_spare,
                                        spare,
                                        formatDecimal(state.mixerLitres, 0),
                                        formatDecimal(state.usableLitres, 1),
                                    )
                                } else {
                                    stringResource(
                                        R.string.calc_in_drum,
                                        formatDecimal(plan.perBatchLitres, 1),
                                        spare,
                                        formatDecimal(state.usableLitres, 1),
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        // Which drum all of that was measured against. Both figures are set on
                        // the Mixer size tab, so on the other two the litres arrived from
                        // nowhere — and a red warning about a drum you cannot see the size of
                        // is a warning you have to go and check before you believe it.
                        if (plan.perBatchLitres != null && state.batchBasis != BatchBasis.MIXER_VOLUME) {
                            Text(
                                text = stringResource(
                                    R.string.calc_drum_settings,
                                    formatDecimal(state.mixerLitres, 0),
                                    formatDecimal(state.headroomPercent, 0),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                // The full ink rather than the muted grey the rest of the notes
                                // are set in: this one is a figure to check, not an aside. Taken
                                // from the scheme rather than written as black, so it is still
                                // legible when the phone is in the dark theme.
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (plan.batches > 0) {
                            if (plan.remainderBatch != null || plan.lastBatchExtra != null) {
                                Text(
                                    text = stringResource(R.string.calc_each_full_batch),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                            plan.perBatch.forEach { part ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(modifier = Modifier.weight(1f, fill = false), text = part.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = quantityFromGrams(part.grams).text, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        // Folded in rather than mixed on its own: named so the last drum still
                        // gets everything, without anyone going back to weigh out a few grams.
                        plan.lastBatchExtra?.let { extra ->
                            Text(
                                text = stringResource(R.string.calc_add_to_last),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            extra.forEach { part ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(modifier = Modifier.weight(1f, fill = false), text = part.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = quantityFromGrams(part.grams).text, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Text(
                                text = stringResource(R.string.calc_add_to_last_why),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        plan.remainderBatch?.let { remainder ->
                            Text(
                                text = stringResource(if (plan.batches > 0) R.string.calc_then_part_batch else R.string.calc_one_part_batch),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            remainder.forEach { part ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(modifier = Modifier.weight(1f, fill = false), text = part.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = quantityFromGrams(part.grams).text, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }

                    if (showMixingReminders) {
                        Text(
                            text = mixingReminder(today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }
            }

            // The plan is a list of batches; this walks them, one trip to the mixer at a time.
            val plan = state.batchPlan
            if (plan != null && plan.problem == null && plan.totalMixes > 0) {
                item {
                    // Worked out and worded here, where there is a language to word it in, and
                    // written down whole when mixing starts. From that moment the run belongs to
                    // the app rather than to this screen — see [MixingHost].
                    val batchWords = batchSizeText(plan.batchSize)
                    val run = remember(plan, data, batchWords, job, jobProjectId, jobRoomId) {
                        SavedMixRun(
                            title = data.solution.coatLabel,
                            batchSize = batchWords,
                            mixSeconds = data.solution.mixSeconds,
                            potLifeMinutes = data.solution.potLifeMinutes,
                            parts = data.parts,
                            steps = mixingSteps(plan),
                            // Carried into the run so that when it finishes, what went in the
                            // drum can be written against the job it went on.
                            projectId = jobProjectId,
                            roomId = jobRoomId,
                            solutionId = data.solution.id,
                            jobLabel = job,
                        )
                    }
                    CardAccent(
                        modifier = Modifier
                            .clip(CardShape)
                            .clickable { if (run.steps.isNotEmpty()) MixRunStore.start(context, run) },
                    ) {
                        Text(
                            text = stringResource(R.string.calc_start_mixing),
                            style = MaterialTheme.typography.titleLarge,
                            color = OnAccentCard,
                        )
                        Text(
                            text = stringResource(R.string.calc_start_mixing_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnAccentCard.copy(alpha = 0.85f),
                        )
                        if (data.solution.mixSeconds <= 0) {
                            Text(
                                text = stringResource(R.string.mix_default_time),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnAccentCard.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        // How long the batch stays workable. It is on every datasheet and was
                        // nowhere in the app, which is what makes somebody mix the whole lot at
                        // once and then throw half of it away.
                        if (data.solution.potLifeMinutes > 0) {
                            Text(
                                text = stringResource(R.string.calc_pot_life, data.solution.potLifeMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnAccentCard.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = stringResource(R.string.calc_log_usage),
                        onClick = { viewModel.logUsage { loggedToast = true } },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        // Named for what it does, which is walk you over to the projects. It
                        // said "Save to project" and saved nothing: a coat gets onto a job from
                        // the job's own Layout tab, and what was actually mixed is written back
                        // at the end of a batch.
                        text = stringResource(R.string.calc_open_projects),
                        onClick = { navController.navigateToTopLevel(Routes.PROJECTS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (loggedToast) {
                item {
                    CardFlat {
                        Text(
                            text = stringResource(R.string.calc_logged),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (jobPickOpen) {
        JobPickSheet(
            rooms = jobRooms,
            onPick = { room, coat ->
                viewModel.applyJob(room, coat)
                job = "${room.projectName} · ${room.roomName}"
                jobProjectId = room.projectId
                jobRoomId = room.roomId
                jobPickOpen = false
            },
            onDismiss = { jobPickOpen = false },
        )
    }
}

/**
 * A standing reminder that a drum filled to the brim mixes nothing. Picked from the date so it
 * stays put while the screen is open but isn't the same line forever. The lines live in
 * resources so each language gets its own, and the pools need not be the same length.
 */
@Composable
private fun mixingReminder(date: LocalDate): String {
    val pool = stringArrayResource(R.array.mixing_reminders)
    if (pool.isEmpty()) return ""
    return pool[Random(date.toEpochDay()).nextInt(pool.size)]
}

/**
 * One coat of the mix to choose, with what goes in it — a line a part, its parts or its share
 * of the rest on the right. The one being worked out is filled in and ticked.
 */
@Composable
private fun CoatChoice(
    name: String,
    ratio: String,
    parts: List<Pair<String, String>>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.primary.copy(alpha = 0.55f),
                shape = FieldShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) scheme.onSurface else scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ratio,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
            }
        }
        if (parts.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = scheme.outline)
            parts.forEach { (what, amount) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(
                        text = "• $what",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = amount,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** The batch size in words. Built here because only the screen knows the language. */
@Composable
private fun batchSizeText(size: BatchSize): String = when (size) {
    is BatchSize.Unknown -> stringResource(R.string.batch_none)
    is BatchSize.WholePack -> stringResource(R.string.batch_whole_pack, size.packSizeKg, packName(size.packType))
    is BatchSize.Litres -> stringResource(R.string.batch_litres, size.litres)
    is BatchSize.Weight -> stringResource(R.string.batch_weight, size.weight)
}

/**
 * Why a batch plan couldn't be worked out.
 *
 * The list of parts missing a density is joined here rather than in the domain: where the
 * "and" goes, and whether there is one at all, is a question about the language.
 */
@Composable
private fun batchProblemText(problem: BatchProblem): String = when (problem) {
    is BatchProblem.NoPackWeight -> stringResource(R.string.batch_problem_no_pack_weight)
    is BatchProblem.NoMixerSize -> stringResource(R.string.batch_problem_no_mixer)
    is BatchProblem.NoMaxWeight -> stringResource(R.string.batch_problem_no_max_weight)
    is BatchProblem.NeedsDensity -> {
        val names = problem.missingLabels
        val named = when (names.size) {
            0 -> stringResource(R.string.batch_one_of_the_parts)
            1 -> names.first()
            else -> stringResource(
                R.string.batch_and,
                names.dropLast(1).joinToString(", "),
                names.last(),
            )
        }
        stringResource(R.string.batch_problem_needs_density, named)
    }
}
