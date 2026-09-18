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
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.formatKg
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.BrandPill
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.RatioBadge
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.Stepper
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.CardShape

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
        factory = viewModelFactory { initializer { CalculatorViewModel(container.productRepository) } },
    )
    val allProducts by viewModel.products.collectAsState()
    val state by viewModel.uiState.collectAsState()

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
            ChipRow(
                options = brands.map { brand ->
                    ChipOption(label = brand, selected = brand == brandFilter, onClick = { brandFilter = brand })
                },
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
                                .clickable { uriHandler.openUri(product.datasheetUrl) },
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
                CardFlat {
                    SectionLabel(text = "Coverage rate (${perLabel(product.dosingMode)})")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatDecimal(state.coverageValue, 0),
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
                            value = state.coverageValue.toFloat(),
                            onValueChange = { viewModel.setCoverage(it.toDouble()) },
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

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(
                        text = "Log actual usage",
                        onClick = { viewModel.logUsage { loggedToast = true } },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Save to project",
                        onClick = { navController.navigate(Routes.PROJECTS) },
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
