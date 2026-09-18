package com.conwic.mixmaster.ui.calculator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.navigation.Routes

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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(text = "Calculator", style = MaterialTheme.typography.headlineMedium) }

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

        val product = state.selectedProduct?.product
        if (product != null) {
            item {
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "${product.brand} · ${product.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(text = product.ratioLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(text = product.rangeNote, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
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
                OutlinedTextField(
                    value = state.areaInput,
                    onValueChange = viewModel::setArea,
                    label = { Text("Area (m²)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (product.dosingMode != DosingMode.POUR) {
                item {
                    val quantityLabel = when (product.dosingMode) {
                        DosingMode.COATS -> "Number of coats"
                        DosingMode.MM -> "Thickness (mm)"
                        else -> "Quantity"
                    }
                    OutlinedTextField(
                        value = state.quantityInput,
                        onValueChange = viewModel::setQuantity,
                        label = { Text(quantityLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                CardFlat {
                    Text(text = "Coverage rate (${perLabel(product.dosingMode)})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${state.coverageValue.toInt()} g/m²",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    if (product.maxDoseGramsPerM2 > product.minDoseGramsPerM2) {
                        Slider(
                            value = state.coverageValue.toFloat(),
                            onValueChange = { viewModel.setCoverage(it.toDouble()) },
                            valueRange = product.minDoseGramsPerM2.toFloat()..product.maxDoseGramsPerM2.toFloat(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "${product.minDoseGramsPerM2.toInt()} g/m²", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "${product.maxDoseGramsPerM2.toInt()} g/m²", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "The datasheet gives a range — nudge it for porous, textured or uneven substrates on the day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        Text(
                            text = "No published range for this product — using the datasheet figure as-is.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val result = state.result
        if (result != null) {
            item {
                Column {
                    SectionLabel(text = "You'll need")
                    CardAccent {
                        Text(text = "Total: ${result.totalKg} kg", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                        result.components.forEach { component ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = component.label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
                                Text(text = "${component.grams / 1000.0} kg", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.logUsage { loggedToast = true } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Log actual usage")
                    }
                    Button(
                        onClick = { navController.navigate(Routes.PROJECTS) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save to project")
                    }
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
