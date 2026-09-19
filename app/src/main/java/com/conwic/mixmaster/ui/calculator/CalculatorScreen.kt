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
import com.conwic.mixmaster.ui.components.DropdownField
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

private fun productLabel(brand: String, name: String) = "$brand — $name"

private fun perLabel(mode: DosingMode?): String = when (mode) {
    DosingMode.COATS -> "per coat"
    DosingMode.POUR -> "per pour"
    DosingMode.MM -> "per mm"
    null -> ""
}

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
        item { Text(text = "Calculator", style = MaterialTheme.typography.headlineLarge) }

        item {
            // Same reason as the Products screen: a row of brand chips runs out of room as the
            // catalogue grows, and whatever is off the right edge may as well not exist.
            DropdownField(
                label = "Brand",
                selected = brandFilter,
                options = brands,
                onSelect = { brandFilter = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            DropdownField(
                label = "Product",
                selected = state.selectedProduct?.product?.let { productLabel(it.brand, it.name) } ?: "Choose a product",
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
                        text = "Datasheet typical: ${formatDecimal(product.typicalDoseGramsPerM2, 0)} g/m² ${perLabel(product.dosingMode)}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (product.datasheetUrl.isNotBlank()) {
                        Text(
                            text = "View official datasheet ↗",
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
                        SectionLabel(text = "Area to cover")
                        Text(
                            text = state.areaInput.toDoubleOrNull()?.let { "${formatArea(it)} m²" } ?: "—",
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
                            text = if (isThickness) "Thickness (mm)" else "Coats",
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
                    SectionLabel(text = "Coverage rate (${perLabel(product.dosingMode)})")
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
                                text = "Datasheet typical: ${formatDecimal(product.typicalDoseGramsPerM2, 0)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = state.siteAverageDose
                                    ?.let { "Your average: ${formatDecimal(it, 0)}" }
                                    ?: "No logged jobs yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "The datasheet gives a range — nudge it for porous, textured or uneven substrates on the day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        Text(
                            text = "No published range for this product — using the datasheet figure as-is.",
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
            item { SectionLabel(text = "You'll need") }

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
                                        text = "ratio ${formatDecimal(parts, 2)} parts",
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
                                        "${pack.packs} × ${pack.packLabel}" +
                                            (pack.amountInPackUnit?.takeIf { pack.packUnit == "L" }
                                                ?.let { " · ${formatDecimal(it, 1)} L" } ?: "")
                                    pack.packSize > 0.0 && pack.packUnit == "L" ->
                                        "Add a density (kg/L) to count ${pack.packType}s"
                                    else -> "Set a pack size to count ${pack.packType}s"
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
                        text = "Total mix",
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

            item { SectionLabel(text = "Mixing", modifier = Modifier.padding(top = 4.dp)) }

            state.densityWarning?.let { warning ->
                item {
                    CardFlat {
                        Text(
                            text = warning,
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
                            BatchBasis.ONE_PACKAGE to "By the bag",
                            BatchBasis.MIXER_VOLUME to "Mixer size",
                            BatchBasis.MAX_WEIGHT to "Max kg",
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
                                Text(text = "Mixer / bucket", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${formatDecimal(state.mixerLitres, 0)} L", style = MaterialTheme.typography.titleMedium)
                            }
                            Stepper(
                                value = formatDecimal(state.mixerLitres, 0),
                                onValueChange = { viewModel.setMixerLitres(it.toDoubleOrNull() ?: 0.0) },
                                step = 5.0,
                                minValue = 5.0,
                                decimals = 0,
                                suffix = "L",
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = "Keep empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${formatDecimal(state.headroomPercent, 0)}% · fill to ${formatDecimal(state.usableLitres, 1)} L",
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
                                text = "The mix is split so every batch fits the drum. \"Keep empty\" is the room " +
                                    "left for it to turn over — around 40% is the usual minimum, less and it " +
                                    "climbs out. Working in litres needs a density on each part of the product.",
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
                                Text(text = "Max per batch", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "${formatDecimal(state.maxBatchKg, 1)} kg", style = MaterialTheme.typography.titleMedium)
                            }
                            Stepper(
                                value = formatDecimal(state.maxBatchKg, 1),
                                onValueChange = { viewModel.setMaxBatchKg(it.toDoubleOrNull() ?: 0.0) },
                                step = 5.0,
                                minValue = 1.0,
                                decimals = 1,
                                suffix = "kg",
                            )
                            Text(
                                text = "The mix is split so no batch weighs more than this — set it by what the " +
                                    "mixer is rated for, or by what one person should be lifting.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        BatchBasis.ONE_PACKAGE -> {
                            Text(
                                text = "One whole bag or bucket per batch — nothing to weigh out on site. " +
                                    "The liquid is scaled to match it, and whatever is left over at the end " +
                                    "is shown as its own smaller batch.",
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
                            text = if (plan.totalMixes == 1) "1 mixing" else "${plan.totalMixes} mixings",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        Text(
                            text = "to get through ${formatKg(result.totalGrams)} kg of mixed material",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (plan.remainderBatch != null && plan.batches > 0) {
                                "${plan.batches} full + 1 part · ${plan.batchSizeLabel}"
                            } else {
                                plan.batchSizeLabel
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (plan.overflows && plan.perBatchLitres != null) {
                            Text(
                                text = "Too big for this mixer — ${formatDecimal(plan.perBatchLitres, 1)} L " +
                                    "in a drum you only want filled to ${formatDecimal(state.usableLitres, 1)} L. " +
                                    "Use a bigger mixer or split the batch.",
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
                                    "Leaves $spare L spare — the drum holds ${formatDecimal(state.mixerLitres, 0)} L " +
                                        "and you're filling it to ${formatDecimal(state.usableLitres, 1)} L."
                                } else {
                                    "That's ${formatDecimal(plan.perBatchLitres, 1)} L in the drum, which leaves " +
                                        "$spare L of the ${formatDecimal(state.usableLitres, 1)} L you can use."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (plan.batches > 0) {
                            if (plan.remainderBatch != null) {
                                Text(
                                    text = "Each full batch:",
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
                                text = if (plan.batches > 0) "Then one part batch:" else "One part batch:",
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
                        text = "Log actual usage",
                        onClick = { viewModel.logUsage { loggedToast = true } },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Save to project",
                        onClick = { navController.navigateToTopLevel(Routes.PROJECTS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (loggedToast) {
                item {
                    CardFlat {
                        Text(
                            text = "Logged — this will feed the product's site average.",
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
 * stays put while the screen is open but isn't the same line forever.
 */
private val mixingReminders = listOf(
    "Leave room to move — a full bucket mixes nothing but the floor.",
    "Fill it to the brim and you'll mop the difference.",
    "The paddle needs air. Give it some.",
    "Empty space isn't wasted — that's where the mixing happens.",
    "A bucket filled to the top is a bucket on the floor.",
    "Room at the top, or dust everywhere.",
    "If it looks full before you start, it's already too late.",
    "Mix needs headroom. So do you.",
)

private fun mixingReminder(date: LocalDate): String =
    mixingReminders[Random(date.toEpochDay()).nextInt(mixingReminders.size)]
