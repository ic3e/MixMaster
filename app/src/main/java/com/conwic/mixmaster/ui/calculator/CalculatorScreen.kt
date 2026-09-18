package com.conwic.mixmaster.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.SectionLabel

private fun productLabel(brand: String, name: String) = "$brand — $name"

@Composable
fun CalculatorScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: CalculatorViewModel = viewModel(
        factory = viewModelFactory { initializer { CalculatorViewModel(container.productRepository) } },
    )
    val products by viewModel.products.collectAsState()
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(text = "Calculator", style = MaterialTheme.typography.headlineMedium) }

        item {
            DropdownField(
                label = "Product",
                selected = state.selectedProduct?.product?.let { productLabel(it.brand, it.name) } ?: "Choose a product",
                options = products.map { productLabel(it.brand, it.name) },
                onSelect = { label ->
                    products.firstOrNull { productLabel(it.brand, it.name) == label }?.let { viewModel.selectProduct(it.id) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.selectedProduct != null) {
            item {
                OutlinedTextField(
                    value = state.areaInput,
                    onValueChange = viewModel::setArea,
                    label = { Text("Area (m²)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val quantityLabel = when (state.selectedProduct?.product?.dosingMode) {
                    DosingMode.COATS -> "Number of coats"
                    DosingMode.POUR -> "Number of pours"
                    DosingMode.MM -> "Thickness (mm)"
                    null -> "Quantity"
                }
                OutlinedTextField(
                    value = state.quantityInput,
                    onValueChange = viewModel::setQuantity,
                    label = { Text(quantityLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CardFlat {
                    Text(text = state.selectedProduct?.product?.rangeNote.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    if (state.selectedProduct?.product?.sourceNote?.isNotBlank() == true) {
                        Text(
                            text = state.selectedProduct?.product?.sourceNote.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        val result = state.result
        if (result != null) {
            item {
                Column {
                    SectionLabel(text = "Your mix")
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
        }
    }
}
