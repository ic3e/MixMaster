package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SectionLabel

@Composable
fun AddEditProductScreen(navController: NavHostController, productId: Long?) {
    val container = LocalAppContainer.current
    val viewModel: AddEditProductViewModel = viewModel(
        factory = viewModelFactory { initializer { AddEditProductViewModel(container.productRepository, productId) } },
    )
    val state by viewModel.formState.collectAsState()
    if (!state.isLoaded) return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MixMasterTopBar(
                title = if (productId == null) "Add product" else "Edit product",
                onBack = { navController.popBackStack() },
            )
        }
        item {
            OutlinedTextField(value = state.brand, onValueChange = viewModel::setBrand, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = state.name, onValueChange = viewModel::setName, label = { Text("Product name") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = state.category, onValueChange = viewModel::setCategory, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            DropdownField(
                label = "Dosing mode",
                selected = state.dosingMode.name,
                options = DosingMode.entries.map { it.name },
                onSelect = { name -> viewModel.setDosingMode(DosingMode.valueOf(name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.doseGramsPerM2Text,
                onValueChange = viewModel::setDose,
                label = { Text("Typical dose (grams per m²)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.doseUnitLabel,
                onValueChange = viewModel::setDoseUnitLabel,
                label = { Text("Dose unit label (e.g. \"per coat\")") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(value = state.rangeNote, onValueChange = viewModel::setRangeNote, label = { Text("Range note (shown on the card)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = state.sourceNote, onValueChange = viewModel::setSourceNote, label = { Text("Source note") }, modifier = Modifier.fillMaxWidth())
        }

        item { SectionLabel(text = "Mix components (by ratio)") }

        items(state.components.size) { index ->
            val row = state.components[index]
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.label,
                    onValueChange = { viewModel.setComponentLabel(index, it) },
                    label = { Text("Label") },
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = row.ratioText,
                    onValueChange = { viewModel.setComponentRatio(index, it) },
                    label = { Text("Ratio") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeComponentRow(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        }

        item {
            OutlinedButton(onClick = { viewModel.addComponentRow() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(text = "Add another part", modifier = Modifier.padding(start = 8.dp))
            }
        }

        item {
            Button(
                onClick = { viewModel.save { navController.popBackStack() } },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Save product")
            }
        }
    }
}
