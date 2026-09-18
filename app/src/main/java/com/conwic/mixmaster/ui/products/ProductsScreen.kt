package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.navigation.Routes

@Composable
fun ProductsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: ProductsViewModel = viewModel(
        factory = viewModelFactory { initializer { ProductsViewModel(container.productRepository) } },
    )
    val state by viewModel.uiState.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)

    Scaffold(
        floatingActionButton = {
            if (role == Role.EMPLOYER) {
                FloatingActionButton(onClick = { navController.navigate(Routes.PRODUCT_ADD) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add product")
                }
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(text = "Products", style = MaterialTheme.typography.headlineMedium) }

            if (role == Role.WORKER) {
                item {
                    CardFlat {
                        Text(
                            text = "Viewing as Worker — this library is set up by your office. You can use these products in the calculator, but can't add, edit or remove them.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                ChipRow(
                    options = state.brands.map { brand ->
                        ChipOption(label = brand, selected = brand == state.brandFilter, onClick = { viewModel.setBrandFilter(brand) })
                    },
                )
            }
            item {
                ChipRow(
                    options = state.categories.map { category ->
                        ChipOption(label = category, selected = category == state.categoryFilter, onClick = { viewModel.setCategoryFilter(category) })
                    },
                )
            }

            items(state.visibleProducts) { product ->
                CardFlat(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate(Routes.productDetail(product.id)) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = product.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = product.rangeNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (product.ratioLabel.isNotBlank()) {
                            Text(
                                text = product.ratioLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .align(Alignment.Top)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
