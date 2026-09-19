package com.conwic.mixmaster.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.conwic.mixmaster.domain.BatchBasis
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.formatKg
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.BrandPill
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.Stepper
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.navigation.navigateToTopLevel
import com.conwic.mixmaster.ui.theme.CardShape
import java.time.LocalDate
import kotlin.random.Random
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.domain.toNumberOrNull

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
fun CalculatorScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val uriHandler = LocalUriHandler.current
    val viewModel: CalculatorViewModel = viewModel(
        factory = viewModelFactory { initializer { CalculatorViewModel(container.productRepository, container.userPrefs) } },
    )
    val allProducts by viewModel.products.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val showMixingReminders by viewModel.showMixingReminders.collectAsState()

    val today = remember { LocalDate.now() }
    var brandFilter by remember { mutableStateOf("All") }
    var loggedToast by remember { mutableStateOf(false) }

    val brands = listOf("All") + allProducts.map { it.brand }.distinct().sorted()
    val visibleProducts = allProducts.filter { brandFilter == "All" || it.brand == brandFilter }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(text = stringResource(R.string.calc_title), style = MaterialTheme.typography.headlineLarge) }

        item {
            // Same reason as the Products screen: a row of brand chips runs out of room as the
            // catalogue grows, and whatever is off the right edge may as well not exist.
            DropdownField(
                label = stringResource(R.string.filter_brand),
                selected = brandFilter,
                options = brands,
                onSelect = { brandFilter = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            DropdownField(
                label = stringResource(R.string.calc_product),
                selected = state.selectedProduct?.product?.let { productLabel(it.brand, it.name) } ?: stringResource(R.string.calc_choose_product),
                options = visibleProducts.map { productLabel(it.brand, it.name) },
                onSelect = { label ->
                    visibleProducts.firstOrNull { productLabel(it.brand, it.name) == label }?.let { viewModel.selectProduct(it.id) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val data = state.selectedProduct
        val product = data?.product
        if (product != null) {
            item {
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BrandPill(text = product.brand)
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        RatioBadge(text = product.ratioLabel)
                    }
                    Text(
                        text = stringResource(R.string.calc_datasheet_typical_full, formatDecimal(product.typicalDoseGramsPerM2, 0), perLabel(product.dosingMode)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (product.datasheetUrl.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.calc_view_datasheet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .tappableText { uriHandler.openUri(product.datasheetUrl) },
                        )
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
                val shownCoverage = dragCoverage?.toDouble() ?: state.coverageValue

                CardFlat {
                    SectionLabel(text = stringResource(R.string.calc_coverage_rate, perLabel(product.dosingMode)))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatDecimal(shownCoverage, 0),
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
                        Slider(
                            value = shownCoverage.toFloat(),
                            onValueChange = { dragCoverage = it },
                            onValueChangeFinished = { dragCoverage?.let { viewModel.setCoverage(it.toDouble()) } },
                            valueRange = product.minDoseGramsPerM2.toFloat()..product.maxDoseGramsPerM2.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = stringResource(R.string.calc_datasheet_typical, formatDecimal(product.typicalDoseGramsPerM2, 0)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.siteAverageDose
                                    ?.let { stringResource(R.string.calc_your_average, formatDecimal(it, 0)) }
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
                    val parts = data.components.getOrNull(index)?.ratioParts
                    val pack = state.packNeeds.getOrNull(index)
                    CardFlat {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = amount.label, style = MaterialTheme.typography.titleLarge)
                                if (parts != null) {
                                    Text(
                                        text = stringResource(R.string.calc_ratio_parts, formatDecimal(parts, 2)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = formatKg(amount.grams), style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    text = "kg",
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
                                        stringResource(R.string.calc_packs, pack.packs, pack.packLabel) +
                                            (pack.amountInPackUnit?.takeIf { pack.packUnit == "L" }
                                                ?.let { " · ${formatDecimal(it, 1)} L" } ?: "")
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
                        text = stringResource(R.string.calc_total_mix),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatKg(result.totalGrams),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "kg",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                }
            }

            if (state.availableAddOns.isNotEmpty()) {
                item { SectionLabel(text = stringResource(R.string.calc_colour_additives), modifier = Modifier.padding(top = 4.dp)) }
                item {
                    val unchosen = state.availableAddOns.filter { addOn ->
                        state.addOnNeeds.none { it.productId == addOn.id }
                    }
                    CardFlat {
                        if (unchosen.isEmpty()) {
                            Text(
                                text = stringResource(R.string.calc_addons_all_used),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            DropdownField(
                                label = stringResource(R.string.calc_add_to_mix),
                                selected = stringResource(R.string.calc_choose_addon),
                                options = unchosen.map { "${it.brand} — ${it.name}" },
                                onSelect = { chosen ->
                                    unchosen.firstOrNull { "${it.brand} — ${it.name}" == chosen }
                                        ?.let { viewModel.addAddOn(it.id) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(R.string.calc_addon_brand_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                items(state.addOnNeeds, key = { it.productId }) { need ->
                    CardFlat {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(text = need.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(text = need.name, style = MaterialTheme.typography.titleLarge)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (need.unit == "L") {
                                        formatDecimal(need.amount, 2)
                                    } else {
                                        formatKg(need.amount * 1000)
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Text(text = need.unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownField(
                            label = stringResource(R.string.calc_measured_against),
                            selected = need.againstLabel,
                            options = data.components.map { it.label },
                            onSelect = { label ->
                                val index = data.components.indexOfFirst { it.label == label }
                                if (index >= 0) viewModel.setAddOnPart(need.productId, index)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                        Text(
                            text = stringResource(R.string.calc_addon_rate, need.rateLabel, formatKg(need.againstKg * 1000)),
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
                        need.problem?.let { problem ->
                            Text(
                                text = problem,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.action_remove),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .tappableText { viewModel.removeAddOn(need.productId) },
                        )
                    }
                }
            }

            item { SectionLabel(text = stringResource(R.string.calc_mixing), modifier = Modifier.padding(top = 4.dp)) }

            if (state.implausibleDensities.isNotEmpty()) {
                item {
                    CardFlat {
                        // Worded here rather than in the view model: the subject inflects with
                        // how many there are, and neither is known where the check runs.
                        val named = state.implausibleDensities.joinToString(", ") {
                            stringResource(R.string.stored_density_item, it.label, it.densityKgPerL)
                        }
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
                CardFlat {
                    ChipRow(
                        options = listOf(
                            BatchBasis.ONE_PACKAGE to R.string.calc_by_the_bag,
                            BatchBasis.MIXER_VOLUME to R.string.calc_mixer_size,
                            BatchBasis.MAX_WEIGHT to R.string.calc_max_kg,
                        ).map { (basis, labelRes) ->
                            ChipOption(
                                label = stringResource(labelRes),
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
                                Text(text = stringResource(R.string.calc_mixer_bucket), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(text = stringResource(R.string.calc_keep_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(text = stringResource(R.string.calc_max_per_batch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                text = stringResource(R.string.calc_by_bag_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }

                    val plan = state.batchPlan
                    if (plan?.problem != null) {
                        Text(
                            text = plan.problem,
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
                            text = stringResource(R.string.calc_to_get_through, formatKg(result.totalGrams)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (plan.remainderBatch != null && plan.batches > 0) {
                                stringResource(R.string.calc_batches_split, plan.batches, plan.batchSizeLabel)
                            } else {
                                plan.batchSizeLabel
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
                        if (plan.batches > 0) {
                            if (plan.remainderBatch != null) {
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
                                    Text(text = part.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "${formatKg(part.grams)} kg", style = MaterialTheme.typography.titleMedium)
                                }
                            }
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
                                    Text(text = part.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "${formatKg(part.grams)} kg", style = MaterialTheme.typography.titleMedium)
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

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = stringResource(R.string.calc_log_usage),
                        onClick = { viewModel.logUsage { loggedToast = true } },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.calc_save_to_project),
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
